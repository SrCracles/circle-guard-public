# Patrones de Diseño — CircleGuard

Este documento identifica y describe los patrones de diseño implementados en la arquitectura de CircleGuard. Para cada patron se indica su nombre, descripcion, el servicio o componente donde aplica, el problema que resuelve y evidencia concreta en el codigo del proyecto.

---

## Patron 1: API Gateway

**Descripcion:** Un punto de entrada unico para todos los clientes de la aplicacion. El gateway concentra la validacion de tokens (JWT y QR), el enrutamiento y el control de acceso antes de que las solicitudes lleguen a los microservicios internos.

**Servicio / Componente:** `circleguard-gateway-service` — `GateController`, `QrValidationService`

**Problema que resuelve:** Sin un gateway, cada microservicio tendria que implementar su propia capa de validacion de tokens y control de acceso, creando codigo duplicado y un perimetro de seguridad inconsistente. El gateway centraliza la barrera de acceso fisico al campus.

**Evidencia:**

```java
// services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/controller/GateController.java
@RestController
@RequestMapping("/api/v1/gate")
public class GateController {
    private final QrValidationService validationService;

    @PostMapping("/validate")
    public ResponseEntity<QrValidationService.ValidationResult> validate(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        return ResponseEntity.ok(validationService.validateToken(token));
    }
}
```

```java
// services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/service/QrValidationService.java
public ValidationResult validateToken(String token) {
    // Valida el JWT del QR y consulta el estado de salud en Redis
    String status = redisTemplate.opsForValue().get(STATUS_KEY_PREFIX + anonymousId);

    if ("CONTAGIED".equals(status) || "POTENTIAL".equals(status)) {
        return new ValidationResult(false, "RED", "Access Denied: Health Risk Detected");
    }
    return new ValidationResult(true, "GREEN", "Welcome to Campus");
}
```

---

## Patron 2: Event-Driven Architecture

**Descripcion:** Los microservicios se comunican de forma asincrona mediante eventos publicados en topicos de Apache Kafka. El productor del evento no conoce ni depende de los consumidores. Los servicios reaccionan autonomamente a los eventos que les son relevantes.

**Servicio / Componente:** `circleguard-promotion-service` → `circleguard-notification-service` via Kafka (`HealthStatusService`, `SurveyListener`, `ExposureNotificationListener`)

**Problema que resuelve:** El acoplamiento sincronico directo entre servicios reduce la disponibilidad del sistema: si el servicio de notificaciones cae, el servicio de promocion no puede procesar estados de salud. Kafka desacopla los servicios y permite que cada uno escale y falle de forma independiente.

**Evidencia:**

```java
// services/circleguard-promotion-service/.../service/HealthStatusService.java
// Productor: publica evento cuando cambia el estado de salud de un usuario
kafkaTemplate.send(TOPIC_STATUS_CHANGED, anonymousId, payload);
kafkaTemplate.send("alert.priority", anonymousId, priorityPayload);
kafkaTemplate.send("circle.fenced", circle.getId().toString(), circlePayload);
```

```java
// services/circleguard-notification-service/.../service/ExposureNotificationListener.java
// Consumidor: reacciona al evento publicado por promotion-service
@KafkaListener(topics = "promotion.status.changed", groupId = "notification-group")
public void handleStatusChange(String eventJson) {
    // ...
    dispatcher.dispatch(userId, status);
    lmsService.syncRemoteAttendance(userId, status);
}
```

```java
// services/circleguard-promotion-service/.../listener/SurveyListener.java
// Consumidor: reacciona a encuestas enviadas por form-service
@KafkaListener(topics = "survey.submitted", groupId = "promotion-service-group")
public void onSurveySubmitted(Map<String, Object> event) {
    if (Boolean.TRUE.equals(hasSymptoms)) {
        healthStatusService.updateStatus(anonymousId, "SUSPECT");
    }
}
```

**Topicos de Kafka utilizados:**

| Topico | Productor | Consumidor |
|--------|-----------|------------|
| `survey.submitted` | `circleguard-form-service` | `circleguard-promotion-service` |
| `promotion.status.changed` | `circleguard-promotion-service` | `circleguard-notification-service` |
| `alert.priority` | `circleguard-promotion-service` | `circleguard-notification-service` |
| `circle.fenced` | `circleguard-promotion-service` | `circleguard-notification-service` |
| `certificate.validated` | `circleguard-form-service` | `circleguard-promotion-service` |

---

## Patron 3: State Machine

**Descripcion:** El estado de salud de cada usuario sigue una maquina de estados finita con transiciones explicitas y reglas de negocio estrictas. Las transiciones son disparadas por eventos (envio de encuesta, validacion de certificado) o por tiempo (vencimiento de la ventana de aislamiento).

**Servicio / Componente:** `circleguard-promotion-service` — `HealthStatusService`, `StatusLifecycleService`

**Problema que resuelve:** Sin una maquina de estados, las reglas de transicion (quien puede pasar a ACTIVE, cuando expira una cuarentena, como se propaga a contactos) estarian dispersas en multiples lugares del codigo, generando inconsistencias y bugs de logica de negocio graves.

**Diagrama de estados:**

```
ACTIVE ──(sintomas)──> SUSPECT ──(contacto confirmado)──> PROBABLE
  ^                       |                                    |
  |                   (certificado                         (ventana
  |                    aprobado o                          expirada)
  |                    ventana expirada)                       |
  └───────────────────────┴────────────────────────────────────┘
                                    |
                               (alta medica)
                                    v
                               RECOVERED
                                    |
                            (30 dias de inmunidad)
                                    v
                                  ACTIVE
```

**Evidencia:**

```java
// services/circleguard-promotion-service/.../service/HealthStatusService.java
// Propagacion de estado: CONFIRMED -> SUSPECT (1 salto), CONFIRMED -> PROBABLE (2 saltos)
String unifiedQuery =
    "MATCH (source:User {anonymousId: $id}) " +
    "SET source.status = $status, source.statusUpdatedAt = timestamp() " +
    "WITH source " +
    "OPTIONAL MATCH (source)-[r1]-(c1:User) " +
    "WHERE (...contacto valido...) " +
    "  AND c1.status <> 'CONFIRMED' AND c1.status <> 'RECOVERED' " +
    "WITH source, c1, " +
    "     CASE WHEN $status = 'CONFIRMED' THEN 'SUSPECT' " +
    "          WHEN $status = 'SUSPECT' THEN 'PROBABLE' " +
    "          ELSE c1.status END as l1Status " +
    // ...
```

```java
// services/circleguard-promotion-service/.../service/StatusLifecycleService.java
// Transicion automatica por tiempo: SUSPECT/PROBABLE -> ACTIVE al vencer la ventana
@Scheduled(cron = "0 0 * * * *") // Cada hora
public void processAutomaticTransitions() {
    long expirationThreshold = System.currentTimeMillis() -
            ((long)settings.getMandatoryFenceDays() * 24 * 60 * 60 * 1000);
    // Libera usuarios cuya ventana de aislamiento ha expirado
}
```

```java
// Guarda de transicion: impide salir de cuarentena antes de tiempo
private void checkFenceWindow(String anonymousId) {
    if (elapsed < fenceDurationMs) {
        long remainingDays = (fenceDurationMs - elapsed) / (24 * 60 * 60 * 1000);
        throw new FenceException("Cannot transition to ACTIVE. User is in mandatory fence window for "
                + remainingDays + " more days.");
    }
}
```

---

## Patron 4: Repository

**Descripcion:** Capa de abstraccion sobre el acceso a datos que desacopla la logica de negocio del mecanismo de persistencia. El dominio trabaja con interfaces de repositorio sin conocer si el almacenamiento subyacente es PostgreSQL, Neo4j o Redis.

**Servicio / Componente:** Todos los servicios — especialmente `circleguard-promotion-service` con repositorios duales (JPA para PostgreSQL + Spring Data Neo4j para el grafo)

**Problema que resuelve:** Permite cambiar el motor de base de datos sin modificar la logica de negocio. Tambien facilita la escritura de tests con implementaciones mock o en memoria.

**Evidencia:**

```java
// services/circleguard-promotion-service/.../repository/graph/UserNodeRepository.java
// Repositorio Neo4j con queries Cypher personalizados
public interface UserNodeRepository extends Neo4jRepository<UserNode, String> {

    @Query("MATCH (u1:User {anonymousId: $sourceId}), (u2:User {anonymousId: $targetId}) " +
           "MERGE (u1)-[r:ENCOUNTERED {locationId: $locationId}]-(u2) " +
           "ON CREATE SET r.startTime = $timestamp, r.duration = 0 " +
           "ON MATCH SET r.duration = ($timestamp - r.startTime) / 1000")
    void recordEncounter(String sourceId, String targetId, Long timestamp, String locationId);

    @Query("MATCH ()-[r:ENCOUNTERED]-() WHERE r.startTime < $threshold DELETE r RETURN count(r)")
    Long purgeStaleEncounters(Long threshold);
}
```

```java
// services/circleguard-promotion-service/.../repository/jpa/SystemSettingsRepository.java
// Repositorio JPA para configuracion de sistema en PostgreSQL
public interface SystemSettingsRepository extends JpaRepository<SystemSettings, Long> {
    // Acceso unificado independiente del motor de BD
}
```

El mismo servicio (`HealthStatusService`) usa ambos repositorios de forma transparente:

```java
private final UserNodeRepository userNodeRepository;          // Neo4j
private final SystemSettingsRepository systemSettingsRepository; // PostgreSQL
private final StringRedisTemplate redisTemplate;              // Redis
```

---

## Patron 5: Chain of Responsibility

**Descripcion:** La autenticacion de usuarios sigue una cadena de responsabilidad: primero se intenta autenticar via LDAP corporativo (directorio del campus), y si ese proveedor falla, la solicitud pasa al siguiente eslabon de la cadena: la base de datos local.

**Servicio / Componente:** `circleguard-auth-service` — `DualChainAuthenticationProvider`

**Problema que resuelve:** El sistema debe soportar dos tipos de usuarios: miembros del campus (autenticados via LDAP universitario) y usuarios locales (creados directamente en la BD). Sin la cadena, se necesitaria logica condicional compleja en el punto de autenticacion. La cadena permite agregar nuevos proveedores de identidad sin modificar el codigo cliente.

**Evidencia:**

```java
// services/circleguard-auth-service/.../security/DualChainAuthenticationProvider.java
@Component
public class DualChainAuthenticationProvider implements AuthenticationProvider {

    private final LdapAuthenticationProvider ldapProvider;
    private final DaoAuthenticationProvider localProvider;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        try {
            // Eslabon 1: Intenta autenticar via LDAP del campus
            return ldapProvider.authenticate(authentication);
        } catch (AuthenticationException e) {
            // Eslabon 2: Fallback a la base de datos local
            return localProvider.authenticate(authentication);
        }
    }
}
```

---

## Patron 6: External Configuration

**Descripcion:** Toda la configuracion sensible o que varia entre ambientes (URLs, credenciales, secretos JWT, usuarios de DockerHub) se externaliza fuera del codigo fuente y de las imagenes Docker. Se implementa en dos niveles: variables de entorno inyectadas en Kubernetes y propiedades globales configuradas en Jenkins.

**Servicio / Componente:** Todos los microservicios (capa Kubernetes: `k8s/base/configmap.yaml`, `k8s/base/secret.yaml`) y todos los pipelines CI/CD (capa Jenkins: `jenkins/jenkins.properties`)

**Problema que resuelve:** Las imagenes Docker no contienen URLs de produccion ni credenciales. Si alguien cambia el usuario de DockerHub o las credenciales en Jenkins, no es necesario modificar ningun archivo del repositorio. Esto permite que cualquier equipo haga un fork y despliegue el proyecto simplemente configurando unas pocas variables en su entorno.

**Evidencia — Capa Kubernetes (ConfigMap + Secret):**

```yaml
# k8s/base/configmap.yaml
# Variables de configuracion no sensibles inyectadas en todos los Pods
envFrom:
  - configMapRef:
      name: circleguard-config   # URLs de servicios, topics Kafka, puertos
  - secretRef:
      name: circleguard-secrets  # Credenciales de BD, Neo4j, LDAP
```

Los Deployments consumen la configuracion sin referencias hardcodeadas:

```yaml
# k8s/base/auth-deployment.yaml
env:
  - name: SPRING_APPLICATION_NAME
    value: "circleguard-auth-service"
  - name: SPRING_DATASOURCE_URL
    value: "jdbc:postgresql://postgresql.infra.svc.cluster.local:5432/circleguard_auth"
```

**Evidencia — Capa Jenkins (Global Properties + jenkins.properties):**

```groovy
// jenkins/master/Jenkinsfile-master (patron aplicado en todos los Jenkinsfiles)
environment {
    DOCKER_USER = "${env.CG_DOCKER_USER ?: 'srcracles'}"
    DOCKERHUB_CREDENTIALS_ID = "${env.CG_DOCKERHUB_CREDENTIALS_ID ?: 'dockerhub-credentials'}"
}
```

```properties
# jenkins/jenkins.properties — configuracion del proyecto (rutas de tests)
NEWMAN_COLLECTION=tests/postman/circle-guard-e2e-collection.json
LOCUST_PERF_FILE=tests/locustfile-performance.py
```

| Variable Jenkins | Descripcion | Fallback por defecto |
|-----------------|-------------|----------------------|
| `CG_DOCKER_USER` | Usuario de DockerHub | `srcracles` |
| `CG_GITHUB_OWNER` | Owner del repo GitHub | `SrCracles` |
| `CG_GITHUB_REPO` | Nombre del repo GitHub | `circle-guard-public` |
| `CG_DOCKERHUB_CREDENTIALS_ID` | ID de credencial Jenkins | `dockerhub-credentials` |
| `CG_GITHUB_TOKEN_ID` | ID de token GitHub en Jenkins | `github-token` |

---

## Patron 7: Cache-Aside

**Descripcion:** El estado de salud de cada usuario se almacena en Redis como cache de lectura rapida. La logica de escritura actualiza explicitamente tanto el grafo de Neo4j (fuente de verdad) como la cache de Redis en cada transicion de estado. Para lecturas de baja latencia (como la validacion en el gateway), Redis responde directamente sin consultar Neo4j.

**Servicio / Componente:** `circleguard-promotion-service` (`HealthStatusService`, `CacheConfig`), `circleguard-gateway-service` (`QrValidationService`)

**Problema que resuelve:** Las consultas al grafo de Neo4j que calculan el estado de salud con propagacion de dos saltos son costosas computacionalmente. El gateway necesita responder en milisegundos para validar el acceso fisico al campus. Sin cache, cada validacion de QR ejecutaria una query compleja al grafo.

**Evidencia:**

```java
// services/circleguard-promotion-service/.../config/CacheConfig.java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats());
        cacheManager.setCacheNames(Arrays.asList("userStatus", "proximityMatches",
                "buildingMetadata", "systemSettings"));
        return cacheManager;
    }
}
```

```java
// services/circleguard-promotion-service/.../service/HealthStatusService.java
// Escritura: actualiza Neo4j y Redis atomicamente en cada transicion
redisTemplate.opsForValue().multiSet(cacheUpdates); // hasta 2000 entradas por lote

// Lectura con cache de aplicacion (Caffeine)
@Cacheable(cacheNames = "userStatus", key = "#anonymousId")
public String getCachedStatus(String anonymousId) {
    return redisTemplate.opsForValue().get(STATUS_KEY_PREFIX + anonymousId);
}

// Invalidacion al actualizar
@CacheEvict(cacheNames = "userStatus", allEntries = true)
public void updateStatus(String anonymousId, String status, boolean adminOverride) { ... }
```

```java
// services/circleguard-gateway-service/.../service/QrValidationService.java
// El gateway lee directamente de Redis: O(1) sin tocar Neo4j
String status = redisTemplate.opsForValue().get(STATUS_KEY_PREFIX + anonymousId);
```

---

## Patron 8: Scheduled Task / Background Processing 

**Descripcion:** Operaciones de mantenimiento periodicas se ejecutan en segundo plano mediante tareas programadas (`@Scheduled`). Esto incluye la limpieza del grafo de encuentros y la revaluacion automatica de estados de cuarentena expirados.

**Servicio / Componente:** `circleguard-promotion-service` — `GraphCleanupTask`, `StatusLifecycleService`

**Problema que resuelve:** La limpieza del grafo y la liberacion de usuarios en cuarentena son operaciones que deben ocurrir periodicamente sin intervencion humana. Hacerlas de forma reactiva (en cada request) introduciria latencia en operaciones criticas del usuario.

**Evidencia:**

```java
// services/circleguard-promotion-service/.../task/GraphCleanupTask.java
// Elimina encuentros de mas de 14 dias (NFR-4: Minimizacion de datos)
@Scheduled(cron = "0 0 * * * *") // Cada hora
@Transactional("neo4jTransactionManager")
public void purgeStaleEncounters() {
    long threshold = System.currentTimeMillis() - FOURTEEN_DAYS_MS;
    Long deletedCount = userNodeRepository.purgeStaleEncounters(threshold);
    log.info("Graph cleanup successful. Purged {} stale ENCOUNTERED relationships.", deletedCount);
}
```

```java
// services/circleguard-promotion-service/.../service/StatusLifecycleService.java
// Libera automaticamente usuarios en SUSPECT/PROBABLE cuya ventana expiro
@Scheduled(cron = "0 0 * * * *") // Cada hora
@Transactional("neo4jTransactionManager")
public void processAutomaticTransitions() {
    long expirationThreshold = System.currentTimeMillis() -
            ((long)settings.getMandatoryFenceDays() * 24 * 60 * 60 * 1000);
    // Transiciona usuarios elegibles de vuelta a ACTIVE y notifica via Kafka
    kafkaTemplate.send(TOPIC_STATUS_CHANGED, id, Map.of("status", "ACTIVE",
            "reason", "AUTO_WINDOW_EXPIRY"));
}
```

---

## Patron 9: Circuit Breaker

**Descripcion:** Patrón de resiliencia que previene que fallos en un servicio dependiente (ej. `identity-service`) provoquen bloqueos en cascada en el servicio que lo invoca (`auth-service`). Cuando la tasa de fallos o los tiempos de espera exceden un umbral, el circuito se abre y las peticiones posteriores retornan una respuesta de fallback inmediata ("error controlado") en lugar de esperar un timeout.

**Servicio / Componente:** `circleguard-auth-service` — `IdentityClient`

**Problema que resuelve:** Si `identity-service` experimenta una caída o un pico de latencia, `auth-service` agotaría sus hilos de conexión esperando respuesta. Esto bloquearía el proceso de login para todos los usuarios. El Circuit Breaker evita el agotamiento de recursos retornando un UUID por defecto (modo degradado) cuando el servicio de identidad no está disponible, permitiendo que el sistema siga operando.

**Evidencia:**

```java
// services/circleguard-auth-service/src/main/java/com/circleguard/auth/client/IdentityClient.java
@CircuitBreaker(name = "identityService", fallbackMethod = "getAnonymousIdFallback")
public UUID getAnonymousId(String realIdentity) {
    String url = identityServiceUrl + "/api/v1/identities/map";
    Map<String, String> request = Map.of("realIdentity", realIdentity);
    Map response = restTemplate.postForObject(url, request, Map.class);
    return UUID.fromString(response.get("anonymousId").toString());
}

public UUID getAnonymousIdFallback(String realIdentity, Throwable t) {
    System.err.println("Fallback activated for Identity Service. Returning default UUID.");
    return UUID.fromString("00000000-0000-0000-0000-000000000000");
}
```

```yaml
# services/circleguard-auth-service/src/main/resources/application.yml
resilience4j.circuitbreaker:
  instances:
    identityService:
      slidingWindowSize: 10
      failureRateThreshold: 50
      waitDurationInOpenState: 10s
      permittedNumberOfCallsInHalfOpenState: 3
```

---

## Patron 10: Feature Toggle

**Descripcion:** Permite habilitar o deshabilitar funcionalidades especificas del sistema sin necesidad de modificar el codigo fuente ni redesplegar los servicios. Las configuraciones son gestionadas dinamicamente mediante variables de entorno que se leen en tiempo de ejecucion.

**Servicio / Componente:** `circleguard-notification-service` — `EmailServiceImpl`

**Problema que resuelve:** Si hay incidentes con un proveedor externo o se desea probar una nueva caracteristica, el Feature Toggle proporciona un mecanismo para desactivar esa porcion especifica del sistema de forma segura, reduciendo el riesgo y mejorando el control en produccion.

**Evidencia:**

```java
// services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/EmailServiceImpl.java
@org.springframework.beans.factory.annotation.Value("${feature.toggle.email.enabled:true}")
private boolean emailEnabled;

public CompletableFuture<Void> sendAsync(String userId, String message) {
    if (!emailEnabled) {
        log.info("Email feature is disabled via toggle. Skipping email to user: {}", userId);
        auditLogService.logDelivery(userId, "EMAIL", "SKIPPED_TOGGLE", correlationId);
        return CompletableFuture.completedFuture(null);
    }
    // ...
}
```

```yaml
# k8s/base/configmap.yaml
FEATURE_TOGGLE_EMAIL_ENABLED: "true"
```

---

## Patron 11: Health Checks (Readiness/Liveness Probes)

**Descripcion:** Kubernetes sondea periodicamente el estado de cada Pod mediante dos tipos de probes HTTP configurados en cada Deployment. El `readinessProbe` determina si el contenedor esta listo para recibir trafico (si falla, Kubernetes lo elimina del balanceo de carga). El `livenessProbe` determina si el proceso principal sigue vivo (si falla, Kubernetes reinicia el Pod automaticamente).

**Servicio / Componente:** Todos los Deployments en `k8s/base/` — `readinessProbe` y `livenessProbe` apuntando al endpoint `/actuator/health/readiness` y `/actuator/health/liveness` de Spring Boot Actuator.

**Problema que resuelve:** Sin probes, Kubernetes envia trafico a Pods que todavia estan arrancando o cuyo proceso interno ha muerto (deadlock, OOM), lo que genera errores 502/503 para los usuarios. Con las probes configuradas, Kubernetes detecta estos estados de forma automatica y toma accion correctiva sin intervencion humana.

**Evidencia — Kubernetes Deployment:**

```yaml
# k8s/base/promotion-deployment.yaml (servicio mas complejo: PostgreSQL + Neo4j + Kafka + Redis)
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8088
  initialDelaySeconds: 90   # Tiempo de arranque de Neo4j + Kafka
  periodSeconds: 10
  failureThreshold: 5
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8088
  initialDelaySeconds: 120  # Mas holgura para liveness
  periodSeconds: 15
  failureThreshold: 3
```

**Evidencia — Spring Boot Actuator:**

```yaml
# application.yml (todos los servicios)
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      probes:
        enabled: true
      show-details: always
```

**Tiempos configurados por servicio:**

| Servicio | Puerto | `initialDelaySeconds` (readiness) | Razon |
|---------|--------|-----------------------------------|-------|
| `gateway-service` | 8087 | 40s | Solo Redis |
| `file-service` | 8085 | 30s | Sin dependencias externas |
| `identity-service` | 8083 | 50s | PostgreSQL |
| `notification-service` | 8082 | 50s | Kafka + Mail |
| `dashboard-service` | 8084 | 50s | PostgreSQL |
| `auth-service` | 8180 | 60s | PostgreSQL + LDAP + Resilience4j |
| `form-service` | 8086 | 60s | PostgreSQL + Kafka |
| `promotion-service` | 8088 | 90s | PostgreSQL + Neo4j + Kafka + Redis |

---

## Resumen de Patrones

| # | Patron | Categoria | Servicio Principal |
|---|--------|-----------|--------------------|
| 1 | API Gateway | Arquitectural | `circleguard-gateway-service` |
| 2 | Event-Driven Architecture | Arquitectural | `promotion` → `notification` via Kafka |
| 3 | State Machine | Comportamiento | `circleguard-promotion-service` |
| 4 | Repository | Datos | Todos los servicios |
| 5 | Chain of Responsibility | Comportamiento | `circleguard-auth-service` |
| 6 | External Configuration | Configuracion | K8s + Jenkins |
| 7 | Cache-Aside | Rendimiento | `promotion` + `gateway` via Redis |
| 8 | Scheduled Task | Operacional | `circleguard-promotion-service` |
| 9 | Circuit Breaker | Resiliencia | `circleguard-auth-service` |
| 10 | Feature Toggle | Operacional | `circleguard-notification-service` |
| 11 | Health Checks / Probes | Operacional | Todos los microservicios (K8s) |
