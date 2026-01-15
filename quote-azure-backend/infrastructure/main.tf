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
  features {}
}

# Get current subscription
data "azurerm_subscription" "current" {}

# Resource Group
resource "azurerm_resource_group" "rg" {
  name     = "quote-backend-rg"
  location = "West Europe"
}

# Storage Account for Function App and Table Storage
resource "azurerm_storage_account" "sa" {
  name                     = "qbst${random_string.suffix.result}"
  resource_group_name      = azurerm_resource_group.rg.name
  location                 = azurerm_resource_group.rg.location
  account_tier             = "Standard"
  account_replication_type = "LRS"

  tags = {
    environment = "production"
    project     = "quote-backend"
  }
}

# Storage Account with Table Service for data persistence
resource "azurerm_storage_account" "table_sa" {
  name                     = "qbtst${random_string.suffix.result}"
  resource_group_name      = azurerm_resource_group.rg.name
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

# Azure Tables for data persistence
resource "azurerm_storage_table" "quotes" {
  name                 = "quotes"
  storage_account_name = azurerm_storage_account.table_sa.name
}

resource "azurerm_storage_table" "userlikes" {
  name                 = "userlikes"
  storage_account_name = azurerm_storage_account.table_sa.name
}

resource "azurerm_storage_table" "userprogress" {
  name                 = "userprogress"
  storage_account_name = azurerm_storage_account.table_sa.name
}

resource "azurerm_storage_table" "userviewhistory" {
  name                 = "userviewhistory"
  storage_account_name = azurerm_storage_account.table_sa.name
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
      dotnet_version = "v8.0"
    }
  }

  app_settings = {
    "FUNCTIONS_WORKER_RUNTIME" = "dotnet-isolated"
    "AzureWebJobsStorage"        = azurerm_storage_account.sa.primary_connection_string
    "WEBSITE_RUN_FROM_PACKAGE"  = "1"
    "APPINSIGHTS_INSTRUMENTATIONKEY" = azurerm_application_insights.app_insights.instrumentation_key
    "APPLICATIONINSIGHTS_CONNECTION_STRING" = azurerm_application_insights.app_insights.connection_string
    "Logging__LogLevel__Default" = "Information"
    "Logging__LogLevel__Microsoft" = "Warning"
    "Logging__LogLevel__Microsoft.Hosting.Lifetime" = "Information"
    "TableStorageConnectionString" = azurerm_storage_account.table_sa.primary_connection_string
    # Azure AD B2C Settings
    "AzureAdB2C__Instance" = "https://login.microsoftonline.com/"
    "AzureAdB2C__Domain" = "yourtenant.onmicrosoft.com"
    "AzureAdB2C__ClientId" = azuread_application.function_app.client_id
    "AzureAdB2C__ClientSecret" = azuread_application_password.function_app.value
    "AzureAdB2C__SignUpSignInPolicyId" = "B2C_1_sign-up-sign-in"
  }

  tags = {
    environment = "production"
    project     = "quote-backend"
  }
}

# Application Insights for logging
resource "azurerm_application_insights" "app_insights" {
  name                = "quote-backend-ai"
  location            = azurerm_resource_group.rg.location
  resource_group_name = azurerm_resource_group.rg.name
  workspace_id        = azurerm_log_analytics_workspace.workspace.id
  application_type    = "web"

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

# Random suffix for unique storage account name
resource "random_string" "suffix" {
  length  = 6
  special = false
  upper   = false
  numeric = true
}

# Azure AD B2C Resources
provider "azuread" {
  tenant_id = data.azurerm_subscription.current.tenant_id
}

# Azure AD B2C Directory (using existing tenant)
data "azuread_client_config" "current" {}

# Azure AD Application for Function App
resource "azuread_application" "function_app" {
  display_name = "quote-backend-function-app"
  owners       = [data.azuread_client_config.current.object_id]

  web {
    implicit_grant {
      access_token_issuance_enabled = false
      id_token_issuance_enabled     = true
    }
  }

  required_resource_access {
    resource_app_id = "00000003-0000-0000-c000-000000000000" # Microsoft Graph
    resource_access {
      id   = "e1fe6dd8-ba31-4d61-89e7-88639da4633d" # User.Read
      type = "Scope"
    }
    resource_access {
      id   = "b340eb25-3d91-4169-bbdf-9c51564af439" # User.Read.All
      type = "Scope"
    }
    resource_access {
      id   = "5792c5b5-0199-40b6-9c85-c800336b8c2c" # GroupMember.Read.All
      type = "Scope"
    }
  }
}

# Azure AD Service Principal
resource "azuread_service_principal" "function_app" {
  application_id = azuread_application.function_app.client_id
  owners         = [data.azuread_client_config.current.object_id]
}

# Azure AD Application Password (Client Secret)
resource "azuread_application_password" "function_app" {
  application_object_id = azuread_application.function_app.object_id
}

# Azure AD B2C User Groups
resource "azuread_group" "admin" {
  display_name     = "ADMIN"
  security_enabled = true
  owners           = [data.azuread_client_config.current.object_id]
}

resource "azuread_group" "user" {
  display_name     = "USER"
  security_enabled = true
  owners           = [data.azuread_client_config.current.object_id]
}
