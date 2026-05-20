#Requires -Version 7.0

<#
.SYNOPSIS
    Setup script for CircleGuard local development environment.

.DESCRIPTION
    This script automates the creation of a Kind Kubernetes cluster,
    creates the required namespaces, and deploys infrastructure services.
    Run this before executing Jenkins pipelines.

.EXAMPLE
    .\setup-kind.ps1

.EXAMPLE
    .\setup-kind.ps1 -InfraNamespace infra -SkipInfra
#>

[CmdletBinding()]
param(
    [string]$ClusterName = "circleguard-cluster",
    [string]$InfraNamespace = "infra",
    [switch]$SkipInfra
)

$ErrorActionPreference = "Stop"

# ───────────────────────────────────────────────
# Colors
# ───────────────────────────────────────────────
function Write-Info    { param([string]$msg) Write-Host "[INFO]    $msg" -ForegroundColor Cyan }
function Write-Success { param([string]$msg) Write-Host "[OK]      $msg" -ForegroundColor Green }
function Write-Warning { param([string]$msg) Write-Host "[WARN]    $msg" -ForegroundColor Yellow }
function Write-Error   { param([string]$msg) Write-Host "[ERROR]   $msg" -ForegroundColor Red }

# ───────────────────────────────────────────────
# 1. Check Prerequisites
# ───────────────────────────────────────────────
Write-Info "Checking prerequisites..."

function Test-Command {
    param([string]$Command, [string]$Name)
    try {
        $null = Get-Command $Command -ErrorAction Stop
        Write-Success "$Name found"
        return $true
    } catch {
        Write-Error "$Name not found. Please install it."
        return $false
    }
}

$hasDocker = Test-Command "docker" "Docker"
$hasKind   = Test-Command "kind"   "Kind"
$hasKubectl = Test-Command "kubectl" "kubectl"

if (-not ($hasDocker -and $hasKind -and $hasKubectl)) {
    Write-Error "Missing prerequisites. Install Docker, Kind and kubectl first."
    exit 1
}

# Verify Docker is running
try {
    $dockerInfo = docker info 2>$null
    if ($LASTEXITCODE -ne 0) { throw "Docker not running" }
    Write-Success "Docker daemon is running"
} catch {
    Write-Error "Docker daemon is not running. Start Docker Desktop first."
    exit 1
}

# ───────────────────────────────────────────────
# 2. Create Kind Cluster
# ───────────────────────────────────────────────
Write-Info "Checking Kind cluster '$ClusterName'..."

$existingCluster = kind get clusters 2>$null | Select-String $ClusterName

if ($existingCluster) {
    Write-Warning "Cluster '$ClusterName' already exists. Skipping creation."
} else {
    Write-Info "Creating Kind cluster '$ClusterName'..."

    $kindConfig = @"
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: $ClusterName
nodes:
  - role: control-plane
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
    extraPortMappings:
      - containerPort: 80
        hostPort: 80
        protocol: TCP
      - containerPort: 443
        hostPort: 443
        protocol: TCP
      - containerPort: 30080
        hostPort: 30080
        protocol: TCP
      - containerPort: 30443
        hostPort: 30443
        protocol: TCP
  - role: worker
    extraPortMappings:
      - containerPort: 30180
        hostPort: 30180
        protocol: TCP
"@

    $tempConfig = [System.IO.Path]::GetTempFileName() + ".yaml"
    $kindConfig | Out-File -FilePath $tempConfig -Encoding utf8

    try {
        kind create cluster --config $tempConfig
        if ($LASTEXITCODE -ne 0) { throw "kind create cluster failed" }
        Write-Success "Kind cluster '$ClusterName' created"
    } finally {
        Remove-Item $tempConfig -ErrorAction SilentlyContinue
    }
}

# Verify cluster is accessible
kubectl cluster-info | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Error "Cannot connect to Kind cluster. Check kubectl context."
    exit 1
}
Write-Success "kubectl connected to cluster"

# ───────────────────────────────────────────────
# 3. Create Namespaces
# ───────────────────────────────────────────────
Write-Info "Creating namespaces..."

$namespaces = @("dev", "stage", "master", $InfraNamespace)

foreach ($ns in $namespaces) {
    $exists = kubectl get namespace $ns 2>$null
    if ($exists) {
        Write-Warning "Namespace '$ns' already exists"
    } else {
        kubectl create namespace $ns | Out-Null
        Write-Success "Namespace '$ns' created"
    }
}

# ───────────────────────────────────────────────
# 4. Deploy Infrastructure
# ───────────────────────────────────────────────
if (-not $SkipInfra) {
    Write-Info "Deploying infrastructure services to namespace '$InfraNamespace'..."

    if (Test-Path "k8s/infra/kustomization.yaml") {
        kubectl apply -k k8s/infra/ -n $InfraNamespace
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Failed to deploy infrastructure. Check k8s/infra/ manifests."
            exit 1
        }
        Write-Success "Infrastructure manifests applied"

        Write-Info "Waiting for infrastructure pods to be ready..."
        kubectl wait --for=condition=ready pod -l component=infrastructure -n $InfraNamespace --timeout=300s
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "Some infrastructure pods did not become ready within timeout"
        } else {
            Write-Success "All infrastructure pods are ready"
        }
    } else {
        Write-Warning "k8s/infra/kustomization.yaml not found. Skipping infrastructure deployment."
    }
} else {
    Write-Warning "Skipping infrastructure deployment (-SkipInfra flag set)"
}

# ───────────────────────────────────────────────
# 5. Verify Setup
# ───────────────────────────────────────────────
Write-Info "Verifying setup..."

Write-Host "`n=== Namespaces ===" -ForegroundColor Yellow
kubectl get namespaces

Write-Host "`n=== Infrastructure Pods ===" -ForegroundColor Yellow
kubectl get pods -n $InfraNamespace

Write-Host "`n=== Cluster Info ===" -ForegroundColor Yellow
kubectl cluster-info

Write-Host "`n=== Context ===" -ForegroundColor Yellow
kubectl config current-context

# ───────────────────────────────────────────────
# 6. Summary
# ───────────────────────────────────────────────
Write-Host "`n" -NoNewline
Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Green
Write-Host "  CircleGuard Kind Cluster Setup Complete!" -ForegroundColor Green
Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Green
Write-Host ""
Write-Host "Cluster:     $ClusterName"
Write-Host "Namespaces:  dev, stage, master, $InfraNamespace"
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1. Ensure Jenkins is running and configured"
Write-Host "  2. Add DockerHub credentials in Jenkins (ID: dockerhub-credentials)"
Write-Host "  3. Create Jenkins jobs pointing to the Jenkinsfiles in jenkins/"
Write-Host "  4. Run dev pipelines to build and deploy services"
Write-Host ""
Write-Host "Useful commands:" -ForegroundColor Cyan
Write-Host "  kubectl get pods -n dev"
Write-Host "  kubectl get pods -n stage"
Write-Host "  kubectl get pods -n master"
Write-Host "  kubectl get pods -n $InfraNamespace"
Write-Host "  kubectl logs -n <namespace> <pod-name>"
Write-Host ""
