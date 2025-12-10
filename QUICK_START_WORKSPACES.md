# Quick Start: Terraform Workspaces

This is a quick reference for setting up and using Terraform workspaces for development and production environments.

## TL;DR - Automated Setup

```bash
# Run the automated setup script
./setup-workspaces.sh
```

This script will guide you through the entire setup process.

## Manual Setup (If Preferred)

### 1. Backend Setup

```bash
cd quote-lambda-tf-backend/infrastructure/

# Reinitialize with workspace support
terraform init -reconfigure

# Verify production (default workspace)
terraform workspace show  # Should show: default
terraform plan -var="environment=prod"

# Create and deploy development
terraform workspace new dev
terraform apply -var="environment=dev"

# Note the API Gateway URL
terraform output api_gateway_url
```

### 2. Frontend Setup

```bash
cd ../../quote-lambda-tf-frontend/infrastructure/

# Reinitialize with workspace support
terraform init -reconfigure

# Verify production (default workspace)
terraform workspace show  # Should show: default
terraform plan -var="environment=prod"

# Create and deploy development
terraform workspace new dev
terraform apply -var="environment=dev"

# Note the CloudFront URL
terraform output cloudfront_url
```

### 3. Deploy Lambda Code

```bash
cd ../../quote-lambda-tf-backend/
mvn clean package

# Deploy to development
aws lambda update-function-code \
  --function-name quote-lambda-tf-backend-dev \
  --zip-file fileb://target/quote-lambda-tf-backend-1.0-SNAPSHOT.jar \
  --region eu-central-1
```

### 4. Configure Frontend Environment

Create `.env.development`:

```bash
VITE_REACT_APP_API_BASE_URL=https://YOUR_DEV_API_GATEWAY_URL.execute-api.eu-central-1.amazonaws.com
```

### 5. Deploy Frontend

```bash
cd ../quote-lambda-tf-frontend/
npm run build
aws s3 sync dist/ s3://quote-lambda-tf-frontend-dev --delete
```

## Daily Usage

### Switch Between Environments

```bash
# Backend
cd quote-lambda-tf-backend/infrastructure/
terraform workspace select dev     # Switch to development
terraform workspace select default # Switch to production

# Frontend
cd quote-lambda-tf-frontend/infrastructure/
terraform workspace select dev     # Switch to development
terraform workspace select default # Switch to production
```

### Check Current Environment

```bash
terraform workspace show
terraform workspace list
```

### Deploy Changes

**Development:**
```bash
# Backend
cd quote-lambda-tf-backend/infrastructure/
terraform workspace select dev
terraform apply -var="environment=dev"

# Frontend
cd ../../quote-lambda-tf-frontend/infrastructure/
terraform workspace select dev
terraform apply -var="environment=dev"
```

**Production:**
```bash
# Backend
cd quote-lambda-tf-backend/infrastructure/
terraform workspace select default
terraform apply -var="environment=prod"

# Frontend
cd ../../quote-lambda-tf-frontend/infrastructure/
terraform workspace select default
terraform apply -var="environment=prod"
```

## Resource Names

### Production (default workspace)
- Lambda: `quote-lambda-tf-backend`
- DynamoDB: `quote-lambda-tf-quotes`
- S3: `quote-lambda-tf-frontend`
- API Gateway: `quote-lambda-tf-backend-api`

### Development (dev workspace)
- Lambda: `quote-lambda-tf-backend-dev`
- DynamoDB: `quote-lambda-tf-quotes-dev`
- S3: `quote-lambda-tf-frontend-dev`
- API Gateway: `quote-lambda-tf-backend-api-dev`

## State Files

State files are stored in the shared S3 bucket with workspace-specific paths:

- Production: `env/default/quote-lambda-tf-backend/terraform.tfstate`
- Development: `env/dev/quote-lambda-tf-backend/terraform.tfstate`

## Troubleshooting

### Wrong workspace selected

```bash
terraform workspace show  # Check current workspace
terraform workspace select dev  # or default
```

### Resources already exist

Make sure you're in the correct workspace:

```bash
terraform workspace show
```

### Can't find outputs

Make sure you're in the correct workspace:

```bash
terraform workspace select dev
terraform output
```

## Cost Management

To save costs, destroy the development environment when not in use:

```bash
# Backend
cd quote-lambda-tf-backend/infrastructure/
terraform workspace select dev
terraform destroy -var="environment=dev"

# Frontend
cd ../../quote-lambda-tf-frontend/infrastructure/
terraform workspace select dev
terraform destroy -var="environment=dev"
```

Recreate when needed:

```bash
terraform workspace select dev
terraform apply -var="environment=dev"
```

## Complete Documentation

- [Workspace Setup Guide](./WORKSPACE_SETUP_GUIDE.md) - Detailed step-by-step guide
- [Multi-Environment Setup](./MULTI_ENVIRONMENT_SETUP.md) - Complete architecture and options
- [Terraform State Architecture](./TERRAFORM_STATE_ARCHITECTURE.md) - State management details

## Summary

✅ **Production**: Always in `default` workspace, unchanged URLs
✅ **Development**: In `dev` workspace, separate resources and URLs
✅ **Easy Switching**: `terraform workspace select dev/default`
✅ **Safe Testing**: Test in dev without affecting production
✅ **Low Cost**: ~$2-4/month for both environments

Happy developing! 🚀
