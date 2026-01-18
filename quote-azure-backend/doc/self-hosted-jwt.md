# Self-Hosted JWT Authentication

## Overview

The quote-azure-backend implements a comprehensive self-hosted JWT authentication system that provides secure user management without relying on external identity providers. This system handles user registration, login, role-based access control (RBAC), and session management using JSON Web Tokens.

## Key Features

### Authentication & Authorization
- **User Registration**: New users can create accounts with email, username, and password
- **Secure Login**: JWT-based authentication with configurable token expiration
- **Password Management**: Built-in password change functionality with secure hashing
- **Session Management**: Short-lived access tokens (24h) with refresh token support (7 days)

### Role-Based Access Control (RBAC)
- **Two-tier role system**: User and Admin roles
- **Admin endpoints**: Protected management functions for user and quote administration
- **Role assignment**: Admins can promote/demote users between roles
- **Endpoint protection**: All sensitive endpoints require valid JWT tokens

### User Management
- **Profile management**: Users can update their passwords
- **Account status**: Support for active/inactive user accounts
- **Admin oversight**: Admins can view and manage all user accounts
- **Audit trail**: Comprehensive logging of all authentication events

## Architecture

### Component Structure
```
Authentication Layer
├── Handlers (HTTP Endpoints)
│   ├── AuthHandler - Registration, Login, Password Change
│   └── UserManagementHandler - Role Management, User Admin
├── Services (Business Logic)
│   ├── JwtService - Token Generation/Validation
│   └── UserService - User Operations & Authorization
├── Data Layer
│   ├── UserRepository - Azure Table Storage Operations
│   └── UserRoleRepository - Role Assignment Storage
└── Middleware
    └── JwtAuthenticationMiddleware - Request Authentication
```

### Data Flow
1. **Authentication Request** → Handler validates credentials
2. **User Verification** → UserService checks against storage
3. **Token Generation** → JwtService creates signed JWT
4. **Response** → Client receives JWT for subsequent requests
5. **Protected Access** → Middleware validates JWT on each request

### Storage Architecture
- **Primary Storage**: Azure Table Storage (shared with application data)
- **Users Table**: Authentication data and user profiles
- **UserRoles Table**: Role assignments and audit trail
- **Integration**: Seamless integration with existing quote/like tables

## Technology Stack

### Core Technologies
- **.NET 8**: Modern, high-performance framework
- **Azure Functions**: Serverless compute platform
- **Azure Table Storage**: Scalable NoSQL data store
- **JWT (JSON Web Tokens)**: Industry-standard authentication tokens

### Security Components
- **ASP.NET Core Identity**: Password hashing and validation
- **HMAC-SHA256**: Cryptographic signing for JWT tokens
- **Configuration-based secrets**: Secure key management
- **HTTPS Enforcement**: TLS-protected communication

### Development Tools
- **Dependency Injection**: Loosely coupled architecture
- **Structured Logging**: Comprehensive audit trails
- **HTTP Client Testing**: Automated API validation

## API Endpoints

### Authentication Endpoints
- `POST /auth/register` - Create new user account
- `POST /auth/login` - Authenticate and receive JWT
- `PUT /auth/change-password` - Update user password
- `DELETE /auth/unregister` - Delete user account

### Management Endpoints (Admin Only)
- `GET /manage/users` - List all users
- `GET /manage/users/{userId}` - Get specific user details
- `PUT /manage/users/role` - Update user role
- `DELETE /manage/users/role` - Remove user role
- `GET /manage/quotes` - Admin quote management
- `POST /manage/quotes/fetch` - Fetch new quotes
- `DELETE /manage/quotes/{id}` - Delete quote
- `PUT /manage/quotes/{id}` - Update quote

### User Endpoints
- `GET /api/quote` - Get quotes (authenticated)
- `POST /api/quote/{id}/like` - Like a quote
- `DELETE /api/quote/{id}/unlike` - Unlike a quote
- `GET /api/quote/liked` - Get user's liked quotes
- `PUT /api/quote/{id}/reorder` - Reorder liked quotes

## Extending the System

### Adding New Roles
1. Update the `Role` validation in relevant services
2. Extend RBAC checks in handlers/middleware
3. Update role assignment logic in UserService
4. Consider adding role hierarchy if needed

### Custom Authentication Providers
- Implement `IJwtService` for custom token formats
- Extend `JwtAuthenticationMiddleware` for additional validation
- Add multi-factor authentication support
- Integrate with external identity providers if needed

### Enhanced User Features
- Email verification workflows
- Password reset functionality
- User profile customization
- Social login integration
- Account lockout policies

### Security Enhancements
- Rate limiting on authentication endpoints
- IP-based access restrictions
- Device fingerprinting
- Anomaly detection for login patterns
- Certificate-based authentication

### Monitoring & Analytics
- Authentication metrics dashboard
- Failed login attempt tracking
- Token usage analytics
- Performance monitoring
- Security event logging

## Configuration

### Required Settings
```json
{
  "Jwt": {
    "Key": "256-bit-secret-key",
    "Issuer": "quote-azure-backend",
    "Audience": "quote-azure-backend-users"
  },
  "TableStorageConnectionString": "Azure-Storage-Connection-String"
}
```

### Security Considerations
- JWT key must be at least 256 bits
- Use environment variables for secrets
- Enable HTTPS in production
- Regular key rotation recommended
- Consider using Azure Key Vault for secret management

## Best Practices

### Security
- Always validate JWT tokens on protected endpoints
- Use short-lived access tokens
- Implement proper logout functionality
- Log all authentication events
- Use secure password policies

### Performance
- Cache frequently accessed user data
- Optimize Table Storage queries
- Implement connection pooling
- Monitor token validation overhead
- Consider token blacklisting for immediate revocation

### Scalability
- Design for stateless authentication
- Use distributed caching if needed
- Plan for multi-region deployment
- Implement proper error handling
- Consider read replicas for user data

## Migration Notes

When transitioning from Azure AD or other providers:
- Plan user data migration carefully
- Maintain backward compatibility during transition
- Provide clear migration paths for existing users
- Update all client applications to use JWT
- Consider running both systems in parallel initially

## Troubleshooting

### Common Issues
- Token expiration errors
- Invalid signature problems
- Clock skew in distributed systems
- CORS configuration for web clients
- Storage connection issues

### Debugging Tools
- JWT token validation utilities
- Authentication flow logging
- Storage query analysis
- Performance profiling tools
- Security audit logs
