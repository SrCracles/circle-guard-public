# Guia de Inicializacion del Entorno CircleGuard

Esta guia explica paso a paso como levantar todo el entorno desde cero, configurar Jenkins y ejecutar los pipelines.

---

## Requisitos Previos

Instalar en el PC:

- Docker Desktop
- Kind (Kubernetes in Docker)
- kubectl
- Jenkins (instalado nativamente, no en Docker)
- PowerShell 7.0+
- Java JDK 21 (para compilar los servicios)
- Trivy (escaner de vulnerabilidades, debe estar en el PATH del sistema)

---

## Paso 1: Crear el Cluster y la Infraestructura

Desde la raiz del proyecto ejecutar:

```powershell
.\setup-kind.ps1
```

Este script crea:
- El cluster de Kind
- Los namespaces: `dev`, `stage`, `master`, `infra`
- Los servicios de infraestructura: PostgreSQL, Kafka, Redis, Neo4j, Zookeeper, OpenLDAP, SonarQube
- Exporta el kubeconfig a `kind-kubeconfig.yaml`

Al finalizar, anotar la ruta del kubeconfig que muestra en pantalla.

---

## Paso 2: Configurar Jenkins

### 2.1 Iniciar Jenkins

Asegurarse de que Jenkins este corriendo como servicio en el PC. Acceder a:

```
http://localhost:8080
```

### 2.2 Crear Credentials

Ir a **Manage Jenkins > Credentials > System > Global credentials** y agregar:

#### Credential 1: DockerHub

- **Kind:** Username with password
- **ID:** `dockerhub-credentials`
- **Username:** `srcracles` (o el usuario de DockerHub)
- **Password:** El token o contrasena de DockerHub

#### Credential 2: GitHub Token

- **Kind:** Secret text
- **ID:** `github-token`
- **Secret:** El Personal Access Token de GitHub (necesita permisos `repo` y `write:packages`)

### 2.3 Configurar Global Properties (Externalized Configuration)

Ir a **Manage Jenkins > System** y buscar la seccion **Global properties > Environment variables**.

Estas variables centralizan la configuracion del proyecto. Solo se configuran una vez y todos los pipelines las usan automaticamente:

| Nombre | Valor | Descripcion |
|--------|-------|-------------|
| `CG_DOCKER_USER` | `srcracles` | Usuario de DockerHub donde se suben las imagenes |
| `CG_GITHUB_OWNER` | `SrCracles` | Owner del repositorio GitHub |
| `CG_GITHUB_REPO` | `circle-guard-public` | Nombre del repositorio GitHub |
| `CG_TRIVY_SEVERITY_FAIL` | `CRITICAL` | Severidad minima que falla el pipeline en el escaneo Trivy. Valores validos: `CRITICAL` (default) o `HIGH,CRITICAL` |

> **Nota:** Los valores de la tabla son los defaults del proyecto. Si alguien hace un fork o trabaja con su propia cuenta de DockerHub, solo debe cambiar estos valores aqui, sin tocar ningun Jenkinsfile.

> Los pipelines tienen fallback incorporado: si alguna variable no esta definida, usaran el valor por defecto. Sin embargo, se recomienda configurarlas explicitamente para mayor claridad.

### 2.4 Configurar Variable de Entorno KUBECONFIG

Ir a **Manage Jenkins > System** y buscar la seccion **Environment variables**.

Agregar:

| Nombre | Valor |
|--------|-------|
| `KUBECONFIG` | `C:\Users\<tu-usuario>\Desktop\ingesoft\circle-guard-public\kind-kubeconfig.yaml` |

> Ajustar la ruta segun donde se tenga el proyecto. Esta ruta la muestra el script `setup-kind.ps1` al finalizar.

> **Importante:** Si se borra y se recrea el cluster, esta ruta puede cambiar. Re-ejecutar `setup-kind.ps1` y actualizar la variable si es necesario.

### 2.4 Instalar Plugins adicionales (si no se tienen)

Ir a **Manage Jenkins > Plugins > Available plugins** e instalar los que falten:

| Plugin | Viene por defecto? | Para que se usa? | Es indispensable? |
|--------|-------------------|------------------|-------------------|
| **Docker Pipeline** | No | Login y push a DockerHub: `withDockerRegistry(...)` | **Si, obligatorio** |
| **HTML Publisher** | No | Ver el reporte HTML de Newman en Jenkins | No, opcional pero recomendado |
| **JUnit** | Si (suggested plugins) | Publicar resultados de tests | Si, pero ya se tiene |
| **Coverage** | No | Generar gráficos de tendencia de cobertura de pruebas en el dashboard | Si, para HU-23 |

> **Nota:** No es necesario instalar `Kubernetes CLI`. Los pipelines usan `kubectl` directamente como comando del sistema, no usan los steps especiales de Kubernetes de Jenkins.

---

## Paso 3: Crear los Pipelines en Jenkins

### 3.1 Pipeline Dev (ejemplo con auth-service)

Ir al dashboard de Jenkins y hacer clic en **New Item**.

- **Nombre:** `circle-guard-auth-dev`
- **Tipo:** Pipeline
- Hacer clic en **OK**

En la configuracion del pipeline, bajar hasta la seccion **Pipeline** y seleccionar:

- **Definition:** Pipeline script from SCM
- **SCM:** Git
- **Repository URL:** `https://github.com/SrCracles/circle-guard-public.git`
- **Branch:** `*/master`
- **Script Path:** `jenkins/dev/Jenkinsfile-auth`

Guardar.

### 3.2 Repetir para los demas servicios

Repetir el paso anterior para cada servicio. La unica diferencia es el **Script Path**:

| Pipeline Name | Script Path |
|---------------|-------------|
| circle-guard-auth-dev | `jenkins/dev/Jenkinsfile-auth` |
| circle-guard-identity-dev | `jenkins/dev/Jenkinsfile-identity` |
| circle-guard-form-dev | `jenkins/dev/Jenkinsfile-form` |
| circle-guard-promotion-dev | `jenkins/dev/Jenkinsfile-promotion` |
| circle-guard-notification-dev | `jenkins/dev/Jenkinsfile-notification` |
| circle-guard-gateway-dev | `jenkins/dev/Jenkinsfile-gateway` |
| circle-guard-file-dev | `jenkins/dev/Jenkinsfile-file` |
| circle-guard-dashboard-dev | `jenkins/dev/Jenkinsfile-dashboard` |

### 3.3 Pipeline Stage

- **Nombre:** `circle-guard-stage`
- **Script Path:** `jenkins/stage/Jenkinsfile-stage`

### 3.4 Pipeline Master

- **Nombre:** `circle-guard-master`
- **Script Path:** `jenkins/master/Jenkinsfile-master`

---

## Paso 4: Ejecutar los Pipelines

### Orden de ejecucion

Ejecutar primero los 8 pipelines de **dev** (pueden correr en paralelo):

```
circle-guard-auth-dev
circle-guard-identity-dev
circle-guard-form-dev
circle-guard-promotion-dev
circle-guard-notification-dev
circle-guard-gateway-dev
circle-guard-file-dev
circle-guard-dashboard-dev
```

Una vez que todos terminen exitosamente, ejecutar:

```
circle-guard-stage
```

Si stage pasa (E2E + Locust), ejecutar:

```
circle-guard-master
```

Master calculará la versión semántica (ej. `v1.0.1`), publicará las imágenes en DockerHub con los tags inmutables (`vX.Y.Z`) y mutables (`master`), desplegará la versión inmutable en producción y publicará las Release Notes en GitHub.

---

## Paso 5: Verificar el Despliegue

```powershell
# Ver pods en cada namespace
kubectl get pods -n dev
kubectl get pods -n stage
kubectl get pods -n master
kubectl get pods -n infra

# Ver logs de un servicio
kubectl logs -n master -l app=circleguard-auth-service

# Acceder a la interfaz de SonarQube (credenciales: admin/admin)
kubectl port-forward -n infra svc/sonarqube 9000:9000
```

---

## Paso 6: Bajar / Limpiar Todo

### Opcion A: Solo recursos (mantener cluster)

```powershell
.\teardown-kind.ps1
```

Esto borra deployments, services, namespaces e infraestructura, pero deja el cluster de Kind vivo.

Luego se puede volver a crear todo con:

```powershell
.\setup-kind.ps1
```

### Opcion B: Borrar absolutamente todo

```powershell
.\teardown-kind.ps1 -DeleteCluster -CleanDocker
```

Esto elimina:
- Todos los recursos de Kubernetes
- Los namespaces
- El cluster de Kind completo
- Las imagenes Docker de CircleGuard
- El archivo kubeconfig exportado

Para empezar de cero despues:

```powershell
.\setup-kind.ps1
```

> **Recordar:** Si se recrea el cluster, actualizar la variable `KUBECONFIG` en Jenkins con la nueva ruta.

---

## Resumen Rapido

| Accion | Comando |
|--------|---------|
| Crear todo | `.\setup-kind.ps1` |
| Borrar recursos | `.\teardown-kind.ps1` |
| Borrar todo | `.\teardown-kind.ps1 -DeleteCluster -CleanDocker` |
| Ver pods | `kubectl get pods -n <namespace>` |
| Ver logs | `kubectl logs -n <namespace> -l app=<nombre-servicio>` |
