# Monitoreo con Prometheus y Grafana (HU-28, HU-29, HU-31)

CircleGuard expone metricas tecnicas y de negocio mediante **Micrometer + Spring Boot Actuator**, las recolecta **Prometheus** en el namespace `infra` y las visualiza en **Grafana** con dashboards y alertas automaticas.

## Componentes desplegados (namespace `infra`)

| Componente | Proposito | Acceso local (Kind) |
|------------|-----------|---------------------|
| **Prometheus** | Scraping de metricas de microservicios, Locust, nodos y kube-state | `http://localhost:9090` |
| **Grafana** | Dashboards y alertas unificadas | `http://localhost:3000` (admin/admin) |
| **kube-state-metrics** | Estado de pods (CrashLoopBackOff, etc.) | Interno |
| **node-exporter** | Memoria y recursos de nodos | Interno |
| **alert-webhook** | Receptor webhook para notificaciones de alertas | Interno |

Los puertos `9090` y `3000` se mapean automaticamente al ejecutar `setup-kind.ps1`.

## Metricas tecnicas (HU-28)

Los 8 microservicios exponen `/actuator/prometheus` con:

- Latencia HTTP (`http_server_requests_seconds_*`)
- RPS (`rate(http_server_requests_seconds_count)`)
- Tasa de errores 5xx
- CPU de proceso (`process_cpu_usage`)
- Memoria JVM (`jvm_memory_used_bytes`)

Prometheus scrapea cada servicio en los namespaces `dev`, `stage` y `master` mediante `k8s/infra/prometheus.yaml`.

### Dashboards tecnicos

| Dashboard | UID | Archivo versionado |
|-----------|-----|-------------------|
| Vision general del sistema | `cg-system-overview` | `docs/grafana-dashboards/circleguard-system-overview.json` |
| Detalle por servicio | `cg-service-detail` | `docs/grafana-dashboards/circleguard-service-detail.json` |

Paneles incluidos: RPS, latencia promedio/p95, tasa de errores HTTP, CPU y memoria JVM. El dashboard de overview incluye metricas de **Locust** tras ejecutar el pipeline stage.

## Metricas de negocio (HU-29)

Contadores Micrometer personalizados:

| Metrica Prometheus | Servicio | Descripcion |
|--------------------|----------|-------------|
| `circleguard_business_surveys_submitted_total` | form-service | Formularios de salud enviados |
| `circleguard_business_status_promoted_total{status="CONFIRMED\|SUSPECT"}` | promotion-service | Promociones de estado |
| `circleguard_business_campus_access_total{decision="GREEN\|RED"}` | gateway-service | Accesos al campus validados/rechazados |
| `circleguard_business_notifications_sent_total` | notification-service | Alertas de notificacion enviadas |

Dashboard: **CircleGuard - Business Metrics** (`cg-business-metrics`) en `docs/grafana-dashboards/circleguard-business-metrics.json`.

Rangos de tiempo ajustables desde Grafana: ultima hora, ultimo dia, etc.

## Metricas de Locust

Durante el stage **Performance Test - Locust** del pipeline stage:

1. Se despliega un pod con Locust + `locust-exporter` (`k8s/stage/locust-metrics.yaml`).
2. Locust expone su UI en el puerto 8089; el exporter publica metricas en `:9646`.
3. Prometheus scrapea `locust-exporter.stage.svc.cluster.local:9646`.
4. Los datos quedan retenidos 7 dias en Prometheus y son visibles en el dashboard de overview.

## Alertas automaticas (HU-31)

Grafana Unified Alerting provisiona 4 reglas en la carpeta **CircleGuard Alerts**:

| Alerta | Umbral | Severidad |
|--------|--------|-----------|
| HTTP error rate above 5% | > 5% respuestas 5xx por servicio | critical |
| P95 latency above 1000ms (auth/gateway) | p95 > 1000 ms | warning |
| Microservice pod in CrashLoopBackOff | Pod `circleguard-*` en CrashLoopBackOff | critical |
| Cluster memory available below 20% | Memoria disponible < 20% | warning |

Las alertas:

- Tienen mensaje descriptivo en `annotations.summary` y `annotations.description`.
- Se notifican via **webhook** a `alert-webhook.infra.svc.cluster.local:9000`.
- Vuelven a estado normal automaticamente cuando la condicion se resuelve (`for` + evaluacion continua).

Verificar entregas de alertas:

```powershell
kubectl logs -n infra -l app=alert-webhook -f
```

## Despliegue

El stack de monitoreo se instala junto con la infraestructura al ejecutar:

```powershell
.\setup-kind.ps1
```

Verificar:

```powershell
kubectl get pods -n infra -l 'app in (prometheus,grafana,kube-state-metrics,node-exporter,alert-webhook)'
kubectl port-forward -n infra svc/grafana 3000:3000
kubectl port-forward -n infra svc/prometheus 9090:9090
```

## Validacion rapida

```powershell
# Endpoint Prometheus de un microservicio (desde dentro del cluster)
kubectl exec -n stage deploy/circleguard-auth-service -- wget -qO- http://localhost:8180/actuator/prometheus | Select-String circleguard_business

# Targets de Prometheus
curl http://localhost:9090/api/v1/targets
```

## Archivos relevantes

| Ruta | Proposito |
|------|-----------|
| `k8s/infra/prometheus.yaml` | Prometheus + scrape configs |
| `k8s/infra/grafana.yaml` | Grafana + provisioning de alertas |
| `k8s/infra/kube-state-metrics.yaml` | Metricas de estado K8s |
| `k8s/infra/node-exporter.yaml` | Metricas de nodos |
| `k8s/infra/alert-webhook.yaml` | Webhook receptor de alertas |
| `k8s/stage/locust-metrics.yaml` | Pod Locust + exporter para pruebas de carga |
| `docs/grafana-dashboards/*.json` | Dashboards versionados |
