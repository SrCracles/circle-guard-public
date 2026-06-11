# Terraform y Azure para CircleGuard

Esta guia documenta la infraestructura cloud para CircleGuard en Microsoft Azure. Reemplaza el flujo local de Kind para crear el cluster Kubernetes, namespaces y servicios de soporte en la nube con Terraform.

## Objetivo

Terraform aprovisiona, por ambiente:

- Un Resource Group de Azure.
- Un cluster AKS con node pool configurable.
- Los namespaces Kubernetes del ambiente y `infra`.
- Los servicios de infraestructura que hoy existen en `k8s/infra`: PostgreSQL, Redis, Kafka, Zookeeper, Neo4j, OpenLDAP y SonarQube cuando aplique.
- Estado remoto en Azure Storage con bloqueo por blob lease.

La aplicacion sigue desplegandose con los manifiestos Kustomize existentes (`k8s/dev`, `k8s/stage`, `k8s/master`) desde Jenkins, pero `kubectl` ahora apunta a AKS en vez de Kind.

## Requisitos previos

- Azure CLI autenticado con la suscripcion correcta.
- Terraform instalado y disponible en el `PATH`.
- `kubectl` instalado.
- Permisos para crear Resource Groups, AKS, Storage Accounts y role assignments en la suscripcion.

En Windows se puede instalar Terraform con:

```powershell
winget install Hashicorp.Terraform
terraform -version
```

## Estructura

```text
terraform/
├── backend.tf
├── main.tf
├── providers.tf
├── variables.tf
├── outputs.tf
├── modules/
│   ├── aks/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   └── outputs.tf
│   └── infra/
│       ├── main.tf
│       ├── variables.tf
│       └── outputs.tf
└── envs/
    ├── dev/
    │   ├── backend.hcl
    │   └── terraform.tfvars
    ├── stage/
    │   ├── backend.hcl
    │   └── terraform.tfvars
    └── master/
        ├── backend.hcl
        └── terraform.tfvars
```

Cada ambiente usa el mismo root module y cambia solo `-backend-config` y `-var-file`. Esto evita duplicar codigo y mantiene estados separados.

## Paso 1: Preparar Azure

En Azure Portal:

1. Entrar a [portal.azure.com](https://portal.azure.com).
2. Verificar que la suscripcion activa sea la de estudiante.
3. Crear un presupuesto en **Cost Management + Billing > Budgets** para evitar consumir los 100 creditos accidentalmente.
4. Revisar que esten registrados estos Resource Providers en **Subscriptions > Resource providers**:
   - `Microsoft.ContainerService`
   - `Microsoft.Compute`
   - `Microsoft.Network`
   - `Microsoft.Storage`
   - `Microsoft.OperationalInsights`

Con Azure CLI:

```powershell
az login
az account list --output table
az account set --subscription "<SUBSCRIPTION_ID>"

az provider register --namespace Microsoft.ContainerService
az provider register --namespace Microsoft.Compute
az provider register --namespace Microsoft.Network
az provider register --namespace Microsoft.Storage
az provider register --namespace Microsoft.OperationalInsights
```

## Paso 2: Crear el backend remoto

El backend usa Azure Storage. AzureRM backend bloquea el estado usando leases del blob, por lo que dos `apply` simultaneos no escriben el estado al mismo tiempo.

```powershell
$location = "eastus"
$stateRg = "rg-circleguard-tfstate"
$stateStorage = "cgstate<valor-unico>" # solo minusculas y numeros, debe ser globalmente unico
$stateContainer = "tfstate"

az group create --name $stateRg --location $location

az storage account create `
  --resource-group $stateRg `
  --name $stateStorage `
  --location $location `
  --sku Standard_LRS `
  --kind StorageV2 `
  --https-only true `
  --min-tls-version TLS1_2 `
  --allow-blob-public-access false

az storage account blob-service-properties update `
  --resource-group $stateRg `
  --account-name $stateStorage `
  --enable-versioning true

$userObjectId = az ad signed-in-user show --query id -o tsv
$storageId = az storage account show --resource-group $stateRg --name $stateStorage --query id -o tsv

az role assignment create `
  --assignee $userObjectId `
  --role "Storage Blob Data Contributor" `
  --scope $storageId

az storage container create `
  --name $stateContainer `
  --account-name $stateStorage `
  --auth-mode login
```

Despues, reemplazar `storage_account_name = "cgstatereplace"` en:

- `terraform/envs/dev/backend.hcl`
- `terraform/envs/stage/backend.hcl`
- `terraform/envs/master/backend.hcl`

Cada archivo conserva una key distinta:

- `dev/terraform.tfstate`
- `stage/terraform.tfstate`
- `master/terraform.tfstate`

Por eso un `apply` de `dev` no modifica el estado de `stage` ni `master`.

## Paso 3: Variables sensibles

Las credenciales no estan hardcodeadas en Terraform ni en `terraform.tfvars`. Definirlas por variables de entorno antes de ejecutar `plan` o `apply`:

```powershell
$env:TF_VAR_postgres_password = "<password-postgres>"
$env:TF_VAR_neo4j_password = "<password-neo4j>"
$env:TF_VAR_ldap_admin_password = "<password-openldap>"
```

En Jenkins se deben configurar como credenciales o variables protegidas y exponerlas como `TF_VAR_postgres_password`, `TF_VAR_neo4j_password` y `TF_VAR_ldap_admin_password`.

## Paso 4: Inicializar y planear

Desde la raiz del repositorio:

```powershell
cd terraform

terraform init -backend-config="envs/dev/backend.hcl" -reconfigure
terraform plan -var-file="envs/dev/terraform.tfvars"
```

Para stage:

```powershell
terraform init -backend-config="envs/stage/backend.hcl" -reconfigure
terraform plan -var-file="envs/stage/terraform.tfvars"
```

Para master:

```powershell
terraform init -backend-config="envs/master/backend.hcl" -reconfigure
terraform plan -var-file="envs/master/terraform.tfvars"
```

## Paso 5: Aplicar

Con 100 creditos de estudiante, no se recomienda levantar los tres ambientes al mismo tiempo. Empezar por `dev`, validar el flujo completo y destruir recursos que no se esten usando.

```powershell
terraform apply -var-file="envs/dev/terraform.tfvars"
```

Al finalizar:

```powershell
terraform output aks_get_credentials_command
az aks get-credentials --resource-group circleguard-dev-rg --name circleguard-dev-aks --overwrite-existing
kubectl get namespaces
kubectl get pods -n infra
```

Para liberar costos:

```powershell
terraform destroy -var-file="envs/dev/terraform.tfvars"
```

## Paso 6: Conectar Jenkins a AKS

El cambio principal frente a Kind es el kubeconfig. Jenkins ya ejecuta `kubectl apply -k ...`; ahora ese `kubectl` debe apuntar al cluster AKS del ambiente.

Generar un kubeconfig por ambiente:

```powershell
az aks get-credentials `
  --resource-group circleguard-dev-rg `
  --name circleguard-dev-aks `
  --file "$PWD\aks-dev-kubeconfig.yaml" `
  --overwrite-existing

az aks get-credentials `
  --resource-group circleguard-stage-rg `
  --name circleguard-stage-aks `
  --file "$PWD\aks-stage-kubeconfig.yaml" `
  --overwrite-existing

az aks get-credentials `
  --resource-group circleguard-master-rg `
  --name circleguard-master-aks `
  --file "$PWD\aks-master-kubeconfig.yaml" `
  --overwrite-existing
```

En Jenkins:

- Jobs dev: `KUBECONFIG=<ruta>\aks-dev-kubeconfig.yaml`
- Job stage: `KUBECONFIG=<ruta>\aks-stage-kubeconfig.yaml`
- Job master: `KUBECONFIG=<ruta>\aks-master-kubeconfig.yaml`

Los Jenkinsfiles existentes pueden seguir usando:

```powershell
kubectl apply -k k8s/stage/
kubectl rollout status deployment/circleguard-auth-service -n stage --timeout=300s
```

La diferencia es que esos comandos se ejecutan contra AKS.

## Validaciones utiles

```powershell
kubectl get nodes
kubectl get namespaces
kubectl get pods -n infra
kubectl get svc -n infra
kubectl logs -n infra -l app=kafka
```

Para validar el bloqueo de estado, iniciar un `terraform apply` y lanzar otro desde una segunda terminal con el mismo backend. El segundo proceso debe esperar o fallar con un mensaje de lock del backend AzureRM.

## Costos y consideraciones

- AKS control plane en tier Free no cobra por el control plane, pero los nodos si consumen credito.
- Los `terraform.tfvars` usan `Standard_B2ms` para soportar Kafka, Neo4j y PostgreSQL con memoria razonable.
- `dev` habilita SonarQube; `stage` y `master` lo deshabilitan para ahorrar credito.
- La infraestructura de datos usa volumen efimero igual que los manifiestos locales actuales. Para produccion real se debe migrar PostgreSQL, Redis, Kafka y Neo4j a almacenamiento persistente o servicios administrados.
- El backend remoto queda fuera de los resource groups de ambientes para no destruir el estado al ejecutar `terraform destroy`.
