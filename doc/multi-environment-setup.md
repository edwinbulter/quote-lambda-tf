# Multi-Environment Setup Guide

This document describes the current multi-environment setup using **Terraform Workspaces** and explains why this approach was chosen over alternatives.

## Table of Contents

- [Current Setup Overview](#current-setup-overview)
- [Architecture](#architecture)
- [Initial Setup](#initial-setup)
- [How to Use Workspaces](#how-to-use-workspaces)
- [Daily Workflows](#daily-workflows)
- [Why Workspaces Were Chosen](#why-workspaces-were-chosen)
- [Comparison with Other Approaches](#comparison-with-other-approaches)
- [Cost Considerations](#cost-considerations)

## Current Setup Overview

This project uses **Terraform Workspaces** to manage multiple environments:

- ✅ **Production** (`default` workspace): Stable, production-ready environment
- ✅ **Development** (`dev` workspace): Safe testing environment
- ✅ **State Isolation**: Each workspace has its own state file
- ✅ **Shared Code**: Same Terraform configuration for all environments
- ✅ **Environment-Specific Resources**: Resources are named with environment suffix
- ✅ **Automatic Environment Detection**: Environment is automatically derived from workspace name

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│              S3 Bucket: edwinbulter-terraform-state              │
│                                                                   │
│  State Files (organized by workspace):                           │
│  ├── quote-lambda-tf-frontend/                                   │
│  │   ├── env:/default/terraform.tfstate    (Production)         │
│  │   └── env:/dev/terraform.tfstate        (Development)        │
│  └── quote-lambda-tf-backend/                                    │
│      ├── env:/default/terraform.tfstate    (Production)         │
│      └── env:/dev/terraform.tfstate        (Development)        │
│                                                                   │
│  DynamoDB Table: terraform-locks (shared locking)                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │
        ┌─────────────────────┴─────────────────────┐
        │                                            │
        ▼                                            ▼
┌──────────────────────────────┐      ┌──────────────────────────────┐
│  Production (default)        │      │  Development (dev)           │
├──────────────────────────────┤      ├──────────────────────────────┤
│ Frontend:                    │      │ Frontend:                    │
│ - S3: quote-lambda-tf-       │      │ - S3: quote-lambda-tf-       │
│       frontend               │      │       frontend-dev           │
│ - CloudFront:                │      │ - CloudFront:                │
│   d5ly3miadik75              │      │   d1fzgis91zws1k             │
│                              │      │                              │
│ Backend:                     │      │ Backend:                     │
│ - Lambda:                    │      │ - Lambda:                    │
│   quote-lambda-tf-backend    │      │   quote-lambda-tf-backend-dev│
│ - API Gateway:               │      │ - API Gateway:               │
│   blgydc5rjk                 │      │   (dev-specific)             │
│ - DynamoDB:                  │      │ - DynamoDB:                  │
│   quote-lambda-tf-quotes     │      │   quote-lambda-tf-quotes-dev │
└──────────────────────────────┘      └──────────────────────────────┘
```

## Initial Setup

### Automated Setup Script

The easiest way to set up workspaces is using the provided setup script:

```bash
# From the repository root
./scripts/setup-workspaces.sh
```

**What the script does:**
1. Reinitializes Terraform backends with workspace support
2. Verifies your production environment (default workspace)
3. Creates a development workspace
4. Deploys development infrastructure for both backend and frontend
5. Saves development URLs to files for easy reference

The script is interactive and will ask for confirmation before making changes.

### Manual Setup

If you prefer manual setup or need to create additional workspaces:

```bash
# Backend
cd quote-lambda-tf-backend/infrastructure/
terraform init -reconfigure
terraform workspace new dev
terraform workspace select dev
terraform apply  # Environment is automatically set to 'dev' from workspace

# Frontend
cd ../../quote-lambda-tf-frontend/infrastructure/
terraform init -reconfigure
terraform workspace new dev
terraform workspace select dev
terraform apply  # Environment is automatically set to 'dev' from workspace
```

**Note:** The environment variable is automatically derived from the workspace name:
- `default` workspace → `prod` environment
- `dev` workspace → `dev` environment
- Any other workspace → uses the workspace name as the environment

You no longer need to pass `-var="environment=dev"` or set `TF_VAR_environment`!

## How to Use Workspaces

### Automatic Environment Detection

The project uses a `locals.tf` file to automatically derive the environment from the Terraform workspace:

```hcl
# locals.tf
locals {
  # Automatically set environment based on workspace
  # "default" workspace maps to "prod", all others use their workspace name
  environment = terraform.workspace == "default" ? "prod" : terraform.workspace
}
```

This means:
- When you select the `default` workspace, all resources are created with the `prod` environment
- When you select the `dev` workspace, all resources are created with the `dev` environment
- When you select any other workspace (e.g., `staging`), resources use that workspace name as the environment

**Benefits:**
- ✅ No need to pass `-var="environment=dev"` every time
- ✅ No need to set `TF_VAR_environment` environment variable
- ✅ Workspace and environment are always in sync
- ✅ Reduces human error from mismatched workspace/environment

### Check Current Workspace

```bash
# Show current workspace
terraform workspace show

# List all workspaces
terraform workspace list
```

### Switch Between Environments

```bash
# Switch to development
terraform workspace select dev

# Switch to production
terraform workspace select default
```

### Deploy to Development

```bash
cd quote-lambda-tf-backend/infrastructure/
terraform workspace select dev  # Automatically sets environment to 'dev'
terraform plan
terraform apply

cd ../../quote-lambda-tf-frontend/infrastructure/
terraform workspace select dev  # Automatically sets environment to 'dev'
terraform plan
terraform apply
```

### Deploy to Production

```bash
cd quote-lambda-tf-backend/infrastructure/
terraform workspace select default  # Automatically sets environment to 'prod'
terraform plan
terraform apply

cd ../../quote-lambda-tf-frontend/infrastructure/
terraform workspace select default  # Automatically sets environment to 'prod'
terraform plan
terraform apply
```

### Create a New Environment

```bash
# Create a new workspace (e.g., staging)
terraform workspace new staging
terraform workspace select staging
terraform apply
```

### View Environment-Specific Outputs

```bash
# Select the environment
terraform workspace select dev

# View outputs for that environment
terraform output
```

### GitHub Actions Integration

The project uses GitHub Actions with manual workflow dispatch to deploy to specific environments:

**Deploy Frontend:**
```yaml
# .github/workflows/deploy-frontend.yml
# Manually trigger with environment selection (prod/dev)
```

**Deploy Backend:**
```yaml
# .github/workflows/deploy-lambda.yml
# Manually trigger with environment selection (prod/dev)
```

To deploy via GitHub Actions:
1. Go to Actions tab in GitHub
2. Select the workflow (Deploy Frontend or Deploy Lambda)
3. Click "Run workflow"
4. Select environment: `prod` or `dev`
5. Click "Run workflow"

## Daily Workflows

### Deploying Lambda Code

**To Development:**
```bash
cd quote-lambda-tf-backend
mvn clean package

aws lambda update-function-code \
  --function-name quote-lambda-tf-backend-dev \
  --zip-file fileb://target/quote-lambda-tf-backend-1.0-SNAPSHOT.jar \
  --region eu-central-1
```

**To Production:**
```bash
cd quote-lambda-tf-backend
mvn clean package

aws lambda update-function-code \
  --function-name quote-lambda-tf-backend \
  --zip-file fileb://target/quote-lambda-tf-backend-1.0-SNAPSHOT.jar \
  --region eu-central-1
```

### Deploying Frontend

**To Development:**
```bash
cd quote-lambda-tf-frontend
npm run build -- --mode development

aws s3 sync dist/ s3://quote-lambda-tf-frontend-dev --delete

# Invalidate CloudFront cache
aws cloudfront create-invalidation \
  --distribution-id E2RBZCEJJZBIG \
  --paths "/*"
```

**To Production:**
```bash
cd quote-lambda-tf-frontend
npm run build

aws s3 sync dist/ s3://quote-lambda-tf-frontend --delete

# Invalidate CloudFront cache
aws cloudfront create-invalidation \
  --distribution-id E1MX4CB72GG2ZM \
  --paths "/*"
```

### Updating Infrastructure

**Development:**
```bash
# Backend
cd quote-lambda-tf-backend/infrastructure/
terraform workspace select dev  # Environment automatically set to 'dev'
terraform plan
terraform apply

# Frontend
cd ../../quote-lambda-tf-frontend/infrastructure/
terraform workspace select dev  # Environment automatically set to 'dev'
terraform plan
terraform apply
```

**Production:**
```bash
# Backend
cd quote-lambda-tf-backend/infrastructure/
terraform workspace select default  # Environment automatically set to 'prod'
terraform plan
terraform apply

# Frontend
cd ../../quote-lambda-tf-frontend/infrastructure/
terraform workspace select default  # Environment automatically set to 'prod'
terraform plan
terraform apply
```

### Destroying Development Environment

To save costs when not actively developing:

```bash
# Backend
cd quote-lambda-tf-backend/infrastructure/
terraform workspace select dev  # Environment automatically set to 'dev'
terraform destroy

# Frontend
cd ../../quote-lambda-tf-frontend/infrastructure/
terraform workspace select dev  # Environment automatically set to 'dev'
terraform destroy
```

Recreate when needed with `terraform apply`.

### Troubleshooting

**Wrong workspace selected:**
```bash
terraform workspace show  # Check current workspace
terraform workspace select dev  # or default
```

**Resources already exist:**
```bash
# Make sure you're in the correct workspace
terraform workspace show
```

**Can't find outputs:**
```bash
# Select the correct workspace first
terraform workspace select dev
terraform output
```

## Why Workspaces Were Chosen

Terraform Workspaces were selected for this project because they provide the best balance of simplicity, isolation, and maintainability for a small-to-medium monorepo project.

### Key Benefits

1. **Minimal Code Duplication**
   - Single set of Terraform files for all environments
   - Changes apply to all environments consistently
   - Easy to maintain and update

2. **State Isolation**
   - Each workspace has its own state file
   - Production and development states are completely separate
   - Safe to experiment in dev without affecting prod

3. **Simple Workflow**
   - Easy to switch between environments with `terraform workspace select`
   - Clear separation: `default` = production, `dev` = development
   - No complex directory structures to navigate

4. **Low Overhead**
   - No additional tooling required
   - Built into Terraform
   - Minimal learning curve for team members

5. **Cost Effective**
   - Can easily destroy dev environment when not needed
   - Simple to recreate when needed
   - Clear visibility of resources per environment

## Comparison with Other Approaches

### Option 1: Terraform Workspaces (Current Choice) ✅

**How it works:**
- Single set of Terraform files
- Use `terraform workspace` commands to switch environments
- Each workspace gets its own state file in S3

**Pros:**
- ✅ Minimal code duplication
- ✅ Easy to switch between environments
- ✅ State isolation between environments
- ✅ Built into Terraform (no additional tools)
- ✅ Simple to understand and maintain

**Cons:**
- ⚠️ Must remember to select correct workspace before applying (mitigated by automatic environment detection)
- ⚠️ All environments share same Terraform code (can't have env-specific configs easily)

**Best for:** Small to medium projects with similar infrastructure across environments

---

### Option 2: Separate Directories

**How it works:**
- Create separate directories for each environment
- Each directory has its own Terraform files
- Use modules to share common code

```
infrastructure/
├── environments/
│   ├── dev/
│   │   ├── backend.tf
│   │   ├── main.tf
│   │   └── terraform.tfvars
│   └── prod/
│       ├── backend.tf
│       ├── main.tf
│       └── terraform.tfvars
└── modules/
    └── app/
        ├── lambda.tf
        └── api_gateway.tf
```

**Pros:**
- ✅ Complete isolation between environments
- ✅ Different configurations per environment
- ✅ Harder to accidentally deploy to wrong environment
- ✅ Clear directory structure

**Cons:**
- ⚠️ More code duplication
- ⚠️ More complex directory structure
- ⚠️ Changes need to be applied to multiple directories
- ⚠️ More setup and maintenance overhead

**Best for:** Large teams, complex projects with significantly different environment configs

---

### Option 3: Variable Files Only

**How it works:**
- Single set of Terraform files
- Use different `.tfvars` files for each environment
- No workspace switching

```bash
terraform apply -var-file="dev.tfvars"
terraform apply -var-file="prod.tfvars"
```

**Pros:**
- ✅ Very simple to implement
- ✅ No code duplication
- ✅ Easy to understand

**Cons:**
- ❌ **No state isolation** - same state file for all environments
- ❌ **Cannot run multiple environments simultaneously**
- ❌ Each apply destroys the previous environment
- ⚠️ Easy to forget `-var-file` flag and deploy to wrong environment

**Best for:** Quick testing, temporary environments, or when you only need one environment at a time

---

### Why Workspaces Win for This Project

| Criteria | Workspaces | Separate Dirs | Var Files |
|----------|-----------|---------------|-----------|
| **State Isolation** | ✅ Yes | ✅ Yes | ❌ No |
| **Code Duplication** | ✅ None | ⚠️ Some | ✅ None |
| **Simultaneous Envs** | ✅ Yes | ✅ Yes | ❌ No |
| **Setup Complexity** | ✅ Low | ⚠️ Medium | ✅ Very Low |
| **Maintenance** | ✅ Easy | ⚠️ More work | ✅ Easy |
| **Accidental Deploys** | ⚠️ Possible | ✅ Difficult | ❌ Easy |
| **Environment-Specific Config** | ⚠️ Limited | ✅ Full | ✅ Full |

**Decision:** Workspaces provide the best balance for this monorepo project because:
1. We need state isolation (✅)
2. We want to run dev and prod simultaneously (✅)
3. We want minimal code duplication (✅)
4. Our environments are similar enough that we don't need separate configs (✅)
5. The project is small enough that workspace switching is not a burden (✅)

## Cost Considerations

Running multiple environments increases AWS costs proportionally. Here's what to expect:

### Cost Breakdown

| Service | Production | Development | Total |
|---------|-----------|-------------|-------|
| Lambda | ~$0.50/mo | ~$0.50/mo | ~$1.00/mo |
| API Gateway | ~$0.01/mo | ~$0.01/mo | ~$0.02/mo |
| DynamoDB | ~$0.01/mo | ~$0.01/mo | ~$0.02/mo |
| S3 | ~$0.01/mo | ~$0.01/mo | ~$0.02/mo |
| CloudFront | ~$0.50/mo | ~$0.50/mo | ~$1.00/mo |
| **Total** | **~$1.03/mo** | **~$1.03/mo** | **~$2.06/mo** |

### Cost Optimization Strategies

1. **Destroy Dev When Not Needed**
   ```bash
   terraform workspace select dev
   terraform destroy
   ```
   This completely removes the dev environment. Recreate it when needed with `terraform apply`.

2. **Use Smaller Resources in Dev**
   - Reduce DynamoDB capacity
   - Use smaller Lambda memory allocation
   - Skip CloudFront (use S3 website directly)

3. **Scheduled Shutdown**
   - Use AWS Lambda to automatically shut down dev resources at night
   - Recreate in the morning

4. **Share Resources** (Advanced)
   - Use same DynamoDB table with different key prefixes
   - Share CloudFront distribution with different origins
   - **Note:** This reduces isolation and is not recommended for this project

### Recommended Approach

For a learning/demo project like this:
- Keep production running 24/7 (~$1/month)
- Create dev environment only when actively developing
- Destroy dev when done testing
- **Total cost: ~$1-2/month** depending on dev usage

