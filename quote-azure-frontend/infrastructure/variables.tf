# Variables for Frontend Infrastructure

variable "resource_group_name" {
  description = "Name of the resource group"
  type        = string
  default     = "quote-frontend-rg"
}

variable "location" {
  description = "Azure region for resources"
  type        = string
  default     = "westeurope"
}

variable "use_existing_storage_account" {
  description = "Use existing storage account instead of creating new"
  type        = bool
  default     = true
}

variable "frontend_storage_account_name" {
  description = "Name of the storage account for frontend files"
  type        = string
  sensitive   = true
}

variable "frontend_resource_group_name" {
  description = "Resource group name of the frontend storage account"
  type        = string
  sensitive   = true
}

variable "storage_account_name" {
  description = "Name of the storage account for static website (used only if not using existing)"
  type        = string
  default     = "quotefrontend"
}

variable "frontend_domain_name" {
  description = "Custom domain name for the frontend (optional)"
  type        = string
  default     = ""
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "dev"
}

variable "enable_cdn" {
  description = "Enable CDN for the frontend"
  type        = bool
  default     = false
}

variable "custom_domain" {
  description = "Custom domain configuration"
  type = object({
    domain_name = string
    ttl         = number
  })
  default = {
    domain_name = ""
    ttl         = 3600
  }
}

variable "tags" {
  description = "Tags to apply to all resources"
  type        = map(string)
  default = {
    Project     = "quote-azure"
    Component   = "frontend"
    Environment = "dev"
  }
}
