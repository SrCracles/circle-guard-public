param(
    [string]$ClusterName = "circleguard-cluster",
    [string]$InfraNamespace = "infra",
    [switch]$SkipInfra
)

$ErrorActionPreference = "Stop"

# Check prerequisites
Write-Host "Checking prerequisites..."

try { $null = Get-Command "docker" -ErrorAction Stop }
catch { Write-Error "Docker not found. Please install Docker Desktop."; exit 1 }

try { $null = Get-Command "kind" -ErrorAction Stop }
catch { Write-Error "Kind not found. Please install Kind."; exit 1 }

try { $null = Get-Command "kubectl" -ErrorAction Stop }
catch { Write-Error "kubectl not found. Please install kubectl."; exit 1 }

try { $null = Get-Command "trivy" -ErrorAction Stop }
catch { Write-Error "Trivy not found. Please install Trivy."; exit 1 }

try { docker info | Out-Null }
catch { Write-Error "Docker daemon is not running. Start Docker Desktop first."; exit 1 }

Write-Host "All prerequisites OK."

# Create Kind cluster
Write-Host "Checking Kind cluster '$ClusterName'..."

$existingCluster = kind get clusters 2>$null | Select-String $ClusterName

if ($existingCluster) {
    Write-Host "Cluster '$ClusterName' already exists. Skipping creation."
} else {
    Write-Host "Creating Kind cluster '$ClusterName'..."

    $kindConfig = @"
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: $ClusterName
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 80
        hostPort: 80
        protocol: TCP
      - containerPort: 443
        hostPort: 443
        protocol: TCP
      - containerPort: 30090
        hostPort: 9000
        protocol: TCP
      - containerPort: 30091
        hostPort: 9090
        protocol: TCP
      - containerPort: 30300
        hostPort: 3000
        protocol: TCP
      - containerPort: 30561
        hostPort: 5601
        protocol: TCP
      - containerPort: 31686
        hostPort: 16686
        protocol: TCP
  - role: worker
"@

    $tempConfig = [System.IO.Path]::GetTempFileName() + ".yaml"
    $kindConfig | Out-File -FilePath $tempConfig -Encoding utf8

    try {
        kind create cluster --config $tempConfig
        if ($LASTEXITCODE -ne 0) { throw "kind create cluster failed" }
        Write-Host "Kind cluster '$ClusterName' created."
    } finally {
        Remove-Item $tempConfig -ErrorAction SilentlyContinue
    }
}

kubectl cluster-info | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Error "Cannot connect to Kind cluster. Check kubectl context."
    exit 1
}
Write-Host "kubectl connected to cluster."

# Create Namespaces
Write-Host "Creating namespaces..."

$namespaces = @("dev", "stage", "master", $InfraNamespace)

foreach ($ns in $namespaces) {
    $exists = kubectl get namespace $ns 2>$null
    if ($exists) {
        Write-Host "Namespace '$ns' already exists."
    } else {
        kubectl create namespace $ns | Out-Null
        Write-Host "Namespace '$ns' created."
    }
}

# Deploy Infrastructure
if (-not $SkipInfra) {
    Write-Host "Deploying infrastructure services to namespace '$InfraNamespace'..."

    if (Test-Path "k8s/infra/kustomization.yaml") {
        $dashboardSrc = Join-Path $PSScriptRoot "docs\grafana-dashboards"
        $dashboardDst = Join-Path $PSScriptRoot "k8s\infra\grafana-dashboards"
        if (Test-Path $dashboardSrc) {
            New-Item -ItemType Directory -Force -Path $dashboardDst | Out-Null
            Copy-Item (Join-Path $dashboardSrc "*.json") $dashboardDst -Force
        }

        $kibanaSrc = Join-Path $PSScriptRoot "docs\kibana-dashboards"
        $kibanaDst = Join-Path $PSScriptRoot "k8s\infra\kibana-dashboards"
        if (Test-Path $kibanaSrc) {
            New-Item -ItemType Directory -Force -Path $kibanaDst | Out-Null
            Copy-Item (Join-Path $kibanaSrc "*.ndjson") $kibanaDst -Force
        }

        kubectl apply -k k8s/infra/ -n $InfraNamespace
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Failed to deploy infrastructure. Check k8s/infra/ manifests."
            exit 1
        }
        Write-Host "Infrastructure manifests applied."

        Write-Host "Waiting for infrastructure pods to be ready..."
        kubectl wait --for=condition=ready pod -l component=infrastructure -n $InfraNamespace --timeout=300s
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "Some infrastructure pods did not become ready within timeout."
        } else {
            Write-Host "All infrastructure pods are ready."
        }
    } else {
        Write-Warning "k8s/infra/kustomization.yaml not found. Skipping infrastructure deployment."
    }
} else {
    Write-Warning "Skipping infrastructure deployment (-SkipInfra flag set)."
}

# Verify Setup
Write-Host "Verifying setup..."

Write-Host ""
Write-Host "=== Namespaces ==="
kubectl get namespaces

Write-Host ""
Write-Host "=== Infrastructure Pods ==="
kubectl get pods -n $InfraNamespace

Write-Host ""
Write-Host "=== Cluster Info ==="
kubectl cluster-info

Write-Host ""
Write-Host "=== Context ==="
kubectl config current-context

# Export Kubeconfig for Jenkins
Write-Host "Exporting kubeconfig for Jenkins..."

$kubeconfigPath = "$PSScriptRoot\kind-kubeconfig.yaml"
kind export kubeconfig --name $ClusterName --kubeconfig $kubeconfigPath | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host "Kubeconfig exported to: $kubeconfigPath"
} else {
    Write-Warning "Failed to export kubeconfig. Run 'kind export kubeconfig --name $ClusterName' manually."
}

# Summary
Write-Host ""
Write-Host "========================================"
Write-Host "  CircleGuard Setup Complete"
Write-Host "========================================"
Write-Host ""
Write-Host "Cluster:     $ClusterName"
Write-Host "Namespaces:  dev, stage, master, $InfraNamespace"
Write-Host "Kubeconfig:  $kubeconfigPath"
Write-Host ""
Write-Host "Next steps:"
Write-Host "  1. Ensure Jenkins is running and configured"
Write-Host "  2. Add DockerHub credentials in Jenkins (ID: dockerhub-credentials)"
Write-Host "  3. Set KUBECONFIG environment variable in Jenkins to:"
Write-Host "       $kubeconfigPath"
Write-Host "  4. Create Jenkins jobs pointing to the Jenkinsfiles in jenkins/"
Write-Host "  5. Run dev pipelines to build and deploy services"
Write-Host "  6. Open Grafana at http://localhost:3000 (admin/admin) and Prometheus at http://localhost:9090"
Write-Host "  7. Open Kibana at http://localhost:5601 and Jaeger at http://localhost:16686"
Write-Host ""
Write-Host "IMPORTANT:"
Write-Host "  Every time you recreate the cluster, the kubeconfig changes."
Write-Host "  Re-run this script or update the KUBECONFIG path in Jenkins."
Write-Host ""
Write-Host "Useful commands:"
Write-Host "  kubectl get pods -n dev"
Write-Host "  kubectl get pods -n stage"
Write-Host "  kubectl get pods -n master"
Write-Host "  kubectl get pods -n $InfraNamespace"
Write-Host "  kubectl logs -n <namespace> <pod-name>"
Write-Host ""
