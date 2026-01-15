# Azure AD B2C Authentication & Admin Endpoints Implementation Plan

## Overview
This document outlines the implementation plan for adding Azure AD B2C authentication to the Azure Functions backend and translating the Java admin endpoints to C#.

## Phase 1: Azure AD B2C Setup & Configuration

### 1.1 Azure AD B2C Tenant Setup
- [ ] Create or configure Azure AD B2C tenant
- [ ] Set up user flows for sign-up/sign-in
- [ ] Configure application registration for the Function App
- [ ] Set up API permissions and scopes

### 1.2 Azure Functions Authentication
- [ ] Configure App Service Authentication in Azure portal
- [ ] Enable Azure AD B2C as authentication provider
- [ ] Configure JWT token validation
- [ ] Set up authentication level to "Require authentication"

### 1.3 User Groups & Roles
- [ ] Create ADMIN and USER groups in Azure AD B2C
- [ ] Configure group membership claims in token
- [ ] Set up group overage handling if needed

## Phase 1.5: Terraform Infrastructure Updates

### 1.5.1 Azure AD B2C Resources
Add the following to your `infrastructure/main.tf`:

```hcl
# Azure AD B2C Tenant (if creating new)
resource "azuread_b2c_directory" "b2c" {
  display_name     = "quote-backend-b2c"
  initial_domain_name = "quotebackendb2c"
  sku_name        = "PremiumP1"
  data_residency_location = "Europe"
  
  tags = {
    environment = "production"
    project     = "quote-backend"
  }
}

# Azure AD B2C Application Registration
resource "azuread_application" "function_app" {
  display_name = "quote-backend-function"
  sign_in_audience = "AzureADandPersonalMicrosoftAccount"
  
  required_resource_access {
    resource_app_id = "00000003-0000-0000-c000-000000000000" # Microsoft Graph
    
    resource_access {
      id   = "e1fe6dd8-ba31-4d61-89e7-88639da4683d" # Sign in and read user profile
      type = "Scope"
    }
    
    resource_access {
      id   = "06da0dbc-49e2-44d2-8312-53f166af8da9" # User.Read.All
      type = "Role"
    }
    
    resource_access {
      id   = "7ab1d382-f21e-4bb2-b344-4617ed41c552" # User.ReadWrite.All
      type = "Role"
    }
    
    resource_access {
      id   = "5f8c59db-670d-4384-8ab4-5e86df9ab0b1" # GroupMember.ReadWrite.All
      type = "Role"
    }
  }
  
  web {
    redirect_uris = [
      "https://quote-backend-function.azurewebsites.net/.auth/login/aad/callback"
    ]
    
    implicit_grant {
      access_token_issuance_enabled = true
      id_token_issuance_enabled     = true
    }
  }
  
  optional_claims {
    id_token {
      name                  = "groups"
      essential             = false
      additional_properties = []
    }
  }
}

# Azure AD B2C Service Principal
resource "azuread_service_principal" "function_app" {
  application_id = azuread_application.function_app.application_id
  
  tags = ["quote-backend"]
}

# Azure AD B2C User Groups
resource "azuread_group" "admin" {
  display_name     = "ADMIN"
  security_enabled = true
  members          = []
}

resource "azuread_group" "user" {
  display_name     = "USER"
  security_enabled = true
  members          = []
}

# B2C User Flow (Sign-up/Sign-in)
resource "azuread_b2c_user_flow" "sign_up_sign_in" {
  user_flow_name = "B2C_1_sign-up-sign-in"
  display_name   = "Sign up and sign in"
  
  identity_providers {
    oidc {
      issuer_endpoint = "https://login.microsoftonline.com/organizations/v2.0"
      client_id      = azuread_application.function_app.application_id
    }
  }
  
  api_connectors {
    name = "UserAttributes"
  }
}

# Update Function App with B2C Authentication
resource "azurerm_function_app" "function_app" {
  # ... existing configuration ...
  
  auth_settings {
    enabled               = true
    unauthenticated_client_action = "RedirectToLoginPage"
    
    active_directory {
      client_id         = azuread_application.function_app.application_id
      client_secret     = azuread_application_password.function_app.value
      allowed_audiences = [azuread_application.function_app.application_id]
    }
    
    additional_login_params = {
      response_type = "code"
      scope         = "openid profile offline_access"
    }
  }
  
  app_settings = {
    # ... existing app settings ...
    
    # Azure AD B2C Configuration
    "AzureAdB2C:Instance"        = "https://${azuread_b2c_directory.b2c.initial_domain_name}.b2clogin.com/"
    "AzureAdB2C:Domain"          = "${azuread_b2c_directory.b2c.initial_domain_name}.b2clogin.com"
    "AzureAdB2C:ClientId"        = azuread_application.function_app.application_id
    "AzureAdB2C:SignedOutCallbackPath" = "/signout/B2C"
    "AzureAdB2C:SignUpSignInPolicyId" = "B2C_1_sign-up-sign-in"
    
    # Microsoft Graph Configuration
    "MicrosoftGraph:BaseUrl"     = "https://graph.microsoft.com/v1.0"
    "MicrosoftGraph:Scopes"      = "User.Read.All GroupMember.ReadWrite.All User.ReadWrite.All"
  }
}

# Client Secret for B2C Application
resource "azuread_application_password" "function_app" {
  application_object_id = azuread_application.function_app.object_id
  display_name          = "quote-backend-function-secret"
  
  end_date_relative = "2 years"
}
```

### 1.5.2 Required Terraform Providers
Add to your `infrastructure/providers.tf`:

```hcl
terraform {
  required_providers {
    # ... existing providers ...
    
    azuread = {
      source  = "hashicorp/azuread"
      version = "~> 2.40"
    }
  }
}

provider "azuread" {
  tenant_id = var.tenant_id
}
```

### 1.5.3 Variables to Add
Add to your `infrastructure/variables.tf`:

```hcl
variable "tenant_id" {
  description = "The Azure AD tenant ID"
  type        = string
}
```

## Phase 2: Authentication Middleware Implementation

### 2.1 Create Authentication Models
```csharp
// Models/Authentication/
- UserInfo.cs
- AuthenticatedUser.cs
- GroupMembership.cs
```

### 2.2 Create Authentication Service
```csharp
// Services/
- IAuthenticationService.cs
- AuthenticationService.cs
```

### 2.3 Create Authentication Middleware
```csharp
// Middleware/
- AuthenticationMiddleware.cs
- AuthorizationMiddleware.cs
```

### 2.4 Update Program.cs
- [ ] Register authentication services
- [ ] Add middleware pipeline
- [ ] Configure JWT validation

## Phase 3: Admin Endpoints Implementation

### 3.1 Create Admin Models
```csharp
// Models/Admin/
- UserInfo.cs (translated from Java)
- QuoteWithLikeCount.cs
- QuotePageResponse.cs
- QuoteAddResponse.cs
- AdminAuditLog.cs
```

### 3.2 Create Admin Service (C# translation)
```csharp
// Services/
- IAdminService.cs
- AdminService.cs (translated from Java AdminService.java)
```

### 3.3 Create Admin Handler
```csharp
// Handlers/
- AdminHandler.cs (translated from Java QuoteHandler admin methods)
```

### 3.4 Admin Endpoints to Implement
1. **GET /api/admin/users** - List all users
2. **POST /api/admin/users/{username}/groups/{groupName}** - Add user to group
3. **DELETE /api/admin/users/{username}/groups/{groupName}** - Remove user from group
4. **DELETE /api/admin/users/{username}** - Delete user
5. **GET /api/admin/quotes** - Paginated quotes with filtering
6. **POST /api/admin/quotes/fetch** - Fetch new quotes from Zen API
7. **GET /api/admin/likes/total** - Get total likes count

## Phase 4: Azure AD B2C Integration Details

### 4.1 Token Validation
```csharp
// Validate JWT token from Azure AD B2C
- Issuer validation
- Audience validation
- Signature validation
- Claims extraction
```

### 4.2 Group-based Authorization
```csharp
// Check cognito:groups claim (translated to Azure AD groups)
- Extract groups from token
- Validate ADMIN group membership
- Implement self-protection (prevent self-deletion/group removal)
```

### 4.3 User Management Integration
```csharp
// Replace AWS Cognito with Azure AD B2C Graph API
- List users via Microsoft Graph API
- Add/remove users from groups via Graph API
- Delete users via Graph API
```

## Phase 5: Implementation Steps

### Step 1: Setup Azure AD B2C
1. Create B2C tenant if not exists
2. Register Function App as B2C application
3. Create user flows (sign-up/sign-in)
4. Configure App Service Authentication

### Step 2: Implement Authentication Layer
1. Create authentication models
2. Implement authentication service
3. Add JWT validation middleware
4. Test authentication flow

### Step 3: Translate AdminService from Java to C#
1. Create IAdminService interface
2. Implement AdminService with Azure AD Graph API
3. Add audit logging functionality
4. Handle group validation

### Step 4: Create AdminHandler
1. Translate admin endpoint handlers from Java
2. Implement pagination for quotes endpoint
3. Add proper error handling
4. Implement audit logging

### Step 5: Update Existing QuoteHandler
1. Add authentication checks to existing endpoints
2. Update to use new authentication service
3. Maintain backward compatibility where needed

## Phase 6: Configuration & Deployment

### 6.1 Application Settings
```json
{
  "AzureAdB2C": {
    "Instance": "https://<tenant-name>.b2clogin.com/",
    "Domain": "<tenant-name>.b2clogin.com",
    "ClientId": "<client-id>",
    "SignedOutCallbackPath": "/signout/B2C",
    "SignUpSignInPolicyId": "B2C_1_sign-up-sign-in",
    "ResetPasswordPolicyId": "B2C_1_reset",
    "EditProfilePolicyId": "B2C_1_edit"
  },
  "MicrosoftGraph": {
    "BaseUrl": "https://graph.microsoft.com/v1.0",
    "Scopes": ["User.Read.All", "GroupMember.ReadWrite.All", "User.ReadWrite.All"]
  }
}
```

### 6.2 Dependencies to Add
```xml
<PackageReference Include="Microsoft.Identity.Web" Version="2.16.0" />
<PackageReference Include="Microsoft.Identity.Web.GraphServiceClient" Version="2.16.0" />
<PackageReference Include="Microsoft.Graph" Version="5.0.0" />
```

## Phase 7: Testing & Validation

### 7.1 Authentication Tests
- [ ] Test JWT token validation
- [ ] Test group-based authorization
- [ ] Test unauthorized access blocking

### 7.2 Admin Endpoint Tests
- [ ] Test user listing
- [ ] Test group management
- [ ] Test user deletion with protection
- [ ] Test quote pagination
- [ ] Test quote fetching

### 7.3 Integration Tests
- [ ] End-to-end admin flow
- [ ] Audit logging verification
- [ ] Performance testing for large user lists

## Phase 8: Security Considerations

### 8.1 Authorization
- Validate ADMIN group on all admin endpoints
- Implement self-protection (cannot delete self or remove own admin rights)
- Log all admin actions with audit trail

### 8.2 Rate Limiting
- Implement rate limiting for admin endpoints
- Add request throttling for expensive operations

### 8.3 Data Protection
- Sanitize all inputs
- Validate pagination limits
- Implement proper error messages (no sensitive data leakage)

## Phase 9: Migration Strategy

### 9.1 Gradual Migration
1. Deploy authentication layer first (optional mode)
2. Enable authentication for admin endpoints only
3. Gradually enable for all endpoints
4. Remove old authentication method if any

### 9.2 Backward Compatibility
- Maintain existing endpoint structure
- Add authentication headers gradually
- Provide migration guide for frontend

## Implementation Timeline

| Phase | Duration | Dependencies |
|-------|----------|--------------|
| Phase 1: Azure AD B2C Setup | 1-2 days | Azure access |
| Phase 1.5: Terraform Updates | 1 day | Phase 1 complete |
| Phase 2: Auth Middleware | 2-3 days | Phase 1 complete |
| Phase 3: Admin Models | 1 day | Phase 2 complete |
| Phase 4: Admin Service | 3-4 days | Phase 3 complete |
| Phase 5: Admin Handler | 2-3 days | Phase 4 complete |
| Phase 6: Config & Deploy | 1-2 days | Phase 5 complete |
| Phase 7: Testing | 2-3 days | Phase 6 complete |
| **Total** | **13-19 days** | |

## Notes

1. **Azure AD B2C vs AWS Cognito**: The Java implementation uses AWS Cognito. We'll translate this to use Azure AD B2C with Microsoft Graph API for user management.

2. **Group Claims**: Azure AD B2C can return group membership in JWT tokens. Configure this to match the existing "cognito:groups" claim structure for minimal frontend changes.

3. **Audit Logging**: Maintain the same audit logging structure as the Java implementation for compliance.

4. **Error Handling**: Ensure error responses match the existing format to minimize frontend changes.

5. **Testing**: Create comprehensive unit tests for all admin operations, especially those involving user management.

6. **Terraform**: The infrastructure code will provision all necessary Azure AD B2C resources including tenant, application, groups, and user flows.

## Next Steps

1. Review and approve this plan
2. Update Terraform code with B2C resources
3. Run `terraform apply` to provision B2C infrastructure
4. Begin Phase 1 implementation
5. Create separate implementation plans for each phase as needed
