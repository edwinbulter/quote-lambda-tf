# Project Scripts

This directory contains utility scripts for managing the Quote Lambda TF project.

## Table of Contents

- [Available Scripts](#available-scripts)
  - [setup-workspaces.sh](#setup-workspacessh)
  - [generate-frontend-config.sh](#generate-frontend-configsh)

## Available Scripts

### `setup-workspaces.sh`

Interactive script for setting up Terraform workspaces for multi-environment deployments (development and production).

**Purpose:** Automates the initial setup of separate development and production environments using Terraform workspaces, ensuring proper isolation and configuration.

#### Usage

```bash
# From the repository root
./scripts/setup-workspaces.sh
```

#### What It Does

1. Reinitializes Terraform backends with workspace support
2. Verifies your production environment (default workspace)
3. Creates a development workspace
4. Deploys development infrastructure for both backend and frontend
5. Saves development URLs to files for easy reference

The script is interactive and will ask for confirmation before making changes.

#### Prerequisites

- Terraform installed and configured
- AWS credentials configured
- Production infrastructure already deployed (or ready to verify)
- Run from the repository root directory

#### Output Files

- `dev-api-url.txt` - Development API Gateway URL
- `dev-cloudfront-url.txt` - Development CloudFront URL

#### Documentation

For detailed information about multi-environment setup and workspace management, see:
- [Multi-Environment Setup Guide](../doc/multi-environment-setup.md)

---

### `generate-frontend-config.sh`

Automatically generates frontend environment configuration files from Terraform outputs.

**Purpose:** Keeps your frontend configuration in sync with your deployed infrastructure, eliminating manual copy-paste errors and ensuring consistency between environments.

#### Usage

```bash
# Generate development environment config
./scripts/generate-frontend-config.sh dev

# Generate production environment config
./scripts/generate-frontend-config.sh prod
```

#### What It Does

1. Selects the appropriate Terraform workspace (`dev` or `default` for prod)
2. Retrieves outputs from Terraform:
   - User Pool ID
   - User Pool Client ID
   - Cognito Domain
   - API Gateway URL
   - AWS Region
3. Generates `.env.development` or `.env.production` in the frontend directory
4. Displays the configuration for verification

#### Prerequisites

- Terraform infrastructure must be deployed (`terraform apply` completed)
- You must be in the project root directory
- Terraform must be initialized in the backend infrastructure directory

#### Output

The script creates environment files in `quote-lambda-tf-frontend/`:

**`.env.development`** (for dev environment):
```bash
VITE_AWS_REGION=eu-central-1
VITE_COGNITO_USER_POOL_ID=eu-central-1_XrKxJWy5u
VITE_COGNITO_CLIENT_ID=7lkohh6t96igkm9q16rdchansh
VITE_COGNITO_DOMAIN=quote-lambda-tf-backend-dev.auth.eu-central-1.amazoncognito.com
VITE_API_URL=https://sy5vvqbh93.execute-api.eu-central-1.amazonaws.com
```

**`.env.production`** (for prod environment):
```bash
VITE_AWS_REGION=eu-central-1
VITE_COGNITO_USER_POOL_ID=eu-central-1_ABC123XYZ
VITE_COGNITO_CLIENT_ID=abc123xyz456
VITE_COGNITO_DOMAIN=quote-lambda-tf-backend.auth.eu-central-1.amazoncognito.com
VITE_API_URL=https://prod123abc.execute-api.eu-central-1.amazonaws.com
```

#### Security Notes

- ✅ Generated `.env` files are excluded from version control via `.gitignore`
- ✅ Example files (`.env.*.example`) are safe to commit
- ✅ The script adds a warning header to generated files
- ⚠️ Never commit actual `.env.development` or `.env.production` files

#### Workflow Integration

**After deploying infrastructure:**
```bash
cd quote-lambda-tf-backend/infrastructure
terraform workspace select dev
terraform apply

# Generate frontend config
cd ../..
./scripts/generate-frontend-config.sh dev
```

**Before deploying frontend:**
```bash
# Ensure config is up to date
./scripts/generate-frontend-config.sh dev

# Run frontend
cd quote-lambda-tf-frontend
npm run dev
```

**In CI/CD pipelines:**
```yaml
# Example GitHub Actions workflow
- name: Generate frontend config
  run: ./scripts/generate-frontend-config.sh prod

- name: Build frontend
  run: |
    cd quote-lambda-tf-frontend
    npm run build
```

#### Troubleshooting

**Error: "No Terraform state found"**
- Solution: Run `terraform apply` in the backend infrastructure directory first

**Error: "Failed to retrieve all required Terraform outputs"**
- Solution: Ensure all resources are created in Terraform
- Check that you're in the correct workspace

**Error: "Environment must be 'dev' or 'prod'"**
- Solution: Use either `dev` or `prod` as the argument

#### Manual Alternative

If you prefer not to use the script, you can manually create the `.env` files:

1. Get Terraform outputs:
   ```bash
   cd quote-lambda-tf-backend/infrastructure
   terraform workspace select dev
   terraform output
   ```

2. Copy the example file:
   ```bash
   cd ../../quote-lambda-tf-frontend
   cp .env.development.example .env.development
   ```

3. Fill in the values from step 1

## Future Scripts

Additional scripts may be added here for:
- Database migrations
- Deployment automation
- Testing utilities
- Infrastructure validation
