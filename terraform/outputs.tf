output "environment" {
  description = "Provisioned environment."
  value       = var.environment
}

output "resource_group_name" {
  description = "Azure resource group name."
  value       = azurerm_resource_group.main.name
}

output "aks_cluster_name" {
  description = "AKS cluster name."
  value       = module.aks.cluster_name
}

output "aks_get_credentials_command" {
  description = "Command to configure kubectl against this AKS cluster."
  value       = "az aks get-credentials --resource-group ${azurerm_resource_group.main.name} --name ${module.aks.cluster_name} --overwrite-existing"
}

output "kubernetes_namespaces" {
  description = "Namespaces managed by Terraform."
  value       = module.infra.namespaces
}

output "infra_service_names" {
  description = "Support services created by Terraform."
  value       = module.infra.service_names
}
