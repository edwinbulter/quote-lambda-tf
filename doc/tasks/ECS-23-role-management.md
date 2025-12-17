# ECS-23: Role Management for Administrators

## Table of Contents
- [Overview](#overview)
- [User Stories](#user-stories)
  - [US-1: View All Users](#us-1-view-all-users)
  - [US-2: Manage User Roles](#us-2-manage-user-roles)
  - [US-3: Delete Users](#us-3-delete-users)
- [Requirements](#requirements)
  - [Navigation Structure](#navigation-structure)
  - [User Management Screen](#user-management-screen)
  - [Role Assignment](#role-assignment)
  - [User Deletion](#user-deletion)
- [Implementation Steps](#implementation-steps)
  - [Phase 1: Backend Updates](#phase-1-backend-updates)
  - [Phase 2: Frontend - User Management Screen](#phase-2-frontend---user-management-screen)
  - [Phase 3: Frontend - API Integration](#phase-3-frontend---api-integration)
  - [Phase 4: Frontend - App Integration](#phase-4-frontend---app-integration)
  - [Phase 5: Styling](#phase-5-styling)
  - [Phase 6: Testing](#phase-6-testing)
- [Technical Considerations](#technical-considerations)
- [API Endpoints Summary](#api-endpoints-summary)
- [Security Considerations](#security-considerations)
- [Acceptance Criteria](#acceptance-criteria)
- [Estimated Effort](#estimated-effort)
- [Dependencies](#dependencies)
- [Future Enhancements](#future-enhancements)

## Overview
Implement a role management interface accessible only to ADMIN users, allowing them to view all users in the system and manage their role assignments (USER and ADMIN groups).

## User Stories

### US-1: View All Users
**As an** administrator  
**I want to** see a list of all users in the system  
**So that** I can understand who has access and what roles they have

### US-2: Manage User Roles
**As an** administrator  
**I want to** add or remove users from USER and ADMIN groups  
**So that** I can control access permissions for different users

### US-3: Delete Users
**As an** administrator  
**I want to** delete users from the system  
**So that** I can remove inactive or unauthorized users and clean up their data

## Requirements

### Navigation Structure
```
Sidepanel
└── Manage Button (enabled only when signed in)
    └── Management Screen
        ├── Manage Favourites (enabled only for USER role)
        ├── Viewed Quotes (enabled only for USER role)
        └── User Management (enabled only for ADMIN role)
```

### User Management Screen

#### Features
1. **Display users table** with columns:
   - Username (preferred_username or Cognito username)
   - Email
   - USER role (checkbox or toggle)
   - ADMIN role (checkbox or toggle)
   - Last sign-in (optional, if available)

2. **Role assignment functionality**
   - Toggle/checkbox for USER group membership
   - Toggle/checkbox for ADMIN group membership
   - Changes apply immediately with optimistic updates
   - Visual feedback (spinner during save, checkmark on success)
   - Error handling with rollback if save fails

3. **User filtering and search** (optional for v1)
   - Search by username or email
   - Filter by role (show only ADMINs, show only USERs, show all)

4. **Delete User Functionality**
   - Delete button for each user (red/warning color)
   - Confirmation dialog before deletion
   - Cannot delete yourself (prevent self-deletion)
   - Deletes user from Cognito User Pool
   - Removes all user data from DynamoDB tables:
     - `user_likes_table` - All likes by this user
     - `user_views_table` - All view history for this user
   - Shows success/error message after deletion
   - User list refreshes after successful deletion

5. **Constraints**
   - Cannot remove yourself from ADMIN group (prevent lockout)
   - Show warning when removing last ADMIN (optional)
   - Cannot delete yourself (prevent self-deletion)

6. **Navigation**
   - Back button to return to Management screen
   - Screen uses full space of quoteview and favourites component area

#### Access Control
- Only visible to authenticated users
- Only enabled for users with ADMIN role
- Show appropriate message if user is not an ADMIN

### Role Assignment

#### USER Role
- Allows user to like quotes
- Allows user to view favourites and history
- Can be assigned/removed by ADMINs

#### ADMIN Role
- Inherits all USER permissions
- Can manage other users' roles
- Can view user management screen
- Can be assigned/removed by other ADMINs (except self)

### User Deletion

#### Deletion Process
1. **Confirmation Dialog**
   - Display user information (username, email)
   - Warning message about permanent deletion
   - Confirmation buttons (Cancel / Delete)

2. **Data Cleanup**
   - Remove user from Cognito User Pool
   - Delete all records in `user_likes_table` for this user
   - Delete all records in `user_views_table` for this user
   - Cascade delete any related data

3. **Constraints**
   - Cannot delete yourself
   - Cannot delete the last ADMIN user (optional, for v2)
   - Deletion is permanent and cannot be undone

#### Error Handling
- If Cognito deletion fails, show error and don't delete DynamoDB data
- If DynamoDB deletion fails, show warning but continue
- Provide clear error messages to the user

## Implementation Steps

### Phase 1: Backend Updates

#### 1.1 Create User Management Endpoints

**File:** `quote-lambda-tf-backend/src/main/java/ebulter/quote/lambda/QuoteHandler.java`

Add new endpoints:

**GET /admin/users** - List all users
- Requires ADMIN role
- Returns list of users with their groups
- Response format:
  ```json
  [
    {
      "username": "user123",
      "email": "user@example.com",
      "groups": ["USER"],
      "enabled": true,
      "userStatus": "CONFIRMED",
      "userCreateDate": "2024-01-15T10:30:00Z",
      "userLastModifiedDate": "2024-01-20T14:45:00Z"
    }
  ]
  ```

**POST /admin/users/{username}/groups/{groupName}** - Add user to group
- Requires ADMIN role
- Path parameters: `username`, `groupName` (USER or ADMIN)
- Returns 204 No Content on success
- Returns 400 if trying to remove self from ADMIN group
- Returns 404 if user or group not found

**DELETE /admin/users/{username}/groups/{groupName}** - Remove user from group
- Requires ADMIN role
- Path parameters: `username`, `groupName` (USER or ADMIN)
- Returns 204 No Content on success
- Returns 400 if trying to remove self from ADMIN group
- Returns 404 if user or group not found

**DELETE /admin/users/{username}** - Delete user
- Requires ADMIN role
- Path parameter: `username`
- Returns 204 No Content on success
- Returns 400 if trying to delete self
- Returns 404 if user not found
- Deletes user from Cognito and all related DynamoDB records

#### 1.2 Create Admin Service

**File:** `quote-lambda-tf-backend/src/main/java/ebulter/quote/lambda/service/AdminService.java`

```java
public class AdminService {
    private final CognitoIdentityProviderClient cognitoClient;
    private final String userPoolId;

    public List<UserInfo> listAllUsers() {
        // Use AdminListGroupsForUser to get user's groups
        // Use ListUsers to get all users
        // Combine data and return
    }

    public void addUserToGroup(String username, String groupName) {
        // Validate groupName is USER or ADMIN
        // Use AdminAddUserToGroup
    }

    public void removeUserFromGroup(String username, String groupName, String requestingUsername) {
        // Prevent removing self from ADMIN
        if (groupName.equals("ADMIN") && username.equals(requestingUsername)) {
            throw new IllegalArgumentException("Cannot remove yourself from ADMIN group");
        }
        // Use AdminRemoveUserFromGroup
    }

    public void deleteUser(String username, String requestingUsername) {
        // Prevent deleting self
        if (username.equals(requestingUsername)) {
            throw new IllegalArgumentException("Cannot delete yourself");
        }
        // Delete user from Cognito
        // Delete all user records from DynamoDB tables
    }
}
```

#### 1.3 Update Authorization Logic

**File:** `quote-lambda-tf-backend/src/main/java/ebulter/quote/lambda/QuoteHandler.java`

Add authorization check for admin endpoints:
```java
private void requireAdminRole(APIGatewayProxyRequestEvent request) {
    String username = extractUsername(request);
    List<String> groups = extractGroups(request);
    
    if (!groups.contains("ADMIN")) {
        throw new UnauthorizedException("ADMIN role required");
    }
}
```

#### 1.4 Add IAM Permissions for Cognito and DynamoDB

**File:** `quote-lambda-tf-backend/infrastructure/iam.tf`

Add Cognito and DynamoDB permissions to Lambda execution role:
```hcl
statement {
  effect = "Allow"
  actions = [
    "cognito-idp:ListUsers",
    "cognito-idp:AdminListGroupsForUser",
    "cognito-idp:AdminAddUserToGroup",
    "cognito-idp:AdminRemoveUserFromGroup",
    "cognito-idp:AdminGetUser",
    "cognito-idp:AdminDeleteUser"
  ]
  resources = [
    aws_cognito_user_pool.quote_user_pool.arn
  ]
}

statement {
  effect = "Allow"
  actions = [
    "dynamodb:Query",
    "dynamodb:DeleteItem"
  ]
  resources = [
    aws_dynamodb_table.user_likes_table.arn,
    "${aws_dynamodb_table.user_likes_table.arn}/index/*",
    aws_dynamodb_table.user_views.arn,
    "${aws_dynamodb_table.user_views.arn}/index/*"
  ]
}
```

### Phase 2: Frontend - User Management Screen

#### 2.1 Create User Management Component

**File:** `quote-lambda-tf-frontend/src/components/UserManagementScreen.tsx`

```typescript
interface UserManagementScreenProps {
  onBack: () => void;
}

interface UserInfo {
  username: string;
  email: string;
  groups: string[];
  enabled: boolean;
  userStatus: string;
}

export function UserManagementScreen({ onBack }: UserManagementScreenProps) {
  const [users, setUsers] = useState<UserInfo[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);
  const { user } = useAuth(); // Get current user to prevent self-removal from ADMIN

  useEffect(() => {
    loadUsers();
  }, []);

  const loadUsers = async () => {
    try {
      setLoading(true);
      const userList = await adminApi.listUsers();
      setUsers(userList);
    } catch (error) {
      console.error('Failed to load users:', error);
      showToast('Failed to load users', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleToggleRole = async (username: string, groupName: string, currentlyInGroup: boolean) => {
    // Prevent removing self from ADMIN
    if (groupName === 'ADMIN' && username === user?.username && currentlyInGroup) {
      showToast('Cannot remove yourself from ADMIN group', 'error');
      return;
    }

    const previousUsers = [...users];
    
    // Optimistic update
    const updatedUsers = users.map(u => {
      if (u.username === username) {
        const newGroups = currentlyInGroup
          ? u.groups.filter(g => g !== groupName)
          : [...u.groups, groupName];
        return { ...u, groups: newGroups };
      }
      return u;
    });
    setUsers(updatedUsers);

    try {
      if (currentlyInGroup) {
        await adminApi.removeUserFromGroup(username, groupName);
        showToast(`Removed ${username} from ${groupName}`, 'success');
      } else {
        await adminApi.addUserToGroup(username, groupName);
        showToast(`Added ${username} to ${groupName}`, 'success');
      }
    } catch (error) {
      console.error('Failed to update role:', error);
      setUsers(previousUsers);
      showToast('Failed to update role', 'error');
    }
  };

  const handleDeleteUser = (username: string) => {
    // Show confirmation dialog
    const userToDelete = users.find(u => u.username === username);
    if (!userToDelete) return;

    const confirmed = window.confirm(
      `Are you sure you want to delete user "${username}" (${userToDelete.email})? This action cannot be undone.\n\nAll user data including likes and view history will be permanently deleted.`
    );

    if (!confirmed) return;

    deleteUserWithCleanup(username);
  };

  const deleteUserWithCleanup = async (username: string) => {
    const previousUsers = [...users];
    
    // Optimistic update - remove user from list
    const updatedUsers = users.filter(u => u.username !== username);
    setUsers(updatedUsers);

    try {
      await adminApi.deleteUser(username);
      showToast(`User "${username}" and all their data have been deleted`, 'success');
    } catch (error) {
      console.error('Failed to delete user:', error);
      setUsers(previousUsers);
      showToast('Failed to delete user', 'error');
    }
  };

  const showToast = (message: string, type: 'success' | 'error') => {
    setToast({ message, type });
  };

  return (
    <div className="user-management-screen">
      <div className="user-management-header">
        <button className="back-button" onClick={onBack}>
          ← Back
        </button>
        <h2>User Management</h2>
      </div>

      {loading ? (
        <div className="loading">Loading users...</div>
      ) : users.length === 0 ? (
        <div className="empty-state">No users found.</div>
      ) : (
        <div className="users-table-container">
          <table className="users-table">
            <thead>
              <tr>
                <th>Username</th>
                <th>Email</th>
                <th>USER Role</th>
                <th>ADMIN Role</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {users.map((userInfo) => {
                const isUser = userInfo.groups.includes('USER');
                const isAdmin = userInfo.groups.includes('ADMIN');
                const isSelf = userInfo.username === user?.username;

                return (
                  <tr key={userInfo.username}>
                    <td className="username-cell">
                      {userInfo.username}
                      {isSelf && <span className="self-badge"> (You)</span>}
                    </td>
                    <td className="email-cell">{userInfo.email}</td>
                    <td className="role-cell">
                      <label className="role-toggle">
                        <input
                          type="checkbox"
                          checked={isUser}
                          onChange={() => handleToggleRole(userInfo.username, 'USER', isUser)}
                        />
                        <span className="toggle-label">
                          {isUser ? '✓ USER' : 'Add USER'}
                        </span>
                      </label>
                    </td>
                    <td className="role-cell">
                      <label className="role-toggle">
                        <input
                          type="checkbox"
                          checked={isAdmin}
                          onChange={() => handleToggleRole(userInfo.username, 'ADMIN', isAdmin)}
                          disabled={isSelf && isAdmin}
                          title={isSelf && isAdmin ? 'Cannot remove yourself from ADMIN' : ''}
                        />
                        <span className="toggle-label">
                          {isAdmin ? '✓ ADMIN' : 'Add ADMIN'}
                        </span>
                      </label>
                    </td>
                    <td className="actions-cell">
                      <button
                        className="delete-button"
                        onClick={() => handleDeleteUser(userInfo.username)}
                        disabled={isSelf}
                        title={isSelf ? 'Cannot delete yourself' : 'Delete user'}
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast(null)}
        />
      )}
    </div>
  );
}
```

### Phase 3: Frontend - API Integration

#### 3.1 Create Admin API Module

**File:** `quote-lambda-tf-frontend/src/api/adminApi.ts`

```typescript
import { BASE_URL } from "../constants/constants";
import { fetchAuthSession } from 'aws-amplify/auth';

async function getAuthHeaders(): Promise<HeadersInit> {
    try {
        const session = await fetchAuthSession();
        const token = session.tokens?.accessToken?.toString();
        if (token) {
            return {
                'Authorization': token,
            };
        }
    } catch (error) {
        console.error('Failed to get auth token:', error);
    }
    return {};
}

interface UserInfo {
    username: string;
    email: string;
    groups: string[];
    enabled: boolean;
    userStatus: string;
    userCreateDate?: string;
    userLastModifiedDate?: string;
}

async function listUsers(): Promise<UserInfo[]> {
    const authHeaders = await getAuthHeaders();
    const response = await fetch(`${BASE_URL}/admin/users`, {
        method: "GET",
        headers: {
            ...authHeaders,
        },
    });
    
    if (!response.ok) {
        throw new Error(`Failed to fetch users: ${response.status} ${response.statusText}`);
    }
    
    return await response.json();
}

async function addUserToGroup(username: string, groupName: string): Promise<void> {
    const authHeaders = await getAuthHeaders();
    const response = await fetch(`${BASE_URL}/admin/users/${encodeURIComponent(username)}/groups/${groupName}`, {
        method: "POST",
        headers: {
            ...authHeaders,
        },
    });
    
    if (!response.ok) {
        throw new Error(`Failed to add user to group: ${response.status} ${response.statusText}`);
    }
}

async function removeUserFromGroup(username: string, groupName: string): Promise<void> {
    const authHeaders = await getAuthHeaders();
    const response = await fetch(`${BASE_URL}/admin/users/${encodeURIComponent(username)}/groups/${groupName}`, {
        method: "DELETE",
        headers: {
            ...authHeaders,
        },
    });
    
    if (!response.ok) {
        throw new Error(`Failed to remove user from group: ${response.status} ${response.statusText}`);
    }
}

async function deleteUser(username: string): Promise<void> {
    const authHeaders = await getAuthHeaders();
    const response = await fetch(`${BASE_URL}/admin/users/${encodeURIComponent(username)}`, {
        method: "DELETE",
        headers: {
            ...authHeaders,
        },
    });
    
    if (!response.ok) {
        throw new Error(`Failed to delete user: ${response.status} ${response.statusText}`);
    }
}

export default {
    listUsers,
    addUserToGroup,
    removeUserFromGroup,
    deleteUser,
};
```

### Phase 4: Frontend - App Integration

#### 4.1 Update ManagementScreen Component

**File:** `quote-lambda-tf-frontend/src/components/ManagementScreen.tsx`

Add new navigation option:
```typescript
interface ManagementScreenProps {
    onBack: () => void;
    onNavigateToFavourites: () => void;
    onNavigateToViewedQuotes: () => void;
    onNavigateToUserManagement: () => void;  // NEW
    hasUserRole: boolean;
    hasAdminRole: boolean;  // NEW
}

// In render:
<button
    className="management-menu-item"
    onClick={onNavigateToUserManagement}
    disabled={!hasAdminRole}
    title={!hasAdminRole ? 'ADMIN role required' : ''}
>
    User Management
</button>
```

#### 4.2 Update App.tsx

**File:** `quote-lambda-tf-frontend/src/App.tsx`

Update management view state:
```typescript
const [managementView, setManagementView] = useState<'main' | 'favourites' | 'viewed' | 'users'>('main');
```

Add conditional rendering for user management:
```typescript
{showManagement ? (
    managementView === 'main' ? (
        <ManagementScreen
            onBack={closeManagement}
            onNavigateToFavourites={() => setManagementView('favourites')}
            onNavigateToViewedQuotes={() => setManagementView('viewed')}
            onNavigateToUserManagement={() => setManagementView('users')}
            hasUserRole={hasRole('USER')}
            hasAdminRole={hasRole('ADMIN')}
        />
    ) : managementView === 'favourites' ? (
        <ManageFavouritesScreen onBack={() => setManagementView('main')} />
    ) : managementView === 'viewed' ? (
        <ViewedQuotesScreen onBack={() => setManagementView('main')} />
    ) : (
        <UserManagementScreen onBack={() => setManagementView('main')} />
    )
) : ...
```

### Phase 5: Styling

#### 5.1 Create CSS File

**File:** `quote-lambda-tf-frontend/src/components/UserManagementScreen.css`

```css
.user-management-screen {
    position: absolute;
    top: 0;
    left: 141px;
    width: calc(100vw - 141px);
    height: 100vh;
    background-color: white;
    display: flex;
    flex-direction: column;
    padding: 20px;
    box-sizing: border-box;
    overflow: hidden;
}

.user-management-header {
    display: flex;
    align-items: center;
    gap: 20px;
    margin-bottom: 20px;
    flex-shrink: 0;
}

.user-management-header h2 {
    margin: 0;
    color: green;
    font-size: 28px;
}

.users-table-container {
    flex: 1;
    overflow-y: auto;
}

.users-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 16px;
}

.users-table th,
.users-table td {
    padding: 12px;
    text-align: left;
    border-bottom: 1px solid #ddd;
}

.users-table th {
    background-color: #f8f9fa;
    font-weight: 600;
    color: green;
    position: sticky;
    top: 0;
}

.users-table tbody tr:hover {
    background-color: #f5f5f5;
}

.username-cell {
    font-weight: 500;
}

.self-badge {
    color: green;
    font-size: 12px;
    font-weight: 600;
}

.email-cell {
    color: #666;
}

.role-cell {
    text-align: center;
}

.role-toggle {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
}

.role-toggle input[type="checkbox"] {
    width: 20px;
    height: 20px;
    cursor: pointer;
}

.role-toggle input[type="checkbox"]:disabled {
    cursor: not-allowed;
    opacity: 0.5;
}

.toggle-label {
    font-size: 14px;
}

.actions-cell {
    text-align: center;
}

.delete-button {
    padding: 6px 12px;
    background-color: #dc3545;
    color: white;
    border: none;
    border-radius: 4px;
    cursor: pointer;
    font-size: 14px;
    font-weight: 500;
    transition: background-color 0.2s;
}

.delete-button:hover:not(:disabled) {
    background-color: #c82333;
}

.delete-button:disabled {
    background-color: #ccc;
    cursor: not-allowed;
    opacity: 0.6;
}

@media (max-width: 768px) {
    .user-management-screen {
        left: 0;
        width: 100vw;
        padding: 15px;
    }

    .users-table {
        font-size: 14px;
    }

    .users-table th,
    .users-table td {
        padding: 8px;
    }
}
```

### Phase 6: Testing

#### 6.1 Backend Testing
- Test list users endpoint returns all users with correct groups
- Test add user to group (USER and ADMIN)
- Test remove user from group (USER and ADMIN)
- Test authorization (only ADMINs can access)
- Test self-removal prevention (cannot remove self from ADMIN)
- Test with non-existent users and groups

#### 6.2 Frontend Testing
- Test user list loads correctly
- Test role toggle (add/remove USER and ADMIN)
- Test self-removal prevention (ADMIN checkbox disabled for self)
- Test optimistic updates and rollback on error
- Test toast notifications
- Test navigation flow
- Test with different user roles (ADMIN vs non-ADMIN)
- Test delete button is disabled for self
- Test delete confirmation dialog appears
- Test user is removed from list after successful deletion
- Test error handling when deletion fails

#### 6.3 Integration Testing
- Test full flow: ADMIN adds user to USER group → user can like quotes
- Test full flow: ADMIN adds user to ADMIN group → user can access user management
- Test full flow: ADMIN removes user from USER group → user cannot like quotes
- Test edge case: Try to remove self from ADMIN (should fail with error message)
- Test full flow: ADMIN deletes user → user is removed from Cognito and all DynamoDB records are deleted
- Test edge case: Try to delete self (should fail with error message)
- Test edge case: Deleted user's likes and view history are completely removed from DynamoDB

## Technical Considerations

### Backend
1. **Cognito Integration**: Use AWS Cognito Admin APIs to manage user groups and deletion
   - `ListUsers` - Get all users in user pool
   - `AdminListGroupsForUser` - Get groups for specific user
   - `AdminAddUserToGroup` - Add user to group
   - `AdminRemoveUserFromGroup` - Remove user from group
   - `AdminDeleteUser` - Delete user from Cognito User Pool

2. **Authorization**: Verify ADMIN role on all admin endpoints
   - Extract groups from JWT token
   - Reject requests without ADMIN group membership

3. **Self-removal prevention**: Backend must prevent ADMINs from removing themselves from ADMIN group
   - Check if requesting user is the same as target user
   - Return 400 Bad Request with clear error message

4. **User deletion**: Backend must handle cascading deletion
   - Prevent self-deletion (return 400 Bad Request)
   - Delete user from Cognito User Pool using `AdminDeleteUser`
   - Query and delete all records from `user_likes_table` where `username` matches
   - Query and delete all records from `user_views_table` where `username` matches
   - Use DynamoDB Query with GSI to find records efficiently
   - Batch delete operations if needed for performance
   - Log deletion with audit trail (who deleted, when, which user)
   - Return 204 No Content on success

5. **Pagination**: Consider pagination for large user lists (100+ users)
   - Cognito ListUsers supports pagination
   - Can implement in v2 if needed

### Frontend
1. **State management**: User list is independent of main app state
   - Loaded fresh when screen opens
   - No need to sync with other components

2. **Optimistic updates**: Update UI immediately, rollback on error
   - Same pattern as favourites management
   - Provides responsive UX

3. **Self-removal prevention**: Disable ADMIN checkbox for current user
   - Visual indicator (disabled state)
   - Backend also enforces this rule

4. **User deletion**: Delete button with confirmation
   - Disabled for current user (cannot delete self)
   - Confirmation dialog before deletion
   - Optimistic update with rollback on error
   - Removes user from list after successful deletion

5. **Error handling**: Clear error messages for common scenarios
   - Network errors
   - Authorization errors
   - Self-removal attempts
   - Self-deletion attempts
   - Deletion failures

### Security
1. **Authorization at multiple layers**:
   - API Gateway: All requests require authentication
   - Lambda: Admin endpoints check for ADMIN role
   - Frontend: UI only shows admin features to ADMINs

2. **Audit logging**: Log all role changes in JSON format
   - Who made the change (requesting user)
   - What changed (target user, group, action: add/remove)
   - When it happened (ISO 8601 timestamp)
   - Result (success/failure)
   - Use structured JSON logging to CloudWatch Logs
   - Example log entry:
     ```json
     {
       "timestamp": "2024-01-15T10:30:45.123Z",
       "level": "INFO",
       "event": "role_change",
       "requestingUser": "admin@example.com",
       "targetUser": "user@example.com",
       "group": "USER",
       "action": "add",
       "result": "success"
     }
     ```

3. **Rate limiting**: Protect admin endpoints from abuse
   - API Gateway throttling already configured
   - Consider additional rate limiting for admin operations

## API Endpoints Summary

### New Endpoints
- `GET /admin/users` - List all users with their groups (ADMIN only)
- `POST /admin/users/{username}/groups/{groupName}` - Add user to group (ADMIN only)
- `DELETE /admin/users/{username}/groups/{groupName}` - Remove user from group (ADMIN only)

### Existing Endpoints (no changes)
- All quote-related endpoints remain unchanged

## Security Considerations

### Access Control
- **ADMIN role required**: All admin endpoints require ADMIN group membership
- **Self-removal prevention**: Cannot remove self from ADMIN group
- **JWT validation**: All requests validated via Cognito authorizer
- **Group validation**: Only USER and ADMIN groups are valid

### Audit Trail
- **CloudWatch Logs**: All admin operations logged to CloudWatch in JSON format
- **Structured logging**: Use JSON format for all log entries to enable easy querying and analysis
- **Log fields**: 
  - `timestamp` (ISO 8601)
  - `level` (INFO, WARN, ERROR)
  - `event` (role_change, list_users, etc.)
  - `requestingUser` (username of admin performing action)
  - `targetUser` (username being modified)
  - `group` (USER or ADMIN)
  - `action` (add, remove, list)
  - `result` (success, failure)
  - `errorMessage` (if result is failure)
- **Retention**: 30 days (configurable)
- **Query examples**:
  - Find all role changes: `{ $.event = "role_change" }`
  - Find failed operations: `{ $.result = "failure" }`
  - Find changes by specific admin: `{ $.requestingUser = "admin@example.com" }`

### Error Handling
- **Sensitive information**: Do not expose internal errors to frontend
- **Generic errors**: Return generic error messages for security
- **Detailed logging**: Log detailed errors server-side in JSON format for debugging
  - Example error log:
    ```json
    {
      "timestamp": "2024-01-15T10:30:45.123Z",
      "level": "ERROR",
      "event": "role_change",
      "requestingUser": "admin@example.com",
      "targetUser": "user@example.com",
      "group": "ADMIN",
      "action": "remove",
      "result": "failure",
      "errorMessage": "Cannot remove self from ADMIN group",
      "errorCode": "SELF_REMOVAL_FORBIDDEN"
    }
    ```

## Acceptance Criteria

### User Management Screen
- [ ] ADMIN users can view list of all users
- [ ] Each user shows username, email, and current roles
- [ ] ADMIN can add users to USER group
- [ ] ADMIN can remove users from USER group
- [ ] ADMIN can add users to ADMIN group
- [ ] ADMIN can remove users from ADMIN group
- [ ] Cannot remove self from ADMIN group (checkbox disabled)
- [ ] ADMIN can delete users (delete button visible)
- [ ] Cannot delete self (delete button disabled for current user)
- [ ] Confirmation dialog appears before deletion
- [ ] Deleted user is removed from list after successful deletion
- [ ] Optimistic updates with rollback on error
- [ ] Toast notifications for success/error
- [ ] Back button returns to Management screen

### Navigation
- [ ] User Management option appears in Management screen
- [ ] User Management is only enabled for ADMIN role
- [ ] Non-ADMIN users see disabled button with tooltip
- [ ] Navigation flow works correctly

### Authorization
- [ ] Only ADMIN users can access admin endpoints
- [ ] Non-ADMIN users get 403 Forbidden
- [ ] Unauthenticated users get 401 Unauthorized
- [ ] Self-removal from ADMIN group is prevented

### User Deletion
- [ ] Delete button appears for each user
- [ ] Delete button is disabled for current user
- [ ] Confirmation dialog shows username and email
- [ ] Confirmation dialog warns about permanent deletion
- [ ] User is removed from Cognito User Pool
- [ ] All user likes are deleted from DynamoDB
- [ ] All user view history is deleted from DynamoDB
- [ ] Success message shown after deletion
- [ ] User list refreshes after deletion
- [ ] Error message shown if deletion fails
- [ ] Cannot delete last ADMIN user (optional for v2)

### General
- [ ] All API calls include proper authentication
- [ ] Error handling for all failure scenarios
- [ ] Loading states during API calls
- [ ] Empty state when no users exist
- [ ] Responsive design works on mobile and desktop
- [ ] Audit logging for all admin operations including deletions

## Estimated Effort
- Backend: 8-10 hours
  - Cognito integration: 3-4 hours
  - Admin endpoints: 2-3 hours
  - Authorization logic: 1 hour
  - User deletion with DynamoDB cleanup: 2-3 hours
- Frontend: 7-9 hours
  - User management screen: 3-4 hours
  - API integration: 2 hours
  - App integration: 1-2 hours
  - Delete functionality and confirmation: 1-2 hours
- Testing: 5-7 hours
  - Backend deletion tests: 2-3 hours
  - Frontend deletion tests: 2-3 hours
  - Integration tests: 1 hour
- **Total: 20-26 hours** (2.5-3 days)

## Dependencies
- Existing authentication system (Cognito)
- Existing role-based access control (USER and ADMIN groups)
- Management screen infrastructure (from ECS-22)

## Future Enhancements
- **User search and filtering**: Search by username/email, filter by role
- **Bulk operations**: Add/remove multiple users to/from groups at once
- **User creation**: Allow ADMINs to create new users directly
- **Prevent last ADMIN deletion**: Prevent deletion of the last ADMIN user
- **Audit log viewer**: Show history of role changes and deletions
- **Email notifications**: Notify users when their roles change or account is deleted
- **Group management**: Create custom groups beyond USER and ADMIN
- **Permission granularity**: Fine-grained permissions beyond group membership
- **User details view**: Show more user information (last login, creation date, etc.)
- **Export user list**: Download user list as CSV/Excel
- **Soft delete**: Archive users instead of hard delete (for audit trail)
- **Data export before deletion**: Allow exporting user data before deletion
