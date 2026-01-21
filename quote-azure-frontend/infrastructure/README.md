# Frontend Infrastructure with Terraform

This directory contains the Terraform configuration for deploying the quote-azure-frontend as a static website on Azure Storage using your existing storage account for frontend files, with a dedicated storage account for Terraform state management.

## Prerequisites

### Terraform State Storage

Create a separate storage account for frontend Terraform state:

1. **Create resource group for frontend state**:
   ```bash
   az group create --name quote-frontend-rg --location westeurope
   ```

2. **Create storage account for frontend state**:
   ```bash
   az storage account create \
     --name quotefrontendstate \
     --resource-group quote-frontend-rg \
     --location westeurope \
     --sku Standard_LRS \
     --kind StorageV2
   ```

3. **Create frontend state container**:
   ```bash
   az storage container create \
     --name terraform-state-frontend \
     --account-name quotefrontendstate \
     --auth-mode login
   ```

4. **Configure Terraform backend**:
   ```bash
   # Copy the template and update with your values
   cp backend.tf.template backend.tf
   
   # Edit backend.tf to use the new frontend state storage:
   # - storage_account_name: quotefrontendstate
   # - resource_group_name: quote-frontend-rg
   # - container_name: terraform-state-frontend
   # - key: frontend-infrastructure.tfstate
   nano backend.tf
   ```

## Quick Start

### Deploy Frontend Infrastructure

1. **Configure Frontend Storage Account**:
   ```bash
   cd quote-azure-frontend/infrastructure
   
   # Copy terraform variables template
   cp terraform.tfvars.template terraform.tfvars
   
   # Edit terraform.tfvars with your storage account details
   nano terraform.tfvars
   ```

2. **Update terraform.tfvars**:
   ```hcl
   use_existing_storage_account = true
   frontend_storage_account_name = "your-storage-account-name"
   frontend_resource_group_name = "your-resource-group-name"
   location = "westeurope"  # Match your existing storage account location
   ```

3. **Deploy**:
   ```bash
   # Make the deploy script executable
   chmod +x deploy.sh
   
   # Run deployment
   ./deploy.sh
   ```

## Quick Setup Script

For automated setup of frontend infrastructure:
```bash
cd quote-azure-frontend/infrastructure
./setup-frontend-infrastructure.sh
```

This script will:
- Prompt for your existing storage account details (for frontend files)
- Verify the storage account exists
- Create terraform.tfvars automatically
- Set up backend.tf for dedicated state storage
- Guide you through the remaining steps

**Note**: You still need to create the separate state storage account first (see Prerequisites).

## Manual Deployment Steps

### 1. Initialize Terraform
```bash
terraform init
```

### 2. Plan the Deployment
```bash
terraform plan
```

### 3. Apply the Configuration
```bash
terraform apply
```

## File Structure

```
infrastructure/
├── backend.tf.template    # Backend configuration template
├── main.tf               # Main resources (storage account, CDN)
├── variables.tf          # Input variables
├── outputs.tf            # Output values
├── terraform.tfvars.template # Environment variables template
├── deploy.sh             # Deployment script
├── setup-frontend-infrastructure.sh # Setup script for existing storage
├── destroy.sh            # Destruction script
└── README.md             # This file
```

## Resources Created

- Static website hosting enabled on existing storage
- $web container created for frontend files
- CORS configuration updated
- Frontend files uploaded to $web container
- CDN Profile & Endpoint (optional)

## Configuration Options

### Basic Configuration
Edit `terraform.tfvars`:
```hcl
# Use existing storage account
use_existing_storage_account = true
existing_storage_account_name = "quote-lambda-tf"
existing_resource_group_name = "quote-lambda-tf-rg"
location = "westeurope"
environment = "dev"
```

### Custom Domain
Add custom domain configuration:
```hcl
custom_domain = {
  domain_name = "quote.yourdomain.com"
  ttl         = 3600
}
```

### CDN Configuration
Enable/disable CDN:
```hcl
enable_cdn = true  # Set to false to save costs
```

## Outputs

After deployment, you'll get:
- `static_website_url`: Direct storage endpoint
- `cdn_endpoint_url`: CDN endpoint (if enabled)
- `storage_account_id`: For reference
- `primary_access_key`: For uploads (sensitive)

## Updating the Frontend

1. Build the frontend:
   ```bash
   cd ..
   npm run build
   cd infrastructure
   ```

2. Upload new files:
   ```bash
   terraform apply  # This will update the blobs
   ```

   Or use the Azure CLI for faster uploads:
   ```bash
   # Get storage account key
   STORAGE_KEY=$(terraform output -raw primary_access_key)
   
   # Upload files
   az storage blob upload-batch \
     --source ../dist \
     --destination '$web' \
     --account-name $(terraform output -raw storage_account_name) \
     --account-key $STORAGE_KEY
   ```

## Environment Variables

The frontend needs to know the backend API URL. Set this in your build process:

```bash
# For production
VITE_API_BASE_URL=https://your-backend-api.azurewebsites.net npm run build

# For development
VITE_API_BASE_URL=http://localhost:7071 npm run build
```

## Cost Optimization

### Using Existing Storage Account:
- **Additional cost**: Minimal (~$0.50-1/month for operations)
- **Storage cost**: Shared with backend
- **Data transfer**: First 100GB/month free

## Security Considerations

1. **HTTPS**: Storage account enforces HTTPS by default
2. **CORS**: Configure to allow only your domains
3. **Access Keys**: Store securely (Terraform state is encrypted)
4. **Shared Storage**: Ensure frontend files don't conflict with backend data

## Troubleshooting

### Common Issues

1. **Storage Account Access**
   - Ensure you have contributor rights to the storage account
   - Check that the storage account exists and is accessible

2. **Static Website Not Working**
   - Verify static website is enabled
   - Check that index.html is uploaded to $web container

3. **CORS Issues**
   - Update backend CORS settings
   - Check storage account CORS configuration

4. **Terraform State Issues**
   - Ensure backend configuration is correct
   - Check storage account access

### Debug Commands

```bash
# Check storage account static website settings
az storage blob service-properties show \
  --account-name $(terraform output -raw storage_account_name) \
  --query "staticWebsite"

# List uploaded files
az storage blob list \
  --container-name '$web' \
  --account-name $(terraform output -raw storage_account_name)
```

## Cleanup

To remove frontend resources:
```bash
terraform destroy
```

This will remove:
- Frontend files from $web container
- Static website configuration
- CDN resources (if enabled)
- Frontend Terraform state from terraform-state-frontend container

**Note**: Your existing storage account, terraform-state container (backend), backend data, and quote-frontend-rg resource group (containing state storage) will remain untouched.

## Integration with CI/CD

### GitHub Actions Example

```yaml
name: Deploy Frontend

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Setup Node.js
        uses: actions/setup-node@v3
        with:
          node-version: '18'
          
      - name: Install dependencies
        run: npm ci
        
      - name: Build frontend
        run: npm run build
        env:
          VITE_API_BASE_URL: ${{ secrets.API_BASE_URL }}
          
      - name: Setup Terraform
        uses: hashicorp/setup-terraform@v2
        
      - name: Deploy to Azure
        run: |
          cd infrastructure
          terraform init
          terraform apply -auto-approve
        env:
          ARM_CLIENT_ID: ${{ secrets.AZURE_CLIENT_ID }}
          ARM_CLIENT_SECRET: ${{ secrets.AZURE_CLIENT_SECRET }}
          ARM_SUBSCRIPTION_ID: ${{ secrets.AZURE_SUBSCRIPTION_ID }}
          ARM_TENANT_ID: ${{ secrets.AZURE_TENANT_ID }}
```

## Next Steps

1. Set up custom domain
2. Configure SSL certificate
3. Set up monitoring and alerts
4. Implement CI/CD pipeline
5. Add backup strategy
