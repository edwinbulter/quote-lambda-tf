# Deploying Quote Azure Frontend as Static Website on Azure

This document describes how to deploy the `quote-azure-frontend` as a static website on Azure using Azure Storage Static Websites or Azure App Service.

## Prerequisites

- Azure CLI installed and configured
- Node.js and npm installed
- Built frontend files (from `npm run build`)
- Azure subscription with appropriate permissions

## Option 1: Azure Storage Static Website (Recommended for Simple Static Sites)

### Step 1: Build the Frontend

```bash
cd quote-azure-frontend
npm install
npm run build
```

This creates the production build in the `dist/` directory.

### Step 2: Create Azure Storage Account

```bash
# Set variables
RESOURCE_GROUP="quote-azure-rg"
LOCATION="eastus"
STORAGE_ACCOUNT_NAME="quotefrontend$(date +%s | tail -c 7)" # Must be unique

# Create resource group
az group create \
  --name $RESOURCE_GROUP \
  --location $LOCATION

# Create storage account
az storage account create \
  --name $STORAGE_ACCOUNT_NAME \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION \
  --sku Standard_LRS \
  --kind StorageV2
```

### Step 3: Enable Static Website Hosting

```bash
# Enable static website hosting
az storage blob service-properties update \
  --account-name $STORAGE_ACCOUNT_NAME \
  --static-website \
  --404-document index.html \
  --index-document index.html

# Get storage account key (for upload)
STORAGE_KEY=$(az storage account keys list \
  --resource-group $RESOURCE_GROUP \
  --account-name $STORAGE_ACCOUNT_NAME \
  --query "[0].value" -o tsv)
```

### Step 4: Upload Frontend Files

```bash
# Upload all files from dist directory
az storage blob upload-batch \
  --source ../quote-azure-frontend/dist \
  --destination '$web' \
  --account-name $STORAGE_ACCOUNT_NAME \
  --account-key $STORAGE_KEY
```

### Step 5: Get the Static Website URL

```bash
# Get the static website URL
STATIC_URL=$(az storage account show \
  --name $STORAGE_ACCOUNT_NAME \
  --resource-group $RESOURCE_GROUP \
  --query "primaryEndpoints.web" -o tsv)

echo "Static website URL: $STATIC_URL"
```

### Step 6: Configure CORS (Optional but Recommended)

```bash
# Configure CORS for the storage account
az storage cors clear \
  --account-name $STORAGE_ACCOUNT_NAME \
  --account-key $STORAGE_KEY \
  --services b

az storage cors add \
  --account-name $STORAGE_ACCOUNT_NAME \
  --account-key $STORAGE_KEY \
  --services b \
  --methods GET POST PUT DELETE OPTIONS \
  --origins "*" \
  --allowed-headers "*" \
  --exposed-headers "*" \
  --max-age 3600
```

## Option 2: Azure App Service (More Features)

### Step 1: Build the Frontend

```bash
cd quote-azure-frontend
npm install
npm run build
```

### Step 2: Create App Service Plan

```bash
# Set variables
RESOURCE_GROUP="quote-azure-rg"
LOCATION="eastus"
APP_SERVICE_PLAN="quote-app-service-plan"
APP_SERVICE_NAME="quote-frontend-$(date +%s | tail -c 7)" # Must be unique

# Create resource group
az group create \
  --name $RESOURCE_GROUP \
  --location $LOCATION

# Create app service plan
az appservice plan create \
  --name $APP_SERVICE_PLAN \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION \
  --sku B1 \
  --is-linux
```

### Step 3: Create Web App

```bash
# Create web app
az webapp create \
  --resource-group $RESOURCE_GROUP \
  --plan $APP_SERVICE_PLAN \
  --name $APP_SERVICE_NAME \
  --runtime "NODE|18-lts"
```

### Step 4: Deploy Frontend Files

```bash
# Deploy using zip deploy
cd ../quote-azure-frontend
az webapp up \
  --resource-group $RESOURCE_GROUP \
  --name $APP_SERVICE_NAME \
  --location $LOCATION \
  --sku B1 \
  --runtime "NODE|18-lts"
```

Or manually:

```bash
# Create zip file
cd dist
zip -r ../frontend.zip .

# Deploy zip file
az webapp deployment source config-zip \
  --resource-group $RESOURCE_GROUP \
  --name $APP_SERVICE_NAME \
  --src ../frontend.zip
```

### Step 5: Get the Web App URL

```bash
# Get the web app URL
WEBAPP_URL=$(az webapp show \
  --name $APP_SERVICE_NAME \
  --resource-group $RESOURCE_GROUP \
  --query "defaultHostName" -o tsv)

echo "Web app URL: https://$WEBAPP_URL"
```

## Configuration for Backend API

### Update API Base URL

The frontend needs to be configured to point to your backend API. Update the `vite.config.ts` or environment variables:

```typescript
// vite.config.ts
export default defineConfig({
  plugins: [react()],
  define: {
    global: 'globalThis',
  },
  server: {
    proxy: {
      '/api': {
        target: 'https://your-backend-api.azurewebsites.net',
        changeOrigin: true,
        secure: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: undefined,
      },
    },
  },
});
```

Or create environment-specific builds:

```bash
# For production
VITE_API_BASE_URL=https://your-backend-api.azurewebsites.net npm run build

# For development
VITE_API_BASE_URL=http://localhost:7071 npm run build
```

### Update Frontend Configuration

In your frontend code, ensure the API base URL is properly configured:

```typescript
// src/api/quoteApi.ts
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:7071';
```

## Option 3: Azure Static Web Apps (Modern Approach)

### Step 1: Install Azure Static Web Apps CLI

```bash
npm install -g @azure/static-web-apps-cli
```

### Step 2: Create Static Web App

```bash
# Set variables
RESOURCE_GROUP="quote-azure-rg"
LOCATION="eastus"
STATIC_WEB_APP_NAME="quote-static-app"

# Create static web app
az staticwebapp create \
  --name $STATIC_WEB_APP_NAME \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION \
  --source https://github.com/yourusername/quote-lambda-tf \
  --branch main \
  --app-location "/quote-azure-frontend" \
  --api-location "/quote-azure-backend" \
  --output-location "dist" \
  --login-with-github
```

### Step 3: Configure GitHub Actions

Create `.github/workflows/azure-static-web-apps.yml`:

```yaml
name: Azure Static Web Apps CI/CD

on:
  push:
    branches:
      - main
  pull_request:
    types: [opened, synchronize, reopened, closed]
    branches:
      - main

jobs:
  build_and_deploy_job:
    if: github.event_name == 'push' || (github.event_name == 'pull_request' && github.event.action != 'closed')
    runs-on: ubuntu-latest
    name: Build and Deploy Job
    steps:
      - uses: actions/checkout@v3
        with:
          submodules: true
      - name: Build And Deploy
        id: builddeploy
        uses: Azure/static-web-apps-deploy@v1
        with:
          azure_static_web_apps_api_token: ${{ secrets.AZURE_STATIC_WEB_APPS_API_TOKEN }}
          repo_token: ${{ secrets.GITHUB_TOKEN }}
          action: "upload"
          app_location: "/quote-azure-frontend"
          api_location: "/quote-azure-backend"
          output_location: "dist"
          skip_app_build: false
          skip_api_build: false
```

## Custom Domain Configuration

### For Storage Static Websites

```bash
# Add custom domain
az storage account network-rule add \
  --account-name $STORAGE_ACCOUNT_NAME \
  --resource-group $RESOURCE_GROUP \
  --ip-address <your-cdn-ip>

# Configure CNAME in your DNS provider to point to the storage endpoint
```

### For App Service

```bash
# Add custom domain
az webapp config hostname add \
  --webapp-name $APP_SERVICE_NAME \
  --resource-group $RESOURCE_GROUP \
  --hostname www.yourdomain.com

# Upload SSL certificate (if needed)
az webapp config ssl upload \
  --webapp-name $APP_SERVICE_NAME \
  --resource-group $RESOURCE_GROUP \
  --certificate-file path/to/certificate.pfx \
  --certificate-password password
```

## Environment Variables and Configuration

### Frontend Environment Variables

Create `.env.production` in the frontend root:

```env
VITE_API_BASE_URL=https://your-backend-api.azurewebsites.net
VITE_AZURE_CLIENT_ID=your-azure-app-client-id
VITE_AZURE_TENANT_ID=your-azure-tenant-id
```

### Backend CORS Configuration

Ensure your backend allows requests from your frontend domain:

```csharp
// In your backend CORS configuration
app.UseCors(policy => policy
    .WithOrigins("https://your-frontend-domain.azurewebsites.net", "https://www.yourdomain.com")
    .AllowAnyMethod()
    .AllowAnyHeader()
    .AllowCredentials());
```

## Monitoring and Logging

### Application Insights (App Service)

```bash
# Create Application Insights
az monitor app-insights component create \
  --app quote-frontend-insights \
  --location $LOCATION \
  --resource-group $RESOURCE_GROUP \
  --application-type web

# Connect to App Service
az webapp config appsettings set \
  --name $APP_SERVICE_NAME \
  --resource-group $RESOURCE_GROUP \
  --settings "APPINSIGHTS_INSTRUMENTATIONKEY=$(az monitor app-insights component show \
    --app quote-frontend-insights \
    --resource-group $RESOURCE_GROUP \
    --query instrumentationKey -o tsv)"
```

### Storage Account Logging

```bash
# Enable logging
az storage account logging update \
  --account-name $STORAGE_ACCOUNT_NAME \
  --log rwd \
  --retention-days 7 \
  --services b
```

## Security Considerations

1. **HTTPS Only**: Ensure all traffic uses HTTPS
2. **CORS Configuration**: Properly configure CORS for your backend
3. **Environment Variables**: Store sensitive data in Azure Key Vault or App Settings
4. **Network Security**: Consider using Private Endpoints for backend resources
5. **Content Security Policy**: Implement CSP headers for additional security

## Cost Optimization

1. **Storage Account**: Use Standard_LRS for cost-effective storage
2. **App Service**: Use B1 tier for small applications
3. **CDN**: Consider Azure CDN for better performance and cost optimization
4. **Auto-scaling**: Configure auto-scaling based on demand

## Troubleshooting

### Common Issues

1. **404 Errors**: Check that `index.html` is properly uploaded and configured as default document
2. **CORS Issues**: Verify backend CORS configuration
3. **API Connection Issues**: Check API base URL configuration
4. **Authentication Issues**: Verify Azure AD configuration and redirect URIs

### Debug Commands

```bash
# Check storage account static website settings
az storage blob service-properties show \
  --account-name $STORAGE_ACCOUNT_NAME \
  --query "staticWebsite"

# Check app service logs
az webapp log tail \
  --name $APP_SERVICE_NAME \
  --resource-group $RESOURCE_GROUP
```

## Cleanup

To remove all created resources:

```bash
# Delete resource group and all resources
az group delete \
  --name $RESOURCE_GROUP \
  --yes --no-wait
```

## Next Steps

1. Set up CI/CD pipeline for automated deployments
2. Configure monitoring and alerting
3. Implement backup and disaster recovery
4. Set up custom domains and SSL certificates
5. Optimize performance with CDN and caching strategies
