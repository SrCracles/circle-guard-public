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

if (Test-Path $SourceKubeconfigPath) {
  $clusterEndpoint = kubectl --kubeconfig $SourceKubeconfigPath config view --minify -o jsonpath='{.clusters[0].cluster.server}'
  $caData = kubectl --kubeconfig $SourceKubeconfigPath config view --raw --minify -o jsonpath='{.clusters[0].cluster.certificate-authority-data}'
} else {
  $clusterEndpoint = kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}'
  $caData = kubectl config view --raw --minify -o jsonpath='{.clusters[0].cluster.certificate-authority-data}'
}

if (-not $clusterEndpoint -or -not $caData) {
  Write-Error "Could not read cluster endpoint or CA from the source kubeconfig or current kubectl context."
    exit 1
}

$token = kubectl create token $ServiceAccount -n $ServiceAccountNamespace --duration="${TokenDurationHours}h"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to create token for ${ServiceAccountNamespace}/${ServiceAccount}. Apply k8s/rbac/ first."
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

$kubeconfig | Out-File -FilePath $OutputPath -Encoding utf8

Write-Host "Jenkins RBAC kubeconfig written to: $OutputPath"
Write-Host "Source kubeconfig: $SourceKubeconfigPath"
Write-Host "Set KUBECONFIG in Jenkins to this file to enforce least-privilege deploy permissions."
Write-Host "Jenkins can deploy to dev, stage and master only (no infra namespace access)."
