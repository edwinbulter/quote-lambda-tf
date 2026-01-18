# API Gateway Integration with Terraform

## Overview

This document describes how to add an Azure API Gateway to your quote-azure-backend infrastructure that automatically forwards all calls with the master key to the Function App, while keeping the master key secure and never exposing it to the frontend.

## Architecture

```
Frontend Application
        ↓
   API Gateway
        ↓ (adds master key)
Azure Function App
```

### Benefits

- **Security**: Master key never exposed to frontend
- **Centralized management**: Single point for API configuration
- **Rate limiting**: Built-in throttling and protection
- **CORS handling**: Centralized CORS management
- **Logging**: Centralized request/response logging
- **Authentication**: JWT validation at gateway level

## Current Implementation Status

The API Gateway implementation has been updated to support the current JWT-based authentication system:

### Authentication Endpoints
- `POST /auth/register` - User registration (public)
- `POST /auth/login` - User login (public)
- `POST /auth/change-password` - Change password (authenticated)
- `DELETE /auth/unregister` - Delete account (authenticated)

### Quote Endpoints
- `GET /quotes/random` - Get random quote (public)
- `POST /quote` - Get quote with exclusions (public)
- `GET /quote` - Get quote and record view (authenticated)
- `GET /quote/viewed` - Get viewed quotes (authenticated)
- `GET /quote/{id}` - Get quote by ID (authenticated)
- `POST /quote/{id}/like` - Like quote (authenticated)
- `DELETE /quote/{id}/unlike` - Unlike quote (authenticated)
- `GET /quote/liked` - Get liked quotes (authenticated)
- `PUT /quote/{id}/reorder` - Reorder liked quote (authenticated)

### Management Endpoints (Admin Only)
- `PUT /manage/users/role` - Update user role
- `DELETE /manage/users/role` - Remove user role
- `GET /manage/users` - Get all users
- `GET /admin/users/{userId}` - Get user by ID
- `GET /manage/quotes` - Get quotes with pagination
- `POST /manage/quotes/fetch` - Fetch new quotes
- `GET /manage/stats` - Get system statistics
- `DELETE /manage/quotes/{id}` - Delete quote
- `PUT /manage/quotes/{id}` - Update quote

## Implementation Files

The Terraform implementation is located in:
- `infrastructure/api-gateway.tf` - Complete API Gateway configuration
- `infrastructure/outputs.tf` - Updated with API Gateway outputs

## Key Features

### JWT Validation
The API Gateway validates JWT tokens using:
- Issuer: `quote-azure-backend`
- Audience: `quote-azure-backend-users`
- Required claims: `sub`, `email`, and `role` (for admin endpoints)

### Rate Limiting
- 100 calls per minute per IP address
- 1000 calls per hour per subscription
- Automatic throttling when limits exceeded

### CORS Configuration
Supports multiple origins including:
- `http://localhost:3000` (React dev)
- `http://localhost:5173` (Vite dev)
- Your production frontend domain

## Deployment

To deploy the API Gateway:

```bash
# Navigate to infrastructure directory
cd infrastructure

# Plan the changes
terraform plan

# Apply the changes
terraform apply
```

## Frontend Integration

Update your frontend configuration:

```javascript
// Before (direct to Function App)
const API_BASE_URL = 'https://quote-backend-function.azurewebsites.net/api';

// After (through API Gateway)
const API_BASE_URL = 'https://quote-api-gateway.azure-api.net/quote';
```

## Security Notes

1. **JWT Key**: The API Gateway needs access to your JWT signing key for validation
2. **Master Key**: Automatically added to all backend requests
3. **Role-Based Access**: Admin endpoints require `role` claim with value "Admin"
4. **HTTPS Only**: All communication must use HTTPS

## Testing

Use the provided test file `doc/test-api-gateway.http` to verify all endpoints work correctly through the API Gateway.

## Cost

- Developer tier: Free (1 million calls/month)
- Standard tier: ~$50/month for production
- No additional storage costs (uses existing storage account)

## Terraform Implementation

The complete Terraform implementation is available in the `infrastructure/api-gateway.tf` file. This file contains:

- API Gateway instance configuration
- All endpoint definitions (auth, quotes, management)
- JWT validation policies
- CORS configuration
- Rate limiting settings
- Product and subscription configuration

## Important Notes

1. **JWT Key Placeholder**: The implementation uses `{{jwt-signing-key}}` as a placeholder. You'll need to replace this with your actual JWT signing key after deployment.

2. **Backend Reference**: The implementation references existing resources like `azurerm_linux_function_app.function_app` and `azurerm_storage_account.sa`. Make sure these exist in your main.tf file.

3. **Windows vs Linux**: The current implementation uses `azurerm_linux_function_app`. If you're using Windows Functions, update the reference accordingly.

## Next Steps

1. Deploy the API Gateway using Terraform
2. Update your frontend to use the API Gateway URL
3. Test all endpoints through the gateway
4. Monitor usage and adjust rate limits as needed
5. Consider upgrading to Standard tier for production

## Troubleshooting

If you encounter issues:

1. Check that all referenced resources exist
2. Verify the JWT signing key configuration
3. Ensure CORS origins match your frontend domains
4. Monitor Application Insights for errors
5. Check Azure Portal for API Gateway health
