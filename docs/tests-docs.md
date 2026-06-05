# Documentación de Pruebas (Tests Docs)

Este documento centraliza todas las pruebas implementadas en el proyecto CircleGuard, organizadas por tipo de prueba y por microservicio. Además, documenta la importancia y justificación de cada conjunto de pruebas dentro de la plataforma.

## 1. Pruebas Unitarias

Las pruebas unitarias validan la lógica interna de cada componente aislando sus dependencias mediante *mocks*.

### `circleguard-auth-service`
* **`JwtTokenServiceTest`**: Generacion y validacion de tokens JWT (claims, expiracion, firma). **Importancia:** El JWT es el mecanismo central de autenticacion stateless de toda la plataforma. Un bug en la generacion o validacion comprometeria la seguridad de todos los servicios.
* **`DualChainAuthenticationProviderTest`**: Login fallback LDAP vs local. **Importancia:** La autenticacion dual es un requisito funcional critico. Si el fallback falla, usuarios invitados o cuentas locales quedarian bloqueadas.
* **`LoginControllerTest`**: Probar controlador de login de forma unitaria.
* **`IdentityClientTest`**: Validación del comportamiento de fallback (Circuit Breaker) en caso de fallo del `identity-service`. **Importancia:** Asegura la resiliencia del proceso de login evitando bloqueos en cascada o caídas (errores 500) cuando el servicio dependiente está inaccesible o saturado.
* **`UserControllerTest`**: Operaciones CRUD de gestión de usuarios vía MockMvc. **Importancia:** Valida que solo usuarios con rol ADMIN puedan crear o eliminar cuentas, previniendo escalación de privilegios.
* **`QrTokenControllerTest`**: Generación y revocación de tokens QR de forma unitaria (MockitoExtension). **Importancia:** Garantiza que los tokens de acceso al campus tengan TTL correcto y que la revocación inmediata funcione ante reportes de salud.
* **`QrTokenServiceTest`**: Lógica de creación, validación y expiración de tokens QR. **Importancia:** Evita que tokens expirados o revocados permitan el acceso físico al campus.
* **`CustomUserDetailsServiceTest`**: Carga de detalles de usuario desde PostgreSQL. **Importancia:** Asegura que Spring Security reciba roles y credenciales correctas para la evaluación de `@PreAuthorize`.
* **`AuthModelsTest`**: Cobertura exhaustiva de getters, setters, builders, equals, hashCode y toString en modelos Lombok (`User`, `Role`, etc.). **Importancia:** Detecta regressiones en la generación de código de Lombok que podrían afectar la serialización JSON o la comparación de entidades.
* **`JwtAuthenticationFilterTest`**: Extracción y validación del token JWT desde el header `Authorization`. **Importancia:** Prueba la primera línea de defensa de cada request HTTP; un fallo aquí podría dejar endpoints desprotegidos.

### `circleguard-identity-service`
* **`IdentityEncryptionConverterTest`**: Encriptacion/desencriptacion de IDs. **Importancia:** FERPA y la privacidad del estudiante dependen de que las identidades reales nunca se expongan. Garantiza que los datos en reposo sean irreversibles sin la clave.
* **`IdentityVaultServiceTest`**: Generacion de IDs anonimizados unicos y hash SHA-256. **Importancia:** La integridad del sistema depende de que cada identidad real genere exactamente un unico ID anonimo determinista. Un fallo aquí rompería el trazado de contactos.
* **`IdentityMappingRepositoryTest`**: Testeo unitario del repositorio de persistencia.
* **`IdentityVaultControllerTest`**: Controladores REST de gestión de identidades (POST, GET by ID, POST /visit, GET /map). **Importancia:** Valida el correcto mapeo entre identidades reales y anónimas, incluyendo el endpoint de visitas que actualiza métricas de auditoría.
* **`JwtAuthenticationFilterTest`**: Extracción del token Bearer y validación de firma. **Importancia:** Asegura que el filtro de seguridad del `identity-service` rechace tokens malformados o ausentes antes de llegar a los controllers.
* **`IdentityModelsTest`**: Cobertura exhaustiva de getters, setters, builders, equals y hashCode en `IdentityMapping` y `IdentityAccessEvent` (incluyendo `IdentityAccessPayload` e `IdentityAccessMetadata`). **Importancia:** Los eventos de acceso a identidades son auditados; cualquier cambio en la estructura de datos podría romper la trazabilidad de quién consultó identidades reales.

### `circleguard-form-service`
* **`SymptomMapperTest`**: Mapeo correcto de sintomas a niveles de riesgo (expandido con validación de lista vacía y casos de no coincidencia). **Importancia:** Este componente decide si un usuario debe ser marcado como sospechoso basado en sus respuestas. Afecta directamente la metrica de "False Positive Rate < 15%".
* **`HealthSurveyControllerTest`**: Probar endpoints de envío de encuestas médicas.
* **`AttachmentControllerTest`**: Validar adjuntos de encuestas.
* **`QuestionnaireControllerTest`**: Operaciones CRUD, listado de cuestionarios y obtención de cuestionario activo. **Importancia:** Garantiza que los estudiantes siempre reciban el cuestionario correcto y vigente.
* **`CertificateValidationControllerTest`**: Endpoints de validación de certificados médicos (listado de pendientes y validación con estados APPROVED/REJECTED). **Importancia:** La validación manual por parte del personal de salud es un paso crítico antes de permitir el acceso al campus.
* **`QuestionnaireServiceTest`**: Lógica de creación de cuestionarios con preguntas asociadas. **Importancia:** Valida la persistencia transaccional correcta de cuestionarios complejos con múltiples preguntas.
* **`HealthSurveyServiceTest`**: Lógica de guardado de encuestas de salud. **Importancia:** Cada encuesta enviada puede disparar alertas de salud; un error de persistencia podría ocultar un caso sospechoso.
* **`FormModelsTest`**: Cobertura exhaustiva de modelos `HealthSurvey`, `Questionnaire`, `Question` y enums `ValidationStatus` / `QuestionType`. **Importancia:** Las encuestas de salud se serializan a JSON y se persisten en campos JSONB; la integridad del modelo afecta la capacidad de consulta posterior.
* **`StorageServiceTest`**: Almacenamiento de archivos adjuntos en disco (incluyendo eliminación). **Importancia:** Los certificados médicos adjuntos son evidencia legal; su pérdida o sobreescritura comprometería auditorías.
* **`FormRepositoryTest`**: Pruebas de repositorio JPA con H2 (`QuestionnaireRepository` y `HealthSurveyRepository`). **Importancia:** Asegura que las consultas derivadas por nombre funcionen correctamente en la base de datos real.
* **`FormApplicationTest`**: Contexto de Spring Boot levantando correctamente. **Importancia:** Valida que no existan beans faltantes o conflictos de dependencias en el arranque del servicio.
* **`FormIntegrationTest`**: End-to-end de controladores con MockMvc y base de datos H2 real. **Importancia:** Verifica que la cadena completa controller -> service -> repository funcione sin errores de mapeo o transacción.

### `circleguard-promotion-service`
* **`StatusLifecycleTest`**: Transiciones de estado validas (ACTIVE->SUSPECT->PROBABLE->CONFIRMED). **Importancia:** La maquina de estados de salud es el nucleo del negocio. La contencion rapida (< 60 segundos) depende de transiciones automaticas correctas.
* **`GraphServiceTest`**: Construccion correcta de queries Cypher y deteccion de circulos. **Importancia:** Las queries Cypher en Neo4j son la base del trazado de contactos. Un error comprometeria la eficacia del aislamiento y la seguridad del campus.
* **`HealthStatusServiceTest`**: Servicios de evaluación de salud.
* **`HealthStatusReevaluationTest`**: Reevaluaciones asíncronas automáticas.
* **`AdministrativeCorrectionTest`**: Funcionalidades administrativas para corrección de estado.
* **`FloorServiceTest`**: Lógica para validar pisos o ubicaciones físicas.
* **`SurveyListenerTest`**: Recepción de encuestas.
* **`HealthStatusControllerTest`**: Controlador REST de estados.
* **`PromotionModelsTest`**: Cobertura exhaustiva de DTOs (`BuildingDTO`, `AccessPointDTO`, `FloorDTO`) y modelos (`Building`, `AccessPoint`, `Floor`, `SystemSettings`) y modelos de grafo (`CircleNode`, `UserNode`, `EncounterRelationship`). **Importancia:** Los DTOs son el contrato de API entre `promotion-service` y otros servicios; un cambio inadvertido rompería la integración. Los modelos de grafo afectan directamente las queries Cypher.
* **`PromotionControllersTest`**: Controladores REST de infraestructura física (`BuildingController`, `AccessPointController`, `FloorController`), señalización (`LocationSignalController`) y sesiones (`SessionHandshakeController`). **Importancia:** Valida la gestión de edificios, pisos y puntos de acceso WiFi, así como la recepción de señales de ubicación que alimentan el trazado de contactos.

### `circleguard-notification-service`
* **`TemplateServiceTest`**: Renderizado de templates Freemarker con variables. **Importancia:** Probar el renderizado garantiza que los usuarios reciban mensajes con su estado correcto y enlaces funcionales, evitando confusion en momentos criticos de salud.
* **`EmailServiceImplTest`**: Funcionalidad de Feature Toggle para el envío de correos. **Importancia:** Valida que el servicio respete la configuración dinámica para encender o apagar el envío de correos y registre adecuadamente la acción en la auditoría sin interrumpir el flujo principal.
* **`NotificationDispatcherTest`**: Gestión y despacho de notificaciones hacia los distintos canales (email, sms).
* **`ExposureNotificationListenerTest`**: Recepción de eventos de exposición de Kafka.
* **`PriorityAlertListenerTest`**: Recepción de notificaciones críticas.
* **`NotificationRetryTest`**: Lógica de reintentos ante caídas o fallos.
* **`LmsServiceTest`**: Interacción con el LMS (Blackboard/Moodle) del campus.
* **`RoomReservationServiceTest`**: Funcionalidad de cancelación o gestión de reservas.
* **`SmsServiceImplTest`**: Envío de SMS vía integración externa y manejo de errores. **Importancia:** En caso de brotes, el SMS es el canal más rápido para alertar a la comunidad estudiantil.
* **`PushServiceImplTest`**: Envío de notificaciones push y gestión de tokens de dispositivo. **Importancia:** Asegura que las notificaciones urgentes lleguen a las aplicaciones móviles instaladas.
* **`CircleFencedListenerTest`**: Listener de Kafka que procesa eventos de cercado de círculos (cuarentena de grupo). **Importancia:** La contención rápida de brotes depende de que las notificaciones de cercado se generen y envíen sin demora.
* **`AuditLogServiceTest`**: Registro de auditoría de notificaciones enviadas. **Importancia:** Permite trazar qué usuario fue notificado, cuándo y por qué canal, cumpliendo con requisitos de trazabilidad.

### `circleguard-gateway-service`
* **`QrValidationServiceTest`** y **`QrValidationServiceAdditionalTest`**: Validacion de tokens expirados/firmados incorrectamente. **Importancia:** Si un token invalido o manipulado pasara la validacion, personas con riesgo sanitario podrian ingresar al campus, violando la seguridad biologica.
* **`GateAccessDecisionTest`** / **`GateControllerTest`**: Decision GREEN/RED basada en estado Redis. **Importancia:** Es el ultimo paso de seguridad fisica. Garantiza que la barrera fisica del campus funcione conforme a la politica de salud.

### `circleguard-file-service`
* **`FileStorageServiceTest`**: Lógica de subida al sistema de archivos local, verificando que genere UUIDs adecuados sin colisiones. **Importancia:** Asegura que los certificados y excusas médicas no se sobreescriban ni se pierdan.
* **`FileUploadControllerTest`**: Lógica del endpoint de subida de archivos de manera unitaria.

### `circleguard-dashboard-service`
* **`KAnonymityFilterTest`**: Valida que las métricas sensibles y agrupaciones de pocos usuarios (K < 5) se camuflen correctamente. **Importancia:** Evita la re-identificación de estudiantes en reportes públicos o administrativos.
* **`AnalyticsServiceTest`**: Prueba el aglomerado de las estadísticas sin depender de las bases de datos.
* **`PromotionClientTest`**: Cliente Feign hacia `promotion-service` con fallback por Circuit Breaker. **Importancia:** El dashboard depende de datos agregados de salud; si el servicio de promoción falla, el dashboard debe mostrar datos en caché o un estado degradado sin caerse.

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
* **`IdentityIntegrationTest`** (`identity-service`): Flujo completo de creación y consulta de identidades anónimas con base de datos H2 real. **Importancia:** Valida que el hashing SHA-256 y la encriptación AES funcionen correctamente en un contexto de persistencia real.
* **`FormIntegrationTest`** (`form-service`): Flujo completo de creación de cuestionarios y envío de encuestas de salud con MockMvc y H2. **Importancia:** Asegura que los cuestionarios dinámicos se persistan correctamente y que las encuestas generen los eventos de salud esperados.

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

---

## 5. Cobertura de Pruebas y Análisis Estático

Para garantizar que el código se mantiene con un estándar alto de calidad con cada iteración, hemos integrado herramientas de análisis y cobertura en nuestros pipelines de Jenkins (HU-14 y HU-23):

* **JaCoCo (Java Code Coverage)**: Configurado mediante un plugin en `build.gradle.kts`. Analiza la ejecución de las pruebas e instrumenta el código para generar métricas de cobertura. Hemos impuesto un límite de **60% de cobertura mínima de instrucciones**. Si la cobertura cae por debajo, el proceso de construcción fallará intencionalmente impidiendo que código sin testear llegue a etapas posteriores.
* **SonarQube**: Se despliega en el namespace `infra` en nuestro clúster Kubernetes local. Después de correr las pruebas, Jenkins ejecuta `sonar-scanner-cli`, el cual recopila el reporte XML de JaCoCo y analiza el código en busca de code smells, vulnerabilidades y deuda técnica. Si las métricas no superan el **Quality Gate** de SonarQube, el pipeline falla.
* **Tendencia en Jenkins**: El plugin de **Coverage** en Jenkins recibe el reporte generado para cada microservicio y dibuja el gráfico de tendencia en el dashboard del respectivo job, brindando visibilidad constante al equipo.

---

## 6. Infraestructura de Análisis Estático para Dev (Paralelismo)

Para soportar la ejecución simultánea de los 8 pipelines de desarrollo sin conflictos de puertos ni credenciales dinámicas, se realizaron los siguientes ajustes:

* **SonarQube expuesto vía NodePort**: El Service de SonarQube en `k8s/infra/sonarqube.yaml` se cambió de tipo `ClusterIP` a `NodePort` con `nodePort: 30090`, permitiendo acceso directo desde fuera del clúster sin necesidad de `kubectl port-forward`.
* **Port mapping en Kind**: `setup-kind.ps1` ahora incluye un `extraPortMappings` que mapea `containerPort: 30090` -> `hostPort: 9000`, haciendo que SonarQube esté disponible en `localhost:9000` del host Windows tras crear el cluster.
* **Autenticación básica en dev**: Los 8 Jenkinsfiles de dev (`jenkins/dev/Jenkinsfile-*`) se simplificaron eliminando:
  - La variable `SONAR_CREDENTIALS_ID` y el bloque `withCredentials`.
  - El `kubectl port-forward` y el `sleep` asociado.
  - El cleanup de `kubectl.exe` en `post/always`.
  - En su lugar, el scanner se conecta directamente a `http://host.docker.internal:9000` y usa `-Dsonar.login=admin -Dsonar.password=admin` (credenciales por defecto de la imagen `sonarqube:lts-community` en entornos de desarrollo).

**Justificación**: Ejecutar 8 `port-forward` simultáneos al mismo puerto `9000` provocaba colisiones y fallos de `address already in use`. Además, el token de SonarQube no era estático porque la instancia se recrea con cada ejecución de `setup-kind.ps1`. Usar NodePort + autenticación básica elimina ambos problemas y permite escalar el número de pipelines en paralelo sin modificar Jenkins ni Kubernetes.
