environment         = "dev"
location            = "eastus"
resource_group_name = "circleguard-dev-rg"

aks_node_count      = 1
aks_vm_size         = "Standard_B2ms"
aks_os_disk_size_gb = 64
enable_auto_scaling = false

infra_namespace     = "infra"
sonarqube_namespace = "sonarqube"
extra_namespaces    = []
enable_shared_infra = false
enable_sonarqube    = true

infra_replicas = {
  postgresql = 0
  zookeeper  = 0
  kafka      = 0
  redis      = 0
  neo4j      = 0
  openldap   = 0
  sonarqube  = 1
}

tags = {
  "cost-center" = "student-credit"
}
