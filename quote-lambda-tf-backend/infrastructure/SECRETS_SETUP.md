# Secrets Management Setup

This document explains how to manage sensitive credentials (like GitHub OAuth secrets) for your Terraform infrastructure.

## Overview

We use environment-specific `.tfvars` files to store sensitive credentials. These files are:
- ✅ **NOT committed to version control** (excluded in `.gitignore`)
- ✅ **Environment-specific** (separate files for dev and prod)
- ✅ **Optional** (GitHub OAuth is optional, you can use email/password only)

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

### Step 2: Get GitHub OAuth Credentials (Optional)

If you want to enable GitHub sign-in:

1. Go to [GitHub Developer Settings](https://github.com/settings/developers)
2. Click **"New OAuth App"**
3. Fill in the details:
   - **Application name**: `Quote App - Development` (or Production)
   - **Homepage URL**: `http://localhost:5173` (dev) or your production URL
   - **Authorization callback URL**: See below
4. Copy the **Client ID** and **Client Secret**

#### Getting the Callback URL

The callback URL depends on your Cognito domain. To get it:

```bash
# First, apply Terraform without GitHub OAuth
terraform workspace select dev
terraform apply

# Then get the Cognito domain
terraform output cognito_domain

# The callback URL will be:
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

**To skip GitHub OAuth:** Leave the values empty:
```hcl
github_oauth_client_id     = ""
github_oauth_client_secret = ""
```

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

### "Want to remove GitHub OAuth"

Set the variables to empty strings in your `.tfvars` file:
```hcl
github_oauth_client_id     = ""
github_oauth_client_secret = ""
```

Then run:
```bash
terraform apply -var-file="dev.tfvars"
```

## Files Overview

| File | Purpose | Committed to Git? |
|------|---------|-------------------|
| `terraform.tfvars.example` | General example template | ✅ Yes |
| `dev.tfvars.example` | Dev-specific example | ✅ Yes |
| `prod.tfvars.example` | Prod-specific example | ✅ Yes |
| `dev.tfvars` | Your actual dev secrets | ❌ No |
| `prod.tfvars` | Your actual prod secrets | ❌ No |

## Alternative: Skip GitHub OAuth

If you don't want to set up GitHub OAuth right now:

1. Don't create the `.tfvars` files
2. Run Terraform without the `-var-file` flag:
   ```bash
   terraform apply
   ```
3. The GitHub identity provider won't be created (thanks to the `count` condition)
4. You can still use email/password authentication

You can add GitHub OAuth later by creating the `.tfvars` file and running `terraform apply -var-file="dev.tfvars"`.
