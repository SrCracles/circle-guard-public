# Documentación de Pruebas (Tests Docs)

Este documento centraliza todas las pruebas implementadas en el proyecto CircleGuard, organizadas por tipo de prueba y por microservicio. Además, documenta la importancia y justificación de cada conjunto de pruebas dentro de la plataforma.

## 1. Pruebas Unitarias

Las pruebas unitarias validan la lógica interna de cada componente aislando sus dependencias mediante *mocks*.

### `circleguard-auth-service`
* **`JwtTokenServiceTest`**: Generacion y validacion de tokens JWT (claims, expiracion, firma). **Importancia:** El JWT es el mecanismo central de autenticacion stateless de toda la plataforma. Un bug en la generacion o validacion comprometeria la seguridad de todos los servicios.
* **`DualChainAuthenticationProviderTest`**: Login fallback LDAP vs local. **Importancia:** La autenticacion dual es un requisito funcional critico. Si el fallback falla, usuarios invitados o cuentas locales quedarian bloqueadas.
* **`LoginControllerTest`**: Probar controlador de login de forma unitaria.
* **`IdentityClientTest`**: Validación del comportamiento de fallback (Circuit Breaker) en caso de fallo del `identity-service`. **Importancia:** Asegura la resiliencia del proceso de login evitando bloqueos en cascada o caídas (errores 500) cuando el servicio dependiente está inaccesible o saturado.

### `circleguard-identity-service`
* **`IdentityEncryptionConverterTest`**: Encriptacion/desencriptacion de IDs. **Importancia:** FERPA y la privacidad del estudiante dependen de que las identidades reales nunca se expongan. Garantiza que los datos en reposo sean irreversibles sin la clave.
* **`IdentityVaultServiceTest`**: Generacion de IDs anonimizados unicos y hash SHA-256. **Importancia:** La integridad del sistema depende de que cada identidad real genere exactamente un unico ID anonimo determinista. Un fallo aquí rompería el trazado de contactos.
* **`IdentityMappingRepositoryTest`**: Testeo unitario del repositorio de persistencia.
* **`IdentityVaultControllerTest`**: Controladores REST de gestión de identidades.

### `circleguard-form-service`
* **`SymptomMapperTest`**: Mapeo correcto de sintomas a niveles de riesgo. **Importancia:** Este componente decide si un usuario debe ser marcado como sospechoso basado en sus respuestas. Afecta directamente la metrica de "False Positive Rate < 15%".
* **`HealthSurveyControllerTest`**: Probar endpoints de envío de encuestas médicas.
* **`AttachmentControllerTest`**: Validar adjuntos de encuestas.
* **`QuestionnaireControllerTest`**: Operaciones CRUD y listado de cuestionarios.

### `circleguard-promotion-service`
* **`StatusLifecycleTest`**: Transiciones de estado validas (ACTIVE->SUSPECT->PROBABLE->CONFIRMED). **Importancia:** La maquina de estados de salud es el nucleo del negocio. La contencion rapida (< 60 segundos) depende de transiciones automaticas correctas.
* **`GraphServiceTest`**: Construccion correcta de queries Cypher y deteccion de circulos. **Importancia:** Las queries Cypher en Neo4j son la base del trazado de contactos. Un error comprometeria la eficacia del aislamiento y la seguridad del campus.
* **`HealthStatusServiceTest`**: Servicios de evaluación de salud.
* **`HealthStatusReevaluationTest`**: Reevaluaciones asíncronas automáticas.
* **`AdministrativeCorrectionTest`**: Funcionalidades administrativas para corrección de estado.
* **`FloorServiceTest`**: Lógica para validar pisos o ubicaciones físicas.
* **`SurveyListenerTest`**: Recepción de encuestas.
* **`HealthStatusControllerTest`**: Controlador REST de estados.

### `circleguard-notification-service`
* **`TemplateServiceTest`**: Renderizado de templates Freemarker con variables. **Importancia:** Probar el renderizado garantiza que los usuarios reciban mensajes con su estado correcto y enlaces funcionales, evitando confusion en momentos criticos de salud.
* **`EmailServiceImplTest`**: Funcionalidad de Feature Toggle para el envío de correos. **Importancia:** Valida que el servicio respete la configuración dinámica para encender o apagar el envío de correos y registre adecuadamente la acción en la auditoría sin interrumpir el flujo principal.
* **`NotificationDispatcherTest`**: Gestión y despacho de notificaciones hacia los distintos canales (email, sms).
* **`ExposureNotificationListenerTest`**: Recepción de eventos de exposición de Kafka.
* **`PriorityAlertListenerTest`**: Recepción de notificaciones críticas.
* **`NotificationRetryTest`**: Lógica de reintentos ante caídas o fallos.
* **`LmsServiceTest`**: Interacción con el LMS (Blackboard/Moodle) del campus.
* **`RoomReservationServiceTest`**: Funcionalidad de cancelación o gestión de reservas.

### `circleguard-gateway-service`
* **`QrValidationServiceTest`** y **`QrValidationServiceAdditionalTest`**: Validacion de tokens expirados/firmados incorrectamente. **Importancia:** Si un token invalido o manipulado pasara la validacion, personas con riesgo sanitario podrian ingresar al campus, violando la seguridad biologica.
* **`GateAccessDecisionTest`** / **`GateControllerTest`**: Decision GREEN/RED basada en estado Redis. **Importancia:** Es el ultimo paso de seguridad fisica. Garantiza que la barrera fisica del campus funcione conforme a la politica de salud.

### `circleguard-file-service`
* **`FileStorageServiceTest`**: Lógica de subida al sistema de archivos local, verificando que genere UUIDs adecuados sin colisiones. **Importancia:** Asegura que los certificados y excusas médicas no se sobreescriban ni se pierdan.
* **`FileUploadControllerTest`**: Lógica del endpoint de subida de archivos de manera unitaria.

### `circleguard-dashboard-service`
* **`KAnonymityFilterTest`**: Valida que las métricas sensibles y agrupaciones de pocos usuarios (K < 5) se camuflen correctamente. **Importancia:** Evita la re-identificación de estudiantes en reportes públicos o administrativos.
* **`AnalyticsServiceTest`**: Prueba el aglomerado de las estadísticas sin depender de las bases de datos.

---

## 2. Pruebas de Integración

Estas pruebas validan la correcta comunicación entre dos o más componentes reales (como Kafka, Base de Datos, Redis o llamadas HTTP mediante RestTemplate/Feign).

* **`AuthIdentityIntegrationTest`** (`auth-service` -> `identity-service`): Login exitoso crea mapeo anonimo; login fallido NO llama a identity service. **Importancia:** Garantiza que la separacion de responsabilidades funcione correctamente y evita llamadas innecesarias cuando la autenticacion falla.
* **`FormToPromotionKafkaTest`** (`form-service` -> `Kafka` -> `promotion-service`): Envio de survey con/sin sintomas emite evento correspondiente. **Importancia:** Valida que el pipeline de eventos de salud funcione end-to-end; si Kafka falla, el sistema no puede reaccionar ante brotes.
* **`PromotionToNotificationKafkaTest`** (`promotion-service` -> `Kafka` -> `notification-service`): Estado CONFIRMED emite alerta de prioridad, SUSPECT no la emite. **Importancia:** Asegura que las alertas se generen con la gravedad adecuada y no generen panico innecesario.
* **`GatewayRedisIntegrationTest`** (`gateway-service` -> `Redis`): Token GREEN permite acceso; token RED deniega. **Importancia:** Protege la barrera fisica del campus.
* **`PromotionNeo4jTracingTest`** (`promotion-service` -> `Neo4j`): `detectAndFormCircles` ejecuta query Cypher con filtros de distancia y tiempo. **Importancia:** El rastreo de contactos es el nucleo del sistema; un error aquí invalidaria todo el modelo de contencion.
* **`FileUploadControllerIntegrationTest`** (`file-service`): Prueba a nivel de contenedor Web MVC del envío Multipart de archivos.
* **`AnalyticsControllerTest`** (`dashboard-service`): Test de integración MockMvc.

> **Configuracion de Tests**: Se configuro **H2 en memoria** para los tests de `form-service` y otros que usen repositorios, permitiendo ejecutar las pruebas de integracion sin depender de PostgreSQL real durante las fases de build y dev. Las dependencias externas como Kafka, Redis y Neo4j se simulan mediante **mocks** (Mockito), sin levantar contenedores reales.

---

## 3. Pruebas E2E (End to End)

Estas pruebas asumen que la plataforma entera (los 8 microservicios + infraestructura) está levantada. El framework utilizado para la ejecución es **Newman / Postman**.

Las pruebas residen en la carpeta `tests/postman/`:
* Archivo de Colección: `circle-guard-e2e-collection.json`
* Entorno de Variables: `circle-guard-environment.json`

**Flujos testeados:**
1. **Flujo de Autenticacion**: Login retorna JWT y anonymousId. **Importancia:** Es la puerta de entrada al sistema; si falla, ningun usuario puede acceder.
2. **Flujo de Formulario de Salud**: Envio de survey persiste síntomas. **Importancia:** La precision de los datos determina si el sistema detecta o no un brote.
3. **Flujo de Promocion de Estado**: Admin con rol puede actualizar casos. **Importancia:** Solo personal autorizado debe poder confirmar casos, evitando que cualquier usuario altere estados.
4. **Flujo de Notificacion**: Confirmación genera evento prioritario. **Importancia:** La cadena de notificacion es critica para la respuesta, un fallo deja a la comunidad sin avisar.
5. **Flujo de Acceso al Campus**: QR valido (GREEN) vs Invalido (RED). **Importancia:** Ultima linea de defensa fisica.

---

## 4. Pruebas de Rendimiento / Estrés (Locust)

Las pruebas están configuradas utilizando `Locust` con base en el script de Python `tests/locustfile.py` (y sus variantes `locustfile-performance.py`, `locustfile-stress.py`). Además existe una prueba en Spring Boot en la suite `PromotionPerformanceTest`.

**Escenarios probados:**
* **Performance Test (carga normal)**: ~20 usuarios concurrentes durante 60 segundos. Latencia promedio < 500ms, tasa de errores < 5%. **Importancia:** Valida comportamiento estable bajo carga representativa del dia a dia.
* **Stress Test (carga extrema)**: ~50 usuarios concurrentes durante 60 segundos. **Importancia:** Identifica el limite del sistema antes de que falle; se utilizan valores conservadores para no saturar la PC local (entorno Kind).
