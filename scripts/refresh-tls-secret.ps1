param(
    [string]$Namespace = "master",
    [string]$SecretName = "circleguard-tls",
    [int]$ValidDays = 365
)

$ErrorActionPreference = "Stop"

$generateScript = Join-Path $PSScriptRoot "generate-tls-cert.ps1"
& $generateScript -ValidDays $ValidDays

$certPath = Join-Path $PSScriptRoot "..\k8s\master\certs\tls.crt"
$keyPath = Join-Path $PSScriptRoot "..\k8s\master\certs\tls.key"

kubectl create secret tls $SecretName -n $Namespace --cert=$certPath --key=$keyPath --dry-run=client -o yaml | kubectl apply -f -

Write-Host "TLS secret '$SecretName' renewed in namespace '$Namespace'."
Write-Host "Ingress will pick up the new certificate automatically (no pod restart required)."
