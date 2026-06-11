locals {
  labels = {
    component   = "infrastructure"
    environment = var.environment
  }

  postgres_init_sql = join("\n", [
    for database_name in var.postgres_databases :
    format("SELECT 'CREATE DATABASE %s' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '%s')\\gexec", database_name, database_name)
  ])
}

resource "kubernetes_namespace_v1" "managed" {
  for_each = toset(var.namespaces)

  metadata {
    name = each.value
    labels = {
      environment = each.value == var.infra_namespace ? "shared" : each.value
      "managed-by" = "terraform"
    }
  }
}

resource "kubernetes_secret_v1" "infra" {
  count = var.enable_shared_infra ? 1 : 0

  metadata {
    name      = "circleguard-infra-secrets"
    namespace = kubernetes_namespace_v1.managed[var.infra_namespace].metadata[0].name
    labels    = local.labels
  }

  type = "Opaque"

  data = {
    POSTGRES_PASSWORD   = var.postgres_password
    NEO4J_AUTH          = "${var.neo4j_username}/${var.neo4j_password}"
    LDAP_ADMIN_PASSWORD = var.ldap_admin_password
  }

  depends_on = [kubernetes_namespace_v1.managed]
}

resource "kubernetes_config_map_v1" "postgres_init" {
  count = var.enable_shared_infra ? 1 : 0

  metadata {
    name      = "postgres-init"
    namespace = kubernetes_namespace_v1.managed[var.infra_namespace].metadata[0].name
    labels = merge(local.labels, {
      app = "postgresql"
    })
  }

  data = {
    "init-db.sql" = local.postgres_init_sql
  }

  depends_on = [kubernetes_namespace_v1.managed]
}

resource "kubernetes_deployment_v1" "postgresql" {
  count = var.enable_shared_infra ? 1 : 0

  metadata {
    name      = "postgresql"
    namespace = kubernetes_namespace_v1.managed[var.infra_namespace].metadata[0].name
    labels = merge(local.labels, {
      app = "postgresql"
    })
  }

  spec {
    replicas = var.replicas.postgresql

    selector {
      match_labels = {
        app = "postgresql"
      }
    }

    template {
      metadata {
        labels = merge(local.labels, {
          app = "postgresql"
        })
      }

      spec {
        container {
          name  = "postgresql"
          image = var.images.postgresql

          port {
            container_port = 5432
          }

          env {
            name  = "POSTGRES_USER"
            value = var.postgres_username
          }

          env {
            name = "POSTGRES_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.infra[0].metadata[0].name
                key  = "POSTGRES_PASSWORD"
              }
            }
          }

          env {
            name  = "POSTGRES_DB"
            value = var.postgres_database
          }

          volume_mount {
            name       = "postgresql-data"
            mount_path = "/var/lib/postgresql/data"
          }

          volume_mount {
            name       = "init-sql"
            mount_path = "/docker-entrypoint-initdb.d"
          }
        }

        volume {
          name = "postgresql-data"
          empty_dir {}
        }

        volume {
          name = "init-sql"
          config_map {
            name = kubernetes_config_map_v1.postgres_init[0].metadata[0].name
          }
        }
      }
    }
  }
}

resource "kubernetes_service_v1" "postgresql" {
  count = var.enable_shared_infra ? 1 : 0

  metadata {
    name      = "postgresql"
    namespace = kubernetes_namespace_v1.managed[var.infra_namespace].metadata[0].name
    labels = merge(local.labels, {
      app = "postgresql"
    })
  }

  spec {
    selector = {
      app = "postgresql"
    }

    port {
      name        = "postgres"
      port        = 5432
      target_port = 5432
    }

    type = "ClusterIP"
  }
}

resource "kubernetes_service_v1" "zookeeper" {
  count = var.enable_shared_infra ? 1 : 0

  metadata {
    name      = "zookeeper"
    namespace = kubernetes_namespace_v1.managed[var.infra_namespace].metadata[0].name
    labels = merge(local.labels, {
      app = "zookeeper"
    })
  }

  spec {
    selector = {
      app = "zookeeper"
    }

    port {
      name        = "client"
      port        = 2181
      target_port = 2181
    }

    type = "ClusterIP"
  }
}

resource "kubernetes_stateful_set_v1" "zookeeper" {
  count = var.enable_shared_infra ? 1 : 0

  metadata {
    name      = "zookeeper"
    namespace = kubernetes_namespace_v1.managed[var.infra_namespace].metadata[0].name
    labels = merge(local.labels, {
      app = "zookeeper"
    })
  }

  spec {
    service_name = kubernetes_service_v1.zookeeper[0].metadata[0].name
    replicas     = var.replicas.zookeeper

    selector {
      match_labels = {
        app = "zookeeper"
      }
    }

    template {
      metadata {
        labels = merge(local.labels, {
          app = "zookeeper"
        })
      }

      spec {
        enable_service_links = false

        container {
          name  = "zookeeper"
          image = var.images.zookeeper

          port {
            container_port = 2181
          }

          env {
            name  = "ZOOKEEPER_CLIENT_PORT"
            value = "2181"
          }

          env {
            name  = "ZOOKEEPER_TICK_TIME"
            value = "2000"
          }
        }
      }
    }
  }
}

resource "kubernetes_service_v1" "kafka" {
  count = var.enable_shared_infra ? 1 : 0

  metadata {
    name      = "kafka"
    namespace = kubernetes_namespace_v1.managed[var.infra_namespace].metadata[0].name
    labels = merge(local.labels, {
      app = "kafka"
    })
  }

  spec {
    selector = {
      app = "kafka"
    }

    port {
      name        = "broker"
      port        = 9092
      target_port = 9092
    }

    type = "ClusterIP"
  }
}

resource "kubernetes_stateful_set_v1" "kafka" {
  count = var.enable_shared_infra ? 1 : 0

  metadata {
    name      = "kafka"
    namespace = kubernetes_namespace_v1.managed[var.infra_namespace].metadata[0].name
    labels = merge(local.labels, {
      app = "kafka"
    })
  }

  spec {
    service_name = kubernetes_service_v1.kafka[0].metadata[0].name
    replicas     = var.replicas.kafka

    selector {
      match_labels = {
        app = "kafka"
      }
    }

    template {
      metadata {
        labels = merge(local.labels, {
          app = "kafka"
        })
      }

      spec {
        enable_service_links = false

        container {
          name  = "kafka"
          image = var.images.kafka

          port {
            container_port = 9092
          }

          env {
            name  = "KAFKA_BROKER_ID"
            value = "1"
          }

          env {
            name  = "KAFKA_ZOOKEEPER_CONNECT"
            value = "zookeeper:2181"
          }

          env {
            name  = "KAFKA_ADVERTISED_LISTENERS"
            value = "PLAINTEXT://kafka:9092"
          }

          env {
            name  = "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP"
            value = "PLAINTEXT:PLAINTEXT"
          }

          env {
            name  = "KAFKA_INTER_BROKER_LISTENER_NAME"
            value = "PLAINTEXT"
          }

          env {
            name  = "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR"
            value = "1"
          }

          env {
            name  = "KAFKA_AUTO_CREATE_TOPICS_ENABLE"
            value = "true"
          }
        }
      }
    }
  }
}

resource "kubernetes_deployment_v1" "redis" {
  count = var.enable_shared_infra ? 1 : 0

  metadata {
    name      = "redis"
    namespace = kubernetes_namespace_v1.managed[var.infra_namespace].metadata[0].name
    labels = merge(local.labels, {
      app = "redis"
    })
  }

  spec {
    replicas = var.replicas.redis

    selector {
      match_labels = {
        app = "redis"
      }
    }

    template {
      metadata {
        labels = merge(local.labels, {
          app = "redis"
        })
      }

      spec {
        container {
          name  = "redis"
          image = var.images.redis

          port {
            container_port = 6379
          }
        }
      }
    }
  }
}

resource "kubernetes_service_v1" "redis" {
  count = var.enable_shared_infra ? 1 : 0

  metadata {
    name      = "redis"
    namespace = kubernetes_namespace_v1.managed[var.infra_namespace].metadata[0].name
    labels = merge(local.labels, {
      app = "redis"
    })
  }

  spec {
    selector = {
      app = "redis"
    }

    port {
      name        = "redis"
      port        = 6379
      target_port = 6379
    }

    type = "ClusterIP"
  }
}

resource "kubernetes_deployment_v1" "neo4j" {
  count = var.enable_shared_infra ? 1 : 0

  metadata {
    name      = "neo4j"
    namespace = kubernetes_namespace_v1.managed[var.infra_namespace].metadata[0].name
    labels = merge(local.labels, {
      app = "neo4j"
    })
  }

  spec {
    replicas = var.replicas.neo4j

    selector {
      match_labels = {
        app = "neo4j"
      }
    }

    template {
      metadata {
        labels = merge(local.labels, {
          app = "neo4j"
        })
      }

      spec {
        container {
          name  = "neo4j"
          image = var.images.neo4j

          port {
            container_port = 7687
          }

          port {
            container_port = 7474
          }

          env {
            name = "NEO4J_AUTH"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.infra[0].metadata[0].name
                key  = "NEO4J_AUTH"
              }
            }
          }

          env {
            name  = "NEO4J_PLUGINS"
            value = "[\"apoc\"]"
          }

          env {
            name  = "NEO4J_server_config_strict__validation_enabled"
            value = "false"
          }

          volume_mount {
            name       = "neo4j-data"
            mount_path = "/data"
          }
        }

        volume {
          name = "neo4j-data"
          empty_dir {}
        }
      }
    }
  }
}

resource "kubernetes_service_v1" "neo4j" {
  count = var.enable_shared_infra ? 1 : 0

  metadata {
    name      = "neo4j"
    namespace = kubernetes_namespace_v1.managed[var.infra_namespace].metadata[0].name
    labels = merge(local.labels, {
      app = "neo4j"
    })
  }

  spec {
    selector = {
      app = "neo4j"
    }

    port {
      name        = "bolt"
      port        = 7687
      target_port = 7687
    }

    port {
      name        = "http"
      port        = 7474
      target_port = 7474
    }

    type = "ClusterIP"
  }
}

resource "kubernetes_deployment_v1" "openldap" {
  count = var.enable_shared_infra ? 1 : 0

  metadata {
    name      = "openldap"
    namespace = kubernetes_namespace_v1.managed[var.infra_namespace].metadata[0].name
    labels = merge(local.labels, {
      app = "openldap"
    })
  }

  spec {
    replicas = var.replicas.openldap

    selector {
      match_labels = {
        app = "openldap"
      }
    }

    template {
      metadata {
        labels = merge(local.labels, {
          app = "openldap"
        })
      }

      spec {
        container {
          name  = "openldap"
          image = var.images.openldap

          port {
            container_port = 389
          }

          port {
            container_port = 636
          }

          env {
            name  = "LDAP_ORGANISATION"
            value = var.ldap_organisation
          }

          env {
            name  = "LDAP_DOMAIN"
            value = var.ldap_domain
          }

          env {
            name = "LDAP_ADMIN_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.infra[0].metadata[0].name
                key  = "LDAP_ADMIN_PASSWORD"
              }
            }
          }
        }
      }
    }
  }
}

resource "kubernetes_service_v1" "openldap" {
  count = var.enable_shared_infra ? 1 : 0

  metadata {
    name      = "openldap"
    namespace = kubernetes_namespace_v1.managed[var.infra_namespace].metadata[0].name
    labels = merge(local.labels, {
      app = "openldap"
    })
  }

  spec {
    selector = {
      app = "openldap"
    }

    port {
      name        = "ldap"
      port        = 389
      target_port = 389
    }

    port {
      name        = "ldaps"
      port        = 636
      target_port = 636
    }

    type = "ClusterIP"
  }
}

resource "kubernetes_deployment_v1" "sonarqube" {
  count = var.enable_sonarqube ? 1 : 0

  metadata {
    name      = "sonarqube"
    namespace = kubernetes_namespace_v1.managed[var.sonarqube_namespace].metadata[0].name
    labels = merge(local.labels, {
      app = "sonarqube"
    })
  }

  spec {
    replicas = var.replicas.sonarqube

    selector {
      match_labels = {
        app = "sonarqube"
      }
    }

    template {
      metadata {
        labels = merge(local.labels, {
          app = "sonarqube"
        })
      }

      spec {
        container {
          name  = "sonarqube"
          image = var.images.sonarqube

          port {
            container_port = 9000
          }

          env {
            name  = "SONAR_ES_BOOTSTRAP_CHECKS_DISABLE"
            value = "true"
          }

          resources {
            requests = {
              memory = "2Gi"
              cpu    = "1000m"
            }
            limits = {
              memory = "4Gi"
              cpu    = "2000m"
            }
          }
        }
      }
    }
  }
}

resource "kubernetes_service_v1" "sonarqube" {
  count = var.enable_sonarqube ? 1 : 0

  metadata {
    name      = "sonarqube"
    namespace = kubernetes_namespace_v1.managed[var.sonarqube_namespace].metadata[0].name
    labels = merge(local.labels, {
      app = "sonarqube"
    })
  }

  spec {
    selector = {
      app = "sonarqube"
    }

    port {
      port        = 9000
      target_port = 9000
      node_port   = var.sonarqube_node_port
    }

    type = "NodePort"
  }
}
