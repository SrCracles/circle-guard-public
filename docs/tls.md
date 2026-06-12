# TLS para servicios expuestos publicamente (HU-37)

En el namespace `master`, **auth-service** y **gateway-service** solo son accesibles desde fuera del cluster a traves del Ingress NGINX con terminacion TLS en el puerto **443**.

## Componentes

| Recurso | Archivo | Descripcion |
|---------|---------|-------------|
| Ingress TLS | `k8s/master/ingress.yaml` | Rutas `/auth` y `/gateway` con redirect HTTP->HTTPS |
| Secret TLS | `circleguard-tls` (`kubernetes.io/tls`) | Certificado autofirmado (Kind) o Let's Encrypt (cloud) |
| Generacion local | `scripts/generate-tls-cert.ps1` | Crea `k8s/master/certs/tls.crt` y `tls.key` |
| Renovacion | `scripts/refresh-tls-secret.ps1` | Regenera certificado y actualiza el Secret |

## Entorno local (Kind)

`setup-kind.ps1`:

1. Instala NGINX Ingress Controller (puertos 80/443 mapeados al host).
2. Genera certificado autofirmado con SAN: `circleguard.local`, `localhost`, `127.0.0.1`.
3. Crea el Secret `circleguard-tls` en `master`.

El pipeline master despliega el Ingress junto con los microservicios (`kubectl apply -k k8s/master/`).

### Rutas HTTPS

| URL | Backend |
|-----|---------|
| `https://localhost/auth/api/v1/auth/login` | auth-service:8180 |
| `https://localhost/gateway/api/v1/gate/validate` | gateway-service:8087 |

El prefijo `/auth` o `/gateway` se elimina via `rewrite-target` antes de llegar al microservicio.

## Entorno cloud (Let's Encrypt)

Para AKS u otro cluster con DNS publico:

1. Instalar **cert-manager** en el cluster.
2. Crear un `ClusterIssuer` de Let's Encrypt (HTTP-01 o DNS-01).
3. Anotar el Ingress con `cert-manager.io/cluster-issuer: letsencrypt-prod`.
4. Reemplazar el Secret manual por uno gestionado por cert-manager.

Ejemplo de anotacion en `k8s/master/ingress.yaml` (cloud):

```yaml
metadata:
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
```

## Renovacion del certificado (Kind / autofirmado)

Cuando el certificado este proximo a expirar (default 365 dias):

```powershell
.\scripts\refresh-tls-secret.ps1
```

Esto regenera los archivos en `k8s/master/certs/` y actualiza el Secret `circleguard-tls` en `master`. El Ingress recarga el certificado sin reiniciar pods.

Verificar fecha de expiracion:

```powershell
openssl x509 -in k8s\master\certs\tls.crt -noout -dates
kubectl get secret circleguard-tls -n master -o jsonpath='{.data.tls\.crt}' | ForEach-Object { [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($_)) } | openssl x509 -noout -dates
```

## Seguridad

- Los Services de auth y gateway permanecen `ClusterIP`; no hay NodePort directo.
- `ssl-redirect` y `force-ssl-redirect` redirigen HTTP (puerto 80) a HTTPS.
- El trafico interno cluster-to-cluster sigue siendo HTTP (mTLS entre pods queda fuera del alcance de esta HU).
