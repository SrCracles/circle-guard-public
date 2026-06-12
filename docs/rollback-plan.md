# Plan de Rollback - Ambiente master

Este documento define cómo revertir un despliegue fallido o un problema detectado en producción (namespace `master`) en CircleGuard. El objetivo es restaurar el servicio en **menos de 5 minutos** cuando sea posible.

El marco de aprobación y tipos de cambio está en [`change-management.md`](change-management.md).

---

## Cuándo hacer rollback

- Pods en `CrashLoopBackOff` o `ImagePullBackOff` tras un deploy en `master`
- Regresión funcional grave (login, gateway, flujo E2E) confirmada justo después de un release
- Pipeline `circle-guard-master` exitoso pero comportamiento incorrecto en producción

Si el problema no está ligado al último deploy, investigar antes de revertir (logs, Grafana, Kibana).

---

## Versiones disponibles

El pipeline master publica en DockerHub, por cada microservicio:

- `:vX.Y.Z` - tag **inmutable** de la release actual (y de releases anteriores)
- `:master` - alias mutable de la última release

Jenkins conserva al menos **dos versiones** inmutables: la que se acaba de desplegar y la anterior (`PREVIOUS_VERSION` en el log del pipeline y en las Release Notes de GitHub).

Para identificar la versión anterior:

```powershell
# Último tag Git en el repositorio (release actual tras deploy)
git fetch --tags
git describe --tags --match "v*.*.*" --abbrev=0

# Penúltimo tag (versión de rollback)
git tag --list "v*.*.*" --sort=-v:refname | Select-Object -First 2
```

Ejemplo: si la actual es `v1.2.5`, el rollback suele ser `v1.2.4` (o la penúltima de la lista).

---

## Microservicios y deployments

Los nombres de Deployment en `master` coinciden con el nombre del servicio:

| Deployment | Imagen DockerHub |
|------------|------------------|
| `circleguard-auth-service` | `srcracles/circleguard-auth-service` |
| `circleguard-identity-service` | `srcracles/circleguard-identity-service` |
| `circleguard-form-service` | `srcracles/circleguard-form-service` |
| `circleguard-promotion-service` | `srcracles/circleguard-promotion-service` |
| `circleguard-notification-service` | `srcracles/circleguard-notification-service` |
| `circleguard-gateway-service` | `srcracles/circleguard-gateway-service` |
| `circleguard-file-service` | `srcracles/circleguard-file-service` |
| `circleguard-dashboard-service` | `srcracles/circleguard-dashboard-service` |

> Ajustar el usuario de DockerHub si no es `srcracles` (variable `CG_DOCKER_USER` en Jenkins).

---

## Opción A: Rollback rápido con Kubernetes (recomendada)

Kubernetes guarda un historial de ReplicaSets por Deployment. `kubectl rollout undo` vuelve a la revisión anterior sin editar manifiestos a mano.

### Un solo servicio afectado

```powershell
# Ver historial de revisiones
kubectl rollout history deployment/circleguard-auth-service -n master

# Revertir a la revisión anterior
kubectl rollout undo deployment/circleguard-auth-service -n master

# Esperar a que el rollout termine
kubectl rollout status deployment/circleguard-auth-service -n master --timeout=120s

# Verificar pods
kubectl get pods -n master -l app=circleguard-auth-service
```

Sustituir `circleguard-auth-service` por el deployment afectado.

### Todos los servicios (fallo global del release)

```powershell
$services = @(
  'auth', 'identity', 'form', 'gateway',
  'notification', 'file', 'promotion', 'dashboard'
)

foreach ($svc in $services) {
  kubectl rollout undo deployment/circleguard-$svc-service -n master
}

foreach ($svc in $services) {
  kubectl rollout status deployment/circleguard-$svc-service -n master --timeout=120s
}

kubectl get pods -n master
```

### Revertir a una revisión concreta

```powershell
kubectl rollout history deployment/circleguard-auth-service -n master
kubectl rollout undo deployment/circleguard-auth-service -n master --to-revision=3
```

**Tiempo estimado:** 1-3 minutos (un servicio) o 3-5 minutos (los ocho en paralelo con los comandos anteriores).

---

## Opción B: Rollback por tag Docker (versión anterior explícita)

Usar cuando `rollout undo` no basta (por ejemplo, el pipeline hizo teardown completo del namespace y no hay revisión anterior útil) o cuando se quiere fijar una versión conocida (`v1.2.4`).

### 1. Actualizar el tag en Kustomize

Editar `k8s/master/kustomization.yaml` y cambiar `newTag` de todos los servicios a la versión anterior:

```yaml
images:
  - name: circleguard-auth-service
    newName: srcracles/circleguard-auth-service
    newTag: v1.2.4   # versión de rollback
  # ... repetir para los 8 servicios
```

### 2. Aplicar y verificar

```powershell
kubectl apply -k k8s/master/

$services = @('auth','identity','form','gateway','notification','file','promotion','dashboard')
foreach ($svc in $services) {
  kubectl rollout status deployment/circleguard-$svc-service -n master --timeout=300s
}
```

### 3. (Opcional) Actualizar el alias `:master` en DockerHub

Si otros procesos consumen `:master`, retaggear la imagen anterior:

```powershell
$prev = "1.2.4"
$svc = "circleguard-auth-service"

docker pull srcracles/${svc}:v$prev
docker tag srcracles/${svc}:v$prev srcracles/${svc}:master
docker push srcracles/${svc}:master
```

Repetir por cada microservicio afectado.

**Tiempo estimado:** 3-5 minutos (apply + rollout de los ocho servicios).

---

## Verificación post-rollback

```powershell
# Estado de pods
kubectl get pods -n master

# Health de un servicio (desde el cluster)
kubectl exec -n master deploy/circleguard-auth-service -- wget -qO- http://localhost:8180/actuator/health

# Smoke test HTTPS (Kind, cert autofirmado)
curl -k -X POST https://localhost/auth/api/v1/auth/login `
  -H "Content-Type: application/json" `
  -d '{"username":"admin","password":"admin"}'
```

Revisar Grafana (`localhost:3000`) y logs en Kibana (`localhost:5601`) si el incidente persistiera.

---

## Tiempos objetivo

| Escenario | Acción | Tiempo estimado |
|-----------|--------|-----------------|
| Un microservicio | `kubectl rollout undo` + `rollout status` | 1-3 min |
| Plataforma completa | `rollout undo` en los 8 deployments | 3-5 min |
| Redeploy por tag `v` anterior | `kubectl apply -k` + rollout status | 3-5 min |

El objetivo del proyecto es **menos de 5 minutos** para el rollback rápido (Opción A) en un incidente típico post-deploy.

---

## Después del rollback

1. Comunicar al equipo que producción volvió a la revisión o versión anterior.
2. Registrar el incidente según [`change-management.md`](change-management.md) (cambio de emergencia).
3. Corregir el defecto en `hotfix/` o en `develop`, volver a pasar por `stage` y no redeployar a `master` hasta validar.
4. Opcional: revertir el merge en Git (`master`) si el commit de la release no debe permanecer en la rama.

---

## Referencias

- [`change-management.md`](change-management.md) - tipos de cambio y aprobaciones
- [`branching-strategy.md`](branching-strategy.md) - flujo hotfix y ramas
- [`tls.md`](tls.md) - acceso HTTPS a auth y gateway en master
