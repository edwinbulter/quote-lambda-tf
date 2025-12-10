# Infrastructure Setup Guide

This directory contains Terraform configuration for deploying the Quote Lambda Frontend infrastructure on AWS.

## Table of Contents

- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [First-Time Setup](#first-time-setup)
  - [Step 1: Initialize Terraform Backend](#step-1-initialize-terraform-backend)
  - [Step 2: Review the Infrastructure Plan](#step-2-review-the-infrastructure-plan)
  - [Step 3: Deploy the Infrastructure](#step-3-deploy-the-infrastructure)
  - [Step 4: Note the Outputs](#step-4-note-the-outputs)
- [Configuration](#configuration)
  - [Variables](#variables)
  - [Backend Configuration](#backend-configuration)
- [Terraform Files & Git Repository](#terraform-files--git-repository)
  - [Files Overview](#files-overview)
  - [Recommended .gitignore](#recommended-gitignore)
  - [Why These Rules?](#why-these-rules)
- [Deploying Your Application](#deploying-your-application)
- [Common Commands](#common-commands)
- [Updating Infrastructure](#updating-infrastructure)
- [Team Collaboration](#team-collaboration)
- [Troubleshooting](#troubleshooting)
  - [State Lock Error](#state-lock-error)
  - [Backend Initialization Error](#backend-initialization-error)
  - [Provider Authentication Issues](#provider-authentication-issues)
- [Security Best Practices](#security-best-practices)
- [Cost Considerations](#cost-considerations)
- [Cleanup](#cleanup)
- [Support](#support)
- [License](#license)

## Architecture

The infrastructure consists of:
- **S3 Bucket**: Hosts the static React application files
- **CloudFront Distribution**: CDN for global content delivery with HTTPS
- **CloudFront Origin Access Identity**: Secure access from CloudFront to S3
- **S3 Backend**: Remote state storage with versioning and encryption
- **DynamoDB Table**: State locking to prevent concurrent modifications

## Prerequisites

Before you begin, ensure you have:

1. **AWS CLI** installed and configured
   ```bash
   aws --version
   aws configure
   ```

2. **Terraform** installed (version ~> 5.0)
   ```bash
   terraform --version
   ```

3. **AWS Credentials** configured with appropriate permissions:
   - S3 bucket creation and management
   - CloudFront distribution management
   - DynamoDB table creation
   - IAM policy management

4. **AWS Profile** named `edwinbulter` configured in `~/.aws/credentials` (or update `provider.tf` with your profile name)

## First-Time Setup

### Step 1: Initialize Terraform Backend

The backend configuration uses S3 for remote state storage. On first setup, the backend is already configured and the required resources (S3 bucket and DynamoDB table) have been created.

```bash
cd infrastructure
terraform init
```

### Step 2: Review the Infrastructure Plan

Preview what resources will be created:

```bash
terraform plan
```

This will show:
- S3 bucket for website hosting
- CloudFront distribution
- S3 bucket policies
- CloudFront Origin Access Identity

### Step 3: Deploy the Infrastructure

Apply the Terraform configuration:

```bash
terraform apply
```

Review the plan and type `yes` to confirm.

### Step 4: Note the Outputs

After successful deployment, Terraform will output:
- **s3_bucket_name**: The S3 bucket where you'll upload your built React app
- **cloudfront_url**: The CloudFront URL where your website will be accessible

Example output:
```
Outputs:

cloudfront_url = "d1234567890abc.cloudfront.net"
s3_bucket_name = "quote-lambda-frontend"
```

## Configuration

### Variables

You can customize the deployment by modifying `variables.tf` or creating a `terraform.tfvars` file:

```hcl
aws_region  = "eu-central-1"
bucket_name = "quote-lambda-frontend"
```

### Backend Configuration

The remote state backend is configured in `backend.tf`:
- **Bucket**: `quote-lambda-frontend-terraform-state`
- **Key**: `quote-lambda-frontend/terraform.tfstate`
- **Region**: `eu-central-1`
- **DynamoDB Table**: `terraform-locks`
- **Encryption**: Enabled

## Terraform Files & Git Repository

### Files Overview

| File | Description | Should be in Git? |
|------|-------------|-------------------|
| `*.tf` | Terraform configuration files | ✅ **YES** - These define your infrastructure |
| `.terraform.lock.hcl` | Dependency lock file | ✅ **YES** - Ensures consistent provider versions |
| `terraform.tfstate` | Current state file | ❌ **NO** - Contains sensitive data, stored in S3 |
| `terraform.tfstate.backup` | Backup of previous state | ❌ **NO** - Contains sensitive data, stored in S3 |
| `.terraform/` | Provider plugins and modules | ❌ **NO** - Downloaded during `terraform init` |

### Recommended .gitignore

Add these entries to your `.gitignore`:

```gitignore
# Terraform files
**/.terraform/*
*.tfstate
*.tfstate.*
crash.log
crash.*.log
override.tf
override.tf.json
*_override.tf
*_override.tf.json
.terraformrc
terraform.rc

# Sensitive variable files
*.tfvars
*.tfvars.json
!example.tfvars

# CLI configuration files
.terraform.lock.hcl  # Optional: some teams commit this, others don't
```

### Why These Rules?

#### ✅ Commit `.terraform.lock.hcl`
- **Ensures consistency**: Everyone on the team uses the same provider versions
- **No sensitive data**: Only contains provider version information
- **Best practice**: Recommended by HashiCorp
- **Safe for public repos**: Contains no secrets or infrastructure details

#### ❌ Never Commit State Files
- **Contains sensitive data**: 
  - Resource IDs
  - IP addresses
  - Potentially secrets or passwords
  - Complete infrastructure layout
- **Security risk**: Especially dangerous in public repositories
- **Remote backend**: State is stored securely in S3 with:
  - Encryption at rest
  - Versioning enabled
  - Access control via IAM
  - State locking via DynamoDB

#### ❌ Never Commit `.terraform/` Directory
- **Large files**: Contains provider binaries (100+ MB)
- **Platform-specific**: Binaries differ between OS/architectures
- **Regenerated**: Automatically downloaded during `terraform init`

## Deploying Your Application

After the infrastructure is created:

1. **Build your React app**:
   ```bash
   npm run build
   ```

2. **Upload to S3**:
   ```bash
   aws s3 sync dist/ s3://quote-lambda-frontend --delete
   ```

3. **Invalidate CloudFront cache** (if needed):
   ```bash
   aws cloudfront create-invalidation \
     --distribution-id <DISTRIBUTION_ID> \
     --paths "/*"
   ```

## Common Commands

```bash
# Initialize Terraform
terraform init

# Format Terraform files
terraform fmt

# Validate configuration
terraform validate

# Plan changes
terraform plan

# Apply changes
terraform apply

# Show current state
terraform show

# List resources
terraform state list

# Destroy infrastructure (use with caution!)
terraform destroy
```

## Updating Infrastructure

To make changes to the infrastructure:

1. Modify the `.tf` files
2. Run `terraform plan` to preview changes
3. Run `terraform apply` to apply changes
4. Commit the `.tf` files to Git

The state file in S3 will be automatically updated.

## Team Collaboration

When working with a team:

1. **Always pull latest code** before making changes
2. **Run `terraform plan`** before `terraform apply`
3. **State locking** prevents concurrent modifications
4. **Review changes** carefully in the plan output
5. **Communicate** with team members about infrastructure changes

## Troubleshooting

### State Lock Error
If you get a state lock error:
```bash
# Force unlock (only if you're sure no one else is running Terraform)
terraform force-unlock <LOCK_ID>
```

### Backend Initialization Error
If the S3 bucket doesn't exist:
1. Comment out the `backend "s3"` block in `backend.tf`
2. Run `terraform init`
3. Run `terraform apply` to create backend resources
4. Uncomment the backend block
5. Run `terraform init -migrate-state`

### Provider Authentication Issues
Ensure your AWS credentials are configured:
```bash
aws sts get-caller-identity
```

## Security Best Practices

1. **Never commit secrets**: Use AWS Secrets Manager or Parameter Store
2. **Use IAM roles**: Prefer IAM roles over access keys when possible
3. **Enable MFA**: For production AWS accounts
4. **Restrict access**: Use least-privilege IAM policies
5. **Audit regularly**: Review CloudTrail logs
6. **Encrypt everything**: State files, S3 buckets, etc.

## Cost Considerations

This infrastructure includes:
- **S3**: Pay for storage and requests (minimal for static sites)
- **CloudFront**: Free tier available, then pay per request
- **DynamoDB**: On-demand pricing (very low cost for state locking)

Estimated cost: **$1-5/month** for low-traffic sites

## Cleanup

To destroy all infrastructure:

```bash
terraform destroy
```

**Warning**: This will delete:
- The S3 bucket (and all website files)
- The CloudFront distribution
- All associated resources

The backend resources (state bucket and DynamoDB table) have `prevent_destroy` enabled and must be removed manually if needed.

## Support

For issues or questions:
1. Check the [Terraform AWS Provider documentation](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
2. Review AWS CloudFront and S3 documentation
3. Check Terraform state in S3 for current infrastructure status

## License

[Your License Here]
