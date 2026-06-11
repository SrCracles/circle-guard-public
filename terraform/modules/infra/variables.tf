variable "environment" {
  description = "Deployment environment."
  type        = string
}

variable "namespaces" {
  description = "Namespaces to create."
  type        = list(string)
}

variable "app_namespace" {
  description = "Application namespace for the current environment."
  type        = string
}

variable "infra_namespace" {
  description = "Infrastructure namespace."
  type        = string
}

variable "sonarqube_namespace" {
  description = "SonarQube namespace."
  type        = string
}

variable "enable_shared_infra" {
  description = "Deploy shared infrastructure services."
  type        = bool
}

variable "enable_sonarqube" {
  description = "Deploy SonarQube."
  type        = bool
}

variable "replicas" {
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

variable "images" {
  description = "Container images for infrastructure workloads."
  type = object({
    postgresql = string
    zookeeper  = string
    kafka      = string
    redis      = string
    neo4j      = string
    openldap   = string
    sonarqube  = string
  })
  default = {
    postgresql = "postgres:16-alpine"
    zookeeper  = "confluentinc/cp-zookeeper:7.6.0"
    kafka      = "confluentinc/cp-kafka:7.6.0"
    redis      = "redis:7-alpine"
    neo4j      = "neo4j:5.13-community"
    openldap   = "osixia/openldap:1.5.0"
    sonarqube  = "sonarqube:lts-community"
  }
}

variable "postgres_username" {
  description = "PostgreSQL admin username."
  type        = string
}

variable "postgres_password" {
  description = "PostgreSQL admin password."
  type        = string
  sensitive   = true
}

variable "postgres_database" {
  description = "Default PostgreSQL database."
  type        = string
}

variable "postgres_databases" {
  description = "Microservice databases to create during PostgreSQL initialization."
  type        = list(string)
}

variable "neo4j_username" {
  description = "Neo4j username."
  type        = string
}

variable "neo4j_password" {
  description = "Neo4j password."
  type        = string
  sensitive   = true
}

variable "ldap_organisation" {
  description = "OpenLDAP organization."
  type        = string
}

variable "ldap_domain" {
  description = "OpenLDAP domain."
  type        = string
}

variable "ldap_admin_password" {
  description = "OpenLDAP admin password."
  type        = string
  sensitive   = true
}

variable "sonarqube_node_port" {
  description = "NodePort used by SonarQube."
  type        = number
  default     = 30090
}
