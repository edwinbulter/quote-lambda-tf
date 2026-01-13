terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.0"
    }
  }
}

provider "azurerm" {
  features {}
}

# Resource Group
resource "azurerm_resource_group" "rg" {
  name     = "quote-backend-rg"
  location = var.location
}

# Storage Account for Function App
resource "azurerm_storage_account" "sa" {
  name                     = "qbst${random_string.suffix.result}"
  resource_group_name    = azurerm_resource_group.rg.name
  location                 = azurerm_resource_group.rg.location
  account_tier             = "Standard"
  account_replication_type = "LRS"

  tags = {
    environment = "production"
    project     = "quote-backend"
  }
}

# App Service Plan (Consumption)
resource "azurerm_service_plan" "asp" {
  name                = "quote-backend-asp"
  resource_group_name = azurerm_resource_group.rg.name
  location            = azurerm_resource_group.rg.location
  os_type             = "Windows"
  sku_name            = "Y1"

  tags = {
    environment = "production"
    project     = "quote-backend"
  }
}

# Function App (Windows Consumption Plan)
resource "azurerm_windows_function_app" "function_app" {
  name                = "quote-backend-function"
  resource_group_name = azurerm_resource_group.rg.name
  location            = azurerm_resource_group.rg.location

  storage_account_name       = azurerm_storage_account.sa.name
  storage_account_access_key = azurerm_storage_account.sa.primary_access_key
  service_plan_id            = azurerm_service_plan.asp.id

  site_config {
    application_stack {
      dotnet_version = "v6.0"
    }
  }

  app_settings = {
    "FUNCTIONS_WORKER_RUNTIME" = "dotnet-isolated"
    "AzureWebJobsStorage"        = azurerm_storage_account.sa.primary_connection_string
    "WEBSITE_RUN_FROM_PACKAGE"  = "1"
  }

  tags = {
    environment = "production"
    project     = "quote-backend"
  }
}

# Random suffix for unique storage account name
resource "random_string" "suffix" {
  length  = 6
  special = false
  upper   = false
  numeric = true
}
