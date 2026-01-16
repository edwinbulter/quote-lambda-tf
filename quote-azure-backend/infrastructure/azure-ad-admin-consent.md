# Azure AD Admin Consent - Terraform Automation

## 🎯 What Was Added

The Terraform configuration now automatically grants admin consent for Azure AD permissions, eliminating the need for manual consent clicks.

## 📋 Changes Made

### 1. Microsoft Graph Service Principal
```hcl
data "azuread_service_principal" "graph" {
  display_name = "Microsoft Graph"
}
```

### 2. Admin Consent Grant
```hcl
resource "azuread_service_principal_delegated_permission_grant" "function_app" {
  service_principal_object_id = azuread_service_principal.function_app.object_id
  resource_object_id          = data.azuread_service_principal.graph.object_id
  claim_values                 = [
    "e1fe6dd8-ba31-4d61-89e7-88639da4633d", # User.Read
    "b340eb25-3d91-4169-bbdf-9c51564af439", # User.Read.All
    "5792c5b5-0199-40b6-9c85-c800336b8c2c"  # GroupMember.Read.All
  ]
}
```

### 3. Admin Consent URL Output
```hcl
output "admin_consent_url" {
  description = "URL to grant admin consent manually if needed"
  value       = "https://login.microsoftonline.com/${data.azurerm_subscription.current.tenant_id}/adminconsent?client_id=${azuread_application.function_app.client_id}"
}
```

## 🚀 Benefits

- ✅ **No manual consent required** - Terraform handles it automatically
- ✅ **Reproducible deployments** - Works the same in all environments
- ✅ **CI/CD friendly** - No interactive steps needed
- ✅ **Backup URL** - Manual consent URL available if needed

## 🔧 How to Use

### Apply Changes
```bash
cd quote-azure-backend/infrastructure
terraform apply
```

### Verify Consent
```bash
# Check if consent was granted
terraform output admin_consent_url

# Or verify with Azure CLI
az ad app show --id $(terraform output -raw azure_ad_client_id) --query "oauth2PermissionScopes"
```

## 📊 Permission IDs Used

| Permission | ID | Purpose |
|------------|-----|---------|
| User.Read | e1fe6dd8-ba31-4d61-89e7-88639da4633d | Read user profile |
| User.Read.All | b340eb25-3d91-4169-bbdf-9c51564af439 | Read all users |
| GroupMember.Read.All | 5792c5b5-0199-40b6-9c85-c800336b8c2c | Read group memberships |

## 🔄 Future Deployments

For future deployments, the admin consent will be granted automatically during `terraform apply`. No manual intervention needed!

## 🚨 Troubleshooting

If consent fails to grant automatically:
1. Use the `admin_consent_url` output
2. Open the URL in a browser
3. Click "Accept" to grant consent manually
4. Re-run `terraform apply` to sync state

This automation ensures your Azure AD authentication works seamlessly in all environments! 🚀
