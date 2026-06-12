# Configuracion de RBAC en Kubernetes (HU-36)

CircleGuard aplica el principio de minimo privilegio mediante ServiceAccounts dedicados, Roles y RoleBindings en cada namespace de aplicacion.

## Estructura de manifiestos

```
k8s/rbac/                          # RBAC de Jenkins (cluster-wide, bindings por namespace)
  jenkins-deployer.yaml              # SA + ClusterRole
  jenkins-rolebindings.yaml          # Bindings en dev, stage, master
k8s/base/rbac/                       # RBAC de microservicios (desplegado con cada overlay)
  serviceaccounts.yaml               # 8 ServiceAccounts
  microservice-roles.yaml            # Role microservice-reader (solo lectura)
  microservice-rolebindings.yaml     # Un binding por microservicio
```

## Jenkins (`jenkins-deployer`)

| Aspecto        | Valor                                                        |
| -------------- | ------------------------------------------------------------ |
| ServiceAccount | `jenkins-deployer` en `kube-system`                          |
| Permisos       | Deployments, Services, ConfigMaps, Pods (rollout/E2E/Locust) |
| Namespaces     | `dev`, `stage`, `master` via RoleBinding                     |
| Sin acceso     | `infra`, `cluster-admin`, escritura de Secrets               |

Los Secrets compartidos (`circleguard-secrets`) se aplican una vez en `setup-kind.ps1`. Jenkins no puede crear ni modificar secrets.

### Kubeconfig con permisos limitados

```powershell
.\scripts\setup-jenkins-kubeconfig.ps1
```

Genera `jenkins-kubeconfig-rbac.yaml` tomando como fuente `kind-kubeconfig.yaml` si existe, para evitar depender del contexto activo de `kubectl`. Configurar `KUBECONFIG` en Jenkins con esa ruta para operar con minimo privilegio.

## Microservicios

Cada Deployment usa su propio ServiceAccount (no `default`):

| Servicio             | ServiceAccount                |
| -------------------- | ----------------------------- |
| auth-service         | `circleguard-auth-sa`         |
| identity-service     | `circleguard-identity-sa`     |
| form-service         | `circleguard-form-sa`         |
| promotion-service    | `circleguard-promotion-sa`    |
| notification-service | `circleguard-notification-sa` |
| gateway-service      | `circleguard-gateway-sa`      |
| file-service         | `circleguard-file-sa`         |
| dashboard-service    | `circleguard-dashboard-sa`    |

Role `microservice-reader`: solo `get`, `list`, `watch` sobre ConfigMaps, Services, Pods, Endpoints y Deployments en su namespace. **Sin acceso a Secrets.**

## Despliegue

RBAC de Jenkins se aplica en `setup-kind.ps1`:

```powershell
kubectl apply -k k8s/rbac/
```

RBAC de microservicios se aplica automaticamente con `kubectl apply -k k8s/{dev,stage,master}/`.

## Renovacion del token de Jenkins

El token generado por `setup-jenkins-kubeconfig.ps1` expira segun `--duration` (default 1 ano). Regenerar el kubeconfig antes de la expiracion y actualizar `KUBECONFIG` en Jenkins.
