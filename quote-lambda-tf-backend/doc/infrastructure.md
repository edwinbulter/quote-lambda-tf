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

This project uses **remote state storage** in AWS S3 for better collaboration and state management.

### State Storage Configuration

The Terraform state is stored in:
- **S3 Bucket**: `quote-lambda-tf-backend-terraform-state`
- **State File Path**: `quote-lambda-tf-backend/terraform.tfstate`
- **Region**: `eu-central-1`
- **Encryption**: Enabled (server-side encryption)
- **State Locking**: DynamoDB table `terraform-locks` (prevents concurrent modifications)

### Benefits of Remote State

- **Collaboration**: Multiple team members can work on the infrastructure
- **State Locking**: Prevents conflicts when multiple users run Terraform simultaneously
- **Encryption**: State file is encrypted at rest in S3
- **Versioning**: S3 versioning allows rollback to previous states
- **Backup**: State is safely stored in AWS, not on local machines

### Backend Bootstrap Process

The backend configuration requires the S3 bucket and DynamoDB table to exist **before** you can use remote state. This creates a chicken-and-egg problem that we solve using a bootstrap process.

#### Why Bootstrap is Needed

Terraform's backend configuration does not support variables and must reference existing resources. The `backend.tf` file is commented out initially to allow you to create these resources first using local state.

#### Step-by-Step Bootstrap Instructions

**Step 1: Initial Setup (Backend Commented Out)**

The `infrastructure/backend.tf` file comes with the backend block commented out:

```hcl
# terraform {
#   backend "s3" {
#     bucket         = "quote-lambda-tf-backend-terraform-state"
#     key            = "quote-lambda-tf-backend/terraform.tfstate"
#     region         = "eu-central-1"
#     dynamodb_table = "terraform-locks"
#     encrypt        = true
#   }
# }
```

**Step 2: Initialize with Local State**

```bash
cd infrastructure
terraform init
```

This initializes Terraform using **local state** (stored in `terraform.tfstate` file).

**Step 3: Create Bootstrap Resources**

The `bootstrap.tf` file contains resources to create:
- S3 bucket: `quote-lambda-tf-backend-terraform-state`
- DynamoDB table: `terraform-locks`

Apply the configuration:

```bash
terraform apply
```

Review the plan and type `yes` to create:
- S3 bucket with versioning and encryption
- DynamoDB table for state locking

**Step 4: Enable Remote Backend**

After the resources are created, uncomment the backend block in `backend.tf`:

```hcl
terraform {
  backend "s3" {
    bucket         = "quote-lambda-tf-backend-terraform-state"
    key            = "quote-lambda-tf-backend/terraform.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "terraform-locks"
    encrypt        = true
  }
}
```

**Step 5: Migrate State to S3**

```bash
terraform init -migrate-state
```

Terraform will detect the backend configuration change and prompt:

```
Do you want to copy existing state to the new backend?
```

Type `yes` to migrate your local state file to S3.

**Step 6: Verify Migration**

Check that your state is now in S3:

```bash
aws s3 ls s3://quote-lambda-tf-backend-terraform-state/
```

You should see: `quote-lambda-tf-backend/terraform.tfstate`

Your local `terraform.tfstate` file can now be safely deleted.

#### Important Notes

- **Backend configuration does not support variables** - This is a Terraform limitation, not a bug
- **Bootstrap resources have `prevent_destroy = true`** - This prevents accidental deletion of your state storage
- **State locking prevents concurrent modifications** - Multiple users can safely work on the infrastructure
- **S3 versioning enables state rollback** - You can recover from mistakes by restoring previous versions

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
