environment         = "stage"
location            = "eastus"
resource_group_name = "circleguard-stage-rg"

aks_node_count      = 1
aks_vm_size         = "Standard_FX2mds_v2"
aks_os_disk_size_gb = 64
enable_aks_monitoring = false
enable_auto_scaling = false

infra_namespace     = "infra"
sonarqube_namespace = "sonarqube"
extra_namespaces    = []
enable_shared_infra = true
enable_sonarqube    = false

infra_replicas = {
  postgresql = 1
  zookeeper  = 1
  kafka      = 1
  redis      = 1
  neo4j      = 1
  openldap   = 1
  sonarqube  = 0
}

tags = {
  "cost-center" = "student-credit"
}
