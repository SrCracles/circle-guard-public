param(
    [string]$KubeconfigPath = (Join-Path $PSScriptRoot "..\jenkins-kubeconfig-rbac.yaml"),
    [string]$AppNamespace = "master"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $KubeconfigPath)) {
    Write-Error "Kubeconfig not found: $KubeconfigPath. Run .\scripts\setup-jenkins-kubeconfig.ps1 first."
    exit 1
}

$resolvedKubeconfig = (Resolve-Path $KubeconfigPath).Path
Write-Host "Using kubeconfig: $resolvedKubeconfig"
Write-Host ""

function Assert-CanI {
    param([string]$Kubeconfig, [string]$Verb, [string]$Resource, [string]$Namespace, [string]$Expected)
    $result = kubectl --kubeconfig $Kubeconfig auth can-i $Verb $Resource -n $Namespace 2>&1
    if ($result -ne $Expected) {
        Write-Host "FAIL  can-i $Verb $Resource -n $Namespace => $result (expected $Expected)" -ForegroundColor Red
        return $false
    }
    Write-Host "OK    can-i $Verb $Resource -n $Namespace => $result"
    return $true
}

function Assert-Kubectl {
    param([string]$Kubeconfig, [string[]]$Args, [string]$ExpectForbidden)
    $output = & kubectl --kubeconfig $Kubeconfig @Args 2>&1 | Out-String
    $isForbidden = $output -match "Forbidden|cannot|not authorized"
    if ($ExpectForbidden -eq "yes" -and -not $isForbidden) {
        Write-Host "FAIL  kubectl $($Args -join ' ') => not forbidden`n$output" -ForegroundColor Red
        return $false
    }
    if ($ExpectForbidden -eq "no" -and $isForbidden) {
        Write-Host "FAIL  kubectl $($Args -join ' ') => unexpected forbidden`n$output" -ForegroundColor Red
        return $false
    }
    Write-Host "OK    kubectl $($Args -join ' ')"
    return $true
}

$allOk = $true
$allOk = (Assert-CanI $resolvedKubeconfig "create" "deployments" "master" "yes") -and $allOk
$allOk = (Assert-CanI $resolvedKubeconfig "create" "deployments" "infra" "no") -and $allOk
$allOk = (Assert-CanI $resolvedKubeconfig "get" "secrets" "infra" "no") -and $allOk
$allOk = (Assert-CanI $resolvedKubeconfig "list" "secrets" "infra" "no") -and $allOk
$allOk = (Assert-Kubectl $resolvedKubeconfig @("get", "secrets", "-n", "infra") "yes") -and $allOk

Write-Host ""
Write-Host "--- Microservice SA (circleguard-auth-sa) must not read secrets in infra ---"
$microSa = "system:serviceaccount:${AppNamespace}:circleguard-auth-sa"
$allOk = (Assert-CanI $resolvedKubeconfig "get" "secrets" "infra" "no") -and $allOk
$microResult = kubectl --kubeconfig $resolvedKubeconfig auth can-i get secrets -n infra --as=$microSa 2>&1
if ($microResult -ne "no") {
    Write-Host "FAIL  can-i get secrets -n infra --as=$microSa => $microResult (expected no)" -ForegroundColor Red
    $allOk = $false
} else {
    Write-Host "OK    can-i get secrets -n infra --as=$microSa => no"
}

if (-not $allOk) {
    Write-Host ""
    Write-Host "RBAC verification failed. Run:" -ForegroundColor Yellow
    Write-Host "  kubectl apply -k k8s/rbac/"
    Write-Host "  kubectl apply -k k8s/$AppNamespace/"
    Write-Host "  .\scripts\setup-jenkins-kubeconfig.ps1"
    exit 1
}

Write-Host ""
Write-Host "All RBAC checks passed."
