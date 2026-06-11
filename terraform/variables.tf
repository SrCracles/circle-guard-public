variable "subscription_id" {
  description = "Azure subscription ID. Leave null to use the Azure CLI selected subscription."
  type        = string
  default     = null
}

variable "project_name" {
  description = "Project name used as Azure resource name prefix."
  type        = string
  default     = "circleguard"
}

variable "environment" {
  description = "Deployment environment."
  type        = string

  validation {
    condition     = contains(["dev", "stage", "master"], var.environment)
    error_message = "environment must be one of: dev, stage, master."
  }
}

variable "location" {
  description = "Azure region where resources will be created."
  type        = string
}

variable "resource_group_name" {
  description = "Optional resource group name. When null, a name is generated from project and environment."
  type        = string
  default     = null
}

variable "aks_dns_prefix" {
  description = "DNS prefix for the AKS API server."
  type        = string
  default     = null
}

variable "aks_kubernetes_version" {
  description = "Optional AKS Kubernetes version. Leave null to use Azure default."
  type        = string
  default     = null
}

variable "aks_node_count" {
  description = "Initial node count for the AKS default node pool."
  type        = number
}

variable "aks_vm_size" {
  description = "VM size for the AKS default node pool."
  type        = string
}

variable "aks_os_disk_size_gb" {
  description = "OS disk size for AKS nodes."
  type        = number
  default     = 64
}

variable "enable_auto_scaling" {
  description = "Enable autoscaling in the AKS default node pool."
  type        = bool
  default     = false
}

variable "aks_min_count" {
  description = "Minimum node count when autoscaling is enabled."
  type        = number
  default     = null
}

variable "aks_max_count" {
  description = "Maximum node count when autoscaling is enabled."
  type        = number
  default     = null
}

variable "infra_namespace" {
  description = "Kubernetes namespace for infrastructure services."
  type        = string
  default     = "infra"
}

variable "extra_namespaces" {
  description = "Additional namespaces to create in the AKS cluster."
  type        = list(string)
  default     = []
}

variable "enable_sonarqube" {
  description = "Deploy SonarQube in the infra namespace."
  type        = bool
  default     = true
}

variable "infra_replicas" {
  description = "Replica counts for infrastructure workloads."
  type = object({
    postgresql = number
    zookeeper  = number
    kafka      = number
    redis      = number
    neo4j      = number
    openldap   = number
    sonarqube  = number
  })
}

variable "postgres_username" {
  description = "PostgreSQL admin username."
  type        = string
  default     = "admin"
}

variable "postgres_database" {
  description = "Default PostgreSQL database."
  type        = string
  default     = "circleguard"
}

variable "postgres_databases" {
  description = "Microservice databases initialized in PostgreSQL."
  type        = list(string)
  default = [
    "circleguard_auth",
    "circleguard_identity",
    "circleguard_promotion",
    "circleguard_dashboard",
    "circleguard_form"
  ]
}

variable "postgres_password" {
  description = "PostgreSQL admin password. Set with TF_VAR_postgres_password."
  type        = string
  sensitive   = true
}

variable "neo4j_username" {
  description = "Neo4j username."
  type        = string
  default     = "neo4j"
}

variable "neo4j_password" {
  description = "Neo4j password. Set with TF_VAR_neo4j_password."
  type        = string
  sensitive   = true
}

variable "ldap_organisation" {
  description = "OpenLDAP organization name."
  type        = string
  default     = "CircleGuard"
}

variable "ldap_domain" {
  description = "OpenLDAP domain."
  type        = string
  default     = "circleguard.edu"
}

variable "ldap_admin_password" {
  description = "OpenLDAP admin password. Set with TF_VAR_ldap_admin_password."
  type        = string
  sensitive   = true
}

variable "tags" {
  description = "Common Azure tags."
  type        = map(string)
  default     = {}
}
