output "function_app_url" {
  description = "The URL of the Function App"
  value       = azurerm_windows_function_app.function_app.default_hostname
}

output "resource_group_name" {
  description = "The name of the Resource Group"
  value       = azurerm_resource_group.rg.name
}

output "storage_account_name" {
  description = "The name of the Storage Account"
  value       = data.azurerm_storage_account.table_storage.name
  sensitive   = true
}

output "function_app_name" {
  description = "The name of the Function App"
  value       = azurerm_windows_function_app.function_app.name
}

output "user_roles_table_name" {
  description = "The name of the UserRoles table"
  value       = azurerm_storage_table.user_roles.name
}

# API Gateway Outputs
output "api_gateway_url" {
  description = "API Gateway URL"
  value       = "https://${azurerm_api_management.quote_api.gateway_url}"
}

output "api_gateway_name" {
  description = "API Gateway name"
  value       = azurerm_api_management.quote_api.name
}

output "api_gateway_resource_id" {
  description = "API Gateway resource ID"
  value       = azurerm_api_management.quote_api.id
}
