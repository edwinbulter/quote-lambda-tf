terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.0"
    }
    azuread = {
      source  = "hashicorp/azuread"
      version = "~> 2.0"
    }
  }
}

provider "azurerm" {
  features {
    resource_group {
      prevent_deletion_if_contains_resources = false
    }
  }
}

# Get current subscription
data "azurerm_subscription" "current" {}

# Resource Group
resource "azurerm_resource_group" "rg" {
  name     = "quote-backend-rg"
  location = "West Europe"
}

# Storage Account for Function App is the same as table storage account

# Data source for table storage account
data "azurerm_storage_account" "table_storage" {
  name                = var.table_storage_account_name
  resource_group_name = azurerm_resource_group.rg.name
}

# User Roles Table for database-based role management
resource "azurerm_storage_table" "user_roles" {
  name                 = "userroles"
  storage_account_name = var.table_storage_account_name
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

# Azure Tables for data persistence (using existing storage account)
resource "azurerm_storage_table" "quotes" {
  name                 = "quotes"
  storage_account_name = var.table_storage_account_name
}

resource "azurerm_storage_table" "userlikes" {
  name                 = "userlikes"
  storage_account_name = var.table_storage_account_name
}

resource "azurerm_storage_table" "userprogress" {
  name                 = "userprogress"
  storage_account_name = var.table_storage_account_name
}

# Function App (Windows Consumption Plan)
resource "azurerm_windows_function_app" "function_app" {
  name                = "quote-backend-function"
  resource_group_name = azurerm_resource_group.rg.name
  location            = azurerm_resource_group.rg.location

  storage_account_name       = data.azurerm_storage_account.table_storage.name
  storage_account_access_key = data.azurerm_storage_account.table_storage.primary_access_key
  service_plan_id            = azurerm_service_plan.asp.id

  site_config {
    application_stack {
      dotnet_version = "v8.0"
    }
  }

  app_settings = {
    "FUNCTIONS_WORKER_RUNTIME" = "dotnet-isolated"
    "AzureWebJobsStorage"        = data.azurerm_storage_account.table_storage.primary_connection_string
    "WEBSITE_RUN_FROM_PACKAGE"  = "1"
    "APPINSIGHTS_INSTRUMENTATIONKEY" = azurerm_application_insights.app_insights.instrumentation_key
    "APPLICATIONINSIGHTS_CONNECTION_STRING" = azurerm_application_insights.app_insights.connection_string
    "Logging__LogLevel__Default" = "Information"
    "Logging__LogLevel__Microsoft" = "Warning"
    "Logging__LogLevel__Microsoft.Hosting.Lifetime" = "Information"
    "TableStorageConnectionString" = "DefaultEndpointsProtocol=https;AccountName=${var.table_storage_account_name};AccountKey=${data.azurerm_storage_account.table_storage.primary_access_key};EndpointSuffix=core.windows.net"
  }

  tags = {
    environment = "production"
    project     = "quote-backend"
  }

  lifecycle {
    ignore_changes = [
      app_settings["Jwt:Key"],
      app_settings["Jwt:Issuer"],
      app_settings["Jwt:Audience"]
    ]
  }
}

# Application Insights
resource "azurerm_application_insights" "app_insights" {
  name                = "quote-backend-ai"
  location            = azurerm_resource_group.rg.location
  resource_group_name = azurerm_resource_group.rg.name
  workspace_id        = azurerm_log_analytics_workspace.workspace.id
  application_type    = "web"
  retention_in_days   = 90

  tags = {
    environment = "production"
    project     = "quote-backend"
  }
}

# Log Analytics Workspace
resource "azurerm_log_analytics_workspace" "workspace" {
  name                = "quote-backend-law"
  location            = azurerm_resource_group.rg.location
  resource_group_name = azurerm_resource_group.rg.name
  sku                 = "PerGB2018"
  retention_in_days   = 30

  tags = {
    environment = "production"
    project     = "quote-backend"
  }
}

# Random suffix removed - using existing storage accounts

# Outputs
output "application_insights_connection_string" {
  description = "Application Insights connection string"
  value       = azurerm_application_insights.app_insights.connection_string
  sensitive   = true
}

output "application_insights_instrumentation_key" {
  description = "Application Insights instrumentation key"
  value       = azurerm_application_insights.app_insights.instrumentation_key
  sensitive   = true
}

# Note: Azure AD authentication is much simpler than B2C
# No user flows needed - just app registration and groups
