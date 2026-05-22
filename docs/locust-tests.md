# Pruebas de Carga con Locust

## Archivos

| Archivo | Proposito |
|---------|-----------|
| `tests/locustfile-performance.py` | Pruebas de rendimiento (carga normal) |
| `tests/locustfile-stress.py` | Pruebas de estres (carga extrema) |

Ambos archivos comparten la misma logica de negocio pero estan diseñados para escenarios distintos.

## Que se prueba

Cada usuario virtual se autentica al inicio y luego ejecuta acciones con distinta frecuencia:

| Accion | Frecuencia | Descripcion |
|--------|------------|-------------|
| Login | 3x | Inicio de sesion con credenciales validas |
| Generar QR | 2x | Generacion de token QR con JWT valido |
| Enviar survey | 1x | Envio de formulario de salud |
| Validar QR | 1x | Validacion de token en el gateway |
| Login invalido | 1x | Intento de login fallido (401 esperado) |

> **Nota sobre login invalido:** El intento de login con credenciales incorrectas devuelve HTTP 401. Esto es el comportamiento esperado del sistema. Se usa `catch_response=True` para marcar el 401 como exito y que no contamine la tasa de fallos.

## Escenarios de Prueba

### 1. Performance Test (Carga Normal)

- **Objetivo:** Evaluar comportamiento bajo carga representativa del dia a dia
- **Mide:** Tiempos de respuesta, throughput (RPS), uso de recursos, estabilidad
- **Usuarios:** 20
- **Ramp-up:** 2 usuarios/segundo
- **Duracion:** 60 segundos
- **Wait time:** 1-3 segundos entre acciones

### 2. Stress Test (Carga Alta)

- **Objetivo:** Encontrar punto de ruptura, cuellos de botella extremos y comportamiento bajo presion
- **Mide:** Cuando degrada el rendimiento, cuando falla, como se recupera
- **Usuarios:** 50
- **Ramp-up:** 3 usuarios/segundo
- **Duracion:** 60 segundos
- **Wait time:** 0.5-2 segundos entre acciones (mas agresivo)

> **Nota:** Los valores son conservadores para no saturar el PC local durante las pruebas.

## Artifacts generados

Locust genera archivos CSV con prefijo indicado por `--csv=`:

| Archivo | Contenido |
|---------|-----------|
| `locust-performance_stats.csv` | Estadisticas agregadas del performance test |
| `locust-performance_failures.csv` | Peticiones fallidas del performance test |
| `locust-performance_exceptions.csv` | Excepciones del performance test |
| `locust-stress_stats.csv` | Estadisticas agregadas del stress test |
| `locust-stress_failures.csv` | Peticiones fallidas del stress test |
| `locust-stress_exceptions.csv` | Excepciones del stress test |
| `PERFORMANCE_REPORT.md` | Reporte consolidado generado por el pipeline de Jenkins |

## Metricas clave

- **Response Time:** Tiempo de respuesta promedio y percentiles (p50, p95, p99)
- **RPS:** Requests per second (peticiones por segundo)
- **Failure Rate:** Porcentaje de peticiones fallidas
- **User Count:** Numero de usuarios activos en cada momento

## Ejecucion local

```bash
# Performance test
locust -f tests/locustfile-performance.py --host http://localhost:8180 -u 20 -r 2 --run-time 60s --headless

# Stress test
locust -f tests/locustfile-stress.py --host http://localhost:8180 -u 50 -r 3 --run-time 60s --headless
```

## Ejecucion en Kubernetes (pipeline)

Los tests se ejecutan dentro de pods en el cluster Kind. Los servicios se alcanzan via nombres de servicio K8s:

- Auth: `http://circleguard-auth-service:8180`
- Form: `http://circleguard-form-service:8086`
- Gateway: `http://circleguard-gateway-service:8087`
