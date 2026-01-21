# Resource Group (only created if not using existing storage in different RG)
resource "azurerm_resource_group" "frontend" {
  count    = var.use_existing_storage_account ? 0 : 1
  name     = var.resource_group_name
  location = var.location
  tags     = var.tags
}

# Use existing storage account or create new one
data "azurerm_storage_account" "existing" {
  count               = var.use_existing_storage_account ? 1 : 0
  name                = var.frontend_storage_account_name
  resource_group_name = var.frontend_resource_group_name
}

resource "azurerm_storage_account" "frontend" {
  count                    = var.use_existing_storage_account ? 0 : 1
  name                     = var.storage_account_name
  resource_group_name      = azurerm_resource_group.frontend[0].name
  location                 = azurerm_resource_group.frontend[0].location
  account_tier             = "Standard"
  account_replication_type = "LRS"
  account_kind             = "StorageV2"
  min_tls_version          = "TLS1_2"

  tags = var.tags
}

# Enable Static Website Hosting
resource "azurerm_storage_account_static_website" "frontend" {
  storage_account_id = var.use_existing_storage_account ? data.azurerm_storage_account.existing[0].id : azurerm_storage_account.frontend[0].id
  index_document     = "index.html"
  error_404_document = "index.html"
}

# CORS Configuration for Storage Account
resource "azurerm_storage_account_network_rules" "frontend" {
  storage_account_id = var.use_existing_storage_account ? data.azurerm_storage_account.existing[0].id : azurerm_storage_account.frontend[0].id
  default_action     = "Allow"
  ip_rules           = []
  bypass             = ["AzureServices"]
}

# Storage Container for frontend files (created automatically by static website)
resource "azurerm_storage_container" "frontend" {
  name                  = "$web"
  storage_account_name  = var.use_existing_storage_account ? var.frontend_storage_account_name : azurerm_storage_account.frontend[0].name
  container_access_type = "private"
}

# Upload frontend files to storage
resource "azurerm_storage_blob" "frontend_files" {
  for_each = fileset("${path.root}/../dist", "**/*")
  
  name                   = each.value
  storage_account_name   = var.use_existing_storage_account ? var.frontend_storage_account_name : azurerm_storage_account.frontend[0].name
  storage_container_name = azurerm_storage_container.frontend.name
  type                   = "Block"
  source                 = "${path.root}/../dist/${each.value}"
  content_md5            = filemd5("${path.root}/../dist/${each.value}")
}
