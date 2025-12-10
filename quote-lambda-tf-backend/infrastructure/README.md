# Backend Infrastructure

This directory contains the Terraform configuration for the **backend application infrastructure** - Lambda function, API Gateway, and DynamoDB table for the Quote API.

## Architecture

- **Lambda Function**: Serverless Java 21 function with SnapStart enabled
- **API Gateway**: HTTP API for REST endpoints
- **DynamoDB Table**: NoSQL database for storing quotes
- **IAM Roles**: Least-privilege execution roles for Lambda

## Prerequisites

Before deploying this infrastructure, ensure the bootstrap infrastructure exists:

The bootstrap infrastructure (S3 bucket and DynamoDB table for Terraform state) is **shared across all projects** and only needs to be created once.

If not already created:
```bash
cd ../quote-lambda-tf-frontend/infrastructure/bootstrap/
terraform init
terraform apply
cd ../../../quote-lambda-tf-backend/infrastructure/
```

See [../../quote-lambda-tf-frontend/infrastructure/bootstrap/README.md](../../quote-lambda-tf-frontend/infrastructure/bootstrap/README.md) for details.

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
- `api_gateway_url`: API Gateway endpoint URL
- `lambda_function_name`: Name of the Lambda function
- `dynamodb_table_name`: Name of the DynamoDB table

## Destroying the Application

To destroy the backend infrastructure while preserving the Terraform state:

```bash
terraform destroy
```

This removes all backend resources but keeps the state in S3, allowing you to recreate the infrastructure later with `terraform apply`.

## State Management

- **State Storage**: S3 bucket `edwinbulter-terraform-state` (shared across all projects)
- **State Locking**: DynamoDB table `terraform-locks` (shared across all projects)
- **State Key**: `quote-lambda-tf-backend/terraform.tfstate` (unique to this project)

The state infrastructure is managed separately in `../quote-lambda-tf-frontend/infrastructure/bootstrap/` and is shared across all your Terraform projects.

## Configuration

### Variables

- `aws_region`: AWS region (default: `eu-central-1`)
- `lambda_function_name`: Name of the Lambda function (default: `quotes-lambda-java`)
- `dynamodb_table_name`: Name of the DynamoDB table (default: `quotes`)

### Provider

- AWS provider configured with profile `edwinbulter`
- Region: `eu-central-1`

## Files

- `provider.tf`: Terraform and AWS provider configuration
- `backend.tf`: S3 backend configuration for remote state
- `lambda.tf`: Lambda function with SnapStart
- `api_gateway.tf`: API Gateway HTTP API
- `dynamodb.tf`: DynamoDB table for quotes
- `variables.tf`: Input variables
- `outputs.tf`: Output values
- `bootstrap.tf`: Deprecated (see shared bootstrap in frontend project)

## Related Documentation

- [Infrastructure Details](../doc/infrastructure.md)
- [GitHub Workflows](../doc/github-workflows.md)
- [SnapStart Setup](../doc/snapstart-setup.md)
- [Shared State Architecture](../../doc/TERRAFORM_STATE_ARCHITECTURE.md)
