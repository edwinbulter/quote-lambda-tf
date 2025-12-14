# Authentication & Authorization Architecture

This document describes how users authenticate and how the system authorizes access to protected resources.

## Table of Contents

- [User Authentication Flows](#user-authentication-flows)
  - [Email Registration with Username](#email-registration-with-username)
  - [Google OAuth Sign-In](#google-oauth-sign-in)
- [Authorization](#authorization)
  - [Protected Endpoints](#protected-endpoints)
  - [Public Endpoints](#public-endpoints)
- [Architecture Overview](#architecture-overview)
- [System Components](#system-components)
  - [AWS Cognito User Pool](#aws-cognito-user-pool)
  - [API Gateway (HTTP API)](#api-gateway-http-api)
  - [Lambda Function (Backend)](#lambda-function-backend)
  - [Frontend (React + Amplify)](#frontend-react--amplify)
- [Security Considerations](#security-considerations)
- [References](#references)

---

## User Authentication Flows

### Email Registration with Username

Users can register with an email address and choose a custom username:

1. **User enters registration details:**
   - Username (custom identifier)
   - Email address
   - Password (8+ characters, uppercase, lowercase, numbers, symbols)

2. **Cognito creates the user account:**
   - Username is stored as the primary identifier
   - Email is stored as a user attribute
   - Password is hashed using bcrypt

3. **User receives confirmation email:**
   - Contains a confirmation code
   - User enters code to verify email ownership

4. **Account is activated:**
   - User can now log in with username and password
   - User is automatically added to the `USER` group
   - User receives JWT tokens (ID token and Access token)

### Google OAuth Sign-In

Users can sign in using their Google account:

1. **User clicks "Sign in with Google"**

2. **Frontend redirects to Cognito's Google OAuth flow:**
   - User is redirected to Google login page
   - User authorizes the application
   - Google returns user information (email, name, etc.)

3. **Cognito creates or updates the user:**
   - Username is set to `Google_<google-user-id>` (technical identifier)
   - Email is stored from Google account
   - User is automatically added to the `USER` group

4. **Username selection (first-time login only):**
   - If user doesn't have a `preferred_username` set, a modal appears
   - User enters their desired username
   - Username is saved to the `preferred_username` attribute
   - User can now use this custom username in the app

5. **User receives JWT tokens:**
   - ID token contains `cognito:groups` claim with `["USER"]`
   - Access token for API requests
   - Refresh token for token renewal (30 days)

---

## Authorization

### Protected Endpoints

The `/quote/{id}/like` endpoint requires the user to be authenticated and have the `USER` role:

1. **Frontend sends request with JWT:**
   ```
   POST /quote/13/like
   Authorization: <JWT_ID_TOKEN>
   ```

2. **API Gateway receives request:**
   - CORS headers are validated
   - Authorization header is passed through to Lambda

3. **Lambda validates authorization:**
   - Decodes the JWT token from the Authorization header
   - Extracts the `cognito:groups` claim
   - Checks if user is in `USER` or `ADMIN` group
   - If authorized: processes the like request
   - If not authorized: returns 403 Forbidden

4. **User action is logged:**
   - User ID, email, and action are recorded in CloudWatch logs
   - Logs are in JSON format for easy searching

### Public Endpoints

These endpoints do not require authentication:

- `GET /quote` - Get a random quote
- `GET /quote/liked` - Get all quotes with at least one like

---

## Architecture Overview

### Architecture Diagram (As Implemented)

```
┌─────────────┐
│   Frontend  │
└──────┬──────┘
       │ 1. Login via Cognito
       ▼
┌─────────────────────┐
│   AWS Cognito       │
│   User Pool         │
└──────┬──────────────┘
       │ 2. JWT ID Token (with cognito:groups claim)
       ▼
┌─────────────┐
│   Frontend  │
└──────┬──────┘
       │ 3. API Request + Authorization: <JWT>
       ▼
┌─────────────────────────────┐
│  API Gateway (HTTP API)     │
│  - CORS configured          │
│  - No authorizer            │
│  - Passes all headers       │
└──────┬──────────────────────┘
       │ 4. Request + JWT in Authorization header
       ▼
┌─────────────────────────────┐
│  Lambda Function            │
│  - Decodes JWT token        │
│  - Checks cognito:groups    │
│  - Authorizes USER/ADMIN    │
│  - Logs user actions        │
└──────┬──────────────────────┘
       │ 5. Authorized action
       ▼
┌─────────────────────┐
│     DynamoDB        │
└─────────────────────┘
```

---

## System Components

### AWS Cognito User Pool

**Configuration:**
- **Name:** `quote-lambda-tf-user-pool-dev` (development environment)
- **Authentication methods:**
  - Email + Password (custom username)
  - Google OAuth
- **User Groups:** `USER`, `ADMIN`
- **Password Policy:** 8+ characters, uppercase, lowercase, numbers, symbols
- **Token Expiration:**
  - ID Token: 1 hour
  - Access Token: 1 hour
  - Refresh Token: 30 days
- **Email Verification:** Auto-verified for email registrations

**Google OAuth Provider:**
- Client ID and Secret configured in Terraform
- Scopes: `email`, `openid`, `profile`
- Attribute mapping:
  - Google `email` → Cognito `email`
  - Google `name` → Cognito `name`
  - Google `sub` → Cognito `username` (as `Google_<id>`)

### API Gateway (HTTP API)

**Configuration:**
- **CORS Settings:**
  - Allowed origins: `*`
  - Allowed methods: `GET`, `POST`, `PATCH`, `OPTIONS`
  - Allowed headers: `content-type`, `authorization`
  - Max age: 300 seconds
- **Routes:**
  - `GET /quote` - Public, no authentication required
  - `POST /quote` - Public, no authentication required
  - `GET /quote/liked` - Public, no authentication required
  - `POST /quote/{id}/like` - Protected, requires USER role
  - `DELETE /quote/{id}/like` - Protected, requires USER role
- **No authorizer configured** - Authorization is handled in Lambda

### Lambda Function (Backend)

**Authorization Logic:**
- Extracts JWT token from `Authorization` header
- Decodes JWT using `java-jwt` library (no signature verification needed - tokens from trusted Cognito)
- Checks `cognito:groups` claim for `USER` or `ADMIN` membership
- Logs user actions (ID, email, action) in JSON format to CloudWatch

**Protected Endpoint Example:**
```java
// Extract and validate JWT
String token = authHeader.substring(7); // Remove "Bearer " prefix
DecodedJWT jwt = JWT.decode(token);

// Check user role
List<String> groups = jwt.getClaim("cognito:groups").asList(String.class);
if (groups == null || (!groups.contains("USER") && !groups.contains("ADMIN"))) {
    return createForbiddenResponse("USER role required");
}

// Log user action
String userId = jwt.getClaim("sub").asString();
String email = jwt.getClaim("email").asString();
logger.info("User action: userId=" + userId + ", email=" + email + ", action=LIKE_QUOTE");
```

### Frontend (React + Amplify)

**Authentication Flow:**
- AWS Amplify v6 handles Cognito integration
- AuthContext manages user state and authentication
- Login component provides:
  - Email/password registration with username input
  - Email/password login
  - Google OAuth sign-in button
- Username selection modal for Google users (first login only)

**Token Management:**
- ID token stored securely (memory or httpOnly cookie)
- Access token used for API requests
- Refresh token automatically refreshed when expired
- Tokens cleared on logout

**Authorization in UI:**
- Like button disabled if user not authenticated
- Like button disabled if user not in USER group
- User profile shows username and email

---

## Security Considerations

### Token Storage
- **Frontend:** Store tokens in memory or httpOnly cookies (not localStorage)
- **Backend:** Never log tokens
- **Transmission:** Always use HTTPS

### Password Security
- Cognito handles password hashing (bcrypt)
- Enforce strong password policy
- Enable password breach detection (Cognito feature)

### Token Expiration
- ID Token: 1 hour (default)
- Access Token: 1 hour (default)
- Refresh Token: 30 days (configurable)

### API Security
- Always validate tokens on backend
- Never trust frontend role checks alone
- Use API Gateway throttling to prevent abuse
- Enable CloudWatch logging for monitoring

### Role Assignment
- Only ADMIN role can assign roles
- Log all role changes
- Consider approval workflow for sensitive roles

---

## References

- [AWS Cognito Documentation](https://docs.aws.amazon.com/cognito/)
- [AWS Amplify Documentation](https://docs.amplify.aws/)
- [API Gateway Authorizers](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-use-lambda-authorizer.html)
- [OAuth 2.0 Best Practices](https://oauth.net/2/)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
