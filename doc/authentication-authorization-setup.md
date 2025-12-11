# Authentication & Authorization Setup

This document describes the authentication and authorization implementation for the quote-lambda-tf application using **AWS Cognito + API Gateway Authorizer**.

## Table of Contents

- [Requirements](#requirements)
- [Architecture Overview](#architecture-overview)
- [Implementation Details](#implementation-details)
- [User Flows](#user-flows)
- [Implementation Roadmap](#implementation-roadmap)
- [Security Considerations](#security-considerations)
- [Cost Estimation](#cost-estimation)
- [Next Steps](#next-steps)

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

This implementation uses **AWS Cognito User Pools** with **API Gateway's built-in Cognito Authorizer** for seamless authentication and authorization.

### Why This Approach?

- ✅ Fully managed AWS service
- ✅ Native integration with API Gateway (no custom Lambda authorizer needed)
- ✅ Built-in Google OAuth support
- ✅ Scalable and secure
- ✅ No additional infrastructure needed
- ✅ Built-in MFA support
- ✅ User pool groups for role management
- ✅ Free tier: 50,000 MAUs
- ✅ Better performance (no extra Lambda invocation for authorization)
- ✅ Automatic token validation

### Architecture Diagram

```
┌─────────────┐
│   Frontend  │
└──────┬──────┘
       │ 1. Login via Cognito
       ▼
┌─────────────────────┐
│   AWS Cognito       │
└──────┬──────────────┘
       │ 2. ID Token
       ▼
┌─────────────┐
│   Frontend  │
└──────┬──────┘
       │ 3. API Request + ID Token
       ▼
┌─────────────────────────────┐
│  API Gateway                │
│  Built-in Cognito Authorizer│
│  (no Lambda needed!)        │
└──────┬──────────────────────┘
       │ 4. Authorized Request
       │    + User claims in context
       ▼
┌─────────────────────┐
│  Lambda Function    │
│  - Reads user from  │
│    request context  │
└─────────────────────┘
```

---

## Implementation Details

### API Gateway Configuration

Configure API Gateway to use Cognito User Pool as the authorizer:

```yaml
# serverless.yml or SAM template
authorizer:
  type: COGNITO_USER_POOLS
  userPoolArn: arn:aws:cognito-idp:region:account:userpool/poolId
  scopes:
    - email
    - openid
```

### Cognito User Pool Configuration

**Required Settings:**
- Enable Google as identity provider
- Enable username/password authentication
- Custom attributes: `custom:roles` (string)
- User pool groups: `Users`, `Admins`, `Moderators`

### Lambda Function (Backend)

User information is automatically available in the request context:

```java
// User info is automatically available in request context
Map<String, Object> authorizer = requestContext.get("authorizer");
Map<String, String> claims = (Map<String, String>) authorizer.get("claims");

String userId = claims.get("sub");
String email = claims.get("email");
String rolesString = claims.get("custom:roles");
List<String> roles = Arrays.asList(rolesString.split(","));

if (!roles.contains("USER")) {
    return response(403, "USER role required");
}
```

### Cognito Post-Confirmation Trigger

Lambda trigger to automatically assign USER role on registration:

```java
// Lambda trigger to assign USER role on registration
public class PostConfirmationTrigger implements RequestHandler<CognitoUserPoolEvent, CognitoUserPoolEvent> {
    @Override
    public CognitoUserPoolEvent handleRequest(CognitoUserPoolEvent event, Context context) {
        // Add user to "Users" group
        cognitoClient.adminAddUserToGroup(
            new AdminAddUserToGroupRequest()
                .withUserPoolId(event.getUserPoolId())
                .withUsername(event.getUserName())
                .withGroupName("Users")
        );
        
        return event;
    }
}
```

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

1. **Review and Approve Architecture**
   - Stakeholder review
   - Security review
   - Cost approval

2. **Set Up Development Environment**
   - Create Cognito User Pool in dev account
   - Configure Google OAuth credentials
   - Set up test users

3. **Start Phase 1 Implementation**
   - Follow implementation roadmap
   - Create feature branch
   - Implement backend changes first
   - Then frontend integration

4. **Testing**
   - Unit tests for Lambda functions
   - Integration tests for auth flows
   - E2E tests with Playwright

5. **Documentation**
   - API documentation
   - User guide for authentication
   - Admin guide for role management

---

## References

- [AWS Cognito Documentation](https://docs.aws.amazon.com/cognito/)
- [AWS Amplify Documentation](https://docs.amplify.aws/)
- [API Gateway Authorizers](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-use-lambda-authorizer.html)
- [OAuth 2.0 Best Practices](https://oauth.net/2/)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
