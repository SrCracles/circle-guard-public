# Change Management - CircleGuard

Este documento describe cómo se proponen, aprueban y rastrean los cambios que llegan al ambiente de producción (`master`) en CircleGuard. El objetivo es que toda modificación en producción sea controlada, revisada y trazable.

El plan operativo de rollback detallado está en [`rollback-plan.md`](rollback-plan.md) (HU-26).

---

## Alcance

Aplica a cambios que afectan:

- Código de los ocho microservicios en `services/`
- Manifiestos Kubernetes en `k8s/master/`
- Pipelines Jenkins (`jenkins/master/`, `jenkins/stage/`)
- Imágenes Docker publicadas en DockerHub con tags de producción (`:master`, `:vX.Y.Z`)

Los cambios que solo viven en `develop` o `stage` siguen el flujo de branching ([`branching-strategy.md`](branching-strategy.md)) pero no requieren el mismo nivel de aprobación que producción.

---

## Tipos de cambio

| Tipo | Descripción | Ejemplo | Rama habitual |
|------|-------------|---------|---------------|
| **Estándar** | Cambio planificado, validado en dev y stage | Nueva HU, mejora de cobertura, ajuste de K8s | `develop` → `stage` → `master` |
| **Urgente** | Hotfix necesario sin esperar el ciclo completo | Bug crítico en login o gateway | `hotfix/*` → `master` (+ backport) |
| **Emergencia** | Incidente activo en producción; se actúa antes de documentar | Caída total del namespace `master`, regresión grave post-deploy | Rollback inmediato; documentación posterior |

---

## Flujo de aprobación

### Cambio estándar (planificado)

1. **Propuesta:** el desarrollador abre una rama `feature/`, `fix/`, `docs/`, `test/` o `infra/` y registra la HU o ticket asociado.
2. **Revisión de código:** merge a `develop` mediante pull request en GitHub. Aprueba al menos un integrante del equipo (revisor distinto del autor cuando sea posible).
3. **Validación técnica:** pipelines dev en Jenkins (rama `develop`) y, tras merge a `stage`, pipeline `circle-guard-stage` (E2E, OWASP ZAP, Locust).
4. **Aprobación de producción:** merge `stage` → `master`. Al ejecutar `circle-guard-master`, Jenkins solicita **aprobación manual** antes de publicar imágenes y desplegar (stage `Approve Production Release`).
5. **Trazabilidad:** el pipeline genera tag Git `vX.Y.Z`, Release Notes en GitHub y notificación por email si falla (`CG_NOTIFY_EMAIL`).

| Paso | Responsable | Canal | Tiempo de respuesta objetivo |
|------|-------------|-------|------------------------------|
| Revisión de PR | Par del equipo / líder técnico | GitHub Pull Request | 24 h hábiles |
| Ejecución stage | DevOps / quien dispare Jenkins | Jenkins (`circle-guard-stage`) | Mismo día del merge a `stage` |
| Aprobación master | Líder del proyecto o delegado | Jenkins `input` + confirmación en canal del equipo (email o chat acordado) | 24 h hábiles tras stage en verde |

### Cambio urgente (hotfix)

1. Rama `hotfix/` desde `master`.
2. Revisión acelerada: un aprobador (líder o senior) valida el PR hacia `master`.
3. Pipeline `circle-guard-master` con aprobación manual en Jenkins.
4. Backport obligatorio a `stage` y `develop` en las siguientes 24 h.
5. Registro del incidente y del cambio en la Release Notes o en un comentario del PR.

| Paso | Responsable | Canal | Tiempo de respuesta objetivo |
|------|-------------|-------|------------------------------|
| Revisión hotfix | Líder del proyecto | GitHub PR (etiqueta `hotfix`) | 2-4 h |
| Aprobación deploy | Líder del proyecto | Jenkins `input` | Inmediato tras revisión |
| Backport | Autor del hotfix | PRs a `stage` y `develop` | 24 h |

### Cambio de emergencia

Cuando producción está degradada o caída, la prioridad es **restaurar el servicio**. No se espera el ciclo estándar.

1. **Decisión:** el líder del proyecto o quien esté de guardia autoriza el rollback o hotfix de forma verbal o por chat (Teams, Slack, WhatsApp del equipo).
2. **Ejecución:** aplicar [`rollback-plan.md`](rollback-plan.md) (objetivo: menos de 5 minutos).
3. **Documentación posterior (en las 24 h):** registrar qué se hizo, versión revertida, causa raíz preliminar y si hace falta un hotfix formal.

| Paso | Responsable | Canal | Tiempo de respuesta objetivo |
|------|-------------|-------|------------------------------|
| Autorización | Líder / guardia | Chat o llamada | Inmediato |
| Rollback | Ingeniero de operaciones | `kubectl` (ver rollback-plan) | Menos de 5 min |
| Post-mortem breve | Equipo | Issue o doc en repo | 24 h |

---

## Trazabilidad

Cada cambio en producción debe poder seguirse mediante:

| Artefacto | Qué registra |
|-----------|--------------|
| Historial Git en `master` | Commits con Conventional Commits |
| Tag `vX.Y.Z` | Versión desplegada |
| GitHub Release + `RELEASE_NOTES_vX.Y.Z.md` | Contenido del release (generado por Jenkins) |
| Tags Docker `:vX.Y.Z` | Imagen inmutable por versión |
| Log de Jenkins | Quién aprobó (`APPROVED_BY`), duración, éxito o fallo |
| Email `CG_NOTIFY_EMAIL` | Alertas de pipeline fallido o recuperado |

---

## Rollback

Ante un despliegue fallido o una regresión en `master`, el equipo ejecuta el plan de [`rollback-plan.md`](rollback-plan.md).

Resumen de las dos vías:

1. **Rápida (recomendada):** `kubectl rollout undo` sobre el deployment afectado (o los ocho si el fallo es global).
2. **Por versión:** redesplegar el tag Docker de la versión anterior (`:vX.Y.Z` previa), documentada en la última Release Notes.

El pipeline master conserva en DockerHub al menos **dos versiones** inmutables (`v` anterior y `v` actual) para soportar la segunda vía.

---

## Implementación en Jenkins

El proceso anterior no es solo documentación: parte del flujo está automatizado en `jenkins/master/Jenkinsfile-master`.

| Mecanismo | Stage / comportamiento |
|-----------|------------------------|
| **Aprobación manual** | `Approve Production Release`: step `input` antes de promover imágenes a producción. Solo usuarios con permiso de aprobar builds en Jenkins pueden continuar. |
| **Versionado semántico** | `Calculate Semantic Version`: deriva `vX.Y.Z` de los commits y del último tag Git. |
| **Etiquetado Docker** | `Promote Docker Images`: publica `:vX.Y.Z` (inmutable) y `:master` (mutable). No elimina tags `v` anteriores. |
| **Retención para rollback** | `Verify Rollback Images`: comprueba que la versión anterior sigue disponible en DockerHub antes del deploy. |
| **Despliegue controlado** | `Deploy To Kind Master`: aplica Kustomize con `newTag: vX.Y.Z` y espera `kubectl rollout status` por servicio. |
| **Notificaciones** | `post { failure / success }`: email vía `jenkins/lib/pipeline-notifications.groovy`. |

Los pipelines `stage` y `dev` actúan como **quality gates** previas: un cambio estándar no debería llegar a la aprobación de master sin haber pasado stage.

---

## Referencias

- [`branching-strategy.md`](branching-strategy.md) - ramas, hotfix y promoción entre ambientes
- [`rollback-plan.md`](rollback-plan.md) - procedimiento operativo de rollback (HU-26)
- [`setup-guide.md`](setup-guide.md) - configuración de Jenkins y pipelines
- [`pipeline-notifications.md`](pipeline-notifications.md) - alertas por email
