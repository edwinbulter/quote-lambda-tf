# Azure Backend Admin API Endpoints

This document describes all the admin API endpoints required by the admin frontend based on the `adminApi.ts` analysis.

## Base URL
```
https://quote-backend-function.azurewebsites.net/api
```

## Authentication
- **All endpoints require authentication** with admin privileges
- Authentication is done via `Authorization` header with JWT token
- Admin users must have appropriate group permissions

## Endpoints

### 1. User Management

#### GET /admin/users
- **Description**: List all users in the system
- **Authentication**: Required (Admin)
- **Response**: `UserInfo[]` array
- **Frontend Usage**: `listUsers()`
- **Current Status**: ❌ Not Implemented

#### POST /admin/users/{username}/groups/{groupName}
- **Description**: Add a user to a specific group
- **Authentication**: Required (Admin)
- **Parameters**: 
  - `username` (string) - Username to add to group (URL encoded)
  - `groupName` (string) - Group name
- **Response**: 204 No Content
- **Frontend Usage**: `addUserToGroup(username, groupName)`
- **Current Status**: ❌ Not Implemented

#### DELETE /admin/users/{username}/groups/{groupName}
- **Description**: Remove a user from a specific group
- **Authentication**: Required (Admin)
- **Parameters**: 
  - `username` (string) - Username to remove from group (URL encoded)
  - `groupName` (string) - Group name
- **Response**: 204 No Content
- **Frontend Usage**: `removeUserFromGroup(username, groupName)`
- **Current Status**: ❌ Not Implemented

#### DELETE /admin/users/{username}
- **Description**: Delete a user from the system
- **Authentication**: Required (Admin)
- **Parameters**: `username` (string) - Username to delete (URL encoded)
- **Response**: 204 No Content
- **Error Response**: Detailed error message in response body
- **Frontend Usage**: `deleteUser(username)`
- **Current Status**: ❌ Not Implemented

### 2. Quote Management

#### GET /admin/quotes
- **Description**: Get paginated list of quotes with filtering and sorting
- **Authentication**: Required (Admin)
- **Query Parameters**:
  - `page` (number) - Page number (default: 1)
  - `pageSize` (number) - Items per page (default: 50)
  - `quoteText` (string, optional) - Filter by quote text content
  - `author` (string, optional) - Filter by author name
  - `sortBy` (string, optional) - Field to sort by
  - `sortOrder` (string, optional) - Sort order (asc/desc)
- **Response**: `QuotePageResponse` object
- **Frontend Usage**: `getQuotes(page, pageSize, quoteText, author, sortBy, sortOrder)`
- **Current Status**: ❌ Not Implemented

#### POST /admin/quotes/fetch
- **Description**: Fetch and add new quotes from external sources
- **Authentication**: Required (Admin)
- **Response**: `QuoteAddResponse` object
- **Frontend Usage**: `fetchAndAddNewQuotes()`
- **Current Status**: ❌ Not Implemented

### 3. Analytics & Statistics

#### GET /admin/likes/total
- **Description**: Get total number of likes across all quotes
- **Authentication**: Required (Admin)
- **Response**: `{ totalLikes: number }`
- **Frontend Usage**: `getTotalLikes()`
- **Current Status**: ❌ Not Implemented

## Data Models

### UserInfo
```typescript
interface UserInfo {
    username: string;
    email: string;
    groups: string[];
    enabled: boolean;
    userStatus: string;
    userCreateDate?: string;
    userLastModifiedDate?: string;
}
```

### QuoteWithLikeCount
```typescript
interface QuoteWithLikeCount {
    id: number;
    quoteText: string;
    author: string;
    likeCount: number;
}
```

### QuotePageResponse
```typescript
interface QuotePageResponse {
    quotes: QuoteWithLikeCount[];
    totalCount: number;
    page: number;
    pageSize: number;
    totalPages: number;
}
```

### QuoteAddResponse
```typescript
interface QuoteAddResponse {
    quotesAdded: number;
    totalQuotes: number;
    message: string;
}
```

## Implementation Status

### ❌ Not Implemented (6/6)
- GET /admin/users
- POST /admin/users/{username}/groups/{groupName}
- DELETE /admin/users/{username}/groups/{groupName}
- DELETE /admin/users/{username}
- GET /admin/quotes
- POST /admin/quotes/fetch
- GET /admin/likes/total

## Priority Implementation Order

### High Priority (Core Admin Functionality)
1. GET /admin/users - User management dashboard
2. GET /admin/quotes - Quote management with pagination
3. POST /admin/users/{username}/groups/{groupName} - Group management
4. DELETE /admin/users/{username}/groups/{groupName} - Group management

### Medium Priority (Enhanced Features)
5. POST /admin/quotes/fetch - Bulk quote import
6. DELETE /admin/users/{username} - User deletion
7. GET /admin/likes/total - Analytics dashboard

## Security Considerations

### Authorization Requirements
- All endpoints require admin-level permissions
- Implement role-based access control (RBAC)
- Validate admin group membership before processing requests

### Input Validation
- Sanitize all input parameters
- Validate pagination limits (prevent excessive page sizes)
- URL-encode usernames to handle special characters
- Validate sorting fields to prevent SQL injection

### Error Handling
- Return appropriate HTTP status codes
- Provide detailed error messages for admin users
- Log administrative actions for audit trails

## Performance Considerations

### Pagination
- Implement efficient pagination for large datasets
- Consider cursor-based pagination for better performance
- Cache frequently accessed data (user lists, statistics)

### Database Queries
- Optimize queries for admin endpoints
- Use appropriate indexes for filtering and sorting
- Implement query timeouts to prevent resource exhaustion

## Notes

- The admin frontend uses AWS Cognito for authentication
- All endpoints support retry logic with exponential backoff
- The frontend shows backend restart notifications on failures
- Usernames are URL-encoded to handle special characters
- Error responses include detailed messages for debugging
- Admin operations should be logged for audit purposes
