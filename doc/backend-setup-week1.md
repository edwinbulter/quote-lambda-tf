# Week 1: Backend Setup Guide

## Overview
This document provides step-by-step instructions for setting up the backend authentication system using AWS Cognito and API Gateway. By the end of this week, you'll have a working authentication backend with email/password and GitHub sign-in capabilities.

## Prerequisites
- AWS Account with admin access
- AWS CLI configured with proper credentials
- Node.js and npm installed
- Basic knowledge of AWS services

## Day 1-2: Cognito Setup with Terraform

### 1. Verify Terraform Configuration

A `cognito.tf` file has already been created in your infrastructure directory with all the necessary configuration. Here's what it includes:

- **Cognito User Pool** with secure password policies
- **User attributes** including a custom `roles` attribute
- **OAuth 2.0 configuration** for web clients
- **Token settings** with appropriate expiration times
- **Output variables** for frontend configuration

You can find the file at:
```
/Users/e.g.h.bulter/IdeaProjects/quote-lambda-tf/quote-lambda-tf-backend/infrastructure/cognito.tf
```

#### Key Configuration Points:
- **User Pool Name**: `${var.project_name}-user-pool-${var.environment}`
- **Password Policy**: 8+ characters with uppercase, lowercase, numbers, and symbols
- **User Attributes**:
  - `email` (required)
  - `custom:roles` (for role-based access control)
- **OAuth Settings**:
  - Redirect: `http://localhost:5173/`
  - Logout: `http://localhost:5173/logout`
  - Scopes: `email`, `openid`, `profile`

#### Customization Options (if needed):
1. Update the `callback_urls` and `logout_urls` if your frontend runs on different ports
2. Adjust token expiration times in the `aws_cognito_user_pool_client` resource
3. Modify password policies in the `password_policy` block
```

### 2. Apply Terraform Configuration

The environment is automatically determined from the Terraform workspace:
- `default` workspace → `prod` environment
- `dev` workspace → `dev` environment
- Any other workspace → uses the workspace name as the environment

```bash
cd /path/to/quote-lambda-tf/quote-lambda-tf-backend/infrastructure

# Initialize Terraform (if not already done)
terraform init

# Create and switch to dev workspace (for dev environment)
terraform workspace new dev
# Or switch to existing workspace
terraform workspace select dev

# Review the planned changes
terraform plan

# Apply the changes
terraform apply
```

**Note**: You no longer need to set `TF_VAR_environment` - the environment is automatically derived from the workspace name!

### 3. Configure GitHub OAuth (Required)

**Why GitHub?** GitHub OAuth is completely free and doesn't require a credit card, making it perfect for learning projects.

**Important:** GitHub OAuth is required for this project. You must create a GitHub OAuth App and provide credentials before deploying.

#### Step 1: Create a GitHub OAuth App

Before applying Terraform, you need to create a GitHub OAuth App. We'll use a placeholder callback URL first, then update it after getting the actual Cognito domain.

1. Go to [GitHub Developer Settings](https://github.com/settings/developers)
2. Click **"New OAuth App"**
3. Fill in the application details:
   - **Application name**: `Quote App - Development`
   - **Homepage URL**: `http://localhost:5173`
   - **Authorization callback URL**: `https://placeholder.auth.eu-central-1.amazoncognito.com/oauth2/idpresponse` (temporary)
4. Click **"Register application"**
5. Copy the **Client ID**
6. Click **"Generate a new client secret"** and copy the **Client Secret**

**Important:** Create separate OAuth apps for dev and prod environments!

#### Step 2: Configure Your Secrets

Create a `dev.tfvars` file from the example:

```bash
cd quote-lambda-tf-backend/infrastructure
cp dev.tfvars.example dev.tfvars
```

Edit `dev.tfvars` and add your GitHub OAuth credentials:

```hcl
github_oauth_client_id     = "Iv1.abc123def456"
github_oauth_client_secret = "abc123def456ghi789jkl012mno345pqr678stu"
```

**Security Note:** The `dev.tfvars` file is excluded from version control via `.gitignore`. Never commit this file!

#### Step 3: Apply Terraform

```bash
cd quote-lambda-tf-backend/infrastructure
terraform workspace select dev
terraform plan -var-file="dev.tfvars"
terraform apply -var-file="dev.tfvars"
```

**Important:** You must always use `-var-file="dev.tfvars"` when running Terraform commands, as GitHub OAuth credentials are required.

#### Step 4: Update GitHub OAuth Callback URL

After Terraform creates your Cognito User Pool, get the actual domain:

```bash
terraform output cognito_domain
```

You'll get something like: `quote-lambda-tf-backend-dev.auth.eu-central-1.amazoncognito.com`

Now update your GitHub OAuth App:
1. Go back to [GitHub Developer Settings](https://github.com/settings/developers)
2. Click on your OAuth App
3. Update the **Authorization callback URL** to: `https://<your-cognito-domain>/oauth2/idpresponse`
   - Example: `https://quote-lambda-tf-backend-dev.auth.eu-central-1.amazoncognito.com/oauth2/idpresponse`
4. Click **"Update application"**

#### Step 5: Verify the Setup

Check that the GitHub provider was created:

```bash
aws cognito-idp list-identity-providers \
  --user-pool-id $(terraform output -raw user_pool_id) \
  --region eu-central-1
```

You should see "GitHub" in the list of providers.

---

**For detailed information about secrets management, see:** `quote-lambda-tf-backend/infrastructure/SECRETS_SETUP.md`

### 4. Generate Frontend Configuration

Instead of hardcoding values, automatically generate environment-specific configuration from Terraform outputs.

#### Option A: Auto-Generate Config (Recommended)

Use the provided script to generate frontend configuration:

```bash
# Generate dev environment config
./scripts/generate-frontend-config.sh dev

# Generate prod environment config
./scripts/generate-frontend-config.sh prod
```

This creates `.env.development` and `.env.production` files in your frontend directory with values from Terraform outputs.

#### Option B: Manual Configuration

If you prefer to set up manually, get the values from Terraform:

```bash
cd quote-lambda-tf-backend/infrastructure
terraform workspace select dev
terraform output
```

Create `.env.development` in your frontend directory:

```bash
VITE_AWS_REGION=eu-central-1
VITE_COGNITO_USER_POOL_ID=<from terraform output user_pool_id>
VITE_COGNITO_CLIENT_ID=<from terraform output user_pool_client_id>
VITE_COGNITO_DOMAIN=<from terraform output cognito_domain>
VITE_API_URL=<from terraform output api_gateway_url>
```

**Security Note:** Add `.env.development` and `.env.production` to your frontend's `.gitignore`. Only commit `.env.*.example` files.

### 5. Create AWS Configuration File

Create `src/config/aws-exports.ts` in your frontend:

```typescript
import { ResourcesConfig } from 'aws-amplify';

const awsConfig: ResourcesConfig = {
  Auth: {
    Cognito: {
      userPoolId: import.meta.env.VITE_COGNITO_USER_POOL_ID,
      userPoolClientId: import.meta.env.VITE_COGNITO_CLIENT_ID,
      loginWith: {
        oauth: {
          domain: import.meta.env.VITE_COGNITO_DOMAIN,
          scopes: ['email', 'openid', 'profile'],
          redirectSignIn: [window.location.origin + '/'],
          redirectSignOut: [window.location.origin + '/logout'],
          responseType: 'code',
        },
      },
    },
  },
};

export default awsConfig;
```

**Note:** This uses AWS Amplify v6 configuration format. The environment variables are automatically selected based on your build mode (`npm run dev` uses `.env.development`, `npm run build` uses `.env.production`).

### 6. Initialize Amplify in Your Frontend

In your main application file (e.g., `src/main.tsx`):

```typescript
import { Amplify } from 'aws-amplify';
import awsConfig from './config/aws-exports';

Amplify.configure(awsConfig);
```

## Day 3-4: API Gateway Setup

### 1. Create API Gateway
```yaml
# template.yaml
AWSTemplateFormatVersion: '2010-09-09'
Transform: AWS::Serverless-2016-10-31

Resources:
  QuoteApi:
    Type: AWS::Serverless::Api
    Properties:
      StageName: prod
      Auth:
        DefaultAuthorizer: CognitoAuthorizer
        Authorizers:
          CognitoAuthorizer:
            UserPoolArn: !GetAtt UserPool.Arn

  LikeFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: backend/like/
      Handler: index.handler
      Runtime: java11
      Events:
        ApiEvent:
          Type: Api
          Properties:
            Path: /like
            Method: post
            RestApiId: !Ref QuoteApi
```

### 2. Configure CORS
```yaml
  QuoteApi:
    Type: AWS::Serverless::Api
    Properties:
      # ... existing config ...
      Cors:
        AllowMethods: "'GET,POST,OPTIONS'"
        AllowHeaders: "'Content-Type,X-Amz-Date,Authorization,X-Api-Key'"
        AllowOrigin: "'*'"
```

## Day 5: Lambda Function

### 1. Create Like Lambda (Java)
```java
// src/main/java/com/example/LikeHandler.java
public class LikeHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        // Get user info from Cognito
        Map<String, String> claims = (Map<String, String>) event.getRequestContext()
            .getAuthorizer()
            .get("claims");
            
        String userId = claims.get("sub");
        List<String> roles = Arrays.asList(claims.get("custom:roles").split(","));
        
        if (!roles.contains("USER")) {
            return new APIGatewayProxyResponseEvent()
                .withStatusCode(403)
                .withBody("{\"error\": \"Forbidden: USER role required\"}");
        }
        
        // Process like
        return new APIGatewayProxyResponseEvent()
            .withStatusCode(200)
            .withBody("{\"message\": \"Like processed\"}");
    }
}
```

### 2. Build and Deploy
```bash
# Build
cd quote-lambda-tf-backend
mvn clean package

# Deploy
sam deploy --guided
```

## Testing

### 1. Create Test User
```bash
aws cognito-idp sign-up \
    --client-id <YOUR_CLIENT_ID> \
    --username user@example.com \
    --password Passw0rd! \
    --user-attributes Name=email,Value=user@example.com

# Confirm user (if auto-verify is off)
aws cognito-idp admin-confirm-sign-up \
    --user-pool-id <YOUR_USER_POOL_ID> \
    --username user@example.com
```

### 2. Test Authentication
```bash
# Get tokens
TOKEN=$(aws cognito-idp initiate-auth \
    --client-id <YOUR_CLIENT_ID> \
    --auth-flow USER_PASSWORD_AUTH \
    --auth-parameters USERNAME=user@example.com,PASSWORD=Passw0rd! \
    --query 'AuthenticationResult.IdToken' \
    --output text)

# Test protected endpoint
curl -X POST https://<YOUR_API_ID>.execute-api.<REGION>.amazonaws.com/prod/like \
    -H "Authorization: $TOKEN" \
    -H "Content-Type: application/json"
```

## Troubleshooting

### Common Issues
1. **CORS Errors**: Double-check CORS configuration in API Gateway
2. **403 Forbidden**: Verify user is in the correct Cognito group
3. **Token Issues**: Check token expiration and scopes

### Useful Commands
```bash
# Get user details
aws cognito-idp admin-get-user \
    --user-pool-id <YOUR_USER_POOL_ID> \
    --username user@example.com

# List user pools
aws cognito-idp list-user-pools --max-results 10

# Check API Gateway logs
aws logs get-log-events \
    --log-group-name "/aws/apigateway/QuoteApi" \
    --log-stream-name "<YOUR_LOG_STREAM>"
```

## Next Steps
- [ ] Set up frontend integration (Week 2)
- [ ] Implement admin interface (Week 2)
- [ ] Add monitoring and alerts (Week 3)

## Resources
- [AWS Cognito Documentation](https://docs.aws.amazon.com/cognito/)
- [API Gateway Authorizers](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-use-cognito-user-pool-authorizer.html)
- [AWS SDK for Java](https://docs.aws.amazon.com/sdk-for-java/)
