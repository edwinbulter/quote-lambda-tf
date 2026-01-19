# API Gateway Instance
resource "azurerm_api_management" "quote_api" {
  name                = var.api_gateway_name
  location            = azurerm_resource_group.rg.location
  resource_group_name = azurerm_resource_group.rg.name
  publisher_email     = var.api_gateway_publisher_email
  publisher_name      = "Quote Backend API"
  sku_name            = "Developer_1"
}

# API Gateway Logger
resource "azurerm_api_management_logger" "quote_logger" {
  name                = "quote-logger"
  resource_group_name = azurerm_resource_group.rg.name
  api_management_name = azurerm_api_management.quote_api.name
  resource_id         = data.azurerm_storage_account.table_storage.id
  application_insights {
    instrumentation_key = azurerm_application_insights.app_insights.instrumentation_key
  }
}

# API Gateway Diagnostic
resource "azurerm_api_management_diagnostic" "quote_diagnostic" {
  identifier               = "applicationinsights"
  resource_group_name      = azurerm_resource_group.rg.name
  api_management_name      = azurerm_api_management.quote_api.name
  api_management_logger_id = azurerm_api_management_logger.quote_logger.id
  sampling_percentage      = 100
  always_log_errors        = true
  http_correlation_protocol = "None"
}

# API Gateway API
resource "azurerm_api_management_api" "quote_backend_api" {
  name                = "quote-backend-api"
  resource_group_name = azurerm_resource_group.rg.name
  api_management_name = azurerm_api_management.quote_api.name
  revision            = "1"
  service_url         = "https://${azurerm_windows_function_app.function_app.default_hostname}/api"
  protocols           = ["https"]
  display_name        = "Quote Backend API"
  description         = "API for Quote Backend Function App"
  path                = "quote"
  api_type            = "http"
}

# Catch-all operation for all endpoints
resource "azurerm_api_management_api_operation" "catch_all" {
  api_name            = azurerm_api_management_api.quote_backend_api.name
  resource_group_name = azurerm_resource_group.rg.name
  api_management_name = azurerm_api_management.quote_api.name
  display_name        = "Catch All"
  method              = "GET"
  url_template        = "/*"
  description         = "Forward all GET requests to Function App"
  operation_id        = "catch-all-get"
}

# Catch-all POST operation
resource "azurerm_api_management_api_operation" "catch_all_post" {
  api_name            = azurerm_api_management_api.quote_backend_api.name
  resource_group_name = azurerm_resource_group.rg.name
  api_management_name = azurerm_api_management.quote_api.name
  display_name        = "Catch All POST"
  method              = "POST"
  url_template        = "/*"
  description         = "Forward all POST requests to Function App"
  operation_id        = "catch-all-post"
}

# Catch-all PUT operation
resource "azurerm_api_management_api_operation" "catch_all_put" {
  api_name            = azurerm_api_management_api.quote_backend_api.name
  resource_group_name = azurerm_resource_group.rg.name
  api_management_name = azurerm_api_management.quote_api.name
  display_name        = "Catch All PUT"
  method              = "PUT"
  url_template        = "/*"
  description         = "Forward all PUT requests to Function App"
  operation_id        = "catch-all-put"
}

# Catch-all DELETE operation
resource "azurerm_api_management_api_operation" "catch_all_delete" {
  api_name            = azurerm_api_management_api.quote_backend_api.name
  resource_group_name = azurerm_resource_group.rg.name
  api_management_name = azurerm_api_management.quote_api.name
  display_name        = "Catch All DELETE"
  method              = "DELETE"
  url_template        = "/*"
  description         = "Forward all DELETE requests to Function App"
  operation_id        = "catch-all-delete"
}

# Backend for Function App
resource "azurerm_api_management_backend" "function_backend" {
  name                = "quote-function-backend"
  resource_group_name = azurerm_resource_group.rg.name
  api_management_name = azurerm_api_management.quote_api.name
  protocol            = "http"
  url                 = "https://${azurerm_windows_function_app.function_app.default_hostname}/api"
}

# API Policy to add Function Key
resource "azurerm_api_management_api_policy" "function_key_policy" {
  api_name            = azurerm_api_management_api.quote_backend_api.name
  resource_group_name = azurerm_resource_group.rg.name
  api_management_name = azurerm_api_management.quote_api.name
  xml_content         = <<XML
<policies>
  <inbound>
    <base />
    <set-query-parameter name="code" exists-action="override">
      <value>${var.api_gateway_master_key}</value>
    </set-query-parameter>
  </inbound>
  <backend>
    <base />
  </backend>
  <outbound>
    <base />
  </outbound>
</policies>
  XML
}

# API Product
resource "azurerm_api_management_product" "quote_product" {
  product_id            = "quote-backend-api"
  resource_group_name   = azurerm_resource_group.rg.name
  api_management_name   = azurerm_api_management.quote_api.name
  display_name          = "Quote Backend API"
  description           = "API for Quote Backend Function App"
  subscription_required = false
  approval_required     = false
  published             = true
}

# Associate API with Product
resource "azurerm_api_management_product_api" "quote_product_api" {
  product_id          = azurerm_api_management_product.quote_product.product_id
  api_name            = azurerm_api_management_api.quote_backend_api.name
  resource_group_name = azurerm_resource_group.rg.name
  api_management_name = azurerm_api_management.quote_api.name
}
