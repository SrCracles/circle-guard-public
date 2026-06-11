output "namespaces" {
  description = "Namespaces created by Terraform."
  value       = sort(keys(kubernetes_namespace_v1.managed))
}

output "service_names" {
  description = "Infrastructure service names exposed inside the cluster."
  value = compact([
    kubernetes_service_v1.postgresql.metadata[0].name,
    kubernetes_service_v1.zookeeper.metadata[0].name,
    kubernetes_service_v1.kafka.metadata[0].name,
    kubernetes_service_v1.redis.metadata[0].name,
    kubernetes_service_v1.neo4j.metadata[0].name,
    kubernetes_service_v1.openldap.metadata[0].name,
    var.enable_sonarqube ? kubernetes_service_v1.sonarqube[0].metadata[0].name : ""
  ])
}
