environment         = "dev"
location            = "eastus"
resource_group_name = "circleguard-dev-rg"

aks_node_count      = 1
aks_vm_size         = "Standard_B2ms"
aks_os_disk_size_gb = 64
enable_auto_scaling = false

infra_namespace  = "infra"
extra_namespaces = []
enable_sonarqube = true

infra_replicas = {
  postgresql = 1
  zookeeper  = 1
  kafka      = 1
  redis      = 1
  neo4j      = 1
  openldap   = 1
  sonarqube  = 1
}

tags = {
  "cost-center" = "student-credit"
}
