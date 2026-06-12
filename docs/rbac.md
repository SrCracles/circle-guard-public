# Configuracion de RBAC en Kubernetes (HU-36)

CircleGuard aplica el principio de minimo privilegio mediante ServiceAccounts dedicados, Roles y RoleBindings en cada namespace de aplicacion.

## Estructura de manifiestos

```
k8s/rbac/                          # RBAC de Jenkins (Roles namespaced, sin ClusterRole)
  jenkins-deployer.yaml              # ServiceAccount en kube-system
  jenkins-roles.yaml                 # Role jenkins-deployer en dev, stage y master
  jenkins-rolebindings.yaml          # RoleBindings en dev, stage, master
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
# 1. Aplicar (o re-aplicar) RBAC y eliminar ClusterRole legado si existia
kubectl apply -k k8s/rbac/
kubectl delete clusterrole circleguard-jenkins-deployer --ignore-not-found

# 2. Generar kubeconfig del ServiceAccount (verifica permisos al finalizar)
.\scripts\setup-jenkins-kubeconfig.ps1

# 3. Verificacion completa
.\scripts\verify-rbac.ps1
```

Genera `jenkins-kubeconfig-rbac.yaml` en UTF-8 **sin BOM** (importante en Windows: si el archivo tiene BOM, `kubectl` lo ignora y usa el kubeconfig admin por defecto).

**Siempre** usa `--kubeconfig` explicito al probar:

```powershell
kubectl --kubeconfig .\jenkins-kubeconfig-rbac.yaml auth can-i create deployments -n master   # yes
kubectl --kubeconfig .\jenkins-kubeconfig-rbac.yaml auth can-i create deployments -n infra    # no
kubectl --kubeconfig .\jenkins-kubeconfig-rbac.yaml get secrets -n infra                    # Forbidden
```

Configurar `KUBECONFIG` en Jenkins con la ruta **absoluta** a ese archivo.

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
