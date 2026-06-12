variable "name_prefix" {
  description = "Name prefix for AKS resources."
  type        = string
}

variable "resource_group" {
  description = "Resource group object where AKS resources are created."
  type = object({
    name     = string
    location = string
  })
}

variable "dns_prefix" {
  description = "AKS DNS prefix."
  type        = string
}

variable "kubernetes_version" {
  description = "Optional Kubernetes version."
  type        = string
  default     = null
}

variable "node_count" {
  description = "Initial node count."
  type        = number
}

variable "vm_size" {
  description = "Default node pool VM size."
  type        = string
}

variable "os_disk_size_gb" {
  description = "Node OS disk size."
  type        = number
}

variable "enable_monitoring" {
  description = "Enable Azure Monitor for AKS through Log Analytics."
  type        = bool
}

variable "enable_auto_scaling" {
  description = "Enable node pool autoscaling."
  type        = bool
}

variable "min_count" {
  description = "Minimum node count when autoscaling is enabled."
  type        = number
  default     = null
}

variable "max_count" {
  description = "Maximum node count when autoscaling is enabled."
  type        = number
  default     = null
}

variable "tags" {
  description = "Common Azure tags."
  type        = map(string)
}
