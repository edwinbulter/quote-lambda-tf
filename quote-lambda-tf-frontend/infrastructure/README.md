# Frontend Infrastructure

This directory contains the Terraform configuration for the **application infrastructure** - the S3 bucket and CloudFront distribution for hosting the React frontend.

## Architecture

- **S3 Bucket**: Stores the static website files
- **CloudFront Distribution**: CDN for global content delivery
- **Origin Access Identity**: Secures S3 bucket access (only CloudFront can read)

## Prerequisites

Before deploying this infrastructure, you must first set up the bootstrap infrastructure:

```bash
cd bootstrap/
terraform init
terraform apply
cd ../
```

See [bootstrap/README.md](bootstrap/README.md) for details.

## Deployment

### Initialize Terraform

```bash
terraform init
```

This will configure the S3 backend for remote state storage.

### Deploy Infrastructure

```bash
terraform apply
```

### Outputs

After deployment, you'll see:
- `s3_bucket_name`: Name of the S3 bucket hosting the website
- `cloudfront_url`: CloudFront distribution URL for accessing the website

## Destroying the Application

To destroy the application infrastructure while preserving the Terraform state:

```bash
terraform destroy
```

This removes all application resources but keeps the state in S3, allowing you to recreate the infrastructure later with `terraform apply`.

## State Management

- **State Storage**: S3 bucket `edwinbulter-terraform-state` (shared across all projects)
- **State Locking**: DynamoDB table `terraform-locks` (shared across all projects)
- **State Key**: `quote-lambda-tf-frontend/terraform.tfstate` (unique per project)

The state infrastructure is managed separately in the `bootstrap/` directory and is shared across all your Terraform projects.

## Configuration

### Variables

- `aws_region`: AWS region (default: `eu-central-1`)
- `bucket_name`: S3 bucket name for the website (default: `quote-lambda-tf-frontend`)

### Provider

- AWS provider configured with profile `edwinbulter`
- Region: `eu-central-1`

## Files

- `provider.tf`: Terraform and AWS provider configuration
- `backend.tf`: S3 backend configuration for remote state
- `main.tf`: Main application resources (S3, CloudFront)
- `variables.tf`: Input variables
- `outputs.tf`: Output values
- `bootstrap.tf`: Deprecated (see `bootstrap/` directory)
