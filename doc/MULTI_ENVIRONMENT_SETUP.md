# Multi-Environment Setup Guide

This guide shows you how to set up separate **development** and **production** environments so you can develop, test, and deploy without affecting your live demo.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Quick Setup](#quick-setup)
- [Detailed Implementation](#detailed-implementation)
- [Workflow](#workflow)
- [Cost Considerations](#cost-considerations)
- [Alternative Approaches](#alternative-approaches)

## Overview

### Current Setup (Single Environment)
- ✅ Production environment on `main` branch
- ❌ No safe place to test changes
- ❌ Every deployment affects live demo

### Recommended Setup (Multi-Environment)
- ✅ **Production**: Stable, always working
- ✅ **Development**: Safe testing environment
- ✅ **Isolated**: Separate resources for each environment
- ✅ **Automated**: Branch-based deployments

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Shared Bootstrap (One-time)                   │
│  S3: edwinbulter-terraform-state                                 │
│  DynamoDB: terraform-locks                                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │
        ┌─────────────────────┴─────────────────────┐
        │                                            │
        ▼                                            ▼
┌──────────────────────┐                  ┌──────────────────────┐
│  Production (main)   │                  │  Development (dev)   │
├──────────────────────┤                  ├──────────────────────┤
│ Frontend:            │                  │ Frontend:            │
│ - S3: quote-prod     │                  │ - S3: quote-dev      │
│ - CloudFront: xxx    │                  │ - CloudFront: yyy    │
│                      │                  │                      │
│ Backend:             │                  │ Backend:             │
│ - Lambda: quote-prod │                  │ - Lambda: quote-dev  │
│ - API Gateway: aaa   │                  │ - API Gateway: bbb   │
│ - DynamoDB: quotes   │                  │ - DynamoDB: quotes-d │
│                      │                  │                      │
│ State Key:           │                  │ State Key:           │
│ prod/frontend.tfstate│                  │ dev/frontend.tfstate │
│ prod/backend.tfstate │                  │ dev/backend.tfstate  │
└──────────────────────┘                  └──────────────────────┘
```

## Quick Setup

### Comparison Table

| Feature | Workspaces | Separate Dirs | Var Files |
|---------|-----------|---------------|-----------|
| **Complexity** | Low | Medium | Very Low |
| **State Isolation** | ✅ Yes | ✅ Yes | ❌ No |
| **Simultaneous Envs** | ✅ Yes | ✅ Yes | ❌ No |
| **Code Duplication** | ✅ None | ⚠️ Some | ✅ None |
| **Accidental Deploy** | ⚠️ Possible | ✅ Difficult | ⚠️ Easy |
| **Default Behavior** | Uses 'default' workspace | N/A | Uses `default` in variables.tf |
| **Recommended For** | Your use case | Large teams | Quick testing |

### Option 1: Terraform Workspaces (Easiest)

**Pros:**
- ✅ Minimal code changes
- ✅ Same infrastructure code
- ✅ Easy to switch between environments

**Cons:**
- ⚠️ Shared state file (different workspaces)
- ⚠️ Easy to accidentally deploy to wrong environment

**Setup:**

```bash
# Backend
cd quote-lambda-tf-backend/infrastructure/

# Create development workspace
terraform workspace new dev
terraform workspace select dev
terraform apply -var="environment=dev"

# Switch back to production
terraform workspace select default
terraform apply -var="environment=prod"

# Frontend
cd ../../quote-lambda-tf-frontend/infrastructure/

terraform workspace new dev
terraform workspace select dev
terraform apply -var="environment=dev"

terraform workspace select default
terraform apply -var="environment=prod"
```

### Option 2: Separate Directories (Recommended)

**Pros:**
- ✅ Complete isolation
- ✅ Clear separation of environments
- ✅ Different configurations per environment
- ✅ Harder to make mistakes

**Cons:**
- ⚠️ More code duplication
- ⚠️ More setup work

**Setup:**

```bash
# Create environment-specific directories
quote-lambda-tf-backend/
├── infrastructure/
│   ├── environments/
│   │   ├── dev/
│   │   │   ├── backend.tf
│   │   │   ├── main.tf -> ../../modules/backend/
│   │   │   └── terraform.tfvars
│   │   └── prod/
│   │       ├── backend.tf
│   │       ├── main.tf -> ../../modules/backend/
│   │       └── terraform.tfvars
│   └── modules/
│       └── backend/
│           ├── lambda.tf
│           ├── api_gateway.tf
│           └── dynamodb.tf
```

### Option 3: Environment Variables (Simplest)

**Pros:**
- ✅ Minimal changes
- ✅ Same codebase
- ✅ Quick to implement

**Cons:**
- ⚠️ Manual environment switching
- ⚠️ Risk of human error
- ⚠️ No state isolation (same state file for all environments)

**Setup:**

Use Terraform variables with different `.tfvars` files:

```bash
# Development
terraform apply -var-file="dev.tfvars"

# Production
terraform apply -var-file="prod.tfvars"

# No var-file specified - uses defaults from variables.tf
terraform apply  # Uses default values (typically production)
```

**Important:** When no `-var-file` is specified, Terraform uses the `default` values defined in `variables.tf`. For safety, set the default to production:

```hcl
# variables.tf
variable "environment" {
  description = "Environment name (dev, prod)"
  type        = string
  default     = "prod"  # Default to production for safety
}
```

**Note:** This approach uses the **same state file** for all environments, which means you can't have dev and prod running simultaneously. Each `terraform apply` will replace the previous environment's resources.

### Understanding Default Behavior

#### Option 1: Workspaces
```bash
# Check current workspace
terraform workspace show
# Output: default (or dev, prod, etc.)

# When you run terraform apply without selecting workspace
terraform apply
# Uses: Currently selected workspace (shown by 'terraform workspace show')
# Default: 'default' workspace (if you never created/selected another)
```

#### Option 3: Var Files
```bash
# Check what defaults will be used
terraform plan
# Uses: default values from variables.tf

# Example variables.tf
variable "environment" {
  default = "prod"  # This is what gets used when no -var-file specified
}

# When you run terraform apply
terraform apply
# Uses: default = "prod" from variables.tf

terraform apply -var-file="dev.tfvars"
# Uses: environment = "dev" from dev.tfvars (overrides default)
```

**Key Difference:**
- **Workspaces**: Default behavior depends on currently selected workspace
- **Var Files**: Default behavior depends on `default` values in `variables.tf`

## Detailed Implementation

### Recommended: Terraform Workspaces

This is the easiest approach for your current setup.

#### Step 1: Add Environment Variable

**Backend: `quote-lambda-tf-backend/infrastructure/variables.tf`**

```hcl
variable "environment" {
  description = "Environment name (dev, prod)"
  type        = string
  default     = "prod"
}

variable "lambda_function_name" {
  description = "Name of the Lambda function"
  type        = string
  default     = "quotes-lambda-java"
}

variable "dynamodb_table_name" {
  description = "Name of the DynamoDB table"
  type        = string
  default     = "quotes"
}
```

**Update resource names in `lambda.tf`, `api_gateway.tf`, `dynamodb.tf`:**

```hcl
# lambda.tf
resource "aws_lambda_function" "quote_handler" {
  function_name = "${var.lambda_function_name}-${var.environment}"
  # ... rest of config
}

# dynamodb.tf
resource "aws_dynamodb_table" "quotes" {
  name = "${var.dynamodb_table_name}-${var.environment}"
  # ... rest of config
}
```

**Frontend: `quote-lambda-tf-frontend/infrastructure/variables.tf`**

```hcl
variable "environment" {
  description = "Environment name (dev, prod)"
  type        = string
  default     = "prod"
}

variable "bucket_name" {
  description = "S3 bucket name for the website"
  type        = string
  default     = "quote-lambda-tf-frontend"
}
```

**Update `main.tf`:**

```hcl
resource "aws_s3_bucket" "website" {
  bucket = "${var.bucket_name}-${var.environment}"
}
```

#### Step 2: Update Backend Configuration

**Backend: `infrastructure/backend.tf`**

```hcl
terraform {
  backend "s3" {
    bucket         = "edwinbulter-terraform-state"
    key            = "quote-lambda-tf-backend/${terraform.workspace}/terraform.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "terraform-locks"
    encrypt        = true
  }
}
```

**Frontend: `infrastructure/backend.tf`**

```hcl
terraform {
  backend "s3" {
    bucket         = "edwinbulter-terraform-state"
    key            = "quote-lambda-tf-frontend/${terraform.workspace}/terraform.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "terraform-locks"
    encrypt        = true
  }
}
```

#### Step 3: Create Environment-Specific Variable Files

**Backend: `infrastructure/dev.tfvars`**

```hcl
environment          = "dev"
lambda_function_name = "quotes-lambda-java"
dynamodb_table_name  = "quotes"
aws_region          = "eu-central-1"
```

**Backend: `infrastructure/prod.tfvars`**

```hcl
environment          = "prod"
lambda_function_name = "quotes-lambda-java"
dynamodb_table_name  = "quotes"
aws_region          = "eu-central-1"
```

**Frontend: `infrastructure/dev.tfvars`**

```hcl
environment = "dev"
bucket_name = "quote-lambda-tf-frontend"
aws_region  = "eu-central-1"
```

**Frontend: `infrastructure/prod.tfvars`**

```hcl
environment = "prod"
bucket_name = "quote-lambda-tf-frontend"
aws_region  = "eu-central-1"
```

#### Step 4: Deploy Development Environment

```bash
# Backend
cd quote-lambda-tf-backend/infrastructure/
terraform workspace new dev
terraform workspace select dev
terraform apply -var-file="dev.tfvars"

# Frontend
cd ../../quote-lambda-tf-frontend/infrastructure/
terraform workspace new dev
terraform workspace select dev
terraform apply -var-file="dev.tfvars"
```

#### Step 5: Update GitHub Actions

Create environment-specific workflows:

**.github/workflows/deploy-lambda-dev.yml**

```yaml
name: Deploy Lambda (Development)

on:
  push:
    branches: [develop]
    paths:
      - 'quote-lambda-tf-backend/**'

env:
  AWS_REGION: eu-central-1
  TERRAFORM_WORKSPACE: dev

jobs:
  deploy:
    runs-on: ubuntu-latest
    permissions:
      id-token: write
      contents: read
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'corretto'
      
      - name: Build with Maven
        run: |
          cd quote-lambda-tf-backend
          mvn clean package -DskipTests
      
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ secrets.AWS_ROLE_ARN }}
          aws-region: ${{ env.AWS_REGION }}
      
      - name: Deploy to Lambda
        run: |
          aws lambda update-function-code \
            --function-name quotes-lambda-java-dev \
            --zip-file fileb://quote-lambda-tf-backend/target/quote-lambda-java-1.0-SNAPSHOT.jar \
            --region ${{ env.AWS_REGION }}
```

**.github/workflows/deploy-lambda-prod.yml**

```yaml
name: Deploy Lambda (Production)

on:
  push:
    branches: [main]
    paths:
      - 'quote-lambda-tf-backend/**'

env:
  AWS_REGION: eu-central-1
  TERRAFORM_WORKSPACE: default

jobs:
  deploy:
    runs-on: ubuntu-latest
    permissions:
      id-token: write
      contents: read
    
    steps:
      # Same as dev but with function name: quotes-lambda-java-prod
```

#### Step 6: Update Frontend Environment Variables

**Create `.env.development.local`:**

```bash
VITE_REACT_APP_API_BASE_URL=https://YOUR_DEV_API_GATEWAY_URL.execute-api.eu-central-1.amazonaws.com
```

**Keep `.env.production`:**

```bash
VITE_REACT_APP_API_BASE_URL=https://blgydc5rjk.execute-api.eu-central-1.amazonaws.com
```

## Workflow

### Daily Development

```bash
# 1. Create feature branch from develop
git checkout develop
git pull
git checkout -b feature/my-feature

# 2. Make changes and test locally
npm run dev  # Frontend
mvn spring-boot:run  # Backend (if testing locally)

# 3. Push to feature branch
git add .
git commit -m "Add new feature"
git push origin feature/my-feature

# 4. Create PR to develop
# GitHub Actions deploys to dev environment automatically

# 5. Test in dev environment
# Visit: https://YOUR_DEV_CLOUDFRONT_URL.cloudfront.net

# 6. When ready, merge to develop
# Test thoroughly in dev

# 7. Create PR from develop to main
# After approval, merge to main
# GitHub Actions deploys to production automatically
```

### Switching Environments Manually

```bash
# Deploy to development
cd quote-lambda-tf-backend/infrastructure/
terraform workspace select dev
terraform apply -var-file="dev.tfvars"

# Deploy to production
terraform workspace select default
terraform apply -var-file="prod.tfvars"
```

### Checking Current Environment

```bash
# Check current workspace
terraform workspace show

# List all workspaces
terraform workspace list

# View outputs for current environment
terraform output
```

## Cost Considerations

### Additional Costs for Dev Environment

Running a separate development environment will approximately **double** your AWS costs:

| Service | Production | Development | Total |
|---------|-----------|-------------|-------|
| Lambda | ~$0.50/mo | ~$0.50/mo | ~$1.00/mo |
| API Gateway | ~$0.01/mo | ~$0.01/mo | ~$0.02/mo |
| DynamoDB | ~$0.01/mo | ~$0.01/mo | ~$0.02/mo |
| S3 | ~$0.01/mo | ~$0.01/mo | ~$0.02/mo |
| CloudFront | ~$0.50/mo | ~$0.50/mo | ~$1.00/mo |
| **Total** | **~$1.03/mo** | **~$1.03/mo** | **~$2.06/mo** |

**Cost Optimization Tips:**
- 🔄 Destroy dev environment when not in use: `terraform destroy`
- ⏰ Use scheduled Lambda to auto-shutdown dev at night
- 📊 Use smaller DynamoDB capacity in dev
- 🎯 Skip CloudFront in dev, use S3 website directly

### Cost-Saving Alternative

**Use dev environment only when needed:**

```bash
# Create dev environment
terraform workspace select dev
terraform apply -var-file="dev.tfvars"

# Test your changes
# ...

# Destroy dev environment
terraform destroy -var-file="dev.tfvars"
```

This way you only pay for dev when actively using it!

## Alternative Approaches

### 1. Feature Flags

Use feature flags to test new features in production without affecting users:

```typescript
// Frontend
const FEATURE_FLAGS = {
  newQuoteUI: import.meta.env.VITE_ENABLE_NEW_UI === 'true'
};

if (FEATURE_FLAGS.newQuoteUI) {
  // Show new UI
} else {
  // Show old UI
}
```

**Pros:** No additional infrastructure
**Cons:** More complex code, features visible in production

### 2. Preview Environments (Advanced)

Create temporary environments for each PR:

```yaml
# .github/workflows/preview-deploy.yml
on:
  pull_request:
    types: [opened, synchronize]

jobs:
  deploy-preview:
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to preview-${{ github.event.pull_request.number }}
        # Deploy to temporary environment
```

**Pros:** Test each PR independently
**Cons:** Complex setup, higher costs

### 3. Local Development Only

Keep production as-is, test everything locally:

```bash
# Frontend
npm run dev

# Backend (use LocalStack or SAM Local)
sam local start-api
```

**Pros:** Zero additional AWS costs
**Cons:** Local environment differs from production

## Recommended Approach

For your use case, I recommend:

1. **Terraform Workspaces** - Easiest to implement
2. **Branch Strategy:**
   - `main` → Production (always stable)
   - `develop` → Development environment
   - `feature/*` → Local development only
3. **GitHub Actions** - Auto-deploy on branch push
4. **Cost Optimization** - Destroy dev when not needed

This gives you:
- ✅ Safe testing environment
- ✅ Always-working production
- ✅ Minimal code changes
- ✅ Low additional cost (~$1-2/month)
- ✅ Easy to manage

## Next Steps

1. **Read this guide** and choose your approach
2. **Implement Terraform workspaces** (recommended)
3. **Update GitHub Actions** for branch-based deployments
4. **Create develop branch** and test deployment
5. **Document your workflow** for team members

Need help implementing? Check the specific implementation guide for your chosen approach!
