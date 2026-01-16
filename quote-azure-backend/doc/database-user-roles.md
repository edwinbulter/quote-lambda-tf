# Database-Based User Roles Implementation

## 🎯 Overview

This document describes how to implement a simple 2-role user management system using Azure Table Storage. The system supports only two roles: **USER** and **ADMIN**. ADMIN users can view all users and assign roles to any user via REST API calls.

## 🚀 Why Database-Based Roles?

| Feature | Hardcoded Config | Database-Based |
|---------|------------------|----------------|
| **Runtime Updates** | ❌ Requires redeployment | ✅ Instant changes |
| **Scalability** | ❌ Limited to config size | ✅ Handle thousands of users |
| **Audit Trail** | ❌ No tracking | ✅ Full audit history |
| **Multi-Environment** | ❌ Same config everywhere | ✅ Different roles per env |
| **Role Management UI** | ❌ No interface possible | ✅ Build admin dashboards |

## 📋 Architecture

### Data Model
```csharp
public class UserRole
{
    public string ObjectId { get; set; }      // Azure AD Object ID
    public string Email { get; set; }         // User email
    public string Role { get; set; }          // "USER" or "ADMIN" (only 2 roles)
    public DateTime CreatedAt { get; set; }   // When role was assigned
    public DateTime? UpdatedAt { get; set; }  // Last update time
    public string CreatedBy { get; set; }     // Who assigned the role
    public string? UpdatedBy { get; set; }    // Who last updated
}
```

### Role System
- **USER**: Standard user with basic access
- **ADMIN**: Administrator who can:
  - View all users and their roles
  - Assign USER role to any user
  - Assign ADMIN role to any user
  - Remove roles from any user

### Azure Table Storage
- **Table Name**: `UserRoles`
- **Partition Key**: `"USER"` (all users in same partition)
- **Row Key**: Azure AD Object ID (unique identifier)

## 🔧 Implementation Steps

### Step 1: Create Data Models

Create `Models/UserRole.cs`:
```csharp
namespace QuoteAzureBackend.Models
{
    public class UserRole
    {
        public string ObjectId { get; set; } = string.Empty;
        public string Email { get; set; } = string.Empty;
        public string Role { get; set; } = string.Empty;
        public DateTime CreatedAt { get; set; }
        public DateTime? UpdatedAt { get; set; }
        public string CreatedBy { get; set; } = string.Empty;
        public string? UpdatedBy { get; set; }
    }
}
```

### Step 2: Create Repository

Create `Data/UserRoleRepository.cs`:
```csharp
using Azure.Data.Tables;

public interface IUserRoleRepository
{
    Task<UserRole?> GetUserRoleAsync(string objectId);
    Task<bool> AssignRoleAsync(string objectId, string email, string role, string assignedBy);
    Task<bool> RemoveRoleAsync(string objectId);
    Task<IEnumerable<UserRole>> GetAllUsersAsync();
    Task<bool> IsUserInRoleAsync(string objectId, string role);
}

public class UserRoleRepository : IUserRoleRepository
{
    private readonly TableClient _tableClient;
    private const string TableName = "UserRoles";

    public UserRoleRepository(TableServiceClient tableServiceClient)
    {
        _tableClient = tableServiceClient.GetTableClient(TableName);
    }

    // Implementation methods...
}
```

### Step 3: Update Authentication Service

Modify `Services/AuthenticationService.cs`:
```csharp
public class AuthenticationService : IAuthenticationService
{
    private readonly IUserRoleRepository _userRoleRepository;

    public AuthenticationService(
        IConfiguration configuration,
        ILogger<AuthenticationService> logger,
        IUserRoleRepository userRoleRepository)
    {
        _userRoleRepository = userRoleRepository;
        // ... rest of constructor
    }

    public async Task<bool> IsUserInGroupAsync(string objectId, string groupName)
    {
        return await _userRoleRepository.IsUserInRoleAsync(objectId, groupName);
    }

    public async Task<bool> IsAdminAsync(string objectId)
    {
        return await IsUserInGroupAsync(objectId, "ADMIN");
    }
}
```

### Step 4: Create Admin API Controller

Create `Api/UserRoleController.cs`:
```csharp
[ApiController]
[Route("api/admin/[controller]")]
[Authorize]
public class UserRoleController : ControllerBase
{
    [HttpGet]
    public async Task<IActionResult> GetAllUsers()
    {
        var users = await _userRoleRepository.GetAllUsersAsync();
        return Ok(users);
    }

    [HttpPost("{objectId}/role")]
    public async Task<IActionResult> AssignRole(string objectId, [FromBody] AssignRoleRequest request)
    {
        var success = await _userRoleRepository.AssignRoleAsync(
            objectId, request.Email, request.Role, getCurrentUserId());
        return success ? Ok() : BadRequest();
    }

    [HttpDelete("{objectId}/role")]
    public async Task<IActionResult> RemoveRole(string objectId)
    {
        var success = await _userRoleRepository.RemoveRoleAsync(objectId);
        return success ? Ok() : BadRequest();
    }
}
```

### Step 5: Register Dependencies

In `Program.cs`:
```csharp
// Add Table Storage client
builder.Services.AddSingleton(new TableServiceClient(connectionString));

// Register repository
builder.Services.AddSingleton<IUserRoleRepository, UserRoleRepository>();
```

## 🚀 Deployment & Setup

### Step 1: Create Table Storage

The UserRoles table is automatically created by Terraform as part of the infrastructure deployment:

```hcl
# In infrastructure/main.tf
resource "azurerm_storage_table" "user_roles" {
  name                 = "UserRoles"
  storage_account_name = azurerm_storage_account.sa.name
}
```

After deployment, you can verify the table exists:
```bash
# Get table name from Terraform outputs
terraform output user_roles_table_name

# List tables in storage account
az storage table list --account-name $(terraform output storage_account_name) --output table
```

### Step 2: Initialize First Admin

After deployment, assign the first admin role:

```bash
# Get your Azure AD Object ID
az ad signed-in-user show --query objectId -o tsv

# Use the API to assign admin role
curl -X POST https://<function-url>/api/admin/userrole/<your-object-id>/role \
  -H "Authorization: Bearer <your-token>" \
  -H "Content-Type: application/json" \
  -d '{"role": "ADMIN", "email": "your-email@example.com"}'
```

### Step 3: Test Role Management

```bash
# Get all users with roles
curl -H "Authorization: Bearer <admin-token>" \
     https://<function-url>/api/admin/userrole

# Check specific user role
curl -H "Authorization: Bearer <admin-token>" \
     https://<function-url>/api/admin/userrole/<object-id>/role
```

## 📊 API Endpoints

### Role Management (Admin Only)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/userrole` | Get list of all users with their roles |
| GET | `/api/admin/userrole/{objectId}/role` | Get specific user's role |
| POST | `/api/admin/userrole/{objectId}/role` | Assign USER or ADMIN role to user |
| DELETE | `/api/admin/userrole/{objectId}/role` | Remove role from user |

### Request Examples

#### Assign USER Role
```json
POST /api/admin/userrole/{object-id}/role
{
  "role": "USER",
  "email": "user@example.com"
}
```

#### Assign ADMIN Role
```json
POST /api/admin/userrole/{object-id}/role
{
  "role": "ADMIN",
  "email": "admin@example.com"
}
```

#### Response
```json
{
  "objectId": "abc-123-def",
  "email": "user@example.com",
  "role": "ADMIN",
  "createdAt": "2024-01-15T10:30:00Z",
  "createdBy": "admin-object-id"
}
```

#### Get All Users
```json
GET /api/admin/userrole

Response:
[
  {
    "objectId": "abc-123-def",
    "email": "user@example.com",
    "role": "USER",
    "createdAt": "2024-01-15T10:30:00Z"
  },
  {
    "objectId": "xyz-789-uvw",
    "email": "admin@example.com",
    "role": "ADMIN",
    "createdAt": "2024-01-15T11:00:00Z"
  }
]
```

## 🔒 Security Considerations

### Authorization
- Only users with ADMIN role can manage other users' roles
- Role changes are tracked with who made the change
- All operations require valid Azure AD token

### Validation
- Role must be "USER" or "ADMIN"
- Object ID must be valid Azure AD format
- Email validation for user identification

### Audit Trail
- Every role assignment is recorded
- Track who assigned roles and when
- Maintain history of role changes


## 🚀 Initial Setup

### Step 1: Deploy Infrastructure
```bash
# Deploy Terraform with UserRoles table
terraform apply

# Verify table creation
terraform output user_roles_table_name
```

### Step 2: Initialize First Admin User
```bash
# Get your Azure AD Object ID
az ad signed-in-user show --query objectId -o tsv

# Create initial admin entry via API
curl -X POST https://<function-url>/api/admin/userrole/<your-object-id>/role \
  -H "Authorization: Bearer <your-token>" \
  -d '{"role": "ADMIN", "email": "your-email@example.com"}'
```

### Step 3: Verify Setup
```bash
# List all users with roles
curl -H "Authorization: Bearer <admin-token>" \
     https://<function-url>/api/admin/userrole

# Check your role
curl -H "Authorization: Bearer <your-token>" \
     https://<function-url>/api/admin/userrole/<your-object-id>/role
```

## 🧪 Testing

### Unit Tests
```csharp
[Test]
public async Task IsAdminAsync_WithAdminRole_ReturnsTrue()
{
    // Arrange
    var objectId = "admin-object-id";
    await _repository.AssignRoleAsync(objectId, "admin@test.com", "ADMIN", "system");
    
    // Act
    var result = await _authService.IsAdminAsync(objectId);
    
    // Assert
    Assert.IsTrue(result);
}
```

### Integration Tests
```csharp
[Test]
public async Task AssignRole_WithAdminToken_ReturnsSuccess()
{
    // Test role assignment via API
    var response = await _client.PostAsync($"/api/admin/userrole/{objectId}/role", 
        new StringContent("{\"role\":\"ADMIN\"}", Encoding.UTF8, "application/json"));
    
    Assert.AreEqual(HttpStatusCode.OK, response.StatusCode);
}
```

## 📚 Best Practices

1. **Always validate input** - Ensure roles are valid values
2. **Log all changes** - Maintain audit trail
3. **Use transactions** - For complex operations
4. **Implement caching** - For frequently accessed roles
5. **Regular cleanup** - Remove inactive users
6. **Monitor usage** - Track role management patterns

This simple 2-role system provides everything needed for effective user management while keeping the implementation clean and focused.
