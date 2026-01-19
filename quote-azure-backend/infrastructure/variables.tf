# Infrastructure Variables
variable "location" {
  description = "Azure region for resources"
  type        = string
  default     = "West Europe"
}

variable "resource_group_name" {
  description = "Name of the resource group"
  type        = string
  default     = "quote-backend-rg"
}

variable "function_app_name" {
  description = "Name of the Function App"
  type        = string
  default     = "quote-backend-function"
}

variable "api_gateway_name" {
  description = "Name of the API Gateway"
  type        = string
  default     = "quote-api-gateway"
}

variable "subscription_id" {
  description = "Azure Subscription ID"
  type        = string
  default     = "740da4a2-18ba-4ffc-827a-1b526cbc3b9f"
  sensitive   = true
}

# Storage Configuration
variable "storage_account_name" {
  description = "Base name for the storage account"
  type        = string
  default     = "quotebackendstorage"
}

variable "table_storage_account_name" {
  description = "Name of the storage account for tables"
  type        = string
  default     = "qbtstk9asli"
}

# JWT Configuration
variable "jwt_signing_key" {
  description = "JWT signing key for authentication"
  type        = string
  sensitive   = true
}

variable "jwt_issuer" {
  description = "JWT issuer"
  type        = string
  default     = "https://quote-backend-function.azurewebsites.net"
}

variable "jwt_audience" {
  description = "JWT audience"
  type        = string
  default     = "quote-azure-backend-users"
}

# API Gateway Configuration
variable "api_gateway_publisher_email" {
  description = "Email for API Gateway notifications"
  type        = string
  default     = "admin@example.com"
}

variable "api_gateway_master_key" {
  description = "Master key for API Gateway"
  type        = string
  sensitive   = true
}
