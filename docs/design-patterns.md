# Patrones de Diseño de Infraestructura

Este documento describe los patrones de infraestructura implementados en el proyecto CircleGuard, con ejemplos concretos de cada archivo involucrado.

---

## Patron 1: External Configuration — Capa Kubernetes (K8s ConfigMap & Secret)

### Contexto

Los microservicios Spring Boot necesitan conectarse a PostgreSQL, Kafka, Redis, Neo4j y LDAP. Los valores de conexion (URLs, usuarios, contrasenas) varian entre ambientes (dev, stage, master) y no deben estar hardcodeados dentro de las imagenes Docker.

### Solucion

Toda la configuracion se inyecta en los Pods de Kubernetes a traves de dos recursos:

| Recurso | Archivo | Proposito |
|---------|---------|-----------|
| `ConfigMap` | `k8s/base/configmap.yaml` | Variables de configuracion no sensibles: URLs de servicios, nombres de topics Kafka, puertos, dialecto de BD |
| `Secret` | `k8s/base/secret.yaml` | Credenciales sensibles: contrasenas de BD, Neo4j, LDAP |

### Como se aplica

Cada `Deployment` en `k8s/base/` carga la configuracion mediante `envFrom`, lo que inyecta **todas** las claves del ConfigMap y Secret como variables de entorno del contenedor:

```yaml
# Ejemplo: auth-deployment.yaml
containers:
  - name: circleguard-auth-service
    image: srcracles/circleguard-auth-service:dev
    envFrom:
      - configMapRef:
          name: circleguard-config    # Carga TODAS las keys del ConfigMap
      - secretRef:
          name: circleguard-secrets   # Carga TODAS las keys del Secret
    env:
      - name: SPRING_APPLICATION_NAME
        value: "circleguard-auth-service"
      - name: SPRING_DATASOURCE_URL
        value: "jdbc:postgresql://postgresql.infra.svc.cluster.local:5432/circleguard_auth"
```

Spring Boot recoge automaticamente las variables de entorno en mayusculas como propiedades de aplicacion (`SPRING_DATASOURCE_URL` → `spring.datasource.url`), por lo que no se necesita ninguna anotacion especial.

### Variables en el ConfigMap

| Variable | Descripcion |
|----------|-------------|
| `SPRING_DATASOURCE_URL` | URL base de PostgreSQL (cada servicio la sobreescribe con su propia DB) |
| `SPRING_DATASOURCE_USERNAME` | Usuario de PostgreSQL |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Broker de Kafka en el namespace `infra` |
| `SPRING_DATA_REDIS_HOST` / `PORT` | Redis para el gateway service |
| `SPRING_NEO4J_URI` | Bolt URI de Neo4j para el promotion service |
| `SPRING_LDAP_URLS` / `BASE` | LDAP del campus para autenticacion |
| `JWT_SECRET` / `JWT_EXPIRATION` | Configuracion de tokens JWT |
| `QR_SECRET` / `QR_EXPIRATION` | Configuracion de tokens QR de acceso |
| `AUTH_API_URL` | URL del auth-service (usada por notification-service) |
| `IDENTITY_SERVICE_URL` | URL del identity-service (usada por auth-service) |

### Variables en el Secret

| Variable | Descripcion |
|----------|-------------|
| `SPRING_DATASOURCE_PASSWORD` | Contrasena de PostgreSQL |
| `SPRING_NEO4J_AUTHENTICATION_PASSWORD` | Contrasena de Neo4j |
| `SPRING_LDAP_PASSWORD` | Contrasena del bind user de LDAP |

### Resultado

Ningun archivo `application.properties` o `application.yml` dentro de las imagenes Docker contiene URLs ni credenciales de produccion. Los valores de conexion se resuelven en tiempo de ejecucion cuando el Pod arranca en el cluster.

---

## Patron 2: External Configuration — Capa CI/CD (Jenkins Global Properties)

### Contexto

Los Jenkinsfiles de los 10 pipelines (8 dev, 1 stage, 1 master) compartian valores hardcodeados como `DOCKER_USER = 'srcracles'` y las IDs de las credenciales de Jenkins (`dockerhub-credentials`, `github-token`). Esto hace dificil que otro equipo o fork use el proyecto sin editar todos los archivos.

### Solucion

Se implemento en dos niveles:

#### Nivel 1: Jenkins Global Properties (por usuario/equipo)

Estas variables se configuran **una sola vez** en `Manage Jenkins > System > Global properties > Environment variables`:

| Variable Jenkins | Descripcion | Default (fallback) |
|-----------------|-------------|-------------------|
| `CG_DOCKER_USER` | Usuario de DockerHub | `srcracles` |
| `CG_GITHUB_OWNER` | Owner del repo en GitHub | `SrCracles` |
| `CG_GITHUB_REPO` | Nombre del repo GitHub | `circle-guard-public` |

En los Jenkinsfiles, se referencian con fallback silencioso para no romper entornos existentes:

```groovy
environment {
    DOCKER_USER = "${env.CG_DOCKER_USER ?: 'srcracles'}"
    DOCKERHUB_CREDENTIALS_ID = "${env.CG_DOCKERHUB_CREDENTIALS_ID ?: 'dockerhub-credentials'}"
}
```

#### Nivel 2: `jenkins/jenkins.properties` (configuracion del proyecto)

Las rutas de archivos de tests que pertenecen al repositorio (no al usuario) se centralizan en `jenkins/jenkins.properties`:

```properties
NEWMAN_COLLECTION=tests/postman/circle-guard-e2e-collection.json
NEWMAN_ENVIRONMENT=tests/postman/circle-guard-environment.json
LOCUST_PERF_FILE=tests/locustfile-performance.py
LOCUST_STRESS_FILE=tests/locustfile-stress.py
```

El `Jenkinsfile-stage` las carga al inicio con `readProperties`:

```groovy
stage('Checkout') {
    steps {
        checkout scm
        script {
            def props = readProperties file: 'jenkins/jenkins.properties'
            env.NEWMAN_COLLECTION = props.NEWMAN_COLLECTION
            env.NEWMAN_ENVIRONMENT = props.NEWMAN_ENVIRONMENT
        }
    }
}
```

### Resultado

Para usar el proyecto en un fork o con otra cuenta de DockerHub, basta con cambiar las 5 variables en Jenkins UI, sin tocar ningun archivo del repositorio.

---

## Resumen: Donde vive cada configuracion

| Tipo de Configuracion | Donde se define | Quien lo cambia |
|-----------------------|----------------|-----------------|
| URLs de servicios K8s | `k8s/base/configmap.yaml` | Equipo (commit en repo) |
| Credenciales de BD / LDAP / Neo4j | `k8s/base/secret.yaml` | Equipo (commit en repo) |
| Usuario de DockerHub | Jenkins Global Properties (`CG_DOCKER_USER`) | Cada equipo en su Jenkins |
| IDs de credenciales Jenkins | Jenkins Global Properties (`CG_*_CREDENTIALS_ID`) | Cada equipo en su Jenkins |
| Rutas de archivos de tests | `jenkins/jenkins.properties` | Equipo (commit en repo) |
| Nombre/owner del repo GitHub | Jenkins Global Properties (`CG_GITHUB_*`) | Cada equipo en su Jenkins |
