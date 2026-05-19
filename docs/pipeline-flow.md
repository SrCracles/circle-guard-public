# Flujo de Pipelines CI/CD

Este documento describe el flujo completo de cada pipeline Jenkins en el proyecto CircleGuard, desde el desarrollo hasta produccion.

---

## Overview del Flujo

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           CIRCLEGUARD CI/CD FLOW                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   DEV                          STAGE                        MASTER          │
│   ───                          ─────                        ──────          │
│                                                                             │
│  ┌──────────┐                 ┌──────────┐               ┌──────────┐      │
│  │  Code    │                 │ Pull :dev│               │Teardown  │      │
│  │  Commit  │───────────────>│  images  │               │ existing │      │
│  └──────────┘                 └────┬─────┘               └────┬─────┘      │
│       │                            │                          │            │
│       ▼                            ▼                          ▼            │
│  ┌──────────┐                 ┌──────────┐               ┌──────────┐      │
│  │  Unit &  │                 │ Deploy to│               │ Pull     │      │
│  │  Integ.  │                 │  Stage   │               │ :stage   │      │
│  │  Tests   │                 │   K8s    │               │  images  │      │
│  └────┬─────┘                 └────┬─────┘               └────┬─────┘      │
│       │                            │                          │            │
│       ▼                            ▼                          ▼            │
│  ┌──────────┐                 ┌──────────┐               ┌──────────┐      │
│  │ Build    │                 │   E2E    │               │ Deploy   │      │
│  │   JAR    │                 │  Tests   │               │  to K8s  │      │
│  └────┬─────┘                 │ (Newman) │               │ (master) │      │
│       │                       └────┬─────┘               └────┬─────┘      │
│       ▼                            │                          │            │
│  ┌──────────┐                      ▼                          ▼            │
│  │ Build    │                 ┌──────────┐               ┌──────────┐      │
│  │ Docker   │                 │  Locust  │               │  Git     │      │
│  │  Image   │                 │  Smoke   │               │  Tag     │      │
│  └────┬─────┘                 └────┬─────┘               └──────────┘      │
│       │                            │                                       │
│       ▼                            ▼                                       │
│  ┌──────────┐                 ┌──────────┐                                 │
│  │  Push    │                 │ Promote  │                                 │
│  │  :dev    │                 │ :dev to  │                                 │
│  │  tag     │                 │ :stage   │                                 │
│  └────┬─────┘                 └────┬─────┘                                 │
│       │                            │                                       │
│       ▼                            ▼                                       │
│  ┌──────────┐                 ┌──────────┐                                 │
│  │ Deploy   │                 │  Push    │                                 │
│  │  to K8s  │                 │ :stage   │                                 │
│  │  (dev)   │                 │   tag    │                                 │
│  └──────────┘                 └──────────┘                                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 1. Pipelines de Desarrollo (Dev)

**Ubicacion:** `jenkins/dev/Jenkinsfile-{auth,identity,form,promotion,notification,gateway}`

**Objetivo:** Compilar, probar y desplegar cada microservicio individualmente en el ambiente de desarrollo.

### Flujo paso a paso:

| Paso | Accion | Comando/Detalle |
|------|--------|-----------------|
| 1 | **Checkout** | Descarga el codigo fuente del repositorio |
| 2 | **Test** | Ejecuta pruebas unitarias e integracion: `.\\gradlew.bat :services:<servicio>:test` |
| 3 | **Build JAR** | Compila el archivo JAR ejecutable: `.\\gradlew.bat :services:<servicio>:bootJar` |
| 4 | **Build Docker Image** | Construye la imagen con tag `dev`: `docker build -t srcracles/<servicio>:dev` |
| 5 | **Push Docker Image** | Sube a DockerHub: `docker push srcracles/<servicio>:dev` |
| 6 | **Deploy to Kind Dev** | Actualiza el deployment en Kubernetes: `kubectl set image deployment/<servicio> <servicio>=srcracles/<servicio>:dev -n dev` |

### Servicios y puertos:

| Servicio | Puerto | Pipeline |
|----------|--------|----------|
| auth-service | 8180 | `Jenkinsfile-auth` |
| identity-service | 8083 | `Jenkinsfile-identity` |
| form-service | 8086 | `Jenkinsfile-form` |
| promotion-service | 8088 | `Jenkinsfile-promotion` |
| notification-service | 8082 | `Jenkinsfile-notification` |
| gateway-service | 8087 | `Jenkinsfile-gateway` |

### Tag DockerHub resultante:
```
srcracles/circleguard-auth-service:dev
srcracles/circleguard-identity-service:dev
srcracles/circleguard-form-service:dev
srcracles/circleguard-promotion-service:dev
srcracles/circleguard-notification-service:dev
srcracles/circleguard-gateway-service:dev
```

---

## 2. Pipeline de Staging (Stage)

**Ubicacion:** `jenkins/stage/Jenkinsfile-stage`

**Objetivo:** Validar la integracion completa de todos los microservicios mediante pruebas E2E y de rendimiento antes de promover a produccion.

### Flujo paso a paso:

| Paso | Accion | Comando/Detalle |
|------|--------|-----------------|
| 1 | **Checkout** | Descarga el codigo fuente del repositorio |
| 2 | **Pull dev images** | Descarga todas las imagenes `:dev` de DockerHub para los 6 servicios |
| 3 | **Deploy to Stage** | Despliega las imagenes `:dev` en el namespace `stage` de Kind |
| 4 | **Port-forward** | Expone los servicios localmente para que Newman pueda acceder (puertos 8180, 8086, 8087, 8088) |
| 5 | **E2E Tests (Newman)** | Ejecuta la coleccion Postman: `newman run tests/postman/circle-guard-e2e-collection.json` |
| 6 | **Locust Smoke Test** | Prueba de carga ligera: `locust -f tests/locustfile.py -u 10 -r 2 --run-time 30s` |
| 7 | **Promote to stage** | Si todo pasa: `docker tag <servicio>:dev <servicio>:stage` y push a DockerHub |

### Flujo de decision:

```
┌─────────────┐
│  E2E Tests  │
│   Newman    │
└──────┬──────┘
       │
       ▼
┌─────────────┐     ┌─────────────┐
│   PASS?     │──NO─┤   FAIL      │
└──────┬──────┘     │  Pipeline   │
       │YES         │   ENDS      │
       ▼            └─────────────┘
┌─────────────┐
│ Locust Test │
└──────┬──────┘
       │
       ▼
┌─────────────┐     ┌─────────────┐
│   PASS?     │──NO─┤   FAIL      │
└──────┬──────┘     │  Pipeline   │
       │YES         │   ENDS      │
       ▼            └─────────────┘
┌─────────────┐
│   Promote   │
│   :stage    │
└─────────────┘
```

### Tag DockerHub resultante (solo si pasa todo):
```
srcracles/circleguard-auth-service:stage
srcracles/circleguard-identity-service:stage
srcracles/circleguard-form-service:stage
srcracles/circleguard-promotion-service:stage
srcracles/circleguard-notification-service:stage
srcracles/circleguard-gateway-service:stage
```

---

## 3. Pipeline de Produccion (Master)

**Ubicacion:** `jenkins/master/Jenkinsfile-master`

**Objetivo:** Desplegar la version validada en staging al ambiente de produccion (master).

### Flujo paso a paso:

| Paso | Accion | Comando/Detalle |
|------|--------|-----------------|
| 1 | **Checkout** | Descarga el codigo fuente del repositorio |
| 2 | **Teardown** | Elimina deployments y services existentes en namespace `master`: `kubectl delete deployment --all -n master` |
| 3 | **Pull stage images** | Descarga todas las imagenes `:stage` de DockerHub |
| 4 | **Deploy to Master** | Aplica manifiestos K8s y actualiza imagenes a `:stage` en namespace `master` |
| 5 | **Git Tag** | Crea y push un tag de release: `git tag -a v<BUILD_NUMBER>` |

### Flujo de despliegue:

```
┌─────────────────┐
│   Teardown      │
│  existing K8s   │
│   (master ns)   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Pull :stage    │
│  from DockerHub │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Deploy fresh   │
│   to master ns  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Git Tag        │
│  v<BUILD_NUMBER>│
└─────────────────┘
```

### Tag Git resultante:
```
v1
v2
v3
...
v<N>
```

---

## Estrategia de Tags DockerHub

```
┌────────────────────────────────────────────────────────────┐
│                    DOCKERHUB TAGS                          │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  :dev          :stage          :latest (no usado en CI)   │
│   │              │                                          │
│   │  Promote    │                                          │
│   ├────────────>│                                          │
│   │   (stage)   │                                          │
│   │              │                                          │
│   │              │  Deploy                                   │
│   │              ├──────────> K8s master                     │
│   │              │   (master)                                │
│   │              │                                          │
│   ▼              ▼                                          │
│                                                            │
│  Creado por     Creado por     Consumido por               │
│  pipelines dev  pipeline stage pipeline master              │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

---

## Resumen de Archivos

| Pipeline | Archivo | Servicios |
|----------|---------|-----------|
| Dev - Auth | `jenkins/dev/Jenkinsfile-auth` | auth-service |
| Dev - Identity | `jenkins/dev/Jenkinsfile-identity` | identity-service |
| Dev - Form | `jenkins/dev/Jenkinsfile-form` | form-service |
| Dev - Promotion | `jenkins/dev/Jenkinsfile-promotion` | promotion-service |
| Dev - Notification | `jenkins/dev/Jenkinsfile-notification` | notification-service |
| Dev - Gateway | `jenkins/dev/Jenkinsfile-gateway` | gateway-service |
| Stage | `jenkins/stage/Jenkinsfile-stage` | Todos (E2E + Locust) |
| Master | `jenkins/master/Jenkinsfile-master` | Todos (deploy prod) |

---

## Pre-requisitos

1. **Jenkins** corriendo nativo en la PC (no en contenedor)
2. **Kind** (Kubernetes in Docker) configurado con los namespaces `dev`, `stage`, `master`
3. **Docker** instalado y configurado con credenciales de DockerHub
4. **kubectl** configurado para comunicarse con el cluster de Kind
5. Credencial `dockerhub-credentials` configurada en Jenkins
6. **Newman** instalado para E2E tests
7. **Locust** instalado para performance tests
8. Manifiestos Kubernetes en `k8s/dev/`, `k8s/stage/`, `k8s/master/`
