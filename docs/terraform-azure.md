# Terraform y Azure para CircleGuard

Esta guia documenta la infraestructura cloud para CircleGuard en Microsoft Azure. Reemplaza el flujo local de Kind para crear el cluster Kubernetes, namespaces y servicios de soporte en la nube con Terraform.

## Objetivo

Terraform aprovisiona, por ambiente:

- Un Resource Group de Azure.
- Un cluster AKS con node pool configurable.
- En `dev`: los namespaces `dev` y `sonarqube`; solo se despliega SonarQube.
- En `stage` y `master`: el namespace del ambiente y `infra`; se despliegan PostgreSQL, Redis, Kafka, Zookeeper, Neo4j y OpenLDAP.
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

## Que crea cada ambiente

| Ambiente | Namespaces | Servicios creados por Terraform |
|----------|------------|----------------------------------|
| `dev` | `dev`, `sonarqube` | SonarQube |
| `stage` | `stage`, `infra` | PostgreSQL, Redis, Kafka, Zookeeper, Neo4j, OpenLDAP |
| `master` | `master`, `infra` | PostgreSQL, Redis, Kafka, Zookeeper, Neo4j, OpenLDAP |

En `dev` no se crea `infra` porque los pipelines de desarrollo solo necesitan compilar, probar, publicar imagenes y validar SonarQube. Las dependencias completas quedan para `stage` y `master`, donde se ejecutan despliegues integrados.

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

Estas contrasenas las define el equipo. No salen de Azure ni del repositorio. Son las claves que usaran los servicios internos cuando Terraform cree PostgreSQL, Neo4j y OpenLDAP en Kubernetes.

### 3.1 Si ejecutas Terraform desde tu terminal

Crear las variables en la terminal antes de correr `terraform plan` o `terraform apply`:

```powershell
cd terraform

$env:TF_VAR_postgres_password = "CambiarPorUnPasswordPostgresSeguro"
$env:TF_VAR_neo4j_password = "CambiarPorUnPasswordNeo4jSeguro"
$env:TF_VAR_ldap_admin_password = "CambiarPorUnPasswordLdapSeguro"

terraform plan -var-file="envs/stage/terraform.tfvars"
terraform apply -var-file="envs/stage/terraform.tfvars"
```

Terraform lee automaticamente cualquier variable de entorno que empiece por `TF_VAR_`. Por ejemplo, `TF_VAR_postgres_password` llena la variable Terraform `postgres_password`.

Si el equipo va a seguir ejecutando `terraform plan` y `terraform apply` manualmente, esto es suficiente. No hace falta crear credenciales en Jenkins para estas contrasenas. Solo recordar que las variables `$env:TF_VAR_*` viven en la sesion actual de PowerShell; si se cierra la terminal, hay que definirlas otra vez antes del siguiente `plan/apply`.

### 3.2 Solo si en el futuro ejecutas Terraform desde Jenkins

Crear 3 credenciales en Jenkins:

1. Entrar a **Manage Jenkins > Credentials > System > Global credentials**.
2. Hacer clic en **Add Credentials**.
3. Crear cada una como **Kind: Secret text**.
4. Usar estos IDs:

| Jenkins Credential ID | Valor que guardas ahi |
|-----------------------|------------------------|
| `tf-postgres-password` | Password que quieres para PostgreSQL |
| `tf-neo4j-password` | Password que quieres para Neo4j |
| `tf-ldap-admin-password` | Password que quieres para OpenLDAP |

Luego, el Jenkinsfile que ejecute Terraform debe tomar esas credenciales y convertirlas en variables `TF_VAR_*`:

```groovy
withCredentials([
    string(credentialsId: 'tf-postgres-password', variable: 'TF_VAR_postgres_password'),
    string(credentialsId: 'tf-neo4j-password', variable: 'TF_VAR_neo4j_password'),
    string(credentialsId: 'tf-ldap-admin-password', variable: 'TF_VAR_ldap_admin_password')
]) {
    bat 'terraform plan -var-file="envs/stage/terraform.tfvars"'
    bat 'terraform apply -var-file="envs/stage/terraform.tfvars"'
}
```

En resumen: Jenkins guarda el secreto con un ID, el Jenkinsfile lo lee con `withCredentials`, y Terraform lo recibe porque la variable se llama `TF_VAR_<nombre_variable_terraform>`.

Reglas claras:

- Para `dev`, esas variables pueden omitirse si solo se despliega SonarQube (`enable_shared_infra = false`).
- Para `stage` y `master`, son obligatorias porque se despliega PostgreSQL, Neo4j y OpenLDAP.
- No deben escribirse en `terraform.tfvars`, ni en `.tf`, ni en documentación con valores reales.

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
kubectl get pods -n sonarqube
```

Para liberar costos:

```powershell
terraform destroy -var-file="envs/dev/terraform.tfvars"
```

## Paso 6: Conectar Jenkins a AKS

El cambio principal frente a Kind es el kubeconfig. Jenkins ya ejecuta `kubectl apply -k ...`; ahora ese `kubectl` debe apuntar al cluster AKS del ambiente.

`KUBECONFIG` tiene el mismo nombre en todos los casos porque es la variable estandar que lee `kubectl`. Lo que cambia es su valor segun el job. No se crean variables llamadas `KUBECONFIG_DEV`, `KUBECONFIG_STAGE` y `KUBECONFIG_MASTER` porque `kubectl` no las lee automaticamente.

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

- En cada job dev, configurar `KUBECONFIG=<ruta>\aks-dev-kubeconfig.yaml`.
- En el job stage, configurar `KUBECONFIG=<ruta>\aks-stage-kubeconfig.yaml`.
- En el job master, configurar `KUBECONFIG=<ruta>\aks-master-kubeconfig.yaml`.

Esto se puede hacer de dos formas:

1. Manualmente en Jenkins: entrar a cada job, ir a **Configure > Build Environment > Inject environment variables** o usar las variables del pipeline/job, y definir `KUBECONFIG` con la ruta correspondiente.
2. Recomendado para el proyecto: usar credenciales tipo secret file en Jenkins:
   - `aks-dev-kubeconfig`
   - `aks-stage-kubeconfig`
   - `aks-master-kubeconfig`

Con la opcion recomendada, cada Jenkinsfile toma su kubeconfig como archivo secreto y lo expone con el nombre estandar `KUBECONFIG` solo durante la ejecucion del job. Asi no hay que cambiar manualmente una variable global antes de correr dev, stage o master.

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
kubectl get pods -n sonarqube # dev
kubectl get pods -n infra     # stage/master
kubectl get svc -n infra
kubectl logs -n infra -l app=kafka
```

Para validar el bloqueo de estado, iniciar un `terraform apply` y lanzar otro desde una segunda terminal con el mismo backend. El segundo proceso debe esperar o fallar con un mensaje de lock del backend AzureRM.

## Costos y consideraciones

- AKS control plane en tier Free no cobra por el control plane, pero los nodos si consumen credito.
- Los `terraform.tfvars` usan `Standard_D2s_v7` porque esta suscripcion lo permite en `eastus`. Si Azure rechaza el tamaño de VM, consultar tamaños disponibles con `az vm list-sizes --location eastus --output table` y cambiar `aks_vm_size`.
- `dev` habilita solo SonarQube; `stage` y `master` habilitan la infraestructura compartida completa.
- La infraestructura de datos usa volumen efimero igual que los manifiestos locales actuales. Para produccion real se debe migrar PostgreSQL, Redis, Kafka y Neo4j a almacenamiento persistente o servicios administrados.
- El backend remoto queda fuera de los resource groups de ambientes para no destruir el estado al ejecutar `terraform destroy`.
