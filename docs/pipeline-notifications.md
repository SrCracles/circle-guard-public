# Notificaciones automaticas de fallos en pipelines (HU-17)

CircleGuard envia notificaciones automaticas cuando un pipeline de Jenkins falla y, opcionalmente, cuando un build exitoso recupera un fallo previo.

## Canales soportados

| Canal | Variable Jenkins | Requisito |
|-------|------------------|-----------|
| Webhook Slack / Teams | `CG_NOTIFY_WEBHOOK_URL` | URL del incoming webhook |
| Email | `CG_NOTIFY_EMAIL` | Plugin **Email Extension** (recomendado) o SMTP configurado en Jenkins |

Opcional: `CG_NOTIFY_WEBHOOK_TYPE` = `slack` o `teams` (si se omite, se infiere por la URL).

## Contenido de la notificacion

Cada alerta incluye:

- Nombre del pipeline
- Etapa que fallo (o `N/A (recovered)` en recuperacion)
- Enlace al log de Jenkins (`BUILD_URL/console`)
- SHA y mensaje del commit (`git rev-parse HEAD`, `git log -1`)

## Implementacion

| Archivo | Proposito |
|---------|-----------|
| `jenkins/lib/pipeline-notifications.groovy` | Logica compartida de notificacion |
| `jenkins/dev/Jenkinsfile-*` | `post { failure/success }` en los 8 pipelines dev |
| `jenkins/stage/Jenkinsfile-stage` | Notificaciones del pipeline stage |
| `jenkins/master/Jenkinsfile-master` | Notificaciones del pipeline master |

### Recuperacion tras fallo

En `post { success }`, se llama a `sendRecoveryNotification()` solo si `currentBuild.previousBuild.result == 'FAILURE'`.

## Configuracion en Jenkins

Ir a **Manage Jenkins > System > Global properties > Environment variables**:

| Variable | Ejemplo | Descripcion |
|----------|---------|-------------|
| `CG_NOTIFY_WEBHOOK_URL` | `https://hooks.slack.com/services/...` | Webhook de Slack o Teams |
| `CG_NOTIFY_EMAIL` | `devops@universidad.edu` | Destinatario de email |
| `CG_NOTIFY_WEBHOOK_TYPE` | `teams` | Opcional si la URL no permite inferir el formato |

## Validacion

1. Configurar al menos un canal (`CG_NOTIFY_WEBHOOK_URL` o `CG_NOTIFY_EMAIL`).
2. Ejecutar un pipeline dev y forzar fallo (por ejemplo, deshabilitar temporalmente Docker).
3. Verificar mensaje en Slack/Teams o bandeja de email.
4. Corregir el problema y volver a ejecutar: debe llegar notificacion de **RECOVERED** solo si el build anterior fallo.

Ver comandos detallados en la seccion de pruebas al final de este documento en la respuesta del agente o en `docs/setup-guide.md`.
