# Terraform Backend Setup Guide

This guide explains how to set up remote Terraform backend storage for the quote-azure-backend project using the existing storage account.

**Note**: This is part of the overall Azure infrastructure setup. See the main [README.md](./README.md) for complete deployment instructions.

## Table of Contents

- [Security Notice](#-security-notice)
- [Quick Start](#quick-start)
- [Finding Your Azure Values](#finding-your-azure-values)
- [Where to Store backend.tf](#where-to-store-backendtf)
- [Detailed Steps](#detailed-steps)
- [Architecture](#architecture)
- [Team Access](#team-access)
- [Benefits of Using Existing Storage](#benefits-of-using-existing-storage)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)
- [Cost Management](#cost-management)
- [Next Steps](#next-steps)

## ⚠️ Security Notice

The `backend.tf` file contains sensitive information (subscription ID, storage account details) and should **NEVER** be committed to version control. This file is already included in `.gitignore`.

## Quick Start

### 1. Create the Backend Container

Run the setup script to create the container:
```bash
cd infrastructure
./setup-backend.sh
```

### 2. Create Your Backend Configuration

Create a local `backend.tf` file (this file is ignored by git):

```hcl
# Terraform Backend Configuration
terraform {
  backend "azurerm" {
    resource_group_name  = "quote-backend-rg"
    storage_account_name = "YOUR_STORAGE_ACCOUNT_NAME"
    container_name       = "terraform-state"
    key                  = "quote-azure-backend.tfstate"
    use_azuread_auth     = true
    subscription_id      = "YOUR_SUBSCRIPTION_ID"
    tenant_id            = "YOUR_TENANT_ID"
  }
}
```

### 3. Initialize Terraform

```bash
terraform init -migrate-state
```

## Finding Your Azure Values

### Storage Account Name
The storage account is the one already used for table storage. To find it:
```bash
# List storage accounts in the resource group
az storage account list --resource-group quote-backend-rg --query "[].name" -o tsv

# Or find the one with table services
az storage account list --resource-group quote-backend-rg --query "[?supportsTable==true].name" -o tsv
```

### Subscription ID
```bash
az account show --query id -o tsv
```

### Tenant ID
```bash
az account show --query tenantId -o tsv
```

## Where to Store backend.tf

### Option 1: Local Only (Recommended)
- Create `backend.tf` in the infrastructure directory
- It's already in `.gitignore` so it won't be committed
- Each team member creates their own

### Option 2: Secure Storage
- Store in a password manager
- Keep in encrypted USB drive
- Use your organization's secret management system

### Option 3: Environment Variables (Advanced)
You can use a script to generate backend.tf from environment variables:
```bash
# create-backend.sh
cat > backend.tf << EOF
terraform {
  backend "azurerm" {
    resource_group_name  = "$RG_NAME"
    storage_account_name = "$STORAGE_ACCOUNT"
    container_name       = "terraform-state"
    key                  = "quote-azure-backend.tfstate"
    use_azuread_auth     = true
    subscription_id      = "$SUBSCRIPTION_ID"
    tenant_id            = "$TENANT_ID"
  }
}
EOF
```

## Detailed Steps

### 1. Create Container in Existing Storage

The setup script will:
- Automatically find the storage account with table services
- Create a new blob container `terraform-state`
- Enable versioning and soft delete
- Configure private access

### 2. Configure Backend

Create `backend.tf` with your specific values:
- `resource_group_name`: The resource group name
- `storage_account_name`: The storage account name (find it with the commands above)
- `container_name`: `terraform-state`
- `subscription_id`: Your Azure subscription ID
- `tenant_id`: Your Azure AD tenant ID

### 3. Migrate State

Initialize Terraform with backend migration:
```bash
terraform init -migrate-state
```

This will:
- Create a new backend configuration
- Migrate existing local state to Azure Storage
- Update the `.terraform/terraform.tfstate` file

### 4. Verify

Check that state is now in Azure:
```bash
terraform state list
```

## Architecture

```
Existing Storage Account (found via setup script)
├── Tables (existing)
│   ├── quotes
│   ├── userlikes
│   ├── userprogress
│   └── userroles
└── Blob Containers
    ├── terraform-state (new) ← Terraform state files
    └── [other containers]
```

## Team Access

To grant access to team members:

### Using Azure CLI
```bash
# Get the storage account ID (replace with your actual storage account name)
STORAGE_ID=$(az storage account show -n <STORAGE_ACCOUNT_NAME> -g quote-backend-rg --query id -o tsv)

# Grant access to a user
az role assignment create --assignee <USER_OBJECT_ID> --role "Storage Blob Data Contributor" --scope $STORAGE_ID

# Grant access to a service principal
az role assignment create --assignee <SP_OBJECT_ID> --role "Storage Blob Data Contributor" --scope $STORAGE_ID
```

### Using Azure Portal
1. Navigate to the storage account (find it in the quote-backend-rg resource group)
2. Go to Access Control (IAM)
3. Add role assignment
4. Select "Storage Blob Data Contributor" role
5. Add the user or service principal

## Benefits of Using Existing Storage

1. **Cost Efficiency**: No additional storage account costs
2. **Simplicity**: One less resource to manage
3. **Consistency**: All infrastructure data in one place
4. **Security**: Reuse existing access controls

## Best Practices

### 1. State Management
- Always run `terraform plan` before applying
- Use `terraform workspace` for multiple environments
- Review state changes carefully

### 2. Security
- Never commit `backend.tf` to version control
- Use Azure AD authentication (configured)
- Limit who has access to the state files
- Regularly review access permissions

### 3. Backup and Recovery
- State versioning is enabled automatically
- Soft delete allows recovery for 30 days
- Consider manual backups before major changes

## Troubleshooting

### State Lock Issues
If state gets locked and you're sure no one else is using it:
```bash
terraform force-unlock <LOCK_ID>
```

### Backend Configuration Errors
1. Ensure the container exists in the storage account
2. Check your Azure AD permissions
3. Verify the subscription and tenant IDs are correct

### Migration Issues
If migration fails:
1. Check your local state file exists
2. Verify Azure credentials
3. Try running with `-reconfigure` flag:
```bash
terraform init -reconfigure -migrate-state
```

## Cost Management

Since we're using the existing storage account:
- Additional storage cost: ~$0.018 per GB/month
- Operation cost: ~$4.35 per 100,000 operations
- Expected total additional cost: $1-2/month

Monitor costs in the Azure Portal under the storage account.

## Next Steps

1. Set up CI/CD pipeline integration
2. Configure remote operations for team collaboration
3. Set up monitoring and alerts for storage operations
4. Document backend configuration for your team
