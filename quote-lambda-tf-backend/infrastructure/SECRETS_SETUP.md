# Secrets Management Setup

This document explains how to manage sensitive credentials (like GitHub OAuth secrets) for your Terraform infrastructure.

## Overview

We use environment-specific `.tfvars` files to store sensitive credentials. These files are:
- ✅ **NOT committed to version control** (excluded in `.gitignore`)
- ✅ **Environment-specific** (separate files for dev and prod)
- ✅ **Required** (GitHub OAuth credentials must be provided)

## Setup Instructions

### Step 1: Create Your `.tfvars` File

Copy the example file for your environment:

**For Development:**
```bash
cd quote-lambda-tf-backend/infrastructure
cp dev.tfvars.example dev.tfvars
```

**For Production:**
```bash
cd quote-lambda-tf-backend/infrastructure
cp prod.tfvars.example prod.tfvars
```

### Step 2: Get GitHub OAuth Credentials (Required)

You must create a GitHub OAuth App to enable authentication:

1. Go to [GitHub Developer Settings](https://github.com/settings/developers)
2. Click **"New OAuth App"**
3. Fill in the details:
   - **Application name**: `Quote App - Development` (or Production)
   - **Homepage URL**: `http://localhost:5173` (dev) or your production URL
   - **Authorization callback URL**: See below
4. Copy the **Client ID** and **Client Secret**

#### Getting the Callback URL

The callback URL depends on your Cognito domain. Since you need credentials before applying Terraform, use a placeholder first:

**Initial setup:** Use `https://placeholder.auth.eu-central-1.amazoncognito.com/oauth2/idpresponse`

**After first apply:**
```bash
# Get the actual Cognito domain
terraform workspace select dev
terraform output cognito_domain

# Update your GitHub OAuth App with the real callback URL:
# https://<cognito-domain>/oauth2/idpresponse
```

Example:
```
https://quote-lambda-tf-backend-dev.auth.eu-central-1.amazoncognito.com/oauth2/idpresponse
```

### Step 3: Edit Your `.tfvars` File

Open `dev.tfvars` (or `prod.tfvars`) and add your credentials:

```hcl
github_oauth_client_id     = "Iv1.abc123def456"
github_oauth_client_secret = "abc123def456ghi789jkl012mno345pqr678stu"
```

**Important:** Both values are required. Terraform will fail if these are not provided.

### Step 4: Apply Terraform with Your Variables

```bash
# For development
terraform workspace select dev
terraform plan -var-file="dev.tfvars"
terraform apply -var-file="dev.tfvars"

# For production
terraform workspace select default
terraform plan -var-file="prod.tfvars"
terraform apply -var-file="prod.tfvars"
```

## Security Best Practices

### ✅ DO:
- Keep separate OAuth apps for dev and prod
- Use different credentials for each environment
- Add `*.tfvars` to `.gitignore` (already done)
- Store production secrets in a password manager
- Rotate secrets regularly

### ❌ DON'T:
- Commit `.tfvars` files to version control
- Share credentials via email or chat
- Use the same OAuth app for dev and prod
- Hardcode secrets in `.tf` files

## Troubleshooting

### "Error: Missing required variable"

If you see this error, you forgot to pass the `-var-file` flag:
```bash
terraform apply -var-file="dev.tfvars"
```

### "GitHub OAuth not working"

1. Check that your callback URL in GitHub matches exactly
2. Verify your credentials are correct in the `.tfvars` file
3. Make sure you applied Terraform with the `-var-file` flag

### "Missing required variable"

If you see an error like `Error: No value for required variable`:
- Solution: You must provide GitHub OAuth credentials in your `.tfvars` file
- GitHub OAuth is required for this project
- Follow the steps above to create a GitHub OAuth App and configure your `.tfvars` file

## Files Overview

| File | Purpose | Committed to Git? |
|------|---------|-------------------|
| `terraform.tfvars.example` | General example template | ✅ Yes |
| `dev.tfvars.example` | Dev-specific example | ✅ Yes |
| `prod.tfvars.example` | Prod-specific example | ✅ Yes |
| `dev.tfvars` | Your actual dev secrets | ❌ No |
| `prod.tfvars` | Your actual prod secrets | ❌ No |

## Important Notes

- **GitHub OAuth is required** - You cannot deploy without providing credentials
- **Always use `-var-file`** - Running `terraform apply` without the var file will fail
- **Separate apps per environment** - Create different OAuth apps for dev and prod
- **Update callback URL** - Remember to update the GitHub OAuth App callback URL after first deployment
