# Backend Authentication Setup Guide

## Overview
This document provides step-by-step instructions for setting up the backend authentication system using AWS Cognito. The infrastructure (API Gateway, Lambda, DynamoDB) is already configured via Terraform. This guide focuses on deploying and configuring the authentication components.

**Estimated Time:** 2-3 hours for initial setup + testing

## Prerequisites
- AWS Account with admin access
- AWS CLI configured with proper credentials
- Node.js and npm installed
- Basic knowledge of AWS services

## Part 1: Cognito Setup and Deployment (~1-2 hours)

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

## Part 2: Verify Infrastructure (~10 minutes)

### API Gateway Configuration

The API Gateway is already configured in your Terraform infrastructure. The setup includes:

**File:** `quote-lambda-tf-backend/infrastructure/api_gateway.tf`

**What's already configured:**
- **HTTP API Gateway** (API Gateway v2) - Modern, lower latency than REST API
- **CORS Configuration** - Allows origins: `*`, methods: `GET, POST, PATCH, OPTIONS`
- **Lambda Integration** - Configured with `ANY /{proxy+}` route for all requests
- **CloudWatch Logging** - Detailed request/response logging enabled
- **Throttling** - Burst limit: 100, Rate limit: 100 requests/second

**Key Resources:**
```hcl
# API Gateway with CORS
resource "aws_apigatewayv2_api" "quote_api"

# Stage with logging and throttling
resource "aws_apigatewayv2_stage" "api_stage"

# Lambda integration (uses SnapStart alias)
resource "aws_apigatewayv2_integration" "lambda_integration"

# Catch-all route
resource "aws_apigatewayv2_route" "api_route"
```

**Get your API Gateway URL:**
```bash
cd quote-lambda-tf-backend/infrastructure
terraform output api_gateway_url
```

### Adding Cognito Authorization (Optional)

If you want to protect specific endpoints with Cognito authentication, you can add a JWT authorizer:

```hcl
# Add to api_gateway.tf
resource "aws_apigatewayv2_authorizer" "cognito" {
  api_id           = aws_apigatewayv2_api.quote_api.id
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]
  name             = "cognito-authorizer"

  jwt_configuration {
    audience = [aws_cognito_user_pool_client.client.id]
    issuer   = "https://${aws_cognito_user_pool.pool.endpoint}"
  }
}

# Update route to use authorizer
resource "aws_apigatewayv2_route" "protected_route" {
  api_id             = aws_apigatewayv2_api.quote_api.id
  route_key          = "POST /like"
  target             = "integrations/${aws_apigatewayv2_integration.lambda_integration.id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito.id
}
```

**Note:** The current setup uses a catch-all route (`ANY /{proxy+}`), which means authorization is handled in the Lambda function code, not at the API Gateway level. This provides more flexibility for mixed public/protected endpoints.

## Part 3: Add Authorization to Lambda Function (~30 minutes)

### 1. Add Role-Based Authorization to QuoteHandler

The existing `QuoteHandler` already handles the like functionality at `/quote/{id}/like`. You need to add authorization checks to ensure only authenticated users with the `USER` role can like quotes.

**File:** `quote-lambda-tf-backend/src/main/java/ebulter/quote/lambda/QuoteHandler.java`

**Add authorization check before processing the like request:**

```java
public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
    try {
        String path = event.getPath();
        String httpMethod = event.getHttpMethod();

        logger.info("path={}, httpMethod={}", path, httpMethod);

        if (path.endsWith("/quote")) {
            // ... existing quote retrieval code ...
        } else if (path.endsWith("/like")) {
            // Check authorization for like endpoint
            if (!hasUserRole(event)) {
                return createForbiddenResponse("USER role required to like quotes");
            }
            
            // Extract ID from path like "/quote/75/like"
            String[] pathParts = path.split("/");
            int id = Integer.parseInt(pathParts[pathParts.length - 2]);
            Quote quote = quoteService.likeQuote(id);
            return createResponse(quote);
        } else if (path.endsWith("/liked")) {
            // ... existing liked quotes code ...
        } else {
            return createErrorResponse("Invalid request");
        }
    } catch (Exception e) {
        logger.error("Error handling request", e);
        return createErrorResponse("Internal server error: " + e.getMessage());
    }
}

// Add helper method to check user role
private boolean hasUserRole(APIGatewayProxyRequestEvent event) {
    try {
        Map<String, Object> requestContext = event.getRequestContext();
        if (requestContext == null || !requestContext.containsKey("authorizer")) {
            return false;
        }
        
        Map<String, Object> authorizer = (Map<String, Object>) requestContext.get("authorizer");
        if (authorizer == null || !authorizer.containsKey("claims")) {
            return false;
        }
        
        Map<String, String> claims = (Map<String, String>) authorizer.get("claims");
        String roles = claims.get("custom:roles");
        
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        
        return Arrays.asList(roles.split(",")).contains("USER");
    } catch (Exception e) {
        logger.error("Error checking user role", e);
        return false;
    }
}

// Add helper method for forbidden response
private static APIGatewayProxyResponseEvent createForbiddenResponse(String message) {
    APIGatewayProxyResponseEvent response = createBaseResponse();
    response.setStatusCode(HttpStatus.SC_FORBIDDEN); // 403
    String responseBody = gson.toJson(QuoteUtil.getErrorQuote(message), quoteType);
    response.setBody(responseBody);
    return response;
}
```

**Optional: Log user information for auditing:**
```java
private void logUserInfo(APIGatewayProxyRequestEvent event, String action) {
    try {
        Map<String, Object> authorizer = (Map<String, Object>) event.getRequestContext().get("authorizer");
        Map<String, String> claims = (Map<String, String>) authorizer.get("claims");
        String userId = claims.get("sub");
        String email = claims.get("email");
        logger.info("User action: userId={}, email={}, action={}", userId, email, action);
    } catch (Exception e) {
        logger.warn("Could not log user info", e);
    }
}
```

### 2. Build and Deploy

The Lambda function is already configured in Terraform. To deploy code changes:

```bash
# Build the Lambda function
cd quote-lambda-tf-backend
mvn clean package

# The JAR is created at: target/quote-lambda-tf-backend-1.0-SNAPSHOT.jar

# Deploy via Terraform (infrastructure + code)
cd infrastructure
terraform workspace select dev
terraform apply -var-file="dev.tfvars"
```

**Alternative: Update Lambda code only (faster for code-only changes):**
```bash
# After building with Maven
aws lambda update-function-code \
  --function-name quote-lambda-tf-backend-dev \
  --zip-file fileb://target/quote-lambda-tf-backend-1.0-SNAPSHOT.jar \
  --region eu-central-1

# Publish new version and update alias
NEW_VERSION=$(aws lambda publish-version \
  --function-name quote-lambda-tf-backend-dev \
  --region eu-central-1 \
  --query 'Version' \
  --output text)

aws lambda update-alias \
  --function-name quote-lambda-tf-backend-dev \
  --name live \
  --function-version $NEW_VERSION \
  --region eu-central-1
```

**Note:** The Lambda function uses SnapStart for faster cold starts. The infrastructure includes:
- Lambda function with Java 21 runtime
- SnapStart enabled for performance
- Alias-based deployment (`live` alias)
- API Gateway integration via the alias

## Part 4: Testing (~30 minutes)

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
- [ ] Integrate authentication in frontend components
- [ ] Implement role-based access control in Lambda functions
- [ ] Add user management features (admin interface)
- [ ] Set up monitoring and alerts for authentication flows

## Resources
- [AWS Cognito Documentation](https://docs.aws.amazon.com/cognito/)
- [API Gateway Authorizers](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-use-cognito-user-pool-authorizer.html)
- [AWS SDK for Java](https://docs.aws.amazon.com/sdk-for-java/)
