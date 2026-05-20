# Microservicios Seleccionados para Pruebas y Release

## 1. Los 6 Microservicios Escogidos

Basandose en el criterio del taller (*"que se comuniquen entre si para permitir la posterior implementacion de pruebas que los involucren"*), estos son los 6 microservicios seleccionados:

| # | Microservicio | Puerto | Justificacion |
|---|--------------|--------|---------------|
| 1 | **circleguard-auth-service** | `8180` | Punto de entrada de autenticacion. Se comunica via HTTP con **identity-service**. Ideal para tests de integracion HTTP. |
| 2 | **circleguard-identity-service** | `8083` | Vault de identidades. Recibe llamadas de **auth-service** y produce eventos Kafka (`audit.identity.accessed`). Tiene encriptacion y logica de anonimizacion interesante para testear. |
| 3 | **circleguard-form-service** | `8086` | Motor de formularios de salud. **Produce** eventos Kafka (`survey.submitted`, `certificate.validated`) que alimentan a promotion-service. Tiene tests unitarios e integracion existentes. |
| 4 | **circleguard-promotion-service** | `8088` | **El corazon del sistema**. Consume Kafka de form-service, usa Neo4j para grafos de contacto, produce Kafka para notification-service. Tiene tests de performance con Testcontainers. |
| 5 | **circleguard-notification-service** | `8082` | **Consume** Kafka de promotion-service y hace llamadas HTTP a **auth-service** para validar permisos. Multi-canal (email/SMS/push). Ideal para tests de integracion end-to-end. |
| 6 | **circleguard-gateway-service** | `8087` | Validacion de QR para acceso fisico al campus. Usa **Redis** para estado de tokens. Es un flujo de usuario real y diferente (no usa Kafka ni BD relacional), aporta variedad al pipeline. |

## 2. Microservicios NO Escogidos

| Microservicio | Puerto | Razon de Exclusion |
|--------------|--------|-------------------|
| **circleguard-file-service** | `8085` | Es completamente standalone, no se comunica con ningun otro servicio. No aporta valor para pruebas de integracion ni E2E entre servicios. |
| **circleguard-dashboard-service** | `8084` | Aunque lee de promotion-service, es un consumidor pasivo de analytics. No genera eventos ni afecta el flujo core de negocio. Su exclusion permite enfocar las pruebas en los flujos transaccionales criticos. |

## 3. Flujos de Comunicacion que Podemos Probar

### Diagrama de Comunicacion entre Servicios Seleccionados

```
+-------------+     HTTP      +-----------------+
|   Auth      |-------------->|    Identity     |
|  Service    |<--------------|    Service      |
+-------------+               +-----------------+
       ^                              |
       | HTTP (permisos)              | Kafka
       |                              v
+-----------------+          +-----------------+
|  Notification   |<--Kafka---|    Identity     |
|    Service      |          |   (audit log)   |
+-----------------+          +-----------------+
       ^
       | Kafka
       |
+-----------------+     Kafka      +-----------------+
|   Promotion     |<---------------|      Form       |
|    Service      |                |    Service      |
|   (Neo4j)       |--------------->|                 |
+-----------------+   (survey/cert)+-----------------+
```

### Flujo E2E Completo que Validaremos

1. **Autenticacion**: Usuario se autentica (`auth-service` -> `identity-service`)
2. **Formulario de Salud**: Usuario envia formulario de salud (`form-service`)
3. **Promocion de Estado**: El estado de salud se procesa y promueve (`promotion-service` procesa en Neo4j)
4. **Notificacion**: Se generan alertas multi-canal (`notification-service` consume Kafka y consulta permisos a `auth-service`)
5. **Acceso al Campus**: Usuario escanea QR en puerta (`gateway-service` valida en Redis)

### Patrones de Comunicacion Identificados

| Tipo | Origen | Destino | Tecnologia | Proposito |
|------|--------|---------|------------|-----------|
| Sincrono HTTP | auth-service | identity-service | RestTemplate | Mapeo de identidades anonimas |
| Sincrono HTTP | notification-service | auth-service | RestTemplate | Validacion de permisos de alerta |
| Asincrono Kafka | form-service | promotion-service | Kafka | Disparar promocion de estado ante survey |
| Asincrono Kafka | form-service | promotion-service | Kafka | Restaurar acceso ante certificado validado |
| Asincrono Kafka | promotion-service | notification-service | Kafka | Notificar cambio de estado |
| Asincrono Kafka | promotion-service | notification-service | Kafka | Alertar administradores de brotes |
| Asincrono Kafka | promotion-service | notification-service | Kafka | Cancelar reservas de espacios |
| Cache | gateway-service | Redis | Spring Data Redis | Validar tokens QR de acceso |

## 4. Tests Propuestos a Implementar

### A. Pruebas Unitarias (5+ nuevas)

| Servicio | Test Propuesto | Que Valida | Por que es Importante |
|----------|---------------|------------|----------------------|
| **auth-service** | `JwtTokenServiceTest` | Generacion y validacion de tokens JWT (claims, expiracion, firma) | El JWT es el mecanismo central de autenticacion stateless de toda la plataforma. Un bug en la generacion o validacion del token comprometeria la seguridad de todos los servicios, permitiendo accesos no autorizados o sesiones invalidas. |
| **auth-service** | `DualChainAuthenticationProviderTest` | Login fallback LDAP vs local | La autenticacion dual (LDAP universitario + base de datos local) es un requisito funcional critico. Si el fallback no funciona correctamente, usuarios invitados o cuentas locales quedarian bloqueadas cuando LDAP este disponible o viceversa, afectando la disponibilidad del sistema. |
| **identity-service** | `IdentityEncryptionConverterTest` | Encriptacion/desencriptacion de IDs (ya existe, ampliar) | FERPA y la privacidad del estudiante dependen de que las identidades reales nunca se expongan. Probar el cifrado garantiza que los datos en reposo sean irreversibles sin la clave correcta, cumpliendo con regulaciones de privacidad. |
| **identity-service** | `IdentityVaultServiceTest` | Generacion de IDs anonimizados unicos y hash SHA-256 | La integridad del sistema de anonimizacion depende de que cada identidad real genere exactamente un unico ID anonimo determinista (via hash) y que nuevos usuarios reciban IDs unicos. Un fallo aqui romperia el trazado de contactos en Neo4j. |
| **form-service** | `SymptomMapperTest` | Mapeo correcto de sintomas a niveles de riesgo | Este componente decide si un usuario debe ser marcado como sospechoso basado en sus respuestas. Un falso negativo (no detectar sintomas) retrasaria la contencion; un falso positivo generaria cuarentenas innecesarias. La precision aqui afecta directamente la metrica de "False Positive Rate < 15%". |
| **promotion-service** | `StatusLifecycleTest` | Transiciones de estado validas (ACTIVE->SUSPECT->PROBABLE->CONFIRMED) | La maquina de estados de salud es el nucleo del negocio. Transiciones invalidas podrian dejar usuarios en estados inconsistentes, afectando la logica de notificaciones y el acceso al campus. La contencion rapida (< 60 segundos) depende de transiciones automaticas correctas. |
| **promotion-service** | `GraphServiceTest` | Construccion correcta de queries Cypher y deteccion de circulos | Las queries Cypher en Neo4j son la base del trazado de contactos. Un error en la construccion de la query podria omitir contactos cercanos o crear falsos circulos, comprometiendo la eficacia del aislamiento y la seguridad del campus. |
| **notification-service** | `TemplateServiceTest` | Renderizado de templates Freemarker con variables | La comunicacion de alertas de salud debe ser clara y personalizada. Probar el renderizado garantiza que los usuarios reciban mensajes con su nombre, estado correcto y enlaces funcionales, evitando confusion en momentos criticos de salud. |
| **gateway-service** | `QrValidationServiceTest` | Validacion de tokens expirados/firmados incorrectamente | La puerta de acceso al campus depende de la validacion criptografica del QR. Si un token invalido o manipulado pasara la validacion, personas con riesgo sanitario (CONTAGIED/POTENTIAL) podrian ingresar al campus, violando la seguridad biologica. |
| **gateway-service** | `GateAccessDecisionTest` | Decision GREEN/RED basada en estado Redis | Este es el ultimo paso de seguridad fisica. Probar que Redis retorna el estado correcto y que el sistema deniega acceso a usuarios de riesgo garantiza que la barrera fisica del campus funcione conforme a la politica de salud institucional. |

> **Nota Importante**: Los tests **existentes** de `form-service` y `notification-service` requieren infraestructura externa corriendo (PostgreSQL y Kafka respectivamente), por lo que fallaran si no levantaste primero `docker-compose -f docker-compose.dev.yml up -d`. Las pruebas **nuevas** implementadas no tienen esta dependencia y pasan sin problemas.

### B. Pruebas de Integracion (5+ nuevas)

| Test | Servicios Involucrados | Que Valida | Por Que Es Importante |
|------|----------------------|------------|----------------------|
| `AuthIdentityIntegrationTest` | auth -> identity | Login exitoso crea mapeo anonimo; login fallido NO llama a identity service (demuestra el corte temprano de la cadena de autenticacion) | Garantiza que la separacion de responsabilidades entre autenticacion e identidad funcione correctamente y evita llamadas innecesarias cuando la autenticacion falla, optimizando recursos |
| `FormToPromotionKafkaTest` | form -> Kafka -> promotion | Envio de survey con/sin sintomas emite evento `survey.submitted` con flag `hasSymptoms`; aprobacion de certificado emite `certificate.validated` con estado APPROVED | Valida que el pipeline de eventos de salud funcione end-to-end; si Kafka falla, el sistema no puede reaccionar ante brotes |
| `PromotionToNotificationKafkaTest` | promotion -> Kafka -> notification | Estado CONFIRMED emite `promotion.status.changed` + `alert.priority` (con affectedCount y eventType); estado SUSPECT solo emite `promotion.status.changed` sin alerta de prioridad | Asegura que las alertas se generen con la gravedad adecuada: un caso confirmado debe disparar notificaciones prioritarias, mientras que un sospechoso no debe generar panico innecesario |
| `GatewayRedisIntegrationTest` | gateway + Redis | Token GREEN permite acceso; token RED (CONTAGIED) deniega acceso; token invalido/manipulado deniega acceso | Protege la barrera fisica del campus; si Redis retorna un estado incorrecto o el gateway no valida bien, usuarios contagiosos podrian ingresar |
| `PromotionNeo4jTracingTest` | promotion + Neo4j | `recordEncounter` delega correctamente al repository; `detectAndFormCircles` ejecuta query Cypher con filtro de duracion > 300s, cluster >= 3 usuarios y MERGE de Circle | El rastreo de contactos es el nucleo del sistema; un error en la creacion de nodos o en la logica de deteccion de circulos invalidaria todo el modelo de contencion |

> **Configuracion de Tests**: Se configuro **H2 en memoria** para los tests de `form-service` mediante el archivo `services/circleguard-form-service/src/test/resources/application.yml`, permitiendo ejecutar las pruebas de integracion sin depender de PostgreSQL real.

### C. Pruebas E2E con Newman (5+ nuevas)

| Flujo E2E | Que Valida | Por Que Es Importante |
|-----------|------------|----------------------|
| **Flujo de Autenticacion** | Login con credenciales validas retorna JWT y anonymousId; login con credenciales invalidas retorna 401 con mensaje de error; generacion de QR token con JWT valido retorna token con expiracion de 60s | Es la puerta de entrada al sistema; si falla, ningun usuario puede acceder a ningun servicio |
| **Flujo de Formulario de Salud** | Envio de survey con sintomas persiste `hasFever=true` y `hasCough=true`; envio sin sintomas persiste ambos en `false`; el sistema acepta campos JSON arbitrarios en `responses` | La precision de los datos de salud determina si el sistema detecta o no un brote; un survey mal procesado podria ocultar un caso positivo |
| **Flujo de Promocion de Estado** | Admin con rol HEALTH_CENTER puede actualizar estado a CONFIRMED via `/api/v1/health/confirmed`; el cambio de estado dispara eventos Kafka correctamente | Solo personal autorizado debe poder confirmar casos; si esto falla, cualquier usuario podria alterar estados y generar falsas alertas |
| **Flujo de Notificacion** | Estado CONFIRMED genera evento `alert.priority` con `affectedCount` y `eventType`; el notification-service consume el evento y genera la alerta correspondiente | La cadena de notificacion es critica para la respuesta ante brotes; un fallo aqui deja a la comunidad sin avisar |
| **Flujo de Acceso al Campus** | QR valido permite acceso (GREEN); QR manipulado o invalido deniega acceso (RED); token expirado o sin JWT en headers deniega acceso | Es la ultima linea de defensa fisica; un fallo aqui pone en riesgo la salud de toda la comunidad del campus |

### D. Pruebas de Rendimiento con Locust

| Escenario | Que Valida | Por Que Es Importante |
|-----------|------------|----------------------|
| **Login masivo** (auth-service) | Tiempo de respuesta < 500ms bajo 100 usuarios concurrentes, throughput > 50 req/s, tasa de errores < 1% | En horarios pico (entrada de clases) cientos de usuarios intentan loguearse simultaneamente; latencia alta genera colas en las puertas |
| **Envio concurrente de surveys** (form-service) | Latencia < 1s bajo 50 usuarios concurrentes, manejo de picos de 200 req/s sin perdida de datos | Durante una alerta de salud, todos los usuarios intentan reportar estado al mismo tiempo; el sistema no puede perder surveys |
| **Procesamiento de Kafka** (promotion-service) | Throughput de mensajes > 100 msg/s, latencia end-to-end < 2s desde emision hasta consumo | Si Kafka se satura, los cambios de estado no se propagan y el rastreo de contactos queda desactualizado |
| **Validacion de QR en puerta** (gateway-service) | Respuesta < 100ms, concurrencia de 200 escaneos/s sin degradacion | Los usuarios escanean QR al pasar por las puertas; mas de 100ms de latencia genera cuellos de botella fisicos |

## 5. Estructura de Carpetas del Proyecto

Todo el trabajo de pipelines, Kubernetes, pruebas y automatizacion se mantendra **dentro** del repositorio `circle-guard-public`:

```
circle-guard-public/
├── docs/
│   └── selectedServices.md              # Este documento
├── tests/
│   ├── postman/
│   │   ├── circle-guard-e2e-collection.json    # Coleccion de pruebas E2E para Newman
│   │   └── circle-guard-environment.json       # Environment de Postman (variables)
│   └── locustfile.py                    # Pruebas de rendimiento con Locust
├── k8s/
│   ├── base/                            # Manifiestos base (Deployments + Services + ConfigMap + Secret)
│   │   ├── kustomization.yaml           # Recursos base de Kustomize
│   │   ├── configmap.yaml               # Configuracion compartida (URLs de DB, Kafka, Redis, etc.)
│   │   ├── secret.yaml                  # Credenciales (passwords, tokens)
│   │   ├── auth-deployment.yaml         # Deployment de auth-service
│   │   ├── auth-service.yaml            # Service de auth-service
│   │   ├── identity-deployment.yaml     # Deployment de identity-service
│   │   ├── identity-service.yaml        # Service de identity-service
│   │   ├── form-deployment.yaml         # Deployment de form-service
│   │   ├── form-service.yaml            # Service de form-service
│   │   ├── promotion-deployment.yaml    # Deployment de promotion-service
│   │   ├── promotion-service.yaml       # Service de promotion-service
│   │   ├── notification-deployment.yaml # Deployment de notification-service
│   │   ├── notification-service.yaml    # Service de notification-service
│   │   ├── gateway-deployment.yaml      # Deployment de gateway-service
│   │   └── gateway-service.yaml         # Service de gateway-service
│   ├── dev/
│   │   └── kustomization.yaml           # Overlay Kustomize para namespace dev
│   ├── stage/
│   │   └── kustomization.yaml           # Overlay Kustomize para namespace stage (tag :stage)
│   └── master/
│       └── kustomization.yaml           # Overlay Kustomize para namespace master (tag :stage)
│   ├── infra/                           # Dependencias externas (PostgreSQL, Kafka, Redis, Neo4j, OpenLDAP, Zookeeper)
│   │   ├── kustomization.yaml           # Despliegue conjunto de infraestructura
│   │   ├── postgresql.yaml              # Deployment + Service PostgreSQL (puerto 5432)
│   │   ├── zookeeper.yaml               # Deployment + Service Zookeeper (puerto 2181)
│   │   ├── kafka.yaml                   # Deployment + Service Kafka (puerto 9092)
│   │   ├── redis.yaml                   # Deployment + Service Redis (puerto 6379)
│   │   ├── neo4j.yaml                   # Deployment + Service Neo4j (puertos 7687, 7474)
│   │   └── openldap.yaml                # Deployment + Service OpenLDAP (puertos 389, 636)
├── jenkins/
│   ├── dev/
│   │   ├── Jenkinsfile-auth             # Pipeline dev para auth-service
│   │   ├── Jenkinsfile-identity         # Pipeline dev para identity-service
│   │   ├── Jenkinsfile-form             # Pipeline dev para form-service
│   │   ├── Jenkinsfile-promotion        # Pipeline dev para promotion-service
│   │   ├── Jenkinsfile-notification     # Pipeline dev para notification-service
│   │   └── Jenkinsfile-gateway          # Pipeline dev para gateway-service
│   ├── stage/
│   │   └── Jenkinsfile-stage            # Pipeline stage (E2E + Locust)
│   └── master/
│       └── Jenkinsfile-master           # Pipeline master (deploy K8s + release notes)
├── services/
│   ├── circleguard-auth-service/
│   │   └── Dockerfile                   # Imagen Docker para auth-service (port 8180)
│   ├── circleguard-identity-service/
│   │   └── Dockerfile                   # Imagen Docker para identity-service (port 8083)
│   ├── circleguard-form-service/
│   │   └── Dockerfile                   # Imagen Docker para form-service (port 8086)
│   ├── circleguard-promotion-service/
│   │   └── Dockerfile                   # Imagen Docker para promotion-service (port 8088)
│   ├── circleguard-notification-service/
│   │   └── Dockerfile                   # Imagen Docker para notification-service (port 8082)
│   ├── circleguard-gateway-service/
│   │   └── Dockerfile                   # Imagen Docker para gateway-service (port 8087)
│   ├── circleguard-file-service/        # No seleccionado
│   └── circleguard-dashboard-service/   # No seleccionado
└── ... (archivos existentes del proyecto)
```

## 6. Resumen de Decisiones de Arquitectura de Pipelines

| Aspecto | Decision |
|---------|----------|
| **Orquestador** | Jenkins corriendo nativo en la PC (no en contenedor) |
| **Runtime de Tests** | Kubernetes (Kind) gestionado via `kubectl` |
| **Registry de Imagenes** | DockerHub (usuario: `srcracles`) |
| **E2E Testing** | Newman CLI con colecciones Postman |
| **Performance Testing** | Locust (Python) |
| **Estrategia de Tags** | `dev` -> `stage` -> `master`. Dev compila y pushea `:dev`. Stage prueba y promueve a `:stage`. Master hace pull de `:stage` y deploya. |
| **Release Notes** | GitHub API con token personal |
| **Permisos Jenkins** | Usuario `jenkins` con acceso a `kubeconfig` y permisos adecuados |

## 7. Matriz de Servicios vs. Ambientes

| Servicio | Dev Pipeline | Stage Pipeline | Master Pipeline |
|----------|-------------|----------------|-----------------|
| auth-service | Unit Tests -> Build -> Push (`:dev`) | Pull (`:dev`) -> E2E + Locust -> Promote (`:stage`) | Pull (`:stage`) -> Deploy K8s |
| identity-service | Unit Tests -> Build -> Push (`:dev`) | Pull (`:dev`) -> E2E + Locust -> Promote (`:stage`) | Pull (`:stage`) -> Deploy K8s |
| form-service | Unit Tests -> Build -> Push (`:dev`) | Pull (`:dev`) -> E2E + Locust -> Promote (`:stage`) | Pull (`:stage`) -> Deploy K8s |
| promotion-service | Unit Tests -> Build -> Push (`:dev`) | Pull (`:dev`) -> E2E + Locust -> Promote (`:stage`) | Pull (`:stage`) -> Deploy K8s |
| notification-service | Unit Tests -> Build -> Push (`:dev`) | Pull (`:dev`) -> E2E + Locust -> Promote (`:stage`) | Pull (`:stage`) -> Deploy K8s |
| gateway-service | Unit Tests -> Build -> Push (`:dev`) | Pull (`:dev`) -> E2E + Locust -> Promote (`:stage`) | Pull (`:stage`) -> Deploy K8s |

> **Nota**: La infraestructura compartida (Kafka, Neo4j, PostgreSQL, Redis, Zookeeper, OpenLDAP) se levanta como parte del ambiente Stage para las pruebas E2E y de rendimiento.
