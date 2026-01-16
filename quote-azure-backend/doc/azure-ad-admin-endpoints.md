# Azure AD Admin Endpoints Implementation Guide

## 🎯 Overview

This document describes how to implement admin endpoints for Azure AD authentication with database-based role management. The system uses Azure AD for authentication and Azure Table Storage for role management, supporting only two roles: **USER** and **ADMIN**.

## 🚀 Architecture Overview

### Authentication Flow
1. **User logs in** with Azure AD credentials
2. **JWT token** contains user object ID (`oid` claim)
3. **Middleware validates** token and extracts user info
4. **Database check** verifies if user has ADMIN role
5. **Access granted** based on database role assignment

### Role System
- **USER**: Standard user with basic access
- **ADMIN**: Administrator who can:
  - View all users and their roles
  - Assign USER role to any user
  - Assign ADMIN role to any user
  - Remove roles from any user

## 📋 Data Models

### AdminUserInfo Model
```csharp
// Models/Admin/AdminUserInfo.cs
namespace QuoteAzureBackend.Models.Admin
{
    public class AdminUserInfo
    {
        public string ObjectId { get; set; } = string.Empty;
        public string Email { get; set; } = string.Empty;
        public string DisplayName { get; set; } = string.Empty;
        public string Role { get; set; } = string.Empty;
        public DateTime CreatedAt { get; set; }
        public DateTime? UpdatedAt { get; set; }
        public string CreatedBy { get; set; } = string.Empty;
        public string? UpdatedBy { get; set; }
        public bool Enabled { get; set; }
    }
}
```

### QuoteWithLikeCount Model
```csharp
// Models/Admin/QuoteWithLikeCount.cs
namespace QuoteAzureBackend.Models.Admin
{
    public class QuoteWithLikeCount
    {
        public int Id { get; set; }
        public string QuoteText { get; set; } = string.Empty;
        public string Author { get; set; } = string.Empty;
        public int LikeCount { get; set; }
        public DateTime CreatedAt { get; set; }
    }
}
```

### QuotePageResponse Model
```csharp
// Models/Admin/QuotePageResponse.cs
namespace QuoteAzureBackend.Models.Admin
{
    public class QuotePageResponse
    {
        public List<QuoteWithLikeCount> Quotes { get; set; } = new List<QuoteWithLikeCount>();
        public int TotalCount { get; set; }
        public int Page { get; set; }
        public int PageSize { get; set; }
        public int TotalPages { get; set; }
    }
}
```

### QuoteAddResponse Model
```csharp
// Models/Admin/QuoteAddResponse.cs
namespace QuoteAzureBackend.Models.Admin
{
    public class QuoteAddResponse
    {
        public int QuotesAdded { get; set; }
        public int TotalQuotes { get; set; }
        public string Message { get; set; } = string.Empty;
    }
}
```

## 🔧 AdminService Implementation

### IAdminService Interface
```csharp
// Services/IAdminService.cs
using QuoteAzureBackend.Models.Admin;

namespace QuoteAzureBackend.Services
{
    public interface IAdminService
    {
        Task<List<AdminUserInfo>> ListAllUsersAsync();
        Task<QuotePageResponse> GetQuotesAsync(int page, int pageSize, string? quoteText, string? author, string? sortBy, string? sortOrder);
        Task<QuoteAddResponse> FetchAndAddNewQuotesAsync(string requestingUsername);
        Task<int> GetTotalLikesAsync();
    }
}
```

### AdminService Implementation
```csharp
// Services/AdminService.cs
using QuoteAzureBackend.Models.Admin;
using QuoteAzureBackend.Data;

namespace QuoteAzureBackend.Services
{
    public class AdminService : IAdminService
    {
        private readonly IUserRoleRepository _userRoleRepository;
        private readonly IQuoteService _quoteService;
        private readonly ILogger<AdminService> _logger;

        public AdminService(
            IUserRoleRepository userRoleRepository,
            IQuoteService quoteService,
            ILogger<AdminService> logger)
        {
            _userRoleRepository = userRoleRepository;
            _quoteService = quoteService;
            _logger = logger;
        }

        public async Task<List<AdminUserInfo>> ListAllUsersAsync()
        {
            _logger.LogInformation("Listing all users from database roles");
            
            try
            {
                var userRoles = await _userRoleRepository.GetAllUsersAsync();
                var adminUsers = new List<AdminUserInfo>();
                
                foreach (var userRole in userRoles)
                {
                    var adminUser = new AdminUserInfo
                    {
                        ObjectId = userRole.ObjectId,
                        Email = userRole.Email,
                        DisplayName = userRole.Email, // Could be enhanced with Azure AD lookup
                        Role = userRole.Role,
                        CreatedAt = userRole.CreatedAt,
                        UpdatedAt = userRole.UpdatedAt,
                        CreatedBy = userRole.CreatedBy,
                        UpdatedBy = userRole.UpdatedBy,
                        Enabled = true // Azure AD users are enabled by default
                    };
                    
                    adminUsers.Add(adminUser);
                }
                
                _logger.LogInformation("Successfully listed {Count} users", adminUsers.Count);
                return adminUsers;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to list users");
                throw new InvalidOperationException("Failed to list users: " + ex.Message, ex);
            }
        }

        public async Task<QuotePageResponse> GetQuotesAsync(int page, int pageSize, string? quoteText, string? author, string? sortBy, string? sortOrder)
        {
            _logger.LogInformation("Getting quotes with filters - Page: {Page}, Size: {PageSize}", page, pageSize);
            
            try
            {
                // Use existing quote service with admin parameters
                var quotes = await _quoteService.GetQuotesAsync(page, pageSize, quoteText, author, sortBy, sortOrder);
                var totalCount = await _quoteService.GetTotalQuotesCountAsync(quoteText, author);
                
                return new QuotePageResponse
                {
                    Quotes = quotes.Select(q => new QuoteWithLikeCount
                    {
                        Id = q.Id,
                        QuoteText = q.QuoteText,
                        Author = q.Author,
                        LikeCount = q.LikeCount,
                        CreatedAt = q.CreatedAt
                    }).ToList(),
                    TotalCount = totalCount,
                    Page = page,
                    PageSize = pageSize,
                    TotalPages = (int)Math.Ceiling((double)totalCount / pageSize)
                };
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to get quotes");
                throw new InvalidOperationException("Failed to get quotes: " + ex.Message, ex);
            }
        }

        public async Task<QuoteAddResponse> FetchAndAddNewQuotesAsync(string requestingUsername)
        {
            _logger.LogInformation("Fetching and adding new quotes (requested by {RequestingUsername})", requestingUsername);
            
            try
            {
                var result = await _quoteService.FetchAndAddNewQuotesAsync();
                
                return new QuoteAddResponse
                {
                    QuotesAdded = result.QuotesAdded,
                    TotalQuotes = result.TotalQuotes,
                    Message = result.Message
                };
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to fetch and add new quotes");
                throw new InvalidOperationException("Failed to fetch and add new quotes: " + ex.Message, ex);
            }
        }

        public async Task<int> GetTotalLikesAsync()
        {
            _logger.LogInformation("Getting total likes count");
            
            try
            {
                return await _quoteService.GetTotalLikesAsync();
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to get total likes");
                throw new InvalidOperationException("Failed to get total likes: " + ex.Message, ex);
            }
        }
    }
}
```

## 🚀 AdminHandler Implementation

### AdminHandler Class Structure
```csharp
// Handlers/AdminHandler.cs
using Microsoft.Azure.Functions.Worker;
using Microsoft.Azure.Functions.Worker.Http;
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models.Admin;
using QuoteAzureBackend.Services;
using QuoteAzureBackend.Data;
using System.Net;

namespace QuoteAzureBackend.Handlers
{
    public class AdminHandler
    {
        private readonly IAdminService _adminService;
        private readonly IAuthenticationService _authService;
        private readonly ILogger<AdminHandler> _logger;

        public AdminHandler(
            IAdminService adminService,
            IAuthenticationService authService,
            ILogger<AdminHandler> logger)
        {
            _adminService = adminService;
            _authService = authService;
            _logger = logger;
        }

        private async Task<bool> IsCurrentUserAdmin(HttpRequestData req)
        {
            var objectId = req.Headers.TryGetValues("X-User-ObjectId", out var values) 
                ? values.FirstOrDefault() 
                : null;
            
            if (string.IsNullOrEmpty(objectId))
            {
                return false;
            }

            return await _authService.IsAdminAsync(objectId);
        }

        [Function("AdminListUsers")]
        public async Task<HttpResponseData> AdminListUsersAsync(
            [HttpTrigger(AuthorizationLevel.Anonymous, "get", Route = "admin/users")] HttpRequestData req)
        {
            try
            {
                if (!await IsCurrentUserAdmin(req))
                {
                    var forbiddenResponse = req.CreateResponse(HttpStatusCode.Forbidden);
                    await forbiddenResponse.WriteStringAsync("Admin access required");
                    return forbiddenResponse;
                }

                var users = await _adminService.ListAllUsersAsync();
                
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(users);
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error listing users");
                var errorResponse = req.CreateResponse(HttpStatusCode.InternalServerError);
                await errorResponse.WriteStringAsync("Internal server error");
                return errorResponse;
            }
        }

        [Function("AdminGetQuotes")]
        public async Task<HttpResponseData> AdminGetQuotesAsync(
            [HttpTrigger(AuthorizationLevel.Anonymous, "get", Route = "admin/quotes")] HttpRequestData req)
        {
            try
            {
                if (!await IsCurrentUserAdmin(req))
                {
                    var forbiddenResponse = req.CreateResponse(HttpStatusCode.Forbidden);
                    await forbiddenResponse.WriteStringAsync("Admin access required");
                    return forbiddenResponse;
                }

                // Parse query parameters
                var query = System.Web.HttpUtility.ParseQueryString(req.Url.Query);
                var page = int.TryParse(query["page"] ?? "1", out var p) ? p : 1;
                var pageSize = int.TryParse(query["pageSize"] ?? "10", out var ps) ? ps : 10;
                var quoteText = query["quoteText"];
                var author = query["author"];
                var sortBy = query["sortBy"];
                var sortOrder = query["sortOrder"];

                var quotes = await _adminService.GetQuotesAsync(page, pageSize, quoteText, author, sortBy, sortOrder);
                
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(quotes);
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting quotes");
                var errorResponse = req.CreateResponse(HttpStatusCode.InternalServerError);
                await errorResponse.WriteStringAsync("Internal server error");
                return errorResponse;
            }
        }

        [Function("AdminAddQuotes")]
        public async Task<HttpResponseData> AdminAddQuotesAsync(
            [HttpTrigger(AuthorizationLevel.Anonymous, "post", Route = "admin/quotes/fetch")] HttpRequestData req)
        {
            try
            {
                if (!await IsCurrentUserAdmin(req))
                {
                    var forbiddenResponse = req.CreateResponse(HttpStatusCode.Forbidden);
                    await forbiddenResponse.WriteStringAsync("Admin access required");
                    return forbiddenResponse;
                }

                var currentUserId = req.Headers.TryGetValues("X-User-ObjectId", out var values) 
                    ? values.FirstOrDefault() ?? "system"
                    : "system";

                var result = await _adminService.FetchAndAddNewQuotesAsync(currentUserId);
                
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(result);
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error adding quotes");
                var errorResponse = req.CreateResponse(HttpStatusCode.InternalServerError);
                await errorResponse.WriteStringAsync("Internal server error");
                return errorResponse;
            }
        }

        [Function("AdminGetStats")]
        public async Task<HttpResponseData> AdminGetStatsAsync(
            [HttpTrigger(AuthorizationLevel.Anonymous, "get", Route = "admin/stats")] HttpRequestData req)
        {
            try
            {
                if (!await IsCurrentUserAdmin(req))
                {
                    var forbiddenResponse = req.CreateResponse(HttpStatusCode.Forbidden);
                    await forbiddenResponse.WriteStringAsync("Admin access required");
                    return forbiddenResponse;
                }

                var totalLikes = await _adminService.GetTotalLikesAsync();
                
                var stats = new
                {
                    TotalLikes = totalLikes,
                    Timestamp = DateTime.UtcNow
                };
                
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(stats);
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting stats");
                var errorResponse = req.CreateResponse(HttpStatusCode.InternalServerError);
                await errorResponse.WriteStringAsync("Internal server error");
                return errorResponse;
            }
        }
    }
}
```

## 📊 API Endpoints

### Admin Management (Admin Only)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/admin/users` | Get list of all users with their roles |
| GET | `/admin/quotes` | Get quotes with pagination and filtering |
| POST | `/admin/quotes/fetch` | Fetch and add new quotes from external source |
| GET | `/admin/stats` | Get system statistics (total likes, etc.) |

### Role Management (Admin Only)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/admin/userrole` | Get list of all users with their roles |
| GET | `/admin/userrole/{objectId}/role` | Get specific user's role |
| POST | `/admin/userrole/{objectId}/role` | Assign USER or ADMIN role to user |
| DELETE | `/admin/userrole/{objectId}/role` | Remove role from user |

### Request Examples

#### Get All Users
```json
GET /admin/users

Response:
[
  {
    "objectId": "abc-123-def",
    "email": "user@example.com",
    "displayName": "John Doe",
    "role": "USER",
    "createdAt": "2024-01-15T10:30:00Z",
    "enabled": true
  },
  {
    "objectId": "xyz-789-uvw",
    "email": "admin@example.com",
    "displayName": "Jane Admin",
    "role": "ADMIN",
    "createdAt": "2024-01-15T11:00:00Z",
    "enabled": true
  }
]
```

#### Get Quotes with Pagination
```json
GET /admin/quotes?page=1&pageSize=5&author=Einstein

Response:
{
  "quotes": [
    {
      "id": 1,
      "quoteText": "Life is like riding a bicycle...",
      "author": "Albert Einstein",
      "likeCount": 42,
      "createdAt": "2024-01-15T10:30:00Z"
    }
  ],
  "totalCount": 15,
  "page": 1,
  "pageSize": 5,
  "totalPages": 3
}
```

#### Get System Stats
```json
GET /admin/stats

Response:
{
  "totalLikes": 1250,
  "timestamp": "2024-01-15T12:00:00Z"
}
```

## 🔒 Security Considerations

### Authentication
- All endpoints require valid Azure AD JWT token
- Token validation via `IAuthenticationService`
- User object ID extracted from `oid` claim

### Authorization
- Only users with ADMIN role can access admin endpoints
- Role verification via database (`IUserRoleRepository`)
- All operations logged with user identity

### Input Validation
- All query parameters validated
- Page size limited to prevent abuse
- SQL injection protection via parameterized queries

### Audit Trail
- All admin operations logged
- User identity tracked in logs
- Error details logged for debugging

## 🚀 Deployment & Setup

### Step 1: Update Program.cs
```csharp
// Register admin services
services.AddSingleton<IAdminService, AdminService>();
services.AddSingleton<IUserRoleRepository, UserRoleRepository>();

// Register authentication services
services.AddSingleton<IAuthenticationService, AuthenticationService>();

// Register Table Storage client
services.AddSingleton(sp => {
    var configuration = sp.GetRequiredService<IConfiguration>();
    var connectionString = configuration["TableStorageConnectionString"];
    return new TableServiceClient(connectionString);
});
```

### Step 2: Deploy Infrastructure
```bash
# Deploy Terraform with UserRoles table
terraform apply

# Verify table creation
terraform output user_roles_table_name
```

### Step 3: Initialize First Admin User
```bash
# Get your Azure AD Object ID
az ad signed-in-user show --query objectId -o tsv

# Create initial admin entry via API
curl -X POST https://<function-url>/admin/userrole/<your-object-id>/role \
  -H "Authorization: Bearer <your-token>" \
  -d '{"role": "ADMIN", "email": "your-email@example.com"}'
```

### Step 4: Test Admin Endpoints
```bash
# Get all users
curl -H "Authorization: Bearer <admin-token>" \
     https://<function-url>/admin/users

# Get quotes
curl -H "Authorization: Bearer <admin-token>" \
     https://<function-url>/admin/quotes?page=1&pageSize=10

# Get stats
curl -H "Authorization: Bearer <admin-token>" \
     https://<function-url>/admin/stats
```

## 🧪 Testing Strategy

### Unit Tests
```csharp
// Tests/Services/AdminServiceTests.cs
[TestClass]
public class AdminServiceTests
{
    [TestMethod]
    public async Task ListAllUsers_ReturnsUserList()
    {
        // Arrange
        var adminService = CreateAdminService();
        
        // Act
        var result = await adminService.ListAllUsersAsync();
        
        // Assert
        Assert.IsNotNull(result);
        Assert.IsTrue(result.Count > 0);
    }
    
    [TestMethod]
    public async Task GetQuotes_WithPagination_ReturnsPagedResults()
    {
        // Arrange
        var adminService = CreateAdminService();
        
        // Act
        var result = await adminService.GetQuotesAsync(1, 10, null, null, "createdAt", "desc");
        
        // Assert
        Assert.IsNotNull(result);
        Assert.IsTrue(result.Quotes.Count <= 10);
    }
}
```

### Integration Tests
```csharp
// Tests/Handlers/AdminHandlerTests.cs
[TestClass]
public class AdminHandlerTests
{
    [TestMethod]
    public async Task AdminListUsers_WithAdminToken_ReturnsUserList()
    {
        // Arrange
        var handler = CreateAdminHandler();
        var request = CreateAuthenticatedRequest(isAdmin: true);
        
        // Act
        var response = await handler.AdminListUsersAsync(request, _functionContext);
        
        // Assert
        Assert.AreEqual(HttpStatusCode.OK, response.StatusCode);
    }
}
```

## 📚 Best Practices

1. **Always validate input** - Ensure all parameters are valid
2. **Log all operations** - Maintain audit trail
3. **Use dependency injection** - Testable and maintainable code
4. **Handle errors gracefully** - Return appropriate HTTP status codes
5. **Implement rate limiting** - Prevent abuse of admin endpoints
6. **Regular security reviews** - Check for vulnerabilities

## 🔄 Migration from Java

### Key Differences
- **Authentication**: Azure AD JWT vs AWS Cognito tokens
- **User Management**: Database roles vs Cognito groups
- **Dependencies**: Azure SDK vs AWS SDK
- **Deployment**: Azure Functions vs AWS Lambda

### Migration Steps
1. **Deploy authentication layer** with Azure AD
2. **Create admin handlers** alongside existing handlers
3. **Test admin functionality** with Azure AD users
4. **Initialize database roles** for existing users
5. **Gradually migrate** existing admin functionality

This Azure AD-based admin system provides secure, scalable user management with database-driven role assignment! 🚀
