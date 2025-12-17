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

### Select Workspace

Terraform workspaces allow you to manage multiple environments (dev, prod) with the same configuration:

```bash
# List available workspaces
terraform workspace list

# Create a new workspace
terraform workspace new dev

# Switch to a workspace
terraform workspace select dev
```

### Deploy Infrastructure

Deploy using environment-specific variables file:

```bash
# For development environment
terraform apply -var-file="dev.tfvars"

# For production environment
terraform apply -var-file="prod.tfvars"
```

Or with auto-approval (use with caution):

```bash
terraform apply -var-file="dev.tfvars" -auto-approve
```

### Variables Files

Environment-specific `.tfvars` files are required for each environment. Example files are provided:

1. **Copy the example files:**
   ```bash
   cp dev.tfvars.example dev.tfvars
   cp prod.tfvars.example prod.tfvars
   ```

2. **Update the values in each file:**
   - Open `dev.tfvars` and set the values as described in `dev.tfvars.example`
   - Open `prod.tfvars` and set the values as described in `prod.tfvars.example`

3. **Typical variables to configure:**
   - `aws_region` - AWS region for deployment
   - `lambda_function_name` - Name of the Lambda function
   - `dynamodb_table_name` - Name of the DynamoDB table
   - `environment` - Environment name (dev or prod)

See `dev.tfvars.example` and `prod.tfvars.example` for complete configuration options and descriptions.

### Outputs

After deployment, you'll see:
- `api_gateway_url`: API Gateway endpoint URL
- `lambda_function_name`: Name of the Lambda function
- `dynamodb_table_name`: Name of the DynamoDB table

## Destroying the Application

To destroy the backend infrastructure while preserving the Terraform state:

```bash
# Destroy resources in the current workspace
terraform destroy -var-file="dev.tfvars"
```

Or with auto-approval (use with caution):

```bash
terraform destroy -var-file="dev.tfvars" -auto-approve
```

This removes all backend resources but keeps the state in S3, allowing you to recreate the infrastructure later with `terraform apply`.

**Warning**: Always specify the correct `.tfvars` file to ensure you're destroying the right environment.

## State Management

### Remote State

- **State Storage**: S3 bucket `edwinbulter-terraform-state` (shared across all projects)
- **State Locking**: DynamoDB table `terraform-locks` (shared across all projects)
- **State Key**: `quote-lambda-tf-backend/terraform.tfstate` (unique to this project)

The state infrastructure is managed separately in `../quote-lambda-tf-frontend/infrastructure/bootstrap/` and is shared across all your Terraform projects.

### Workspaces and State

Each workspace maintains its own state file in S3:

```
s3://edwinbulter-terraform-state/
├── quote-lambda-tf-backend/
│   ├── terraform.tfstate (default workspace)
│   ├── env:/dev/terraform.tfstate
│   └── env:/prod/terraform.tfstate
```

**View current workspace state:**
```bash
terraform state list
terraform state show <resource_name>
```

**Switch between workspaces:**
```bash
terraform workspace select dev
terraform state list  # Shows state for dev workspace
```

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

## Best Practices

### Workspace Management

1. **Always select the correct workspace before applying changes:**
   ```bash
   terraform workspace select dev
   terraform apply -var-file="dev.tfvars"
   ```

2. **Verify workspace before destroying:**
   ```bash
   terraform workspace show  # Confirm current workspace
   terraform destroy -var-file="dev.tfvars" -auto-approve
   ```

3. **Use consistent naming:**
   - Workspace names: `dev`, `prod`
   - Variable files: `dev.tfvars`, `prod.tfvars`
   - Resource names: Include environment suffix (e.g., `quotes-lambda-java-dev`)

### Variables Files

1. **Copy example files to create your environment-specific files:**
   ```bash
   cp dev.tfvars.example dev.tfvars
   cp prod.tfvars.example prod.tfvars
   ```

2. **Edit the copied files with your environment-specific values:**
   - Follow the descriptions and comments in the `.example` files
   - Update resource names, regions, and other configuration as needed

3. **Keep `.tfvars` files out of version control:**
   ```
   *.tfvars
   !*.tfvars.example
   ```
   The `.example` files are committed to document the required variables and their descriptions.

### Common Commands

```bash
# Initialize and select workspace
terraform init
terraform workspace new dev
terraform workspace select dev

# Plan changes
terraform plan -var-file="dev.tfvars"

# Apply changes
terraform apply -var-file="dev.tfvars"

# Destroy resources
terraform destroy -var-file="dev.tfvars"

# View current state
terraform state list
terraform state show aws_lambda_function.quote_handler

# Switch between workspaces
terraform workspace select prod
terraform plan -var-file="prod.tfvars"
```

## Related Documentation

- [Infrastructure Details](../doc/infrastructure.md)
- [GitHub Workflows](../doc/github-workflows.md)
- [SnapStart Setup](../doc/snapstart-setup.md)
- [Shared State Architecture](../../doc/terraform-state-architecture.md)
