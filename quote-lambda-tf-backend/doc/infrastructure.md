# Quote Lambda Java Infrastructure

This Terraform configuration sets up a serverless architecture for a REST API that manages quotes. The infrastructure includes:

- AWS Lambda function for handling API requests
- API Gateway for HTTP endpoints
- DynamoDB for data storage
- S3 for Terraform state storage (already configured)

## Table of Contents

- [Prerequisites](#prerequisites)
- [Project Structure](#project-structure)
- [Terraform State Management](#terraform-state-management)
- [Deployment](#deployment)
- [API Endpoints](#api-endpoints)
- [Environment Variables](#environment-variables)
- [Important Configuration Notes](#important-configuration-notes)
- [Clean Up](#clean-up)
- [Security](#security)
- [Monitoring](#monitoring)
- [AWS Costs](#aws-costs)

## Prerequisites

1. [Terraform](https://www.terraform.io/downloads.html) >= 1.0.0
2. AWS CLI configured with appropriate credentials
3. Java 21 for building the Lambda function
4. Maven for dependency management

## Project Structure

```
quote-lambda-tf-backend/
├── src/                    # Java source code
├── target/                 # Compiled artifacts (generated)
├── doc/                    # Documentation
│   └── infrastructure.md  # This file
├── infrastructure/         # Terraform configuration
│   ├── backend.tf         # S3 backend configuration (initially commented)
│   ├── bootstrap.tf       # Bootstrap resources (S3 bucket & DynamoDB table)
│   ├── provider.tf        # AWS provider configuration
│   ├── variables.tf       # Input variables
│   ├── lambda.tf          # Lambda function and IAM roles
│   ├── dynamodb.tf        # DynamoDB table and autoscaling
│   ├── api_gateway.tf     # API Gateway configuration
│   └── outputs.tf         # Output values
├── pom.xml                # Maven configuration
└── README.md              # Project README
```

## Terraform State Management

This project uses **remote state storage** in AWS S3 with a **shared bucket architecture** for better collaboration and state management.

> **📚 New Documentation**: This project now uses a shared Terraform state infrastructure. For complete details, see:
> - [Infrastructure README](../infrastructure/README.md) - Quick reference and deployment guide
> - [Migration Guide](../infrastructure/MIGRATION.md) - How to migrate from old bucket structure
> - [Terraform State Architecture](../../TERRAFORM_STATE_ARCHITECTURE.md) - Complete architecture overview

### State Storage Configuration

The Terraform state is stored in:
- **S3 Bucket**: `edwinbulter-terraform-state` (shared across all projects)
- **State File Path**: `quote-lambda-tf-backend/terraform.tfstate` (unique to this project)
- **Region**: `eu-central-1`
- **Encryption**: Enabled (server-side encryption)
- **State Locking**: DynamoDB table `terraform-locks` (shared across all projects)

### Benefits of Remote State

- **Collaboration**: Multiple team members can work on the infrastructure
- **State Locking**: Prevents conflicts when multiple users run Terraform simultaneously
- **Encryption**: State file is encrypted at rest in S3
- **Versioning**: S3 versioning allows rollback to previous states
- **Backup**: State is safely stored in AWS, not on local machines

### Shared Bootstrap Infrastructure

The bootstrap infrastructure (S3 bucket and DynamoDB table) is **shared across all projects** in this repository and only needs to be created once.

#### Bootstrap Location

The bootstrap infrastructure is managed in:
- **Location**: `../quote-lambda-tf-frontend/infrastructure/bootstrap/`
- **Documentation**: See [Bootstrap README](../../quote-lambda-tf-frontend/infrastructure/bootstrap/README.md)

#### Prerequisites

Before deploying this backend infrastructure, ensure the bootstrap infrastructure exists:

```bash
# Check if shared bucket exists
aws s3 ls s3://edwinbulter-terraform-state/

# If not, create it (one-time setup)
cd ../quote-lambda-tf-frontend/infrastructure/bootstrap/
terraform init
terraform apply
```

#### Why Shared Bootstrap?

Using a single S3 bucket for all project states provides:
- ✅ **Cost efficiency**: One bucket instead of multiple
- ✅ **Centralized management**: All state files in one place
- ✅ **Simplified IAM**: Single bucket policy for all projects
- ✅ **Project isolation**: Each project uses a unique key path
- ✅ **Shared locking**: One DynamoDB table handles locking for all projects

See [Bucket Structure](../../quote-lambda-tf-frontend/infrastructure/bootstrap/BUCKET_STRUCTURE.md) for details.

#### Migrating from Old Structure

If you're migrating from the old project-specific bucket (`quote-lambda-tf-backend-terraform-state`), follow the [Migration Guide](../infrastructure/MIGRATION.md).

## Deployment

### 1. Initialize Terraform

```bash
cd infrastructure
terraform init
```

This will configure the S3 backend and download required providers.

### 2. Review the execution plan

```bash
terraform plan
```

### 3. Apply the configuration

```bash
terraform apply
```

### 4. Deploy the Lambda function

Build the Lambda deployment package:

```bash
mvn clean package -DskipTests
```

Upload the JAR file to the Lambda function:

```bash
aws lambda update-function-code \
  --function-name $(terraform output -raw lambda_function_name) \
  --zip-file fileb://target/quote-lambda-java-1.0.0-aws.jar \
  --region $(terraform output -raw aws_region)
```

## API Endpoints

- `GET /quote` - Get a random quote
- `POST /quote` - Get a random quote excluding specific IDs (body: array of IDs to exclude)
- `PATCH /quote/{id}/like` - Like a specific quote
- `GET /quote/liked` - Get all liked quotes sorted by likes

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DYNAMODB_TABLE` | Name of the DynamoDB table | Automatically set |

## Important Configuration Notes

### API Gateway Throttling

The API Gateway stage **must** have explicit throttling limits configured. Without these settings, the default throttling values are set to 0, which results in **429 (Too Many Requests)** errors for every request.

The current configuration sets:
- `throttling_burst_limit = 100` - Maximum number of concurrent requests
- `throttling_rate_limit = 100` - Maximum requests per second

These values can be adjusted in `api_gateway.tf` based on your needs.

## Clean Up

To destroy all resources created by Terraform:

```bash
terraform destroy
```

## Security

- IAM roles with least privilege
- Server-side encryption for DynamoDB
- TLS 1.2 for all API endpoints
- Secure API Gateway with CORS and logging

## Monitoring

- CloudWatch Logs for Lambda and API Gateway
- CloudWatch Metrics for DynamoDB and Lambda
- API Gateway access logs

## AWS Costs

The infrastructure uses AWS services with the following pricing (as of 2024):

### API Gateway
- **$1.20 per million requests**
- HTTP API pricing for REST API calls

### Lambda
- **$0.20 per million requests**
- **Duration costs with 512MB memory**: $0.0000000083 per ms
  - Example: 6000ms execution = $0.00005 per invocation
- Includes 1 million free requests per month (AWS Free Tier)

### DynamoDB
- **$0.7625 per million write requests**
- **$0.1525 per million read requests**
- On-demand pricing model
- Includes 25GB free storage per month (AWS Free Tier)

### S3 (Terraform State Storage)
- **$0.023 per GB per month**
- Standard storage pricing
- Minimal cost for Terraform state files (typically < 1MB)

### Estimated Monthly Cost

For a low-traffic application (example: 10,000 requests/month):
- API Gateway: $0.012
- Lambda: $0.002 + duration costs (~$0.50)
- DynamoDB: ~$0.01
- S3: < $0.01
- **Total: ~$0.52/month**

Most costs are covered by AWS Free Tier for the first 12 months.
