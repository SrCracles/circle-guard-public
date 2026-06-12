param(
    [string]$CertDir = (Join-Path $PSScriptRoot "..\k8s\master\certs"),
    [string]$CommonName = "circleguard.local",
    [int]$ValidDays = 365
)

$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path $CertDir | Out-Null

$openssl = Get-Command openssl -ErrorAction SilentlyContinue
if (-not $openssl) {
    Write-Error "openssl not found in PATH. Install OpenSSL or Git for Windows (includes openssl.exe)."
    exit 1
}

$keyPath = Join-Path $CertDir "tls.key"
$crtPath = Join-Path $CertDir "tls.crt"
$cnfPath = Join-Path $CertDir "openssl.cnf"

$cnfContent = @"
[req]
default_bits = 2048
prompt = no
default_md = sha256
distinguished_name = dn
x509_extensions = v3_req

[dn]
CN = $CommonName
O = CircleGuard
OU = DevOps

[v3_req]
subjectAltName = @alt_names

[alt_names]
DNS.1 = circleguard.local
DNS.2 = localhost
IP.1 = 127.0.0.1
"@

$cnfContent | Out-File -FilePath $cnfPath -Encoding ascii

if (Test-Path $keyPath) { Remove-Item $keyPath -Force }
if (Test-Path $crtPath) { Remove-Item $crtPath -Force }

& openssl req -x509 -nodes -days $ValidDays -newkey rsa:2048 `
    -keyout $keyPath -out $crtPath -config $cnfPath -extensions v3_req

if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to generate TLS certificate."
    exit 1
}

Write-Host "TLS certificate generated:"
Write-Host "  Certificate: $crtPath"
Write-Host "  Private key: $keyPath"
Write-Host "  Valid for:   $ValidDays days"
Write-Host ""
Write-Host "Create or update the Kubernetes TLS secret in master:"
Write-Host "  kubectl create secret tls circleguard-tls -n master --cert=$crtPath --key=$keyPath --dry-run=client -o yaml | kubectl apply -f -"
