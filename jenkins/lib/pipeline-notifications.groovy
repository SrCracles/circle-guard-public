import groovy.json.JsonOutput
import org.jenkinsci.plugins.workflow.actions.ErrorAction
import org.jenkinsci.plugins.workflow.graph.FlowNode

def sendFailureNotification(Map config = [:]) {
    def pipelineName = config.pipelineName ?: env.JOB_NAME
    def buildUrl = env.BUILD_URL ?: 'N/A'
    def failedStage = resolveFailedStage()
    def commitSha = resolveGitCommit()
    def commitMsg = resolveGitCommitMessage()

    def message = """CircleGuard Pipeline FAILURE
Pipeline: ${pipelineName}
Build: #${env.BUILD_NUMBER}
Failed Stage: ${failedStage}
Commit: ${commitSha}
Commit Message: ${commitMsg}
Log: ${buildUrl}console"""

    echo message
    dispatchNotification(
        pipelineName: pipelineName,
        failedStage: failedStage,
        buildUrl: buildUrl,
        commitSha: commitSha,
        commitMsg: commitMsg,
        status: 'failure',
        plainBody: message
    )
}

def sendRecoveryNotification(Map config = [:]) {
    def previous = currentBuild.previousBuild
    if (previous == null || previous.result != 'FAILURE') {
        return
    }

    def pipelineName = config.pipelineName ?: env.JOB_NAME
    def buildUrl = env.BUILD_URL ?: 'N/A'
    def commitSha = resolveGitCommit()
    def commitMsg = resolveGitCommitMessage()

    def message = """CircleGuard Pipeline RECOVERED
Pipeline: ${pipelineName}
Build: #${env.BUILD_NUMBER}
Previous build #${previous.number} had failed. This build succeeded.
Commit: ${commitSha}
Commit Message: ${commitMsg}
Log: ${buildUrl}console"""

    echo message
    dispatchNotification(
        pipelineName: pipelineName,
        failedStage: 'N/A (recovered)',
        buildUrl: buildUrl,
        commitSha: commitSha,
        commitMsg: commitMsg,
        status: 'recovery',
        plainBody: message
    )
}

def resolveFailedStage() {
    try {
        def execution = currentBuild?.rawBuild?.getExecution()
        if (execution == null) {
            return env.STAGE_NAME ?: 'Unknown'
        }

        def failedStage = findFailedStageName(execution.getCurrentHead())
        if (failedStage) {
            return failedStage
        }

        for (FlowNode head : execution.getCurrentHeads()) {
            failedStage = findFailedStageName(head)
            if (failedStage) {
                return failedStage
            }
        }
    } catch (Exception ignored) {
        // Fall through to env.STAGE_NAME
    }
    return env.STAGE_NAME ?: 'Unknown'
}

def findFailedStageName(FlowNode node) {
    if (node == null) {
        return null
    }

    if (node.getAction(ErrorAction.class) != null) {
        def name = node.getDisplayName()
        if (name && !name.contains('Post Actions')) {
            return name
        }
    }

    for (FlowNode parent : node.getParents()) {
        def name = findFailedStageName(parent)
        if (name) {
            return name
        }
    }
    return null
}

def resolveGitCommit() {
    try {
        return bat(script: '@echo off && git rev-parse HEAD', returnStdout: true).trim()
    } catch (Exception ignored) {
        return env.GIT_COMMIT ?: 'N/A'
    }
}

def resolveGitCommitMessage() {
    try {
        return bat(script: '@echo off && git log -1 --pretty=format:%s', returnStdout: true).trim()
    } catch (Exception ignored) {
        return 'N/A'
    }
}

def dispatchNotification(Map args) {
    def webhookUrl = env.CG_NOTIFY_WEBHOOK_URL ?: ''
    def notifyEmail = env.CG_NOTIFY_EMAIL ?: ''

    if (webhookUrl) {
        sendWebhook(webhookUrl, args)
    }

    if (notifyEmail) {
        sendEmail(notifyEmail, args)
    }

    if (!webhookUrl && !notifyEmail) {
        echo 'WARN (HU-17): Configure CG_NOTIFY_WEBHOOK_URL and/or CG_NOTIFY_EMAIL in Jenkins Global Properties to enable notifications.'
    }
}

def sendWebhook(String webhookUrl, Map args) {
    def webhookType = (env.CG_NOTIFY_WEBHOOK_TYPE ?: detectWebhookType(webhookUrl)).toLowerCase()
    def payload

    if (webhookType == 'teams') {
        def color = args.status == 'failure' ? 'FF0000' : '00AA00'
        def title = args.status == 'failure' ? 'CircleGuard Pipeline FAILED' : 'CircleGuard Pipeline RECOVERED'
        payload = [
            '@type'      : 'MessageCard',
            '@context'   : 'http://schema.org/extensions',
            'summary'    : title,
            'themeColor' : color,
            'title'      : title,
            'sections'   : [[
                'facts': [
                    ['name': 'Pipeline', 'value': args.pipelineName],
                    ['name': 'Failed Stage', 'value': args.failedStage],
                    ['name': 'Commit', 'value': args.commitSha],
                    ['name': 'Commit Message', 'value': args.commitMsg],
                    ['name': 'Log', 'value': "${args.buildUrl}console"]
                ]
            ]]
        ]
    } else {
        def prefix = args.status == 'failure' ? 'FAILED' : 'RECOVERED'
        payload = [
            text: "*CircleGuard Pipeline ${prefix}*: ${args.pipelineName}",
            blocks: [[
                type: 'section',
                text: [type: 'mrkdwn', text: "*Pipeline:* ${args.pipelineName}\n*Stage:* ${args.failedStage}\n*Commit:* `${args.commitSha}`\n*Message:* ${args.commitMsg}\n*Log:* <${args.buildUrl}console|View Console>"]
            ]]
        ]
    }

    writeFile file: 'pipeline-notify-payload.json', text: JsonOutput.toJson(payload)
    def status = bat(
        script: "@echo off\ncurl -s -S -X POST -H \"Content-Type: application/json\" --data-binary @pipeline-notify-payload.json \"${webhookUrl}\"",
        returnStatus: true
    )
    if (status != 0) {
        echo "WARN (HU-17): Webhook notification returned non-zero exit code (${status})."
    }
}

def sendEmail(String notifyEmail, Map args) {
    def subjectPrefix = args.status == 'failure' ? 'FAILED' : 'RECOVERED'
    try {
        emailext(
            subject: "[CircleGuard] ${subjectPrefix}: ${args.pipelineName} - ${args.failedStage}",
            body: args.plainBody,
            to: notifyEmail,
            mimeType: 'text/plain'
        )
    } catch (Exception e) {
        echo "WARN (HU-17): Email Extension plugin not available or email failed: ${e.message}"
        try {
            mail(
                subject: "[CircleGuard] ${subjectPrefix}: ${args.pipelineName}",
                body: args.plainBody,
                to: notifyEmail
            )
        } catch (Exception mailError) {
            echo "WARN (HU-17): Built-in mail step also failed: ${mailError.message}"
        }
    }
}

def detectWebhookType(String webhookUrl) {
    if (webhookUrl.contains('office.com') || webhookUrl.contains('office365.com')) {
        return 'teams'
    }
    return 'slack'
}

return this
