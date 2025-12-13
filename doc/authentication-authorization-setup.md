# Authentication & Authorization Setup

This document describes the authentication and authorization implementation for the quote-lambda-tf application using **AWS Cognito with Lambda-based JWT validation**.

## Table of Contents

- [Overview](#overview)
- [Backend Implementation Status](#backend-implementation-status)
- [Architecture Overview](#architecture-overview)
- [Implementation Details](#implementation-details)
- [User Flows](#user-flows)
- [Security Considerations](#security-considerations)
- [Cost Estimation](#cost-estimation)
- [Next Steps](#next-steps)

## Overview

**Status:** ✅ Backend authentication and authorization **COMPLETED**

The backend authentication system has been fully implemented and tested. This document provides an overview of the implementation and references the detailed setup guide.

For detailed backend setup instructions, see: [`backend-auth-setup.md`](./backend-auth-setup.md)

For frontend implementation guide, see: [`frontend-auth-setup.md`](./frontend-auth-setup.md)

---

## Backend Implementation Status

### ✅ Completed Components

**1. AWS Cognito User Pool**
- User Pool with email/password authentication
- GitHub OAuth integration configured
- USER and ADMIN groups created
- Custom attributes for role management
- Secure password policies enforced

**2. Lambda Authorization**
- JWT token parsing using `java-jwt` library
- Role-based authorization via `cognito:groups` claim
- Protected `/quote/{id}/like` endpoint (requires USER or ADMIN role)
- User audit logging (userId, email, action)
- Public endpoints remain accessible (GET /quote, GET /quote/liked)

**3. JSON Structured Logging**
- CloudWatch logs in JSON format using `logstash-logback-encoder`
- Stack traces as single JSON fields for easy searching
- Structured log fields for CloudWatch Logs Insights queries

**4. Infrastructure**
- Terraform configuration for Cognito User Pool and Groups
- API Gateway CORS configured to allow Authorization header
- Lambda updated with JWT parsing and authorization logic
- All infrastructure deployed and tested

**5. Testing**
- Unit tests with mock JWT tokens
- Authorization tests (both authorized and unauthorized scenarios)
- All 6 tests passing

### Implementation Approach

Unlike the original plan to use API Gateway's built-in Cognito Authorizer, we implemented **Lambda-based JWT validation** for the following reasons:

**Advantages:**
- ✅ Flexible endpoint protection (mix of public and protected endpoints)
- ✅ No API Gateway authorizer needed (simpler configuration)
- ✅ Full control over authorization logic
- ✅ Easy to add custom authorization rules
- ✅ Better for learning JWT concepts

**Trade-offs:**
- JWT parsing happens in Lambda (minimal performance impact)
- Token validation is done by decoding (not verifying signature)
  - This is acceptable since tokens come from trusted Cognito source
  - API Gateway could add JWT authorizer later if needed

For complete implementation details, see [`backend-auth-setup.md`](./backend-auth-setup.md)

---

## Requirements

### Functional Requirements

1. **User Registration**
   - Google OAuth
   - Email + Username + Password

2. **User Authentication**
   - Login with Google
   - Login with email/password
   - Session management

3. **Role-Based Authorization**
   - Default role: `USER` (assigned automatically on registration)
   - Future roles: `ADMIN`, `MODERATOR`, etc.
   - Role assignment via admin console

4. **Endpoint Protection**
   - `/like` endpoint: requires `USER` role
   - Future endpoints: require specific roles
   - Backend validation of user authentication and roles

5. **Frontend Authorization**
   - Disable "Like" button if user doesn't have `USER` role
   - Show/hide features based on user roles

### Non-Functional Requirements

- Secure token storage
- Token refresh mechanism
- Scalable for future role additions
- Integration with existing AWS infrastructure
- Minimal operational overhead

---

## Architecture Overview

This implementation uses **AWS Cognito User Pools** with **Lambda-based JWT validation** for flexible authentication and authorization.

### Why This Approach?

- ✅ Fully managed AWS Cognito service
- ✅ Built-in GitHub OAuth support
- ✅ Scalable and secure
- ✅ User pool groups for role management
- ✅ Free tier: 50,000 MAUs
- ✅ Flexible endpoint protection (mix of public and protected)
- ✅ Full control over authorization logic
- ✅ Easy to add custom authorization rules
- ✅ Better for learning JWT concepts

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

## Implementation Details

### API Gateway Configuration

API Gateway is configured with CORS to allow the Authorization header:

```hcl
# api_gateway.tf
cors_configuration {
  allow_origins = ["*"]
  allow_methods = ["GET", "POST", "PATCH", "OPTIONS"]
  allow_headers = ["content-type", "authorization"]  # Authorization header required!
  max_age       = 300
}
```

**No API Gateway Authorizer** - Authorization is handled in Lambda for flexibility.

### Cognito User Pool Configuration

**Implemented Settings:**
- GitHub OAuth as identity provider
- Email/password authentication enabled
- User pool groups: `USER`, `ADMIN`
- Custom attributes: `custom:roles` (for backward compatibility)
- Secure password policy (8+ chars, uppercase, lowercase, numbers, symbols)
- Token expiration: 1 hour (ID and Access tokens), 30 days (Refresh token)

### Lambda Function (Backend)

JWT token is parsed from the Authorization header:

```java
// Extract JWT from Authorization header
Map<String, String> headers = event.getHeaders();
String authHeader = headers.get("authorization");
String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

// Decode JWT (using java-jwt library)
DecodedJWT jwt = JWT.decode(token);

// Check for Cognito Groups
List<String> groups = jwt.getClaim("cognito:groups").asList(String.class);
if (groups != null && (groups.contains("USER") || groups.contains("ADMIN"))) {
    // Authorized - proceed with action
    String userId = jwt.getClaim("sub").asString();
    String email = jwt.getClaim("email").asString();
    logger.info("User action: userId=" + userId + ", email=" + email + ", action=LIKE_QUOTE");
    // ... process like
} else {
    return createForbiddenResponse("USER role required to like quotes");
}
```

**Key Dependencies:**
- `com.auth0:java-jwt:4.4.0` - JWT parsing
- `net.logstash.logback:logstash-logback-encoder:7.4` - JSON logging

### User Group Assignment

Users must be manually added to groups after registration:

```bash
# Add user to USER group
aws cognito-idp admin-add-user-to-group \
    --user-pool-id <POOL_ID> \
    --username user@example.com \
    --group-name USER
```

**Future Enhancement:** Implement Cognito Post-Confirmation trigger to automatically assign USER role on registration.

### Frontend (React)

Use AWS Amplify for Cognito integration:

```typescript
import { Amplify, Auth } from 'aws-amplify';

// Configure Amplify
Amplify.configure({
  Auth: {
    region: 'us-east-1',
    userPoolId: 'us-east-1_xxxxx',
    userPoolWebClientId: 'xxxxx',
    oauth: {
      domain: 'your-domain.auth.us-east-1.amazoncognito.com',
      scope: ['email', 'openid', 'profile'],
      redirectSignIn: 'http://localhost:5173/',
      redirectSignOut: 'http://localhost:5173/',
      responseType: 'code'
    }
  }
});

// Check if user has USER role
const hasUserRole = async () => {
  const user = await Auth.currentAuthenticatedUser();
  const roles = user.attributes['custom:roles']?.split(',') || [];
  return roles.includes('USER');
};

// Disable like button if no USER role
<button disabled={!hasUserRole()} onClick={like}>Like</button>
```

---

## Detailed Architecture

### High-Level Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                         Frontend (React)                      │
│  - AWS Amplify for Cognito integration                       │
│  - Role-based UI rendering                                   │
│  - Token management                                          │
└────────────┬─────────────────────────────────────────────────┘
             │
             │ HTTPS + JWT (ID Token)
             │
┌────────────▼─────────────────────────────────────────────────┐
│                    API Gateway (REST API)                     │
│  - Cognito User Pool Authorizer                              │
│  - Validates JWT automatically                               │
│  - Injects user claims into Lambda context                   │
└────────────┬─────────────────────────────────────────────────┘
             │
             │ Authorized Request + User Context
             │
┌────────────▼─────────────────────────────────────────────────┐
│                    Lambda Functions                           │
│  - quote-lambda (existing)                                   │
│    - Checks USER role for /like endpoint                     │
│  - admin-lambda (future)                                     │
│    - Role management                                         │
│    - User management                                         │
└────────────┬─────────────────────────────────────────────────┘
             │
             │
┌────────────▼─────────────────────────────────────────────────┐
│                        DynamoDB                               │
│  - Quotes table (existing)                                   │
│  - Add userId field to liked quotes                          │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                    AWS Cognito User Pool                      │
│  - Google OAuth provider                                     │
│  - Email/password authentication                             │
│  - User Groups: Users, Admins, Moderators                   │
│  - Custom attributes: custom:roles                           │
│  - Post-confirmation trigger → assign USER role              │
└──────────────────────────────────────────────────────────────┘
```

### User Flows

#### Registration Flow

```
1. User clicks "Sign up with Google" or "Sign up with Email"
   ↓
2. Frontend → Cognito Hosted UI or Amplify Auth
   ↓
3. User completes registration
   ↓
4. Cognito Post-Confirmation Trigger (Lambda)
   - Adds user to "Users" group
   - Assigns USER role
   ↓
5. User receives ID Token + Access Token
   ↓
6. Frontend stores tokens securely
   ↓
7. User is logged in with USER role
```

#### Like Quote Flow

```
1. User clicks "Like" button
   ↓
2. Frontend checks if user has USER role
   - If no: button is disabled
   - If yes: proceed
   ↓
3. Frontend → API Gateway + ID Token in Authorization header
   ↓
4. API Gateway validates token with Cognito
   - Invalid: 401 Unauthorized
   - Valid: proceed
   ↓
5. API Gateway → Lambda with user context
   ↓
6. Lambda checks USER role
   - No role: 403 Forbidden
   - Has role: proceed
   ↓
7. Lambda updates quote in DynamoDB
   ↓
8. Lambda returns success
   ↓
9. Frontend updates UI
```

#### Admin Role Assignment Flow (Future)

```
1. Admin logs into admin console
   ↓
2. Admin selects user and role to assign
   ↓
3. Frontend → Admin API + ID Token
   ↓
4. API Gateway validates token
   ↓
5. Admin Lambda checks ADMIN role
   - No ADMIN role: 403 Forbidden
   - Has ADMIN role: proceed
   ↓
6. Admin Lambda → Cognito
   - Add user to group (e.g., "Moderators")
   - Update custom:roles attribute
   ↓
7. User's next login will have new role
```

---

## Implementation Roadmap

### Phase 1: Basic Authentication (2-3 weeks)

**Goals:**
- User registration and login
- Google OAuth integration
- Protect /like endpoint

**Week 1: Backend Setup (Cognito + API Gateway)**
1. **Cognito Configuration** (2-3 days)
   - Set up User Pool with email/password auth
   - Configure Google as identity provider
   - Define custom attributes for roles
   - Set up User Pool Client for SPA

2. **API Gateway & Lambda** (2-3 days)
   - Configure Cognito authorizer
   - Update Lambda to handle user context
   - Implement role validation for /like endpoint
   - Set up Post-Confirmation trigger for default roles

**Week 2: Frontend Integration** (3-4 days)
1. **Amplify Setup**
   - Install and configure AWS Amplify
   - Set up authentication flows
   - Implement token management

2. **UI Components**
   - Create login/signup forms
   - Implement protected routes
   - Add role-based UI elements (like button)
   - Set up error handling and loading states

**Week 3: Testing & Polish** (2-3 days)
- End-to-end testing
- Error handling and edge cases
- Performance optimization
- Documentation updates

**Deliverables:**
- ✅ Users can register with Google or email/password
- ✅ Users automatically get USER role on registration
- ✅ Protected /like endpoint with role validation
- ✅ Frontend integration with role-based UI
- ✅ Basic error handling and user feedback

### Phase 2: Role Management (2 weeks)

**Week 1: Admin Console Foundation**
- Basic admin UI
- User listing and search
- Role management interface

**Week 2: Role Assignment API**
- Secure API endpoints for role management
- Permission validation
- Audit logging

**Week 1: Backend Implementation**
1. **Admin API Endpoints** (3 days)
   - `/admin/users` - List users with filtering
   - `/admin/users/{userId}/roles` - Manage user roles
   - Role-based access control (RBAC) validation
   - Audit logging for admin actions

2. **Cognito Integration** (2 days)
   - Set up Cognito groups for role management
   - Configure custom attributes for role storage
   - Implement group-based permissions

**Week 2: Admin Console** (5 days)
1. **User Management Interface**
   - User listing with search and pagination
   - Role assignment interface
   - User status management (enable/disable)

2. **Security & Testing**
   - Comprehensive test coverage
   - Security review and hardening
   - Documentation for admin users

**Deliverables:**
- ✅ Secure admin API endpoints
- ✅ Functional admin dashboard
- ✅ Audit logging for role changes
- ✅ Documentation for administrators

### Phase 3: Enhanced Security & UX (2-3 weeks)

**Week 1: Token Management**
- Implement refresh token rotation
- Handle token expiration gracefully
- Add token revocation on logout

**Week 2: Error Handling & Feedback**
- User-friendly error messages
- Loading states and feedback
- Session timeout handling

**Week 3: User Experience**
- Profile management
- Role-based UI components
- Responsive design improvements
- Accessibility enhancements

**Deliverables:**
- ✅ Seamless authentication flow
- ✅ Professional error handling
- ✅ Improved user experience
- ✅ Comprehensive test coverage

### Phase 4: Advanced Security (Future, 3-4 weeks)

**Weeks 1-2: Fine-grained Permissions**
- Define permission model
- Implement permission checks
- Update admin interface

**Weeks 3-4: Security Enhancements**
- MFA implementation
- Suspicious activity detection
- Security audit and penetration testing

**Deliverables:**
- ✅ Granular permission system
- ✅ Enhanced security features
- ✅ Security audit report
- ✅ Compliance documentation

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

## Cost Estimation

### Small Scale (< 1,000 users)
- **Cognito:** Free (within free tier)
- **API Gateway:** ~$3.50/month (1M requests)
- **Lambda:** ~$0.20/month (within free tier)
- **DynamoDB:** Free (within free tier)
- **Total:** ~$4/month

### Medium Scale (10,000 users)
- **Cognito:** Free (within 50,000 MAU free tier)
- **API Gateway:** ~$35/month (10M requests)
- **Lambda:** ~$2/month
- **DynamoDB:** ~$5/month
- **Total:** ~$42/month

### Large Scale (100,000 users)
- **Cognito:** ~$275/month (50,000 free + 50,000 paid MAUs)
- **API Gateway:** ~$350/month (100M requests)
- **Lambda:** ~$20/month
- **DynamoDB:** ~$50/month
- **Total:** ~$695/month

---

## Next Steps

### ✅ Backend Complete - Ready for Frontend Integration

The backend authentication system is **fully implemented and tested**. The next phase is frontend integration.

### Frontend Implementation (Estimated: 4-6 hours)

Follow the detailed guide: [`frontend-auth-setup.md`](./frontend-auth-setup.md)

**Phase Breakdown:**

1. **AWS Amplify Setup** (1 hour)
   - Install dependencies
   - Configure Amplify with Cognito settings
   - Set up environment variables

2. **Authentication UI** (1-2 hours)
   - Create AuthContext for state management
   - Build Login/SignUp components
   - Implement confirmation flow

3. **Protected Routes** (30 minutes)
   - Optional: Add React Router
   - Create ProtectedRoute component

4. **API Integration** (1-2 hours)
   - Create API client with Authorization header
   - Update quote components to use authenticated API
   - Handle 403 Forbidden errors

5. **Role-Based UI** (1 hour)
   - Disable like button for non-authenticated users
   - Show/hide features based on USER/ADMIN roles
   - Add user info display

6. **Testing** (1 hour)
   - Manual testing of all flows
   - Browser DevTools verification
   - End-to-end testing

### Future Enhancements

1. **Automatic Role Assignment**
   - Implement Cognito Post-Confirmation trigger
   - Automatically assign USER role on registration
   - Eliminate manual group assignment

2. **GitHub OAuth UI**
   - Add "Sign in with GitHub" button
   - Test OAuth flow end-to-end

3. **Admin Console**
   - User management interface
   - Role assignment UI
   - Audit logs viewer

4. **Enhanced Security**
   - Implement MFA
   - Add password reset flow
   - Session timeout handling
   - Token refresh optimization

---

## References

- [AWS Cognito Documentation](https://docs.aws.amazon.com/cognito/)
- [AWS Amplify Documentation](https://docs.amplify.aws/)
- [API Gateway Authorizers](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-use-lambda-authorizer.html)
- [OAuth 2.0 Best Practices](https://oauth.net/2/)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
