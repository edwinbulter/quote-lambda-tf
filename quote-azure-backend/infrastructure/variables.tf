variable "location" {
  description = "Azure region"
  type        = string
  default     = "Germany West Central"
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
