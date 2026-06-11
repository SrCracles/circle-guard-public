#!/bin/bash
# OWASP ZAP baseline scans for CircleGuard stage environment (runs inside ZAP Docker image).
# Targets: auth-service, form-service, gateway-service (HU-22).

set -euo pipefail

# /tmp is used because the pod overrides the image entrypoint with `sleep`;
# in that mode /zap/wrk is not created by the ZAP image startup.
REPORT_DIR="/tmp/zap-reports"
SCAN_MINUTES="${ZAP_SCAN_MINUTES:-5}"

mkdir -p "${REPORT_DIR}"

TARGETS=(
  "auth|http://circleguard-auth-service:8180"
  "form|http://circleguard-form-service:8086"
  "gateway|http://circleguard-gateway-service:8087"
)

for entry in "${TARGETS[@]}"; do
  name="${entry%%|*}"
  url="${entry#*|}"

  echo "=========================================="
  echo " OWASP ZAP baseline scan: ${name} -> ${url}"
  echo "=========================================="

  # -I: do not fail on ZAP warn/fail (pipeline evaluates HIGH/CRITICAL separately)
  # -m: time limit per target to avoid hanging the stage pipeline
  zap-baseline.py \
    -t "${url}" \
    -r "${REPORT_DIR}/zap-${name}-report.html" \
    -J "${REPORT_DIR}/zap-${name}-report.json" \
    -x "${REPORT_DIR}/zap-${name}-report.xml" \
    -m "${SCAN_MINUTES}" \
    -I \
    || echo "WARN: ZAP scan for ${name} returned non-zero (continuing)"
done

cat > "${REPORT_DIR}/zap-consolidated-report.html" << 'EOF'
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>CircleGuard OWASP ZAP Security Reports</title>
  <style>
    body { font-family: Arial, sans-serif; margin: 2rem; }
    h1 { color: #2c3e50; }
    ul { line-height: 1.8; }
  </style>
</head>
<body>
  <h1>CircleGuard OWASP ZAP Security Scan Results</h1>
  <p>Baseline scans executed against stage microservices (auth, form, gateway).</p>
  <ul>
    <li><a href="zap-auth-report.html">Auth Service Report</a></li>
    <li><a href="zap-form-report.html">Form Service Report</a></li>
    <li><a href="zap-gateway-report.html">Gateway Service Report</a></li>
  </ul>
</body>
</html>
EOF

echo "ZAP scans completed. Reports:"
ls -la "${REPORT_DIR}"
