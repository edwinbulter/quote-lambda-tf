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

variable "azure_ad_client_secret" {
  description = "Azure AD client secret"
  type        = string
  sensitive   = true
}

variable "azure_ad_domain" {
  description = "Azure AD domain"
  type        = string
  default     = "edwinbulteroutlook.onmicrosoft.com"
}

variable "azure_ad_instance" {
  description = "Azure AD instance URL"
  type        = string
  default     = "https://login.microsoftonline.com/"
}

variable "table_storage_account_name" {
  description = "Table storage account name"
  type        = string
  default     = "qbtstk9asli"
}
