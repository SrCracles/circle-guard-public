locals {
  name_prefix         = lower(replace("${var.project_name}-${var.environment}", "_", "-"))
  resource_group_name = coalesce(var.resource_group_name, "${local.name_prefix}-rg")
  aks_dns_prefix      = coalesce(var.aks_dns_prefix, "${local.name_prefix}-aks")
  app_namespace       = var.environment
  infra_namespaces    = var.enable_shared_infra ? [var.infra_namespace] : []
  sonar_namespaces    = var.enable_sonarqube ? [var.sonarqube_namespace] : []
  namespaces          = distinct(concat([local.app_namespace], local.infra_namespaces, local.sonar_namespaces, var.extra_namespaces))

  common_tags = merge(
    {
      project     = var.project_name
      environment = var.environment
      "managed-by" = "terraform"
    },
    var.tags
  )
}

resource "azurerm_resource_group" "main" {
  name     = local.resource_group_name
  location = var.location
  tags     = local.common_tags
}

module "aks" {
  source = "./modules/aks"

  name_prefix        = local.name_prefix
  resource_group     = azurerm_resource_group.main
  dns_prefix         = local.aks_dns_prefix
  kubernetes_version = var.aks_kubernetes_version
  node_count         = var.aks_node_count
  vm_size            = var.aks_vm_size
  os_disk_size_gb    = var.aks_os_disk_size_gb
  enable_auto_scaling = var.enable_auto_scaling
  min_count          = var.aks_min_count
  max_count          = var.aks_max_count
  tags               = local.common_tags
}

module "infra" {
  source = "./modules/infra"

  providers = {
    kubernetes = kubernetes
  }

  environment         = var.environment
  namespaces          = local.namespaces
  app_namespace       = local.app_namespace
  infra_namespace     = var.infra_namespace
  sonarqube_namespace = var.sonarqube_namespace
  enable_shared_infra = var.enable_shared_infra
  enable_sonarqube    = var.enable_sonarqube
  replicas            = var.infra_replicas
  postgres_username   = var.postgres_username
  postgres_password   = var.postgres_password
  postgres_database   = var.postgres_database
  postgres_databases  = var.postgres_databases
  neo4j_username      = var.neo4j_username
  neo4j_password      = var.neo4j_password
  ldap_organisation   = var.ldap_organisation
  ldap_domain         = var.ldap_domain
  ldap_admin_password = var.ldap_admin_password

  depends_on = [module.aks]
}
