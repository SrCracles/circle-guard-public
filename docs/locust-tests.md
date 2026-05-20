# Pruebas de Carga con Locust

## Archivo

`tests/locustfile.py`

## Que se prueba

El script simula usuarios reales interactuando con los microservicios de CircleGuard. Cada usuario virtual se autentica al inicio y luego ejecuta acciones con distinta frecuencia:

| Accion | Frecuencia | Descripcion |
|--------|------------|-------------|
| Login | 3x | Inicio de sesion con credenciales validas |
| Generar QR | 2x | Generacion de token QR con JWT valido |
| Enviar survey | 1x | Envio de formulario de salud |
| Validar QR | 1x | Validacion de token en el gateway |
| Login invalido | 1x | Intento de login fallido |

## Escenarios de Prueba

### 1. Performance Test (Carga Normal)

- **Usuarios:** 20
- **Ramp-up:** 2 usuarios/segundo
- **Duracion:** 60 segundos (1 minuto)
- **Objetivo:** Medir comportamiento bajo carga representativa del dia a dia

### 2. Stress Test (Carga Alta)

- **Usuarios:** 50
- **Ramp-up:** 5 usuarios/segundo
- **Duracion:** 60 segundos (1 minuto)
- **Objetivo:** Detectar punto de degradacion y comportamiento bajo presion

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

## Métricas clave

- **Response Time:** Tiempo de respuesta promedio y percentiles (p50, p95, p99)
- **RPS:** Requests per second (peticiones por segundo)
- **Failure Rate:** Porcentaje de peticiones fallidas
- **User Count:** Numero de usuarios activos en cada momento
