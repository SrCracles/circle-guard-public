# Logging centralizado (ELK) y Tracing distribuido (Jaeger) — HU-30, HU-32

CircleGuard centraliza logs de los 8 microservicios en un stack **ELK** (Elasticsearch + Logstash + Kibana) y exporta trazas distribuidas a **Jaeger** mediante **Micrometer Tracing**, permitiendo correlacionar logs y trazas por `traceId`.

## Componentes desplegados (namespace `infra`)

| Componente | Proposito | Acceso local (Kind) |
|------------|-----------|---------------------|
| **Elasticsearch** | Almacen de logs indexados | Interno (`:9200`) |
| **Logstash** | Ingesta TCP de logs JSON desde microservicios | Interno (`:5044`) |
| **Kibana** | Busqueda, index patterns y dashboards | `http://localhost:5601` |
| **Jaeger** | UI y collector de trazas distribuidas | `http://localhost:16686` |

Los puertos `5601` (Kibana) y `16686` (Jaeger) se mapean automaticamente al ejecutar `setup-kind.ps1`.

## Logging estructurado (HU-30)

Los 8 microservicios envian logs a Logstash via **LogstashTcpSocketAppender** (`logback-spring.xml` + `logstash-logback-encoder`).

### Campos JSON

| Campo | Descripcion |
|-------|-------------|
| `timestamp` | Marca de tiempo UTC del evento |
| `nivel` | Nivel de log (INFO, WARN, ERROR, etc.) |
| `servicio` | Nombre del microservicio (`spring.application.name`) |
| `traceId` | ID de traza distribuida (MDC de Micrometer Tracing) |
| `mensaje` | Texto del log |

### Flujo de logs

```
Microservicio (Logback JSON) --> Logstash :5044 --> Elasticsearch (circleguard-logs-*) --> Kibana
```

### Kibana

- **Index pattern:** `circleguard-logs-*` (campo de tiempo: `timestamp`)
- **Dashboard:** `CircleGuard - Logs Overview` con visualizacion de volumen de errores/warnings por servicio en el tiempo
- Los saved objects se importan automaticamente con el Job `kibana-setup` al desplegar `k8s/infra/`

### Buscar por traceId

En Kibana (Discover o busqueda global):

```
traceId: "<uuid-de-la-traza>"
```

Muestra todos los logs de los microservicios que participaron en el mismo request.

## Tracing distribuido (HU-32)

Los 8 microservicios exportan trazas a Jaeger via **Micrometer Tracing + Brave + Zipkin reporter**.

### Configuracion

| Variable / propiedad | Valor |
|---------------------|-------|
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | `1.0` (100% en dev/stage) |
| `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT` | `http://jaeger.infra.svc.cluster.local:9411/api/v2/spans` |
| `SPRING_KAFKA_TEMPLATE_OBSERVATION_ENABLED` | `true` |
| `SPRING_KAFKA_LISTENER_OBSERVATION_ENABLED` | `true` |

### Propagacion HTTP

Los clientes `RestTemplate` inyectados con `RestTemplateBuilder` propagan headers de traza (auth → identity, notification → auth, dashboard → promotion).

### Flujos validados

| Flujo | Trazas esperadas en Jaeger |
|-------|---------------------------|
| Login (auth → identity) | Spans HTTP encadenados en un mismo trace |
| E2E (auth → form → promotion → notification) | Trazas HTTP + spans Kafka enlazados |
| File-service (upload/download) | Trazas independientes por operacion de archivo |

### Correlacion logs ↔ trazas

1. Copiar el `traceId` desde Jaeger UI (columna Trace ID).
2. Buscar en Kibana: `traceId: "<id>"`.
3. Ver todos los logs de los servicios involucrados en el mismo request.

## Despliegue

El stack se instala junto con la infraestructura:

```powershell
.\setup-kind.ps1
```

Verificar:

```powershell
kubectl get pods -n infra -l 'app in (elasticsearch,logstash,kibana,jaeger)'
kubectl get job -n infra kibana-setup
kubectl port-forward -n infra svc/kibana 5601:5601
kubectl port-forward -n infra svc/jaeger 16686:16686
```

Con Kind + `setup-kind.ps1`, Kibana y Jaeger ya estan en `localhost:5601` y `localhost:16686`.

## Validacion rapida

```powershell
# Login E2E (genera traza auth -> identity)
curl -X POST http://localhost/auth/api/v1/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin"}'

# Buscar trazas en Jaeger: servicio circleguard-auth-service
# Abrir http://localhost:16686

# Verificar indices en Elasticsearch (desde un pod)
kubectl exec -n infra deploy/elasticsearch -- curl -s http://localhost:9200/_cat/indices?v

# Buscar logs por traceId en Kibana Discover
# Abrir http://localhost:5601 -> Discover -> circleguard-logs-*
```

## Archivos relevantes

| Ruta | Proposito |
|------|-----------|
| `k8s/infra/elasticsearch.yaml` | Deployment + Service Elasticsearch |
| `k8s/infra/logstash.yaml` | Pipeline Logstash + Service TCP 5044 |
| `k8s/infra/kibana.yaml` | Kibana NodePort |
| `k8s/infra/kibana-setup.yaml` | Job de importacion de saved objects |
| `k8s/infra/jaeger.yaml` | Jaeger all-in-one (UI + Zipkin collector) |
| `k8s/base/configmap.yaml` | Variables LOGSTASH_* y MANAGEMENT_TRACING_* |
| `docs/kibana-dashboards/circleguard-saved-objects.ndjson` | Index pattern + dashboard Kibana |
| `services/*/src/main/resources/logback-spring.xml` | Appender JSON + Logstash en cada servicio |
| `build.gradle.kts` | Dependencias tracing + logstash-logback-encoder |
