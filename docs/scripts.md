# Scripts de Automatizacion

## `setup-kind.ps1` — Setup del Cluster Kubernetes (Kind)

**Ubicacion:** `setup-kind.ps1` (raiz del proyecto)

Este script PowerShell automatiza la creacion del entorno local de desarrollo para CircleGuard, preparando todo lo necesario para que los pipelines de Jenkins puedan ejecutarse correctamente.

---

## Que Hace

| Paso | Descripcion |
|------|-------------|
| **1. Prerequisitos** | Verifica que Docker, Kind y kubectl esten instalados y que Docker este corriendo |
| **2. Cluster Kind** | Crea el cluster `circleguard-cluster` con 2 nodos (control-plane + worker) y port mappings |
| **3. Namespaces** | Crea los namespaces: `dev`, `stage`, `master`, `infra` |
| **4. Infraestructura** | Despliega PostgreSQL, Zookeeper, Kafka, Redis, Neo4j y OpenLDAP en namespace `infra` |
| **5. Verificacion** | Espera a que los pods de infra esten ready y muestra resumen del estado |

---

## Como Ejecutarlo

Desde la raiz del proyecto:

```powershell
# Ejecucion estandar (crea cluster + infra completa)
.\setup-kind.ps1

# Saltar despliegue de infraestructura (solo cluster + namespaces)
.\setup-kind.ps1 -SkipInfra

```

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

## Requisitos Previos

- **Docker Desktop** instalado y en ejecucion
- **Kind** (Kubernetes in Docker) instalado
- **kubectl** instalado y en el PATH
- **PowerShell 7.0+** (el script usa `#Requires -Version 7.0`)

---

### Comandos utiles post-ejecucion

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
