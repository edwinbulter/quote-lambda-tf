variable "location" {
  description = "Azure region"
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

variable "storage_account_name" {
  description = "Base name for the storage account"
  type        = string
  default     = "quotebackendstorage"
}

variable "table_storage_account_name" {
  description = "Table storage account name"
  type        = string
}

variable "jwt_signing_key" {
  description = "JWT signing key for authentication"
  type        = string
  sensitive   = true
}

variable "api_gateway_publisher_email" {
  description = "Email for API Gateway notifications"
  type        = string
  default     = "admin@example.com"
}
