# Azure Infrastructure Setup

This directory contains the Terraform configuration for deploying the Azure backend infrastructure.

## Prerequisites

- Azure CLI installed and configured
- Terraform installed
- Appropriate Azure permissions

## Setup Steps

### 1. Initialize Terraform
```bash
terraform init
```

### 2. Configure Variables

Copy the template file and fill in your values:
```bash
cp terraform.tfvars.template terraform.tfvars
```

Edit `terraform.tfvars` with your specific values:
- `b2c_client_secret`: Get from `terraform output b2c_client_secret` after initial apply
- `b2c_domain`: Your Azure AD B2C tenant domain
- `b2c_instance`: Your B2C instance URL
- `table_storage_account_name`: Your table storage account name

### 3. Deploy Infrastructure

Initial deployment (creates Azure AD resources):
```bash
terraform apply
```

### 4. Create Azure AD User Groups

⚠️ **Important**: Azure AD authentication is much simpler than B2C

The Terraform configuration automatically creates:
- **ADMIN** group for administrative users
- **USER** group for regular users

No manual setup required! Just assign users to groups in Azure Portal:
1. Go to Azure Portal → Azure AD
2. Navigate to "Groups"
3. Add users to ADMIN or USER groups as needed

### 5. Deploy Application Code

Deploy the updated Azure Functions with authentication:
```bash
cd ../src
dotnet build --configuration Release
# Deploy using your preferred method (GitHub Actions, Azure CLI, etc.)
```

### 6. Test Authentication

Test the authentication endpoints:
```bash
# Get Azure AD token for testing
az account get-access-token --resource <your-client-id> --query accessToken -o tsv

# Test API with token
curl -H "Authorization: Bearer <token>" https://<function-app-url>/api/quotes
```

## Variables

### Required Variables

| Variable | Description | How to Find |
|-----------|-------------|-------------|
| `azure_ad_client_secret` | Azure AD client secret | `terraform output azure_ad_client_secret` |
| `azure_ad_domain` | Azure AD tenant domain | `az ad tenant show --query "defaultDomain"` |
| `azure_ad_instance` | Azure AD instance URL | Standard: `https://login.microsoftonline.com/` |
| `table_storage_account_name` | Table storage account | `az storage account list -g quote-backend-rg` |

### Optional Variables

| Variable | Default | Description |
|-----------|---------|-------------|
| `location` | "Germany West Central" | Azure region |
| `resource_group_name` | "quote-backend-rg" | Resource group name |
| `function_app_name` | "quote-backend-function" | Function app name |

## Security Notes

- `terraform.tfvars` contains sensitive information and is gitignored
- Never commit `terraform.tfvars` to version control
- Use environment variables for CI/CD deployments
- Regularly rotate secrets

## Outputs

After deployment, you can retrieve important values:

```bash
terraform output azure_ad_client_id
terraform output azure_ad_client_secret
terraform output function_app_url
```

## Troubleshooting

### Common Issues

1. **Authentication failures**: Check Azure AD app registration and client secret
2. **Group membership issues**: Verify users are in correct Azure AD groups
3. **Token validation errors**: Ensure correct tenant ID and client ID

### Useful Commands

```bash
# Check Terraform state
terraform state list

# Import existing resources
terraform import azurerm_storage_account.sa /subscriptions/.../resourceGroups/.../providers/Microsoft.Storage/storageAccounts/account-name

# Get B2C tenant info
az ad tenant show
az ad app list --display-name "quote-backend-function-app"
```
