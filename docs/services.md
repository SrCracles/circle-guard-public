# Microservicios del Sistema

## 1. Los Microservicios

Estos son los 8 microservicios que componen la plataforma CircleGuard:

| # | Microservicio | Puerto | Descripción |
|---|--------------|--------|---------------|
| 1 | **circleguard-auth-service** | `8180` | Punto de entrada de autenticacion. Se comunica via HTTP con **identity-service** para resolver y verificar la identidad anónima del usuario. |
| 2 | **circleguard-identity-service** | `8083` | Vault de identidades. Recibe llamadas de **auth-service** y produce eventos Kafka (`audit.identity.accessed`). Contiene la lógica de encriptación y anonimización de datos sensibles. |
| 3 | **circleguard-form-service** | `8086` | Motor de formularios de salud. Produce eventos Kafka (`survey.submitted`, `certificate.validated`) que alimentan a promotion-service para recalcular el riesgo del usuario. |
| 4 | **circleguard-promotion-service** | `8088` | **El corazon del sistema**. Consume Kafka de form-service, usa Neo4j para grafos de contacto y produce eventos Kafka de promocion de estado para el notification-service. |
| 5 | **circleguard-notification-service** | `8082` | Consume eventos Kafka de promotion-service y hace llamadas HTTP a **auth-service** para validar permisos antes de enviar alertas. Es el encargado de la mensajería multi-canal (email/SMS/push). |
| 6 | **circleguard-gateway-service** | `8087` | Validacion de QR para acceso fisico al campus. Usa **Redis** para mantener el estado temporal de los tokens. |
| 7 | **circleguard-file-service** | `8085` | Manejo de archivos y adjuntos (certificados, resultados médicos). Se expone a través de endpoints REST para la subida y descarga de archivos. |
| 8 | **circleguard-dashboard-service** | `8084` | Lee de promotion-service y presenta analíticas globales de la salud del campus aplicando técnicas de k-anonimidad. |

## 2. Flujos de Comunicacion

### Diagrama de Comunicacion entre Servicios

```
+-------------+     HTTP      +-----------------+
|   Auth      |-------------->|    Identity     |
|  Service    |<--------------|    Service      |
+-------------+               +-----------------+
       ^                              |
       | HTTP (permisos)              | Kafka
       |                              v
+-----------------+          +-----------------+
|  Notification   |<--Kafka--|    Identity     |
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
       ^                              |
       | HTTP (datos)                 | HTTP (uploads)
       v                              v
+-----------------+          +-----------------+
|   Dashboard     |          |      File       |
|    Service      |          |    Service      |
+-----------------+          +-----------------+
```

### Flujo E2E Completo

1. **Autenticacion**: Usuario se autentica (`auth-service` -> `identity-service`)
2. **Formulario de Salud**: Usuario envia formulario de salud (`form-service` adjunta archivos usando `file-service` si es necesario)
3. **Promocion de Estado**: El estado de salud se procesa y promueve (`promotion-service` procesa en Neo4j)
4. **Notificacion**: Se generan alertas multi-canal (`notification-service` consume Kafka y consulta permisos a `auth-service`)
5. **Acceso al Campus**: Usuario escanea QR en puerta (`gateway-service` valida en Redis)
6. **Analíticas y Dashboard**: Personal autorizado visualiza datos consolidados (`dashboard-service` consulta a `promotion-service` mediante HTTP y formatea los datos)

### Patrones de Comunicacion Identificados

| Tipo | Origen | Destino | Tecnologia | Proposito |
|------|--------|---------|------------|-----------|
| Sincrono HTTP | auth-service | identity-service | RestTemplate | Mapeo de identidades anonimas |
| Sincrono HTTP | notification-service | auth-service | RestTemplate | Validacion de permisos de alerta |
| Sincrono HTTP | dashboard-service | promotion-service | Feign/RestTemplate | Obtener datos y métricas base de la red de contactos |
| Sincrono HTTP | front/form-service | file-service | HTTP/Multipart | Subida y descarga de adjuntos para las encuestas médicas |
| Asincrono Kafka | form-service | promotion-service | Kafka | Disparar promocion de estado ante survey |
| Asincrono Kafka | form-service | promotion-service | Kafka | Restaurar acceso ante certificado validado |
| Asincrono Kafka | promotion-service | notification-service | Kafka | Notificar cambio de estado |
| Asincrono Kafka | promotion-service | notification-service | Kafka | Alertar administradores de brotes |
| Asincrono Kafka | promotion-service | notification-service | Kafka | Cancelar reservas de espacios |
| Cache | gateway-service | Redis | Spring Data Redis | Validar tokens QR de acceso |

## 3. Estructura de Carpetas del Proyecto

Todo el trabajo de pipelines, Kubernetes, pruebas y automatizacion se mantendra **dentro** del repositorio `circle-guard-public`:

```
circle-guard-public/
├── setup-kind.ps1                     # Script para crear cluster Kind + namespaces + infra
├── docs/
│   ├── services.md                    # Este documento
│   ├── branching-strategy.md          # Estrategia de ramas, commits y tags Docker (HU-02)
│   ├── tests-docs.md                  # Documentación de Pruebas
│   └── terraform-azure.md             # Guia de despliegue cloud con Terraform y AKS
├── terraform/
│   ├── backend.tf                      # Backend remoto AzureRM para estado Terraform
│   ├── modules/
│   │   ├── aks/                        # Modulo de cluster AKS y recursos asociados
│   │   └── infra/                      # Modulo de infraestructura Kubernetes
│   └── envs/
│       ├── dev/                        # tfvars y backend config de dev
│       ├── stage/                      # tfvars y backend config de stage
│       └── master/                     # tfvars y backend config de master
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
│       ├── kustomization.yaml           # Overlay Kustomize para namespace master (tag :stage)
│       └── ingress.yaml                 # Ingress TLS para auth y gateway (HU-37)
│   ├── rbac/                            # RBAC de Jenkins (HU-36)
│   │   ├── jenkins-deployer.yaml
│   │   └── jenkins-rolebindings.yaml
│   ├── infra/                           # Dependencias externas (PostgreSQL, Kafka, Redis, Neo4j, OpenLDAP, Zookeeper, SonarQube, Prometheus, Grafana)
│   │   ├── kustomization.yaml           # Despliegue conjunto de infraestructura
│   │   ├── postgresql.yaml              # Deployment + Service PostgreSQL (puerto 5432)
│   │   ├── zookeeper.yaml               # Deployment + Service Zookeeper (puerto 2181)
│   │   ├── kafka.yaml                   # Deployment + Service Kafka (puerto 9092)
│   │   ├── redis.yaml                   # Deployment + Service Redis (puerto 6379)
│   │   ├── neo4j.yaml                   # Deployment + Service Neo4j (puertos 7687, 7474)
│   │   ├── openldap.yaml                # Deployment + Service OpenLDAP (puertos 389, 636)
│   │   ├── prometheus.yaml              # Prometheus + scrape configs de microservicios
│   │   ├── grafana.yaml                 # Grafana + dashboards y alertas provisionadas
│   │   ├── elasticsearch.yaml           # Elasticsearch para logs centralizados
│   │   ├── logstash.yaml                # Logstash ingest de logs JSON
│   │   ├── kibana.yaml                  # Kibana + dashboards de logs
│   │   └── jaeger.yaml                  # Jaeger tracing distribuido
│   ├── grafana-dashboards/              # Dashboards exportados en JSON (versionados)
├── jenkins/
│   ├── dev/
│   │   ├── Jenkinsfile-auth             # Pipeline dev para auth-service
│   │   ├── Jenkinsfile-identity         # Pipeline dev para identity-service
│   │   ├── Jenkinsfile-form             # Pipeline dev para form-service
│   │   ├── Jenkinsfile-promotion        # Pipeline dev para promotion-service
│   │   ├── Jenkinsfile-notification     # Pipeline dev para notification-service
│   │   ├── Jenkinsfile-gateway          # Pipeline dev para gateway-service
│   │   ├── Jenkinsfile-file             # Pipeline dev para file-service
│   │   └── Jenkinsfile-dashboard        # Pipeline dev para dashboard-service
│   ├── stage/
│   │   └── Jenkinsfile-stage            # Pipeline stage (E2E + Locust)
│   └── master/
│       └── Jenkinsfile-master           # Pipeline master (versionado semantico, deploy K8s + release notes)
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
│   ├── circleguard-file-service/
│   │   └── Dockerfile                   # Imagen Docker para file-service (port 8085)
│   └── circleguard-dashboard-service/
│       └── Dockerfile                   # Imagen Docker para dashboard-service (port 8084)
└── ... (archivos existentes del proyecto)
```

## 4. Resumen de Decisiones de Arquitectura de Pipelines

| Aspecto | Decision |
|---------|----------|
| **Orquestador** | Jenkins corriendo nativo en la PC (no en contenedor) |
| **Runtime de Tests** | Kubernetes (Kind) gestionado via `kubectl` |
| **Registry de Imagenes** | DockerHub (usuario: `srcracles`) |
| **E2E Testing** | Newman CLI con colecciones Postman |
| **Performance Testing** | Locust (Python) |
| **Estrategia de Tags** | `dev` -> `stage` -> `vX.Y.Z` y `master`. Dev compila y pushea `:dev`. Stage prueba y promueve a `:stage`. Master calcula version semantica, pushea tags inmutables (`:vX.Y.Z`) y mutables (`:master`), y deploya en K8s. Ver [`docs/branching-strategy.md`](branching-strategy.md). |
| **Estrategia de Branching** | Ramas permanentes `develop`, `stage`, `master`; ramas temporales `feature/`, `fix/`, `hotfix/`, `docs/`, `test/`, `infra/`. Conventional Commits. Ver [`docs/branching-strategy.md`](branching-strategy.md). |
| **Release Notes** | GitHub API con token personal |
| **Permisos Jenkins** | Usuario `jenkins` con acceso a `kubeconfig` y permisos adecuados |

## 5. Matriz de Servicios vs. Ambientes

| Servicio | Dev Pipeline | Stage Pipeline | Master Pipeline |
|----------|-------------|----------------|-----------------|
| auth-service | Unit Tests -> Build -> Push (`:dev`) | Pull (`:dev`) -> E2E + Locust -> Promote (`:stage`) | Versioning -> Push (`:vX.Y.Z`, `:master`) -> Deploy K8s |
| identity-service | Unit Tests -> Build -> Push (`:dev`) | Pull (`:dev`) -> E2E + Locust -> Promote (`:stage`) | Versioning -> Push (`:vX.Y.Z`, `:master`) -> Deploy K8s |
| form-service | Unit Tests -> Build -> Push (`:dev`) | Pull (`:dev`) -> E2E + Locust -> Promote (`:stage`) | Versioning -> Push (`:vX.Y.Z`, `:master`) -> Deploy K8s |
| promotion-service | Unit Tests -> Build -> Push (`:dev`) | Pull (`:dev`) -> E2E + Locust -> Promote (`:stage`) | Versioning -> Push (`:vX.Y.Z`, `:master`) -> Deploy K8s |
| notification-service | Unit Tests -> Build -> Push (`:dev`) | Pull (`:dev`) -> E2E + Locust -> Promote (`:stage`) | Versioning -> Push (`:vX.Y.Z`, `:master`) -> Deploy K8s |
| gateway-service | Unit Tests -> Build -> Push (`:dev`) | Pull (`:dev`) -> E2E + Locust -> Promote (`:stage`) | Versioning -> Push (`:vX.Y.Z`, `:master`) -> Deploy K8s |
| file-service | Unit Tests -> Build -> Push (`:dev`) | Pull (`:dev`) -> E2E + Locust -> Promote (`:stage`) | Versioning -> Push (`:vX.Y.Z`, `:master`) -> Deploy K8s |
| dashboard-service | Unit Tests -> Build -> Push (`:dev`) | Pull (`:dev`) -> E2E + Locust -> Promote (`:stage`) | Versioning -> Push (`:vX.Y.Z`, `:master`) -> Deploy K8s |

> **Nota**: La infraestructura compartida (Kafka, Neo4j, PostgreSQL, Redis, Zookeeper, OpenLDAP) se levanta como parte del ambiente Stage para las pruebas E2E y de rendimiento.