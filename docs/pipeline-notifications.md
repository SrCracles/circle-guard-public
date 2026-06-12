# Notificaciones automaticas de fallos en pipelines (HU-17)

CircleGuard envia notificaciones por **email** cuando un pipeline de Jenkins falla y, cuando un build exitoso recupera un fallo previo.

## Canal de notificacion

| Canal | Variable Jenkins | Requisito |
|-------|------------------|-----------|
| Email | `CG_NOTIFY_EMAIL` | Plugin **Email Extension** (recomendado) y SMTP configurado en Jenkins |

## Contenido del email

Cada mensaje incluye:

- Nombre del pipeline
- Etapa que fallo (o `N/A (recovered)` en recuperacion)
- Enlace al log de Jenkins (`BUILD_URL/console`)
- SHA y mensaje del commit (`git rev-parse HEAD`, `git log -1`)

## Implementacion

| Archivo | Proposito |
|---------|-----------|
| `jenkins/lib/pipeline-notifications.groovy` | Logica compartida de notificacion por email |
| `jenkins/dev/Jenkinsfile-*` | `post { failure/success }` en los 8 pipelines dev |
| `jenkins/stage/Jenkinsfile-stage` | Notificaciones del pipeline stage |
| `jenkins/master/Jenkinsfile-master` | Notificaciones del pipeline master |

### Recuperacion tras fallo

En `post { success }`, se llama a `sendRecoveryNotification()` solo si `currentBuild.previousBuild.result == 'FAILURE'`.

## Configuracion en Jenkins

### 1. SMTP del servidor

Ir a **Manage Jenkins > System > Extended E-mail Notification** (o **E-mail Notification**) y configurar el servidor SMTP (host, puerto, credenciales si aplica).

### 2. Destinatario

Ir a **Manage Jenkins > System > Global properties > Environment variables** y agregar:

| Variable | Ejemplo | Descripcion |
|----------|---------|-------------|
| `CG_NOTIFY_EMAIL` | `devops@universidad.edu` | Email del equipo DevOps que recibe las alertas |

### 3. Plugin recomendado

Instalar **Email Extension** desde **Manage Jenkins > Plugins** para usar `emailext` con mejor formato y trazabilidad.

## Validacion

1. Configurar SMTP en Jenkins y definir `CG_NOTIFY_EMAIL`.
2. Ejecutar un pipeline dev y forzar fallo (por ejemplo, detener Docker temporalmente).
3. Verificar el email con asunto `[CircleGuard] FAILED: ...`.
4. Corregir el problema y volver a ejecutar: debe llegar `[CircleGuard] RECOVERED: ...` solo si el build anterior fallo.
