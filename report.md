# Reporte Técnico CircleGuard

## 1. Contexto General

Para este proyecto se montó un entorno local completo usando **Kind** como clúster Kubernetes de desarrollo y **Jenkins** instalado de forma nativa en una PC con **Windows**. La idea fue tener una plataforma reproducible para automatizar despliegues, pruebas y promociones entre ambientes sin depender de infraestructura externa. Como repositorio de artefactos de contenedores, se usa **DockerHub** para almacenar y distribuir las imágenes de los microservicios.

El flujo general quedó organizado en tres niveles de pipeline:

- **6 pipelines de dev**: uno por cada microservicio seleccionado.
- **1 pipeline de stage**: ejecuta pruebas E2E y de carga sobre el entorno integrado.
- **1 pipeline de master**: despliega en el namespace final y genera la release.

La secuencia busca simular un ciclo real de integración continua: validar cada servicio por separado, probar la interacción completa entre servicios y finalmente publicar una versión estable.

## 2. Pipelines Del Proyecto

### 2.1 Pipelines de Dev

Los 6 pipelines de dev son prácticamente iguales. La diferencia principal es el microservicio que construyen, prueban y publican como imagen Docker con tag `dev`.

Los pipelines son:

- `jenkins/dev/Jenkinsfile-auth`
- `jenkins/dev/Jenkinsfile-identity`
- `jenkins/dev/Jenkinsfile-form`
- `jenkins/dev/Jenkinsfile-promotion`
- `jenkins/dev/Jenkinsfile-notification`
- `jenkins/dev/Jenkinsfile-gateway`

Paso a paso, todos siguen una lógica similar:

1. Hacen checkout del código.
2. Ejecutan primero las pruebas unitarias del servicio.
3. Ejecutan después las pruebas de integración, levantando dependencias reales con Testcontainers en lugar de mocks.
4. Construyen la imagen Docker del microservicio.
5. Publican la imagen en DockerHub con tag `dev`.
6. Dejan el artefacto listo para que el pipeline de stage lo consuma.

La utilidad de separarlos por servicio es que cada uno puede fallar de forma independiente sin bloquear a los demás y sin mezclar la responsabilidad de prueba de cada microservicio.

### 2.2 Pipeline de Stage

El pipeline de stage usa el archivo `jenkins/stage/Jenkinsfile-stage` y sirve para validar el sistema integrado antes de pasar a master.

A nivel general hace esto:

1. Hace checkout del repositorio.
2. Descarga las imágenes `:dev` de los microservicios.
3. Despliega infraestructura y servicios en el namespace de stage.
4. Ejecuta pruebas E2E con Newman.
5. Ejecuta pruebas de rendimiento con Locust (al final genera reportes).
6. Ejecuta pruebas de estrés con Locust (al final genera reportes).
7. Promueve las imágenes a tag `:stage` si todo sale bien.
8. Si todo sale bien, limpia el namespace de stage y borra las imágenes temporales `:dev` e intermedias que se buildearon y se pullearon.

Este pipeline es importante porque no prueba un servicio aislado, sino el comportamiento conjunto del sistema completo.

### 2.3 Pipeline de Master

El pipeline de master usa `jenkins/master/Jenkinsfile-master` y es el último paso antes de publicar una release.

Su flujo general es:

1. Hace checkout del repositorio.
2. Limpia el namespace de master para evitar residuos de ejecuciones previas.
3. Descarga las imágenes `:stage` desde DockerHub.
4. Despliega la arquitectura completa en Kubernetes.
5. Genera las release notes a partir de los commits desde el último tag.
6. Crea el tag de versión en Git.
7. Publica la release en GitHub.

En otras palabras, master se encarga de dejar el entorno productivo listo y documentado.

## 3. Credenciales Y Variables En Jenkins

En Jenkins se usan **dos credentials** y **una variable de entorno** clave:

- `dockerhub-credentials`: credencial tipo usuario y contraseña para autenticarse en DockerHub y publicar o consumir imágenes.
- `github-token`: credencial tipo secret text para crear tags y publicar releases en GitHub.
- `KUBECONFIG`: variable de entorno que apunta al archivo `kind-kubeconfig.yaml` generado para conectar Jenkins con el clúster Kind.

Estas configuraciones son necesarias para que Jenkins pueda interactuar con DockerHub, GitHub y Kubernetes sin hardcodear secretos en los pipelines.

## 4. Scripts De Automatizacion

Se usan dos scripts principales para agilizar el entorno local:

### 4.1 `setup-kind.ps1`

Este script crea el clúster Kind, levanta los namespaces del proyecto y despliega la infraestructura base necesaria para que los pipelines funcionen.

En resumen:

- crea el clúster Kind;
- crea los namespaces `dev`, `stage`, `master` e `infra`;
- despliega PostgreSQL, Kafka, Redis, Neo4j, Zookeeper y OpenLDAP;
- exporta el kubeconfig para que Jenkins lo use.

### 4.2 `teardown-kind.ps1`

Este script borra recursos del entorno para dejar el sistema limpio.

Puede eliminar:

- deployments y services;
- namespaces;
- el kubeconfig exportado;
- el clúster Kind completo;
- e incluso imágenes Docker del proyecto, si se pide.

La combinación de ambos scripts permite levantar y limpiar todo el entorno sin hacerlo manualmente paso a paso.

## 5. Estructura de Manifiestos de Kubernetes (k8s)

El despliegue y la configuración de toda la infraestructura y microservicios se gestionan mediante **Kustomize**, estructurando la configuración de manera modular y reutilizable bajo la carpeta `k8s`. Esta arquitectura modular separa claramente la base de la aplicación de las personalizaciones requeridas para cada entorno de ejecución.

La distribución de directorios y archivos dentro de `k8s/` es la siguiente:

*   **`infra/`**: Contiene la definición de los recursos de base de datos y mensajería requeridos por el sistema.
    *   `postgresql.yaml`: Base de datos relacional para el almacenamiento persistente.
    *   `redis.yaml`: Caché en memoria para la validación y gestión de tokens/estados rápidos.
    *   `kafka.yaml` y `zookeeper.yaml`: Bus de eventos distribuidos para la comunicación asíncrona entre microservicios.
    *   `neo4j.yaml`: Base de datos de grafos para el rastreo y modelado de círculos de contacto.
    *   `openldap.yaml`: Servidor de directorio para la autenticación de usuarios.
    *   `kustomization.yaml`: Declara y agrupa todos los servicios de infraestructura bajo el tag/etiqueta común `component: infrastructure`.
*   **`base/`**: Contiene los manifiestos base que definen el estado deseado estándar de los microservicios sin especificar configuraciones del entorno.
    *   *Configuraciones*: `configmap.yaml` (variables globales) y `secret.yaml` (credenciales codificadas en base64).
    *   *Microservicios*: Duplas de `[servicio]-deployment.yaml` y `[servicio]-service.yaml` para cada uno de los 6 microservicios seleccionados (`auth`, `identity`, `form`, `promotion`, `notification`, `gateway`).
    *   `kustomization.yaml`: Agrupa todos los archivos de configuración y microservicios base.
*   **Entornos / Overlays (`dev/`, `stage/`, `master/`)**:
    Implementan la lógica de personalización para cada namespace mediante parches y reemplazos automáticos de tags de imágenes:
    *   **`dev/`**: Despliega en el namespace `dev`. Asocia la etiqueta común `environment: dev` a todos los recursos generados y hereda las definiciones directas de `base/`.
    *   **`stage/`**: Despliega en el namespace `stage`. Añade la etiqueta común `environment: stage` y sobreescribe mediante Kustomize las imágenes de los microservicios para utilizar el tag `:stage` (`newTag: stage`), permitiendo pruebas E2E e integración en un entorno aislado con las últimas compilaciones.
    *   **`master/`**: Representa el entorno productivo final. Despliega en el namespace `master`. Asocia la etiqueta común `environment: master` y despliega la versión de imágenes estables `:stage` aprobadas tras superar todos los pipelines anteriores.

Esta separación mediante Kustomize evita la duplicación de código YAML, garantizando consistencia en las definiciones de red (`Services`) y despliegue (`Deployments`) en todos los ambientes.

## 6. Microservicios Seleccionados

Los 6 microservicios elegidos fueron:

- `circleguard-auth-service`
- `circleguard-identity-service`
- `circleguard-form-service`
- `circleguard-promotion-service`
- `circleguard-notification-service`
- `circleguard-gateway-service`

La razón de elegir esos 6 es que cubren el flujo principal del sistema y se comunican entre sí por HTTP, Kafka o Redis, lo que permite construir pruebas unitarias, de integración y E2E con valor real.

### Por qué sí fueron elegidos

- **auth-service**: es la puerta de entrada del sistema y valida autenticación.
- **identity-service**: gestiona la identidad anonimizada y la protección de datos.
- **form-service**: recibe la información clínica inicial y dispara eventos de negocio.
- **promotion-service**: procesa el estado de salud y coordina la lógica central del dominio.
- **notification-service**: envía notificaciones cuando cambia el estado del usuario.
- **gateway-service**: valida el acceso al campus a partir del estado del usuario.

### Por qué no se eligieron los otros dos

- **file-service**: es demasiado aislado y no aporta tanto al flujo transversal entre servicios.
- **dashboard-service**: se orienta más a visualización/consumo de datos que a la lógica crítica de negocio.

La selección se hizo para priorizar los flujos que realmente conectan autenticación, procesamiento de estado, notificación y control de acceso.

## 7. Pruebas Unitarias Nuevas

A continuación se resumen 5 pruebas unitarias nuevas relevantes:

| Servicio           | Prueba                                | Qué valida                                                            | Por qué es importante                                                                        |
| ------------------ | ------------------------------------- | --------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `auth-service`     | `JwtTokenServiceTest`                 | Generación y validación de JWT, incluyendo claims, expiración y firma | El JWT sostiene la autenticación de toda la plataforma; un error aquí rompe el acceso seguro |
| `auth-service`     | `DualChainAuthenticationProviderTest` | El fallback entre autenticación LDAP y autenticación local            | Es crítico para no bloquear usuarios si una fuente de autenticación falla                    |
| `identity-service` | `IdentityEncryptionConverterTest`     | Cifrado y descifrado de identificadores                               | Protege la privacidad de los datos personales y evita exposición de identidades reales       |
| `identity-service` | `IdentityVaultServiceTest`            | Generación de IDs anonimizados únicos y deterministas                 | Garantiza integridad en la capa de anonimización y trazabilidad consistente                  |
| `form-service`     | `SymptomMapperTest`                   | Mapeo de respuestas de síntomas a niveles de riesgo                   | Afecta directamente la detección temprana de casos y reduce falsos negativos                 |

Estas pruebas se enfocan en piezas sensibles del dominio, donde un fallo tendría impacto directo en seguridad, privacidad o detección de riesgo.

## 8. Pruebas De Integracion Nuevas

Las 5 pruebas de integración nuevas cubren comunicación entre servicios y dependencias externas:

| Test                               | Servicios Involucrados             | Que Valida                                                                                                                                                                         | Por Que Es Importante                                                                                                                                                                     |
| ------------------ | ------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `AuthIdentityIntegrationTest`      | auth -> identity                   | Login exitoso crea mapeo anonimo; login fallido NO llama a identity service (demuestra el corte temprano de la cadena de autenticacion)                                            | Garantiza que la separacion de responsabilidades entre autenticacion e identidad funcione correctamente y evita llamadas innecesarias cuando la autenticacion falla, optimizando recursos |
| `FormToPromotionKafkaTest`         | form -> Kafka -> promotion         | Envio de survey con/sin sintomas emite evento `survey.submitted` con flag `hasSymptoms`; aprobacion de certificado emite `certificate.validated` con estado APPROVED               | Valida que el pipeline de eventos de salud funcione end-to-end; si Kafka falla, el sistema no puede reaccionar ante brotes                                                                |
| `PromotionToNotificationKafkaTest` | promotion -> Kafka -> notification | Estado CONFIRMED emite `promotion.status.changed` + `alert.priority` (con affectedCount y eventType); estado SUSPECT solo emite `promotion.status.changed` sin alerta de prioridad | Asegura que las alertas se generen con la gravedad adecuada: un caso confirmado debe disparar notificaciones prioritarias, mientras que un sospechoso no debe generar panico innecesario  |
| `GatewayRedisIntegrationTest`      | gateway + Redis                    | Token GREEN permite acceso; token RED (CONTAGIED) deniega acceso; token invalido/manipulado deniega acceso                                                                         | Protege la barrera fisica del campus; si Redis retorna un estado incorrecto o el gateway no valida bien, usuarios contagiosos podrian ingresar                                            |
| `PromotionNeo4jTracingTest`        | promotion + Neo4j                  | `recordEncounter` delega correctamente al repository; `detectAndFormCircles` ejecuta query Cypher con filtro de duracion > 300s, cluster >= 3 usuarios y MERGE de Circle           | El rastreo de contactos es el nucleo del sistema; un error en la creacion de nodos o en la logica de deteccion de circulos invalidaria todo el modelo de contencion                       |

> **Configuracion de Tests**: Se configuro **H2 en memoria** para los tests de `form-service` mediante el archivo `services/circleguard-form-service/src/test/resources/application.yml`, permitiendo ejecutar las pruebas de integracion sin depender de PostgreSQL real.

## 9. Pruebas E2E Nuevas

Las 5 pruebas E2E principales se ejecutan con Newman y validan el recorrido completo del usuario a través del sistema:

| Flujo E2E           | Qué valida                                            | Por qué es importante                                                  |
| ------------------- | ----------------------------------------------------- | ---------------------------------------------------------------------- |
| Autenticación       | Login válido, login inválido y generación de token QR | Es la entrada al sistema; si falla, el resto del flujo no funciona     |
| Formulario de salud | Envío de survey con y sin síntomas                    | Determina si se detectan o no casos de riesgo correctamente            |
| Promoción de estado | Cambio de estado por un usuario autorizado            | Garantiza que solo personal permitido pueda modificar estados críticos |
| Notificación        | Generación y consumo de alertas según el estado       | Confirma que el sistema avisa cuando hay un caso relevante             |
| Acceso al campus    | Validación final del QR en gateway                    | Es la última barrera antes del ingreso físico al campus                |

Estas pruebas son claves porque confirman el recorrido completo del negocio, no solo módulos aislados.

## 10. Pruebas De Locust

Se implementaron dos tipos de pruebas de carga con Locust:

### 10.1 Performance Test

Mide el comportamiento bajo una carga normal y representativa del día a día.

- 20 usuarios concurrentes
- duración de 60 segundos
- sirve para revisar latencia, estabilidad y tasa de error (al final genera reportes)

### 10.2 Stress Test

Mide el comportamiento bajo carga alta para encontrar el punto de degradación.

- 30 usuarios concurrentes
- duración de 60 segundos
- sirve para detectar cuellos de botella y límites del sistema (al final genera reportes)

### Reporte generado

El pipeline genera un reporte consolidado llamado `PERFORMANCE_REPORT.md`, además de los archivos CSV con estadísticas, fallos y excepciones.

## 11. Cierre

El proyecto quedó armado para trabajar con un entorno local realista basado en Windows + Jenkins + Kind + Kubernetes, con pipelines separados por etapa y con pruebas orientadas a validar tanto la lógica interna como la integración completa del sistema.

La combinación de pipelines, scripts de automatización, credenciales, pruebas y despliegue por namespaces permite tener un flujo ordenado de desarrollo, validación y publicación.
