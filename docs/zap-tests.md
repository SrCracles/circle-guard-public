# Pruebas de Seguridad con OWASP ZAP (HU-22)

## Objetivo

Ejecutar escaneos automatizados de seguridad con **OWASP ZAP** contra los endpoints expuestos en el ambiente **stage**, detectando vulnerabilidades del OWASP Top 10 (inyección SQL, XSS, autenticación rota, exposición de datos sensibles, etc.).

## Servicios escaneados

| Servicio | URL interna (namespace `stage`) | Endpoints representativos |
|----------|--------------------------------|---------------------------|
| **auth-service** | `http://circleguard-auth-service:8180` | `/api/v1/auth/login`, `/api/v1/auth/qr/generate` |
| **form-service** | `http://circleguard-form-service:8086` | `/api/v1/surveys`, `/api/v1/questionnaires` |
| **gateway-service** | `http://circleguard-gateway-service:8087` | `/api/v1/gate/validate` |

## Archivos

| Archivo | Propósito |
|---------|-----------|
| `tests/zap/run-zap-scans.sh` | Script que ejecuta `zap-baseline.py` contra los 3 servicios |
| `jenkins/stage/Jenkinsfile-stage` | Stage `Security Scan - OWASP ZAP` en el pipeline de stage |
| `jenkins/jenkins.properties` | Ruta del script (`ZAP_SCAN_SCRIPT`) |

## Modo de ejecución

- **Imagen Docker:** `ghcr.io/zaproxy/zaproxy:stable`
- **Herramienta:** `zap-baseline.py` (spider + reglas pasivas, recomendado para CI/CD)
- **Ubicación:** Pod temporal en el namespace `stage` (misma red que los microservicios)

## Reportes generados

Por cada servicio:

| Archivo | Formato |
|---------|---------|
| `zap-reports/zap-auth-report.html` | HTML |
| `zap-reports/zap-auth-report.xml` | XML |
| `zap-reports/zap-auth-report.json` | JSON |
| `zap-reports/zap-form-report.*` | HTML / XML / JSON |
| `zap-reports/zap-gateway-report.*` | HTML / XML / JSON |
| `zap-reports/zap-consolidated-report.html` | Índice HTML con enlaces a los 3 reportes |
| `ZAP_SECURITY_REPORT.md` | Resumen consolidado (generado por Jenkins) |

Los reportes se publican como **artefactos de Jenkins** (`archiveArtifacts`).

## Política de fallo del pipeline

| Riesgo | Acción |
|--------|--------|
| **CRITICAL** | Falla el pipeline |
| **HIGH** | Falla el pipeline |
| **MEDIUM** | Se documenta en `ZAP_SECURITY_REPORT.md`; no bloquea |
| **LOW** | Se documenta en `ZAP_SECURITY_REPORT.md`; no bloquea |
| **Informational** | Solo aparece en reportes detallados |

## Ejecución local (opcional)

Con el cluster Kind levantado y los servicios en `stage`:

```powershell
kubectl run zap-scan --restart=Never --image=ghcr.io/zaproxy/zaproxy:stable -n stage --command -- sleep 3600
kubectl wait --for=condition=ready pod/zap-scan -n stage --timeout=120s
kubectl exec zap-scan -n stage -- mkdir -p /tmp/zap-reports
kubectl cp tests/zap/run-zap-scans.sh zap-scan:/tmp/run-zap-scans.sh -n stage
kubectl exec zap-scan -n stage -- chmod +x /tmp/run-zap-scans.sh
kubectl exec zap-scan -n stage -- /tmp/run-zap-scans.sh
kubectl cp zap-scan:/tmp/zap-reports/. zap-reports/ -n stage
kubectl delete pod zap-scan -n stage
```

## Orden en el pipeline stage

1. Deploy to Stage
2. E2E Tests (Newman)
3. **Security Scan (OWASP ZAP)** ← HU-22
4. Performance Test (Locust)
5. Stress Test (Locust)
6. Promote dev → stage
