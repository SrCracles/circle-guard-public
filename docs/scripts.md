# Scripts de Automatizacion

## `setup-kind.ps1` — Setup del Cluster Kubernetes (Kind)

**Ubicacion:** `setup-kind.ps1` (raiz del proyecto)

Este script PowerShell automatiza la creacion del entorno local de desarrollo para CircleGuard, preparando todo lo necesario para que los pipelines de Jenkins puedan ejecutarse correctamente.

> Para el despliegue en Azure, la creacion del cluster, namespaces e infraestructura base ya no se hace con este script sino con Terraform. Ver [`docs/terraform-azure.md`](terraform-azure.md).

---

## Que Hace

| Paso | Descripcion |
|------|-------------|
| **1. Prerequisitos** | Verifica que Docker, Kind y kubectl esten instalados y que Docker este corriendo |
| **2. Cluster Kind** | Crea el cluster `circleguard-cluster` con 2 nodos (control-plane + worker) y port mappings |
| **3. Namespaces** | Crea los namespaces: `dev`, `stage`, `master`, `infra` |
| **4. Infraestructura** | Despliega PostgreSQL, Zookeeper, Kafka, Redis, Neo4j y OpenLDAP en namespace `infra` |
| **5. Verificacion** | Espera a que los pods de infra esten ready y muestra resumen del estado |
| **6. Kubeconfig** | Exporta el kubeconfig a `kind-kubeconfig.yaml` para que Jenkins pueda usarlo |

---

## Como Ejecutarlo

Desde la raiz del proyecto:

```powershell
# Ejecucion estandar (crea cluster + infra completa)
.\setup-kind.ps1

# Saltar despliegue de infraestructura (solo cluster + namespaces)
.\setup-kind.ps1 -SkipInfra

```

---

## Port Mapping del Cluster

El cluster expone los siguientes puertos del host al container:

| Puerto Host | Puerto Container | Nodo | Uso |
|-------------|------------------|------|-----|
| 80 | 80 | control-plane | Ingress HTTP |
| 443 | 443 | control-plane | Ingress HTTPS |
| 30080 | 30080 | control-plane | NodePort alternativo |
| 30443 | 30443 | control-plane | NodePort alternativo HTTPS |
| 30180 | 30180 | worker | Worker node port |

---

## Kubeconfig para Jenkins

El script exporta automaticamente el kubeconfig a `kind-kubeconfig.yaml` en la raiz del proyecto.

### Por que es necesario?

El `kubeconfig` es el archivo que contiene la informacion de conexion al cluster (API Server, certificados, contexto). Cada vez que se recrea el cluster con Kind, este archivo cambia (especialmente el puerto del API Server).

Jenkins necesita conocer este archivo para ejecutar `kubectl`. Configuralo en Jenkins como una **variable de entorno**:

```
KUBECONFIG = C:\Users\<tu-usuario>\Desktop\ingesoft\circle-guard-public\kind-kubeconfig.yaml
```

> **IMPORTANTE:** Cada vez que se borre y se recree el cluster, volver a ejecutar `setup-kind.ps1` para regenerar el kubeconfig y actualiza la variable en Jenkins si la ruta cambia.

---

## Comandos utiles post-ejecucion

```powershell
# Ver pods en cada namespace
kubectl get pods -n dev
kubectl get pods -n stage
kubectl get pods -n master
kubectl get pods -n infra

# Ver logs de un servicio
kubectl logs -n infra -l app=postgresql
kubectl logs -n infra -l app=kafka

# Ver todos los namespaces
kubectl get namespaces

# Ver info del cluster
kubectl cluster-info
```

---

## `teardown-kind.ps1` — Limpieza del Entorno

**Ubicacion:** `teardown-kind.ps1` (raiz del proyecto)

Este script limpia todos los recursos desplegados en el cluster. Puede mantener el cluster vivo (para recrear solo los recursos) o borrarlo completamente.

---

## Que Hace

| Paso | Descripcion |
|------|-------------|
| **1. Confirmacion** | Pide confirmacion antes de eliminar recursos (a menos que se use `-Force`) |
| **2. Borra recursos** | Elimina deployments, services, configmaps, secrets y pods de los namespaces |
| **3. Borra namespaces** | Elimina los namespaces `dev`, `stage`, `master`, `infra` |
| **4. Limpia kubeconfig** | Borra el archivo `kind-kubeconfig.yaml` exportado |
| **5. Limpia Docker** | Opcional: elimina las imagenes Docker de CircleGuard (`-CleanDocker`) |
| **6. Borra cluster** | Opcional: elimina el cluster Kind completo (`-DeleteCluster`) |

---

## Como Ejecutarlo

```powershell
# Borra solo los recursos y namespaces, deja el cluster vivo
.\teardown-kind.ps1

# Borra recursos, namespaces y el cluster completo
.\teardown-kind.ps1 -DeleteCluster

# Borra TODO: recursos + cluster + imagenes Docker
.\teardown-kind.ps1 -DeleteCluster -CleanDocker

# Fuerza la ejecucion sin pedir confirmacion
.\teardown-kind.ps1 -DeleteCluster -Force
```

---

## Escenarios de Uso

| Escenario | Comando | Resultado |
|-----------|---------|-----------|
| **Reiniciar recursos** | `.\teardown-kind.ps1` | Borra solo los recursos. Luego ejecuta `setup-kind.ps1` para recrearlos |
| **Borrar todo** | `.\teardown-kind.ps1 -DeleteCluster -CleanDocker` | Cluster, recursos e imagenes eliminados. Espacio liberado |
| **Borrar rapido** | `.\teardown-kind.ps1 -DeleteCluster -Force` | Sin confirmaciones, borra inmediatamente |

---

## Flujo Tipico de Desarrollo

```powershell
# 1. Crear entorno desde cero
.\setup-kind.ps1

# 2. Ejecutar pipelines de Jenkins (dev, stage, master)
# ...

# 3. Al finalizar, limpiar recursos para recrear
.\teardown-kind.ps1

# 4. Si se quiere empezar de cero completamente
.\teardown-kind.ps1 -DeleteCluster -CleanDocker
.\setup-kind.ps1
```

---

## Terraform Azure — Setup Cloud

**Ubicacion:** `terraform/`

La infraestructura cloud se define como codigo con Terraform:

| Carpeta | Proposito |
|---------|-----------|
| `terraform/modules/aks` | Crea AKS y recursos asociados en Azure |
| `terraform/modules/infra` | Despliega SonarQube o infraestructura compartida segun el ambiente |
| `terraform/envs/dev` | Configuracion y estado remoto de dev; crea `dev` + `sonarqube` |
| `terraform/envs/stage` | Configuracion y estado remoto de stage; crea `stage` + `infra` |
| `terraform/envs/master` | Configuracion y estado remoto de master; crea `master` + `infra` |

Flujo basico:

```powershell
cd terraform
terraform init -backend-config="envs/dev/backend.hcl" -reconfigure
terraform plan -var-file="envs/dev/terraform.tfvars"
terraform apply -var-file="envs/dev/terraform.tfvars"
```

Antes de ejecutar, configurar el backend Azure Storage. Las variables sensibles `TF_VAR_postgres_password`, `TF_VAR_neo4j_password` y `TF_VAR_ldap_admin_password` son obligatorias para `stage` y `master`; en `dev` se pueden omitir porque no se despliega la infraestructura compartida.
