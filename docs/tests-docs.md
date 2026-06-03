# Documentación de Pruebas (Tests Docs)

Este documento centraliza todas las pruebas implementadas en el proyecto CircleGuard, organizadas por tipo de prueba y por microservicio. 

## 1. Pruebas Unitarias

Las pruebas unitarias validan la lógica interna de cada componente aislando sus dependencias mediante *mocks*.

### `circleguard-auth-service`
* `JwtTokenServiceTest`: Validar generación, expiración y parseo de tokens JWT.
* `DualChainAuthenticationProviderTest`: Probar la lógica de autenticación fallback (LDAP y Base de datos local).
* `LoginControllerTest`: Probar controlador de login de forma unitaria.

### `circleguard-identity-service`
* `IdentityVaultServiceTest`: Generación de IDs anonimizados únicos y uso de algoritmos criptográficos.
* `IdentityEncryptionConverterTest`: Validar encriptación y desencriptación de los IDs reales.
* `IdentityMappingRepositoryTest`: Testeo unitario del repositorio de persistencia.
* `IdentityVaultControllerTest`: Controladores REST de gestión de identidades.

### `circleguard-form-service`
* `SymptomMapperTest`: Verifica que los síntomas se mapen de forma correcta al nivel de riesgo respectivo.
* `HealthSurveyControllerTest`: Probar endpoints de envío de encuestas médicas.
* `AttachmentControllerTest`: Validar adjuntos de encuestas.
* `QuestionnaireControllerTest`: Operaciones CRUD y listado de cuestionarios.

### `circleguard-promotion-service`
* `StatusLifecycleTest`: Verifica que las transiciones de estado de salud (ACTIVE -> SUSPECT -> PROBABLE -> CONFIRMED) se hagan correctamente.
* `GraphServiceTest`: Prueba la creación de queries y la lógica de grafos en Neo4j.
* `HealthStatusServiceTest`: Servicios de evaluación de salud.
* `HealthStatusReevaluationTest`: Reevaluaciones asíncronas automáticas.
* `AdministrativeCorrectionTest`: Funcionalidades administrativas para corrección de estado.
* `FloorServiceTest`: Lógica para validar pisos o ubicaciones físicas.
* `SurveyListenerTest`: Recepción de encuestas.
* `HealthStatusControllerTest`: Controlador REST de estados.

### `circleguard-notification-service`
* `TemplateServiceTest`: Renderizado de templates (FreeMarker) inyectando variables.
* `NotificationDispatcherTest`: Gestión y despacho de notificaciones hacia los distintos canales (email, sms).
* `ExposureNotificationListenerTest`: Recepción de eventos de exposición de Kafka.
* `PriorityAlertListenerTest`: Recepción de notificaciones críticas.
* `NotificationRetryTest`: Lógica de reintentos ante caídas o fallos.
* `LmsServiceTest`: Interacción con el LMS (Blackboard/Moodle) del campus.
* `RoomReservationServiceTest`: Funcionalidad de cancelación o gestión de reservas.

### `circleguard-gateway-service`
* `QrValidationServiceTest` y `QrValidationServiceAdditionalTest`: Lógica criptográfica y de expiración de tokens QR.
* `GateControllerTest`: Operaciones REST del controlador de ingreso.

### `circleguard-file-service`
* `FileStorageServiceTest`: Lógica de subida al sistema de archivos local, verificando que genere UUIDs adecuados sin colisiones.
* `FileUploadControllerTest`: Lógica del endpoint de subida de archivos de manera unitaria.

### `circleguard-dashboard-service`
* `KAnonymityFilterTest`: Valida que las métricas sensibles y agrupaciones de pocos usuarios (K < 5) se camuflen correctamente.
* `AnalyticsServiceTest`: Prueba el aglomerado de las estadísticas sin depender de las bases de datos.

---

## 2. Pruebas de Integración

Estas pruebas validan la correcta comunicación entre dos o más componentes reales (como Kafka, Base de Datos, Redis o llamadas HTTP mediante RestTemplate/Feign).

* **`AuthIdentityIntegrationTest`** (`auth-service` -> `identity-service`): Verifica el mapeo de identidad anónima tras un login.
* **`FormToPromotionKafkaTest`** (`form-service` -> `Kafka` -> `promotion-service`): Asegura que al enviar un formulario, el evento viaje por Kafka y pueda disparar promociones de estado.
* **`PromotionToNotificationKafkaTest`** (`promotion-service` -> `Kafka` -> `notification-service`): Validar las notificaciones automáticas tras un cambio a "CONFIRMED".
* **`GatewayRedisIntegrationTest`** (`gateway-service` -> `Redis`): Validar que el token de ingreso en Redis se lea e interprete correctamente para permitir/denegar accesos.
* **`PromotionNeo4jTracingTest`** (`promotion-service` -> `Neo4j`): Interacción real contra Neo4j para garantizar la búsqueda Cypher.
* **`FileUploadControllerIntegrationTest`** (`file-service`): Prueba a nivel de contenedor Web MVC del envío Multipart de archivos.
* **`AnalyticsControllerTest`** (`dashboard-service`): Test de integración MockMvc.

---

## 3. Pruebas E2E (End to End)

Estas pruebas asumen que la plataforma entera (los 8 microservicios + infraestructura) está levantada. El framework utilizado para la ejecución es **Newman / Postman**.

Las pruebas residen en la carpeta `tests/postman/`:
* Archivo de Colección: `circle-guard-e2e-collection.json`
* Entorno de Variables: `circle-guard-environment.json`

**Flujos testeados:**
1. Autenticación y generación de JWT de un estudiante.
2. Formulario de salud con y sin síntomas.
3. Promoción de estado a "Confirmado" por un administrador.
4. Generación y consumo de alertas prioritarias tras confirmar casos.
5. Verificación en el gateway simulando validación de entrada por código QR.

---

## 4. Pruebas de Rendimiento / Estrés (Locust)

Las pruebas están configuradas utilizando `Locust` con base en el script de Python `tests/locustfile.py`. Además existe una prueba en Spring Boot en la suite `PromotionPerformanceTest`.

**Escenarios probados:**
* **Performance Test (carga normal)**: ~20 usuarios concurrentes validando comportamiento durante 1 minuto. Simula un uso "normal" universitario.
* **Stress Test (carga alta)**: ~50 usuarios concurrentes, apuntando a ver los límites locales de Kind (o minikube). Mide latencias mayores a 500ms o tasas de error cuando se satura el thread pool.
