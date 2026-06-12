param(
    [string]$ClusterName = "circleguard-cluster",
    [string]$OutputPath = (Join-Path $PSScriptRoot "..\jenkins-kubeconfig-rbac.yaml"),
    [string]$SourceKubeconfigPath = (Join-Path $PSScriptRoot "..\kind-kubeconfig.yaml"),
    [string]$ServiceAccount = "jenkins-deployer",
    [string]$ServiceAccountNamespace = "kube-system",
    [int]$TokenDurationHours = 8760
)

$ErrorActionPreference = "Stop"

$contextName = "circleguard-jenkins-rbac"
$kubectlSource = @()

if (Test-Path $SourceKubeconfigPath) {
    $kubectlSource = @("--kubeconfig", $SourceKubeconfigPath)
} else {
    Write-Warning "Source kubeconfig not found at $SourceKubeconfigPath. Using current kubectl context (admin)."
}

$clusterEndpoint = & kubectl @kubectlSource config view --minify -o jsonpath='{.clusters[0].cluster.server}'
$caData = & kubectl @kubectlSource config view --raw --minify -o jsonpath='{.clusters[0].cluster.certificate-authority-data}'

if (-not $clusterEndpoint -or -not $caData) {
    Write-Error "Could not read cluster endpoint or CA from the source kubeconfig or current kubectl context."
    exit 1
}

$token = & kubectl @kubectlSource create token $ServiceAccount -n $ServiceAccountNamespace --duration="${TokenDurationHours}h"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to create token for ${ServiceAccountNamespace}/${ServiceAccount}. Run: kubectl apply -k k8s/rbac/"
    exit 1
}

$kubeconfig = @"
apiVersion: v1
kind: Config
clusters:
  - name: $ClusterName
    cluster:
      server: $clusterEndpoint
      certificate-authority-data: $caData
contexts:
  - name: $contextName
    context:
      cluster: $ClusterName
      user: $ServiceAccount
      namespace: dev
current-context: $contextName
users:
  - name: $ServiceAccount
    user:
      token: $token
"@

# UTF-8 sin BOM: Out-File -Encoding utf8 en Windows agrega BOM y kubectl ignora el archivo (usa admin por defecto)
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
$outputDir = Split-Path $resolvedOutput -Parent
if (-not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
}
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($resolvedOutput, $kubeconfig, $utf8NoBom)

Write-Host "Jenkins RBAC kubeconfig written to: $resolvedOutput"
Write-Host ""
Write-Host "Verifying RBAC (must match expected values):"

function Test-RbacCanI {
    param([string]$Verb, [string]$Resource, [string]$Namespace, [string]$Expected)
    $result = & kubectl --kubeconfig $resolvedOutput auth can-i $Verb $Resource -n $Namespace 2>&1
  $ok = ($result -eq $Expected)
    $status = if ($ok) { "OK" } else { "FAIL" }
    Write-Host ("  [{0}] can-i {1} {2} -n {3} => {4} (expected {5})" -f $status, $Verb, $Resource, $Namespace, $result, $Expected)
    return $ok
}

$checks = @(
    (Test-RbacCanI -Verb "create" -Resource "deployments" -Namespace "master" -Expected "yes"),
    (Test-RbacCanI -Verb "create" -Resource "deployments" -Namespace "infra" -Expected "no"),
    (Test-RbacCanI -Verb "get" -Resource "secrets" -Namespace "infra" -Expected "no"),
    (Test-RbacCanI -Verb "list" -Resource "secrets" -Namespace "infra" -Expected "no")
)

if ($checks -contains $false) {
    Write-Warning "RBAC verification failed. Re-apply manifests and regenerate:"
    Write-Warning "  kubectl apply -k k8s/rbac/"
    Write-Warning "  .\scripts\setup-jenkins-kubeconfig.ps1"
    exit 1
}

Write-Host ""
Write-Host "Set KUBECONFIG in Jenkins to this file (use absolute path recommended)."
