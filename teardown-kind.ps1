param(
    [string]$ClusterName = "circleguard-cluster",
    [string]$InfraNamespace = "infra",
    [switch]$DeleteCluster,
    [switch]$CleanDocker,
    [switch]$Force
)

$ErrorActionPreference = "Stop"

# Confirmation
if (-not $Force) {
    Write-Warning "This will DELETE resources from your cluster."
    if ($DeleteCluster) {
        Write-Warning "The cluster '$ClusterName' will be COMPLETELY REMOVED."
    }
    $confirm = Read-Host "Are you sure? Type 'yes' to continue"
    if ($confirm -ne "yes") {
        Write-Host "Aborted. No changes were made."
        exit 0
    }
}

# Check prerequisites
try { $null = Get-Command "kubectl" -ErrorAction Stop }
catch { Write-Error "kubectl not found. Cannot proceed."; exit 1 }

try { $null = Get-Command "kind" -ErrorAction Stop }
catch { Write-Error "Kind not found. Cannot proceed."; exit 1 }

# Check if cluster exists
$clusterExists = kind get clusters 2>$null | Select-String $ClusterName
if (-not $clusterExists) {
    Write-Warning "Cluster '$ClusterName' does not exist."
    if (-not $CleanDocker) {
        exit 0
    }
}

# Delete Resources from Namespaces
$namespaces = @("dev", "stage", "master", $InfraNamespace)

foreach ($ns in $namespaces) {
    $nsExists = kubectl get namespace $ns 2>$null
    if ($nsExists) {
        Write-Host "Deleting all resources in namespace '$ns'..."

        kubectl delete deployment --all -n $ns --ignore-not-found=true | Out-Null
        kubectl delete service --all -n $ns --ignore-not-found=true | Out-Null
        kubectl delete configmap --all -n $ns --ignore-not-found=true | Out-Null
        kubectl delete secret --all -n $ns --ignore-not-found=true | Out-Null
        kubectl delete pod --all -n $ns --force --grace-period=0 --ignore-not-found=true 2>$null | Out-Null

        Write-Host "Resources deleted from namespace '$ns'."
    } else {
        Write-Warning "Namespace '$ns' does not exist."
    }
}

# Delete Namespaces (if cluster is not being deleted)
if (-not $DeleteCluster) {
    foreach ($ns in $namespaces) {
        $nsExists = kubectl get namespace $ns 2>$null
        if ($nsExists) {
            Write-Host "Deleting namespace '$ns'..."
            kubectl delete namespace $ns --ignore-not-found=true | Out-Null
            Write-Host "Namespace '$ns' deleted."
        }
    }
}

# Delete Kubeconfig File
$kubeconfigPath = "$PSScriptRoot\kind-kubeconfig.yaml"
if (Test-Path $kubeconfigPath) {
    Write-Host "Deleting exported kubeconfig..."
    Remove-Item $kubeconfigPath -Force
    Write-Host "Kubeconfig removed: $kubeconfigPath"
}

# Clean Docker Images (optional)
if ($CleanDocker) {
    Write-Host "Cleaning CircleGuard Docker images..."

    $images = docker images --format "{{.Repository}}:{{.Tag}}" | Select-String "srcracles/circleguard"
    if ($images) {
        foreach ($img in $images) {
            Write-Host "Removing image: $img"
            docker rmi $img --force 2>$null | Out-Null
        }
        Write-Host "Docker images cleaned."
    } else {
        Write-Warning "No CircleGuard images found."
    }
}

# Delete Kind Cluster
if ($DeleteCluster) {
    if ($clusterExists) {
        Write-Host "Deleting Kind cluster '$ClusterName'..."
        kind delete cluster --name $ClusterName
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Cluster '$ClusterName' deleted."
        } else {
            Write-Error "Failed to delete cluster '$ClusterName'."
        }
    }
}

# Summary
Write-Host ""
Write-Host "========================================"
Write-Host "  CircleGuard Teardown Complete"
Write-Host "========================================"
Write-Host ""
Write-Host "Actions performed:"
Write-Host "  - Deleted resources from namespaces: dev, stage, master, $InfraNamespace"
if (-not $DeleteCluster) {
    Write-Host "  - Deleted namespaces (cluster kept running)."
}
if ($CleanDocker) {
    Write-Host "  - Removed CircleGuard Docker images."
}
if ($DeleteCluster) {
    Write-Host "  - Deleted Kind cluster: $ClusterName"
} else {
    Write-Host "  - Cluster '$ClusterName' is still running."
    Write-Host "  - Run setup-kind.ps1 again to recreate namespaces and infra."
}
Write-Host ""
if (-not $DeleteCluster) {
    Write-Host "To recreate the environment:"
    Write-Host "  .\setup-kind.ps1"
}
Write-Host "To completely remove everything including the cluster:"
Write-Host "  .\teardown-kind.ps1 -DeleteCluster -CleanDocker"
Write-Host ""
