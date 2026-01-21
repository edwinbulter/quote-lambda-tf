# Infrastructure Variables
variable "location" {
  description = "Azure region for resources"
  type        = string
  sensitive   = true
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
  sensitive   = true
}

# Storage Configuration
variable "table_storage_account_name" {
  description = "Name of the storage account for tables"
  type        = string
  sensitive   = true
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
  sensitive   = true
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
