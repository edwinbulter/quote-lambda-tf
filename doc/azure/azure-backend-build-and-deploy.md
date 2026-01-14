# Azure Backend Build and Deploy Guide

## Overview

This guide covers the build and deployment process for the Azure Functions Quote API backend using Azure Functions Core Tools (Option 1). This approach is ideal for local development, testing, and manual deployments.

## Prerequisites

### Required Tools
- **.NET 8 SDK** - For building the project
- **Azure Functions Core Tools v4** - For local development and deployment
- **Azure CLI** - For Azure authentication and management
- **Git** - For version control

### Installation Commands
```bash
# Install .NET 8 SDK (if not already installed)
# Download from: https://dotnet.microsoft.com/download/dotnet/8.0

# Install Azure Functions Core Tools
npm install -g azure-functions-core-tools@4 --unsafe-perm true

# Install Azure CLI
# Download from: https://docs.microsoft.com/en-us/cli/azure/install-azure-cli
# Or on macOS:
brew install azure-cli
```

## Getting Your Azure Subscription ID

### Method 1: Using Azure Portal
1. **Sign in to Azure Portal**: https://portal.azure.com
2. **Navigate to Subscriptions**: Click "Subscriptions" in the left menu
3. **Copy Subscription ID**: Find your subscription and copy the "Subscription ID" (GUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)

### Method 2: Using Azure CLI
```bash
# Login to Azure
az login

# List all subscriptions
az account list --output table

# Get current subscription ID
az account show --query id --output tsv

# Set default subscription (optional)
az account set --subscription "your-subscription-id"
```

### Method 3: Using Azure PowerShell
```powershell
# Login to Azure
Connect-AzAccount

# List subscriptions
Get-AzSubscription

# Get current subscription ID
Get-AzContext | Select-Object Subscription

# Set default subscription
Set-AzContext -Subscription "your-subscription-id"
```

## Option 1: Azure Functions Core Tools Deployment

### Step 1: Authenticate with Azure

#### Using Azure CLI
```bash
# Interactive login (opens browser)
az login

# Service principal login (for automation)
az login --service-principal --username <app-id> --password <password> --tenant <tenant-id>
```

#### Verify Authentication
```bash
# Check current account
az account show

# List available subscriptions
az account list --output table

# Set the correct subscription if needed
az account set --subscription "your-subscription-id"
```

### Step 2: Build the Project

#### Navigate to Source Directory
```bash
cd quote-azure-backend/src
```

#### Restore Dependencies
```bash
# Restore NuGet packages
dotnet restore

# Clean previous builds
dotnet clean
```

#### Build the Project
```bash
# Debug build
dotnet build

# Release build (for production)
dotnet build -c Release
```

#### Publish for Deployment
```bash
# Create publish folder
dotnet publish -c Release -o ./publish

# Verify publish output
ls -la publish/
```

### Step 3: Local Testing (Optional)

#### Run Functions Locally
```bash
# Start Azure Functions runtime
func start

# Test endpoints
curl http://localhost:7071/api/quote
```

#### Debug in VS Code
1. Open `src/` folder in VS Code
2. Set breakpoints in your code
3. Press F5 to start debugging
4. Test endpoints locally

### Step 4: Deploy to Azure

#### Method 4A: Direct Deploy (Recommended)
```bash
# Deploy to existing function app
func azure functionapp publish quote-backend-function

# Deploy with local settings (includes configuration)
func azure functionapp publish quote-backend-function --publish-local-settings

# Deploy specific build configuration
func azure functionapp publish quote-backend-function --build-configuration Release
```

#### Method 4B: Zip Deploy
```bash
# Create zip file from publish folder
cd publish
zip -r ../publish.zip .
cd ..

# Deploy zip file
az functionapp deployment source config-zip \
  --resource-group quote-backend-rg \
  --name quote-backend-function \
  --src ../publish.zip
```

#### Method 4C: Using Azure CLI
```bash
# Deploy using Azure CLI
az functionapp deployment source config-zip \
  --resource-group quote-backend-rg \
  --name quote-backend-function \
  --src publish.zip
```

### Step 5: Verify Deployment

#### Check Function App Status
```bash
# Get function app details
az functionapp show --resource-group quote-backend-rg --name quote-backend-function

# Check app settings
az functionapp config appsettings list --resource-group quote-backend-rg --name quote-backend-function
```

#### Test Deployed Functions
```bash
# Get function keys
az functionapp function keys list \
  --resource-group quote-backend-rg \
  --name quote-backend-function \
  --function-name QuoteHandler

# Test endpoint (replace with actual function key)
curl "https://quote-backend-function.azurewebsites.net/api/quote?code=YOUR_FUNCTION_KEY"
```

#### Monitor Logs
```bash
# Stream logs in real-time
az webapp log tail --resource-group quote-backend-rg --name quote-backend-function

# Get recent logs
az webapp log download --resource-group quote-backend-rg --name quote-backend-function
```

## Configuration Management

### Local Settings
Your `local.settings.json` file should contain:
```json
{
  "IsEncrypted": false,
  "Values": {
    "AzureWebJobsStorage": "UseDevelopmentStorage=true",
    "FUNCTIONS_WORKER_RUNTIME": "dotnet-isolated",
    "ZenQuotes:ApiKey": "your_zenquotes_api_key_here"
  }
}
```

### Azure App Settings
```bash
# Set application settings
az functionapp config appsettings set \
  --resource-group quote-backend-rg \
  --name quote-backend-function \
  --settings "ZenQuotes:ApiKey=your_production_api_key"

# List all settings
az functionapp config appsettings list --resource-group quote-backend-rg --name quote-backend-function
```

## Troubleshooting

### Common Issues

#### Authentication Problems
```bash
# Clear Azure CLI cache
az account clear

# Re-login
az login

# Check subscription
az account show
```

#### Build Errors
```bash
# Clean and rebuild
dotnet clean
dotnet restore
dotnet build -c Release
```

#### Deployment Failures
```bash
# Check function app exists
az functionapp show --resource-group quote-backend-rg --name quote-backend-function

# Check deployment history
az functionapp deployment list --resource-group quote-backend-rg --name quote-backend-function
```

#### Runtime Errors
```bash
# Check application logs
az webapp log tail --resource-group quote-backend-rg --name quote-backend-function

# Download detailed logs
az webapp log download --resource-group quote-backend-rg --name quote-backend-function
```

### Error Messages and Solutions

| Error | Solution |
|-------|----------|
| "No subscription found" | Run `az login` and check subscription with `az account list` |
| "Function app not found" | Verify resource group and function app names |
| "Build failed" | Check .NET SDK version, run `dotnet --version` |
| "Deployment failed" | Check zip file contents, verify permissions |
| "401 Unauthorized" | Check function keys and authentication |

## Best Practices

### Before Deployment
1. **Test locally** - Ensure all functions work in local environment
2. **Check configuration** - Verify all required app settings
3. **Review dependencies** - Ensure all NuGet packages are compatible
4. **Clean build** - Use `dotnet clean` before building

### During Deployment
1. **Use Release configuration** - Optimize for production
2. **Monitor deployment** - Watch for any error messages
3. **Verify deployment** - Test endpoints immediately after deployment

### After Deployment
1. **Monitor logs** - Check for runtime errors
2. **Test all endpoints** - Verify complete functionality
3. **Check performance** - Monitor response times
4. **Set up alerts** - Configure Azure Monitor alerts

## Automation Script

### Complete Build and Deploy Script
```bash
#!/bin/bash

# Configuration
RESOURCE_GROUP="quote-backend-rg"
FUNCTION_APP_NAME="quote-backend-function"
PROJECT_PATH="quote-azure-backend/src"

echo "Starting Azure Functions deployment..."

# Check Azure login
if ! az account show > /dev/null 2>&1; then
    echo "Please login to Azure first:"
    az login
fi

# Navigate to project
cd $PROJECT_PATH

# Build project
echo "Building project..."
dotnet clean
dotnet restore
dotnet build -c Release

# Publish
echo "Publishing project..."
dotnet publish -c Release -o ./publish

# Deploy to Azure
echo "Deploying to Azure..."
func azure functionapp publish $FUNCTION_APP_NAME --publish-local-settings

# Verify deployment
echo "Verifying deployment..."
az functionapp show --resource-group $RESOURCE_GROUP --name $FUNCTION_APP_NAME

echo "Deployment completed successfully!"
```

### Usage
```bash
# Make script executable
chmod +x deploy.sh

# Run deployment
./deploy.sh
```

## Next Steps

After successful deployment:
1. **Test all endpoints** - Verify complete functionality
2. **Set up monitoring** - Configure Azure Monitor and Application Insights
3. **Configure CI/CD** - Set up automated deployment pipeline
4. **Document API** - Update API documentation with deployed endpoints
5. **Set up alerts** - Configure alerts for errors and performance issues

---

**Remember**: Always test deployments in a staging environment before deploying to production. Use different function app names for different environments (e.g., `quote-backend-staging`, `quote-backend-production`).
