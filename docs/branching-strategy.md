# Estrategia de Branching — CircleGuard 

Este documento describe la estrategia de ramas adoptada en el repositorio `circle-guard-public`, su relación con los pipelines de Jenkins y la promoción de imágenes Docker entre ambientes.

---

## Modelo adoptado

Para CircleGuard se eligió **GitFlow adaptado**. El proyecto no usa GitHub Flow, porque ese modelo asume una única rama principal y despliegues continuos desde ella; aquí conviven tres ambientes (`dev`, `stage`, `master`) con validaciones intermedias (pruebas E2E, OWASP ZAP, Locust) antes de llegar a producción.

Tampoco se aplicó GitFlow en su forma clásica. Se conservan sus ideas centrales - rama de integración (`develop`), rama de producción (`master`), ramas de trabajo (`feature/*`, `hotfix/*`) - pero se sustituyen las ramas efímeras `release/*` por una rama permanente `stage`, alineada con el namespace de pre-producción y el pipeline `circle-guard-stage`.

En la práctica, el flujo queda así:

```
feature|fix|docs|test|infra/*  →  develop  →  stage  →  master
hotfix/*  →  master  (con backport a stage y develop)
```

Cada rama permanente dispara su pipeline en Jenkins y el tag Docker correspondiente.

---

## Ramas permanentes

CircleGuard es un **monorepo**: los ocho microservicios viven en el mismo repositorio (`services/`), bajo las **mismas** ramas `develop`, `stage` y `master`. No existe una rama de integración por servicio (por ejemplo, no hay `develop-auth` ni `develop-promotion`).

La separación por microservicio ocurre en la capa de CI/CD, no en Git:

| Capa | Qué se separa por servicio | Qué es compartido |
|------|---------------------------|-------------------|
| **Git** | Nada (mismo historial para todos) | `develop`, `stage`, `master` |
| **Jenkins** | Un job dev por servicio (`Jenkinsfile-auth`, `Jenkinsfile-identity`, …) | Todos hacen checkout de la misma rama (`develop`) |
| **Docker** | Una imagen por servicio (`srcracles/circleguard-auth-service`, …) | Mismo tag de ambiente (`:dev`, `:stage`, `:master`) |

Así, un merge a `develop` puede incluir cambios en uno o varios microservicios a la vez. Cada pipeline dev compila **solo su servicio** (vía `SERVICE_NAME` en el Jenkinsfile), pero lee el **mismo commit** de la rama. Si solo cambió `auth-service`, igual conviene ejecutar los ocho jobs dev (o al menos el afectado) para mantener las imágenes `:dev` alineadas con el estado de la rama.

El repositorio define tres ramas de larga duración, una por **ambiente** (no por microservicio):

| Rama | Rol | Pipeline | Tag Docker |
|------|-----|----------|------------|
| `develop` | Integración del trabajo diario | 8 jobs `circle-guard-*-dev` | `:dev` |
| `stage` | Validación pre-producción | `circle-guard-stage` | `:stage` |
| `master` | Producción y versionado | `circle-guard-master` | `:vX.Y.Z` y `:master` |

Los cambios no se integran con push directo a estas ramas, sino mediante merge desde ramas temporales. La promoción es secuencial: primero `develop`, luego `stage` cuando dev esté estable, y por último `master` cuando stage haya superado todas las quality gates.

Creación inicial (si aún no existen las ramas):

```bash
git checkout master
git branch develop
git branch stage
git push -u origin develop stage master
```

En Jenkins, cada job debe apuntar a su rama: `*/develop` (pipelines dev), `*/stage` (pipeline stage) y `*/master` (pipeline master). Ver [`setup-guide.md`](setup-guide.md).

---

## Ramas temporales

El trabajo se realiza en ramas de corta vida con prefijos acordados:

| Prefijo | Uso | Base habitual |
|---------|-----|---------------|
| `feature/` | Nuevas funcionalidades o historias de usuario | `develop` |
| `fix/` | Correcciones no urgentes | `develop` |
| `hotfix/` | Correcciones urgentes en producción | `master` |
| `docs/` | Documentación | `develop` |
| `test/` | Pruebas y cobertura | `develop` |
| `infra/` | K8s, Terraform, Jenkins, scripts | `develop` |

Se recomienda nombres en minúsculas con guiones e identificador de HU cuando aplique (por ejemplo, `feature/HU-02-branching-strategy`). Tras el merge, la rama temporal se elimina.

---

## Convención de commits

El equipo sigue **Conventional Commits** con el formato `<tipo>(<alcance>): <descripción>`. Los tipos más usados son `feat`, `fix`, `docs`, `test`, `ci`, `refactor`, `chore` e `infra`.

El pipeline master calcula la versión semántica (`vX.Y.Z`) a partir de los commits desde el último tag Git: cada `feat:` incrementa el **MINOR** y el resto de commits incrementan el **PATCH**.

Ejemplos del proyecto:

```
feat: integrate JaCoCo test coverage reports and SonarQube validation
fix: identity security config for probes and increase stage rollout timeout
docs: add branching strategy for HU-02
test: add unit tests for AdminController and BuildingService to reach 60% coverage
ci: gate stage rollouts sequentially from master pipeline
```

---

## Flujo de una historia de usuario

El recorrido típico de una HU desde el desarrollo hasta producción es el siguiente:

1. Crear una rama desde `develop` (por ejemplo, `feature/HU-XX-descripcion`).
2. Desarrollar y commitear siguiendo Conventional Commits.
3. Integrar en `develop` mediante merge o pull request.
4. Ejecutar los ocho pipelines dev en Jenkins (rama `develop`), que compilan y publican imágenes con tag `:dev`.
5. Promover el código con merge `develop` → `stage`.
6. Ejecutar `circle-guard-stage`: despliega en el namespace `stage`, corre E2E, OWASP ZAP y Locust, y promueve las imágenes a `:stage`.
7. Si stage pasa, hacer merge `stage` → `master`.
8. Ejecutar `circle-guard-master`: calcula `vX.Y.Z`, publica tags `:vX.Y.Z` (inmutable) y `:master` (mutable), despliega en producción y genera la Release en GitHub.

Los pipelines dev pueden correr en paralelo; stage y master se ejecutan de forma secuencial, una vez validado el paso anterior.

---

## Tags Docker

La promoción de imágenes replica el mismo eje que las ramas:

```
:dev  →  :stage  →  :vX.Y.Z  (inmutable)
                 └→  :master  (última release)
```

- **`:dev`**: lo publica cada pipeline dev al compilar en `develop`.
- **`:stage`**: lo publica el pipeline stage tras retaggear desde `:dev`.
- **`:vX.Y.Z`** y **`:master`**: los publica el pipeline master tras retaggear desde `:stage`.

Aplica a los ocho microservicios (`circleguard-auth-service`, `identity-service`, `form-service`, `promotion-service`, `notification-service`, `gateway-service`, `file-service`, `dashboard-service`). El tag Git `vX.Y.Z` creado en master es independiente del tag Docker y sirve como referencia para el versionado semántico.

---

## Hotfixes

Para incidentes en producción se crea una rama `hotfix/` desde `master`, se corrige con commits `fix:`, se integra en `master` y se ejecuta el pipeline master. La misma corrección debe propagarse a `stage` y `develop` (backport) para mantener las ramas alineadas.

---

## Referencias

- [`setup-guide.md`](setup-guide.md) - configuración de Jenkins y orden de pipelines
- [`services.md`](services.md) - matriz de servicios, ambientes y tags
- [`tests-docs.md`](tests-docs.md) - quality gates en dev y stage
- [`change-management.md`](change-management.md) - aprobación y trazabilidad en producción (HU-24)
- [`rollback-plan.md`](rollback-plan.md) - rollback en master (HU-26)
