# Bootstrap Infrastructure

This directory contains the Terraform configuration for the **bootstrap infrastructure** - the S3 bucket and DynamoDB table used to store and lock the Terraform state for the main application.

## Purpose

By separating the bootstrap resources from the application infrastructure, you can:
- **Destroy and recreate the application** without affecting the Terraform state storage
- **Protect critical state infrastructure** from accidental deletion
- **Manage state infrastructure independently** with its own lifecycle

## Resources Created

- **S3 Bucket**: `edwinbulter-terraform-state`
  - **Shared across all projects** - different projects use different keys
  - Versioning enabled
  - Server-side encryption (AES256)
  - Lifecycle policy to prevent accidental deletion
  
- **DynamoDB Table**: `terraform-locks`
  - **Shared across all projects** - provides state locking for all Terraform operations
  - Used for state locking to prevent concurrent modifications
  - Pay-per-request billing mode

## Why a Shared Bucket?

Using a single S3 bucket for all project states is a best practice because:

✅ **Cost Efficiency**: One bucket instead of multiple buckets (S3 charges per bucket in some regions)

✅ **Centralized Management**: All state files in one place, easier to backup and monitor

✅ **Simplified IAM**: Single bucket policy for all projects

✅ **Project Isolation**: Each project uses a unique key path (e.g., `quote-lambda-tf-frontend/terraform.tfstate`, `quote-lambda-tf-backend/terraform.tfstate`)

✅ **Shared Locking**: One DynamoDB table handles locking for all projects

## Initial Setup

### 1. Initialize and Apply Bootstrap

```bash
cd infrastructure/bootstrap
terraform init
terraform apply
```

This creates the S3 bucket and DynamoDB table that will store the main application's Terraform state.

### 2. Initialize Main Infrastructure

After the bootstrap resources are created:

```bash
cd ../  # Back to infrastructure/
terraform init
terraform apply
```

The main infrastructure will now use the S3 backend for state storage.

## Workflow

### Destroying the Application (Without Destroying State)

```bash
cd infrastructure/
terraform destroy
```

This destroys all application resources (S3 bucket, CloudFront distribution, etc.) but **preserves** the Terraform state in S3.

### Recreating the Application

```bash
cd infrastructure/
terraform apply
```

Since the state is preserved in S3, Terraform knows what was previously deployed and can recreate or update resources accordingly.

### Destroying Everything (Including State Infrastructure)

⚠️ **Warning**: This will delete the Terraform state storage. Only do this if you want to completely remove all infrastructure.

```bash
# First destroy the application
cd infrastructure/
terraform destroy

# Then destroy the bootstrap
cd bootstrap/
terraform destroy
```

## State Management

- **Bootstrap state**: Stored locally in `bootstrap/terraform.tfstate`
- **Application state**: Stored remotely in S3 at `s3://quote-lambda-tf-frontend-terraform-state/quote-lambda-tf-frontend/terraform.tfstate`

## Notes

- The bootstrap resources have `prevent_destroy = true` lifecycle policy to protect against accidental deletion
- To actually destroy bootstrap resources, you'll need to remove or comment out the `prevent_destroy` block first
- The bootstrap configuration uses local state storage (not S3) to avoid circular dependency
