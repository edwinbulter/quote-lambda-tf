# Terraform Workspaces Setup Guide

This guide will help you set up Terraform workspaces to create separate development and production environments while keeping your current production environment unchanged.

## Overview

**Current State:**
- Your existing infrastructure is running in production
- Everything works and is accessible via current URLs

**Goal:**
- Keep current setup as production (in `default` workspace)
- Create new development environment (in `dev` workspace)
- Development will have separate URLs and resources

## Step-by-Step Setup

### Phase 1: Prepare Infrastructure Code

#### Step 1: Add Environment Variable to Backend

**File: `quote-lambda-tf-backend/infrastructure/variables.tf`**

Add the environment variable (or update if it exists):

```hcl
variable "environment" {
  description = "Environment name (dev, prod)"
  type        = string
  default     = "prod"
}
```

#### Step 2: Update Backend Resource Names

**File: `quote-lambda-tf-backend/infrastructure/lambda.tf`**

Find the Lambda function resource and update the name:

```hcl
resource "aws_lambda_function" "quote_handler" {
  function_name = var.environment == "prod" ? "quotes-lambda-java" : "quotes-lambda-java-${var.environment}"
  # ... rest of configuration stays the same
}
```

**File: `quote-lambda-tf-backend/infrastructure/dynamodb.tf`**

Find the DynamoDB table resource and update the name:

```hcl
resource "aws_dynamodb_table" "quotes" {
  name = var.environment == "prod" ? "quotes" : "quotes-${var.environment}"
  # ... rest of configuration stays the same
}
```

**File: `quote-lambda-tf-backend/infrastructure/api_gateway.tf`**

Find the API Gateway resource and update the name:

```hcl
resource "aws_apigatewayv2_api" "quote_api" {
  name          = var.environment == "prod" ? "quote-api" : "quote-api-${var.environment}"
  protocol_type = "HTTP"
  # ... rest of configuration stays the same
}
```

#### Step 3: Update Backend State Configuration

**File: `quote-lambda-tf-backend/infrastructure/backend.tf`**

Update to use workspace-specific state keys:

```hcl
terraform {
  backend "s3" {
    bucket         = "edwinbulter-terraform-state"
    key            = "quote-lambda-tf-backend/terraform.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "terraform-locks"
    encrypt        = true
    workspace_key_prefix = "env"
  }
}
```

This will create state files like:
- `env/default/quote-lambda-tf-backend/terraform.tfstate` (production)
- `env/dev/quote-lambda-tf-backend/terraform.tfstate` (development)

#### Step 4: Add Environment Variable to Frontend

**File: `quote-lambda-tf-frontend/infrastructure/variables.tf`**

Add the environment variable:

```hcl
variable "environment" {
  description = "Environment name (dev, prod)"
  type        = string
  default     = "prod"
}
```

#### Step 5: Update Frontend Resource Names

**File: `quote-lambda-tf-frontend/infrastructure/main.tf`**

Find the S3 bucket resource and update:

```hcl
resource "aws_s3_bucket" "website" {
  bucket = var.environment == "prod" ? "quote-lambda-tf-frontend" : "quote-lambda-tf-frontend-${var.environment}"
  # ... rest of configuration stays the same
}
```

Find the CloudFront distribution and add tags:

```hcl
resource "aws_cloudfront_distribution" "website_cdn" {
  # ... existing configuration ...
  
  tags = {
    Name        = "quote-lambda-tf-frontend-${var.environment}"
    Environment = var.environment
  }
}
```

#### Step 6: Update Frontend State Configuration

**File: `quote-lambda-tf-frontend/infrastructure/backend.tf`**

Update to use workspace-specific state keys:

```hcl
terraform {
  backend "s3" {
    bucket         = "edwinbulter-terraform-state"
    key            = "quote-lambda-tf-frontend/terraform.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "terraform-locks"
    encrypt        = true
    workspace_key_prefix = "env"
  }
}
```

### Phase 2: Import Current Production State

Now we need to ensure your current production infrastructure is properly tracked in the `default` workspace.

#### Step 7: Verify Current Workspace

```bash
cd quote-lambda-tf-backend/infrastructure/

# Check current workspace
terraform workspace show
# Should output: default

# If not, select default
terraform workspace select default
```

#### Step 8: Reinitialize Backend

```bash
# Reinitialize to apply backend configuration changes
terraform init -reconfigure

# Verify everything is still tracked
terraform plan
```

**Expected output:** Should show no changes (or only the name changes we made)

If it shows changes to resource names, that's okay - we'll apply those changes to update the names while keeping the resources.

#### Step 9: Apply Production Changes (if needed)

```bash
# Apply any changes (this updates resource names but doesn't recreate resources)
terraform apply
```

#### Step 10: Repeat for Frontend

```bash
cd ../../quote-lambda-tf-frontend/infrastructure/

# Verify workspace
terraform workspace show

# Reinitialize
terraform init -reconfigure

# Check plan
terraform plan

# Apply if needed
terraform apply
```

### Phase 3: Create Development Environment

#### Step 11: Create Development Workspace for Backend

```bash
cd ../../quote-lambda-tf-backend/infrastructure/

# Create and switch to dev workspace
terraform workspace new dev

# Verify you're in dev workspace
terraform workspace show
# Should output: dev

# Plan the development environment
terraform plan -var="environment=dev"
```

**Review the plan:** You should see it will create:
- Lambda function: `quotes-lambda-java-dev`
- DynamoDB table: `quotes-dev`
- API Gateway: `quote-api-dev`

#### Step 12: Deploy Development Backend

```bash
# Apply the development environment
terraform apply -var="environment=dev"

# Note the API Gateway URL from outputs
terraform output
```

**Save the development API Gateway URL** - you'll need it for the frontend.

#### Step 13: Deploy Lambda Code to Development

```bash
cd ..

# Build the Lambda function
mvn clean package

# Deploy to development Lambda
aws lambda update-function-code \
  --function-name quotes-lambda-java-dev \
  --zip-file fileb://target/quote-lambda-java-1.0-SNAPSHOT.jar \
  --region eu-central-1
```

#### Step 14: Create Development Workspace for Frontend

```bash
cd ../quote-lambda-tf-frontend/infrastructure/

# Create and switch to dev workspace
terraform workspace new dev

# Verify workspace
terraform workspace show
# Should output: dev

# Plan the development environment
terraform plan -var="environment=dev"
```

**Review the plan:** You should see it will create:
- S3 bucket: `quote-lambda-tf-frontend-dev`
- CloudFront distribution: (new distribution with new URL)

#### Step 15: Deploy Development Frontend Infrastructure

```bash
# Apply the development environment
terraform apply -var="environment=dev"

# Note the CloudFront URL from outputs
terraform output cloudfront_url
```

**Save the development CloudFront URL** - this is your dev environment URL.

#### Step 16: Configure Development Environment Variables

**File: `quote-lambda-tf-frontend/.env.development`**

Create or update this file:

```bash
VITE_REACT_APP_API_BASE_URL=https://YOUR_DEV_API_GATEWAY_URL.execute-api.eu-central-1.amazonaws.com
```

Replace `YOUR_DEV_API_GATEWAY_URL` with the actual URL from Step 12.

#### Step 17: Build and Deploy Frontend to Development

```bash
cd ..

# Install dependencies (if not already done)
npm install

# Build for development
npm run build

# Deploy to development S3 bucket
aws s3 sync dist/ s3://quote-lambda-tf-frontend-dev --delete

# Invalidate CloudFront cache
aws cloudfront create-invalidation \
  --distribution-id $(cd infrastructure && terraform output -raw cloudfront_distribution_id) \
  --paths "/*"
```

### Phase 4: Verify Setup

#### Step 18: Test Both Environments

**Production (unchanged):**
```bash
# Visit your production URL
open https://d5ly3miadik75.cloudfront.net/
```

**Development (new):**
```bash
# Visit your development URL (from Step 15)
open https://YOUR_DEV_CLOUDFRONT_URL.cloudfront.net/
```

Both should work independently!

#### Step 19: Verify Workspace Switching

```bash
# Backend
cd quote-lambda-tf-backend/infrastructure/

# List all workspaces
terraform workspace list
# Should show:
#   default
# * dev

# Switch to production
terraform workspace select default
terraform plan -var="environment=prod"
# Should show no changes

# Switch to development
terraform workspace select dev
terraform plan -var="environment=dev"
# Should show no changes

# Frontend
cd ../../quote-lambda-tf-frontend/infrastructure/

terraform workspace list
terraform workspace select default
terraform plan -var="environment=prod"

terraform workspace select dev
terraform plan -var="environment=dev"
```

## Daily Workflow

### Working on Development

```bash
# 1. Make code changes
# 2. Switch to dev workspace
cd quote-lambda-tf-backend/infrastructure/
terraform workspace select dev

# 3. Apply infrastructure changes (if needed)
terraform apply -var="environment=dev"

# 4. Deploy Lambda code
cd ..
mvn clean package
aws lambda update-function-code \
  --function-name quotes-lambda-java-dev \
  --zip-file fileb://target/quote-lambda-java-1.0-SNAPSHOT.jar \
  --region eu-central-1

# 5. Test in development
open https://YOUR_DEV_CLOUDFRONT_URL.cloudfront.net/
```

### Deploying to Production

```bash
# 1. After testing in dev, switch to production workspace
cd quote-lambda-tf-backend/infrastructure/
terraform workspace select default

# 2. Apply infrastructure changes (if any)
terraform apply -var="environment=prod"

# 3. Deploy Lambda code
cd ..
mvn clean package
aws lambda update-function-code \
  --function-name quotes-lambda-java \
  --zip-file fileb://target/quote-lambda-java-1.0-SNAPSHOT.jar \
  --region eu-central-1

# 4. Deploy frontend
cd ../quote-lambda-tf-frontend/
npm run build
aws s3 sync dist/ s3://quote-lambda-tf-frontend --delete
```

## Useful Commands

### Check Current Workspace

```bash
terraform workspace show
```

### List All Workspaces

```bash
terraform workspace list
```

### Switch Workspace

```bash
# Switch to production
terraform workspace select default

# Switch to development
terraform workspace select dev
```

### View Resources in Current Workspace

```bash
terraform state list
```

### View Outputs for Current Workspace

```bash
terraform output
```

## Troubleshooting

### Issue: "Workspace already exists"

If you get an error that the workspace already exists:

```bash
# Just select it instead
terraform workspace select dev
```

### Issue: Resources already exist

If Terraform says resources already exist when creating dev:

```bash
# Make sure you're in the dev workspace
terraform workspace show

# If you're in default, switch to dev
terraform workspace select dev
```

### Issue: Can't switch workspace

If you have uncommitted changes:

```bash
# Check what's different
terraform plan

# If safe, apply or destroy first
terraform apply
# or
terraform destroy
```

### Issue: Wrong API URL in frontend

Make sure you're using the correct environment file:

```bash
# Development
cat .env.development

# Production  
cat .env.production
```

## Cost Implications

Running both environments will approximately double your costs:

- **Production**: ~$1-2/month
- **Development**: ~$1-2/month
- **Total**: ~$2-4/month

### Cost Optimization

If you want to save costs, destroy the dev environment when not using it:

```bash
# Destroy development environment
cd quote-lambda-tf-backend/infrastructure/
terraform workspace select dev
terraform destroy -var="environment=dev"

cd ../../quote-lambda-tf-frontend/infrastructure/
terraform workspace select dev
terraform destroy -var="environment=dev"

# Recreate when needed
terraform apply -var="environment=dev"
```

## Summary

After completing this setup:

✅ **Production Environment (default workspace)**
- Lambda: `quotes-lambda-java`
- DynamoDB: `quotes`
- S3: `quote-lambda-tf-frontend`
- CloudFront: `d5ly3miadik75.cloudfront.net`
- State: `env/default/quote-lambda-tf-backend/terraform.tfstate`

✅ **Development Environment (dev workspace)**
- Lambda: `quotes-lambda-java-dev`
- DynamoDB: `quotes-dev`
- S3: `quote-lambda-tf-frontend-dev`
- CloudFront: `[new-url].cloudfront.net`
- State: `env/dev/quote-lambda-tf-backend/terraform.tfstate`

✅ **Benefits**
- Safe testing without affecting production
- Easy switching between environments
- Separate URLs and resources
- Shared state bucket with isolated state files

## Next Steps

1. Follow this guide step by step
2. Test both environments
3. Set up GitHub Actions for automated deployments (see MULTI_ENVIRONMENT_SETUP.md)
4. Document your dev and prod URLs for team members

Need help with any step? Just ask!
