# Outputs for Frontend Infrastructure

output "resource_group_name" {
  description = "Name of the resource group"
  value       = var.use_existing_storage_account ? var.frontend_resource_group_name : azurerm_resource_group.frontend[0].name
  sensitive   = true
}

output "storage_account_name" {
  description = "Name of the storage account"
  value       = var.use_existing_storage_account ? var.frontend_storage_account_name : azurerm_storage_account.frontend[0].name
  sensitive   = true
}

output "storage_account_id" {
  description = "ID of the storage account"
  value       = var.use_existing_storage_account ? data.azurerm_storage_account.existing[0].id : azurerm_storage_account.frontend[0].id
}

output "static_website_url" {
  description = "URL of the static website"
  value       = var.use_existing_storage_account ? data.azurerm_storage_account.existing[0].primary_web_endpoint : azurerm_storage_account.frontend[0].primary_web_endpoint
}

output "cdn_endpoint_url" {
  description = "URL of the CDN endpoint"
  value       = var.enable_cdn ? azurerm_cdn_endpoint.frontend[0].fqdn : null
}

output "primary_access_key" {
  description = "Primary access key for the storage account"
  value       = var.use_existing_storage_account ? data.azurerm_storage_account.existing[0].primary_access_key : azurerm_storage_account.frontend[0].primary_access_key
  sensitive   = true
}

output "connection_string" {
  description = "Connection string for the storage account"
  value       = var.use_existing_storage_account ? data.azurerm_storage_account.existing[0].primary_connection_string : azurerm_storage_account.frontend[0].primary_connection_string
  sensitive   = true
}
