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
    sendEmail(
        pipelineName: pipelineName,
        failedStage: failedStage,
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
    sendEmail(
        pipelineName: pipelineName,
        failedStage: 'N/A (recovered)',
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

def sendEmail(Map args) {
    def notifyEmail = env.CG_NOTIFY_EMAIL ?: ''
    if (!notifyEmail) {
        echo 'WARN (HU-17): Configure CG_NOTIFY_EMAIL in Jenkins Global Properties to enable email notifications.'
        return
    }

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

return this
