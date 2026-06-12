# Notificaciones automaticas de fallos en pipelines (HU-17)

CircleGuard envia notificaciones por **email** cuando un pipeline de Jenkins falla y, cuando un build exitoso recupera un fallo previo.

## Canal de notificacion

| Canal | Variable Jenkins | Requisito |
|-------|------------------|-----------|
| Email | `CG_NOTIFY_EMAIL` | Plugin **Email Extension**, SMTP configurado y credencial Jenkins |

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

---

## Que debe estar corriendo para probar

| Componente | Obligatorio para HU-17 | Notas |
|------------|------------------------|-------|
| Jenkins | Si | `http://localhost:8080` |
| SMTP + credencial configurados | Si | Ver pasos abajo |
| `CG_NOTIFY_EMAIL` | Si | Global property en Jenkins |
| Plugin **Email Extension** | Si | Usa `emailext` en los pipelines |
| Docker Desktop | Solo para fallo/recuperacion rapida | Parar Docker fuerza fallo en pipeline dev |
| Cluster Kind / kubectl | **No** | Los pipelines **dev** no usan Kubernetes |

Prueba recomendada: job **`circle-guard-auth-dev`** (no necesita cluster ni SonarQube si Test/Sonar/Trivy siguen deshabilitados).

---

## Configuracion en Jenkins (paso a paso)

### Paso 0 — Instalar plugins

1. **Manage Jenkins > Plugins > Available plugins**
2. Instalar **Email Extension** (y **Credentials**, si no viene ya instalado)
3. Reiniciar Jenkins si lo pide

### Paso 1 — Obtener contrasena SMTP (Gmail u Outlook)

Jenkins necesita usuario y contraseña del buzon que **envia** el correo. En versiones recientes de Jenkins **no** se escriben sueltos en Extended E-mail Notification: primero se crea una **credencial** (Paso 2).

#### Gmail (@gmail.com personal)

**No confundir:** *Passkeys and security keys* en Google **no** sirve para SMTP. Necesitas **Contraseñas de aplicaciones** (*App passwords*).

1. Activar **Verificacion en 2 pasos**: [myaccount.google.com/security](https://myaccount.google.com/security)
2. Crear contrasena de aplicacion: [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords)
   - Si el enlace dice que no esta disponible: confirma que 2FA esta activo, o usa Outlook (mas abajo).
   - Tipo de app: **Correo** o **Otro (nombre personalizado)** → ej. `Jenkins`
3. Copiar la clave de **16 caracteres** (sin espacios). **No** uses la contrasena normal de Gmail.

**Si no aparece "App passwords":**

- Cuenta **universidad/empresa** (@uni.edu): el admin suele bloquearlas → usar SMTP institucional u Outlook personal.
- Sin **2FA** activo: Google no muestra la opcion.

#### Outlook / Hotmail (alternativa mas simple)

No requiere contrasena de aplicacion en la mayoria de cuentas personales `@outlook.com` / `@hotmail.com`. Usa la contrasena normal de la cuenta.

---

### Paso 2 — Crear credencial SMTP en Jenkins

En **Extended E-mail Notification** suele aparecer un desplegable **Credentials**, no campos sueltos de usuario/contrasena. Hay que crear la credencial antes:

1. **Manage Jenkins > Credentials**
2. **System > Global credentials (unrestricted) > Add Credentials**
3. Completar:

| Campo | Valor |
|-------|-------|
| Kind | `Username with password` |
| Scope | Global |
| Username | correo completo, ej. `tucorreo@gmail.com` |
| Password | contrasena de aplicacion (Gmail) o contrasena de cuenta (Outlook) |
| ID | `smtp-gmail` (sin espacios; elige el nombre que quieras) |
| Description | `SMTP alertas CircleGuard` |

4. **Create**

---

### Paso 3 — Extended E-mail Notification

1. **Manage Jenkins > System**
2. Seccion **Extended E-mail Notification**

| Campo | Gmail | Outlook |
|-------|-------|---------|
| SMTP server | `smtp.gmail.com` | `smtp.office365.com` |
| SMTP Port | `587` | `587` |
| Use SSL | desmarcado | desmarcado |
| Use TLS | **marcado** | **marcado** |
| **Credentials** | seleccionar `smtp-gmail` (la del Paso 2) | misma credencial con usuario Outlook |

3. **Default Content Type**: `text/plain` (recomendado)
4. **Default Recipients**: opcional (el pipeline usa `CG_NOTIFY_EMAIL`)

---

### Paso 4 — E-mail Notification (seccion basica)

En la misma pagina **System**, seccion **E-mail Notification** (arriba o abajo de Extended):

| Campo | Gmail | Outlook |
|-------|-------|---------|
| SMTP server | `smtp.gmail.com` | `smtp.office365.com` |
| SMTP Port | `587` | `587` |
| Use SMTP Authentication | marcado | marcado |
| User Name | `tucorreo@gmail.com` | `tucorreo@outlook.com` |
| Password | misma contrasena del Paso 2 | misma contrasena |
| Use SSL | desmarcado | desmarcado |
| Reply-To Address | tu correo | tu correo |
| Charset | `UTF-8` | `UTF-8` |

Configurar **ambas** secciones (Extended + basica) con los mismos datos SMTP.

---

### Paso 5 — Probar envio de correo

1. En **E-mail Notification**, marcar **Test configuration by sending test e-mail**
2. **Test e-mail recipient**: tu correo
3. **Test configuration**
4. Debe aparecer **Email was successfully sent** (revisar spam si no llega)

Si falla: credencial incorrecta, puerto/TLS mal, o firewall bloqueando puerto 587.

---

### Paso 6 — Destinatario de alertas (`CG_NOTIFY_EMAIL`)

1. **Manage Jenkins > System > Global properties**
2. Marcar **Environment variables**
3. Agregar:

| Nombre | Valor |
|--------|-------|
| `CG_NOTIFY_EMAIL` | correo que recibe alertas FAILED / RECOVERED |

4. **Save** al final de la pagina

---

## Validacion de HU-17 (sin cluster)

### Probar email FAILED

1. Confirmar que **Test configuration** del Paso 5 funciono
2. **Detener Docker Desktop**
3. En Jenkins, ejecutar **`circle-guard-auth-dev`** (Build Now)
4. El build debe quedar en rojo (falla en **Build Docker Image** o similar)
5. Revisar correo: asunto `[CircleGuard] FAILED: circleguard-auth-service (dev) - ...`
6. En el log del build, en `post { failure }`, debe verse el mismo texto (pipeline, etapa, commit, URL)

### Probar email RECOVERED

1. **Iniciar Docker Desktop** de nuevo
2. Volver a ejecutar el mismo job
3. Si el build anterior fallo y este pasa en verde: correo `[CircleGuard] RECOVERED: ...`
4. Si el build anterior fue verde, **no** debe llegar email de recuperacion (comportamiento esperado)

### Que NO hace falta para esta prueba

```powershell
# No necesario para validar HU-17 con pipeline dev
.\setup-kind.ps1
kubectl get pods
```

`KUBECONFIG` y el cluster solo importan para pipelines **stage** y **master**.

---

## Resumen del flujo

```
Plugin Email Extension
        ↓
Contrasena de aplicacion (Gmail) o cuenta Outlook
        ↓
Credentials: Username with password (smtp-gmail)
        ↓
Extended E-mail Notification: SMTP + TLS + Credentials
        ↓
E-mail Notification: mismo SMTP + Test configuration OK
        ↓
CG_NOTIFY_EMAIL en Global properties
        ↓
Pipeline falla → post { failure } → emailext → bandeja de entrada
```
