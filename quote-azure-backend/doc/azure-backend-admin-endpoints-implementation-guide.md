# Admin Endpoints Implementation Guide

## Overview
This guide provides detailed instructions for translating the Java admin endpoints to C# and implementing them in the Azure Functions backend.

## 1. Model Translations

### 1.1 UserInfo Model
```csharp
// Models/Admin/UserInfo.cs
namespace QuoteAzureBackend.Models.Admin
{
    public class UserInfo
    {
        public string Username { get; set; } = string.Empty;
        public string Email { get; set; } = string.Empty;
        public List<string> Groups { get; set; } = new List<string>();
        public bool Enabled { get; set; }
        public string UserStatus { get; set; } = string.Empty;
        public string? UserCreateDate { get; set; }
        public string? UserLastModifiedDate { get; set; }
    }
}
```

### 1.2 QuoteWithLikeCount Model
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
    }
}
```

### 1.3 QuotePageResponse Model
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

### 1.4 QuoteAddResponse Model
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

## 2. AdminService Implementation

### 2.1 IAdminService Interface
```csharp
// Services/IAdminService.cs
using QuoteAzureBackend.Models.Admin;

namespace QuoteAzureBackend.Services
{
    public interface IAdminService
    {
        Task<List<UserInfo>> ListAllUsersAsync();
        Task AddUserToGroupAsync(string username, string groupName, string requestingUsername);
        Task RemoveUserFromGroupAsync(string username, string groupName, string requestingUsername);
        Task DeleteUserAsync(string username, string requestingUsername);
        Task<QuotePageResponse> GetQuotesAsync(int page, int pageSize, string? quoteText, string? author, string? sortBy, string? sortOrder);
        Task<QuoteAddResponse> FetchAndAddNewQuotesAsync(string requestingUsername);
        Task<int> GetTotalLikesAsync();
    }
}
```

### 2.2 AdminService Implementation (Key Methods)

#### ListAllUsersAsync
```csharp
// Services/AdminService.cs
public async Task<List<UserInfo>> ListAllUsersAsync()
{
    _logger.LogInformation("Listing all users from Azure AD B2C");
    
    try
    {
        var userInfoList = new List<UserInfo>();
        var users = await _graphServiceClient.Users.GetAsync(requestConfiguration =>
        {
            requestConfiguration.QueryParameters.Top = 999;
            requestConfiguration.QueryParameters.Select = new[] { "id", "displayName", "mail", "accountEnabled", "createdDateTime", "lastModifiedDateTime" };
        });
        
        if (users?.Value != null)
        {
            foreach (var user in users.Value)
            {
                // Get user's groups
                var groups = await GetUserGroupsAsync(user.Id);
                
                var userInfo = new UserInfo
                {
                    Username = user.DisplayName ?? user.Id,
                    Email = user.Mail ?? string.Empty,
                    Groups = groups,
                    Enabled = user.AccountEnabled ?? false,
                    UserStatus = user.AccountEnabled == true ? "Enabled" : "Disabled",
                    UserCreateDate = user.CreatedDateTime?.ToString("O"),
                    UserLastModifiedDate = user.LastModifiedDateTime?.ToString("O")
                };
                
                userInfoList.Add(userInfo);
            }
        }
        
        _logger.LogInformation("Successfully listed {Count} users", userInfoList.Count);
        await LogAdminAuditAsync("INFO", "list_users", null, null, null, "list", "success", null, null);
        
        return userInfoList;
    }
    catch (Exception ex)
    {
        _logger.LogError(ex, "Failed to list users");
        await LogAdminAuditAsync("ERROR", "list_users", null, null, null, "list", "failure", ex.Message, "LIST_USERS_FAILED");
        throw new InvalidOperationException("Failed to list users: " + ex.Message, ex);
    }
}

private async Task<List<string>> GetUserGroupsAsync(string userId)
{
    try
    {
        var memberOf = await _graphServiceClient.Users[userId].MemberOf.GetAsync();
        var groups = new List<string>();
        
        if (memberOf?.Value != null)
        {
            foreach (var group in memberOf.Value.OfType<Microsoft.Graph.Group>())
            {
                groups.Add(group.DisplayName ?? string.Empty);
            }
        }
        
        return groups;
    }
    catch (Exception ex)
    {
        _logger.LogWarning(ex, "Failed to get groups for user {UserId}", userId);
        return new List<string>();
    }
}
```

#### AddUserToGroupAsync
```csharp
public async Task AddUserToGroupAsync(string username, string groupName, string requestingUsername)
{
    _logger.LogInformation("Adding user {Username} to group {GroupName} (requested by {RequestingUsername})", 
        username, groupName, requestingUsername);
    
    ValidateGroupName(groupName);
    
    try
    {
        // Find user by display name
        var user = await FindUserByUsernameAsync(username);
        if (user == null)
        {
            throw new ArgumentException("User not found: " + username);
        }
        
        // Find group by display name
        var group = await FindGroupByNameAsync(groupName);
        if (group == null)
        {
            throw new ArgumentException("Group not found: " + groupName);
        }
        
        // Add user to group
        await _graphServiceClient.Groups[group.Id].Members.References.PostAsync(new Reference
        {
            ODataId = new Uri($"{_graphServiceClient.RequestUrl}/users/{user.Id}")
        });
        
        _logger.LogInformation("Successfully added user {Username} to group {GroupName}", username, groupName);
        await LogAdminAuditAsync("INFO", "role_change", requestingUsername, username, groupName, "add", "success", null, null);
    }
    catch (Exception ex)
    {
        _logger.LogError(ex, "Failed to add user to group");
        await LogAdminAuditAsync("ERROR", "role_change", requestingUsername, username, groupName, "add", "failure", ex.Message, "ADD_USER_TO_GROUP_FAILED");
        throw;
    }
}
```

#### RemoveUserFromGroupAsync
```csharp
public async Task RemoveUserFromGroupAsync(string username, string groupName, string requestingUsername)
{
    _logger.LogInformation("Removing user {Username} from group {GroupName} (requested by {RequestingUsername})", 
        username, groupName, requestingUsername);
    
    ValidateGroupName(groupName);
    
    // Prevent self-removal from ADMIN group
    if ("ADMIN".Equals(groupName, StringComparison.OrdinalIgnoreCase) && 
        username.Equals(requestingUsername, StringComparison.OrdinalIgnoreCase))
    {
        _logger.LogWarning("User {Username} attempted to remove themselves from ADMIN group", username);
        await LogAdminAuditAsync("WARN", "role_change", requestingUsername, username, groupName, "remove", "failure", 
            "Cannot remove self from ADMIN group", "SELF_REMOVAL_FORBIDDEN");
        throw new InvalidOperationException("Cannot remove yourself from ADMIN group");
    }
    
    try
    {
        var user = await FindUserByUsernameAsync(username);
        var group = await FindGroupByNameAsync(groupName);
        
        if (user == null || group == null)
        {
            throw new ArgumentException("User or group not found");
        }
        
        await _graphServiceClient.Groups[group.Id].Members[user.Id].Reference.DeleteAsync();
        
        _logger.LogInformation("Successfully removed user {Username} from group {GroupName}", username, groupName);
        await LogAdminAuditAsync("INFO", "role_change", requestingUsername, username, groupName, "remove", "success", null, null);
    }
    catch (Exception ex)
    {
        _logger.LogError(ex, "Failed to remove user from group");
        await LogAdminAuditAsync("ERROR", "role_change", requestingUsername, username, groupName, "remove", "failure", ex.Message, "REMOVE_USER_FROM_GROUP_FAILED");
        throw;
    }
}
```

#### DeleteUserAsync
```csharp
public async Task DeleteUserAsync(string username, string requestingUsername)
{
    _logger.LogInformation("Deleting user {Username} (requested by {RequestingUsername})", username, requestingUsername);
    
    // Prevent self-deletion
    if (username.Equals(requestingUsername, StringComparison.OrdinalIgnoreCase))
    {
        _logger.LogWarning("User {Username} attempted to delete themselves", username);
        await LogAdminAuditAsync("WARN", "user_deletion", requestingUsername, username, null, "delete", "failure", 
            "Cannot delete yourself", "SELF_DELETION_FORBIDDEN");
        throw new InvalidOperationException("Cannot delete yourself");
    }
    
    try
    {
        var user = await FindUserByUsernameAsync(username);
        if (user == null)
        {
            throw new ArgumentException("User not found: " + username);
        }
        
        // Delete user from Azure AD
        await _graphServiceClient.Users[user.Id].DeleteAsync();
        
        // Delete user data from Table Storage
        await _userActivityRepository.DeleteAllUserDataAsync(username);
        
        _logger.LogInformation("Successfully deleted user {Username}", username);
        await LogAdminAuditAsync("INFO", "user_deletion", requestingUsername, username, null, "delete", "success", null, null);
    }
    catch (Exception ex)
    {
        _logger.LogError(ex, "Failed to delete user");
        await LogAdminAuditAsync("ERROR", "user_deletion", requestingUsername, username, null, "delete", "failure", ex.Message, "DELETE_USER_FAILED");
        throw;
    }
}
```

## 3. AdminHandler Implementation

### 3.1 AdminHandler Class Structure
```csharp
// Handlers/AdminHandler.cs
public class AdminHandler
{
    private readonly ILogger<AdminHandler> _logger;
    private readonly IAdminService _adminService;
    private readonly IAuthenticationService _authService;

    public AdminHandler(
        ILogger<AdminHandler> logger,
        IAdminService adminService,
        IAuthenticationService authService)
    {
        _logger = logger;
        _adminService = adminService;
        _authService = authService;
    }

    [Function("AdminListUsers")]
    public async Task<HttpResponseData> AdminListUsersAsync(
        [HttpTrigger(AuthorizationLevel.Anonymous, "get", Route = "admin/users")] HttpRequestData req,
        FunctionContext executionContext)
    {
        try
        {
            // Authenticate and authorize
            var authenticatedUser = await _authService.AuthenticateAsync(req);
            if (authenticatedUser == null || !authenticatedUser.IsAdmin)
            {
                return req.CreateResponse(HttpStatusCode.Forbidden);
            }

            var users = await _adminService.ListAllUsersAsync();
            
            var response = req.CreateResponse(HttpStatusCode.OK);
            await response.WriteAsJsonAsync(users);
            return response;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error listing users");
            return req.CreateResponse(HttpStatusCode.InternalServerError);
        }
    }

    [Function("AdminAddUserToGroup")]
    public async Task<HttpResponseData> AdminAddUserToGroupAsync(
        [HttpTrigger(AuthorizationLevel.Anonymous, "post", Route = "admin/users/{username}/groups/{groupName}")] HttpRequestData req,
        FunctionContext executionContext, string username, string groupName)
    {
        try
        {
            var authenticatedUser = await _authService.AuthenticateAsync(req);
            if (authenticatedUser == null || !authenticatedUser.IsAdmin)
            {
                return req.CreateResponse(HttpStatusCode.Forbidden);
            }

            var decodedUsername = Uri.UnescapeDataString(username);
            
            await _adminService.AddUserToGroupAsync(decodedUsername, groupName, authenticatedUser.Username);
            
            return req.CreateResponse(HttpStatusCode.NoContent);
        }
        catch (ArgumentException ex)
        {
            return CreateBadRequestResponse(req, ex.Message);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error adding user to group");
            return req.CreateResponse(HttpStatusCode.InternalServerError);
        }
    }

    // Similar implementations for other admin endpoints...
}
```

## 4. Authentication Service

### 4.1 IAuthenticationService Interface
```csharp
// Services/IAuthenticationService.cs
using QuoteAzureBackend.Models;

namespace QuoteAzureBackend.Services
{
    public interface IAuthenticationService
    {
        Task<AuthenticatedUser?> AuthenticateAsync(HttpRequestData request);
        Task<bool> IsAdminAsync(HttpRequestData request);
        Task<string?> ExtractUsernameAsync(HttpRequestData request);
    }
}
```

### 4.2 AuthenticationService Implementation
```csharp
// Services/AuthenticationService.cs
public class AuthenticationService : IAuthenticationService
{
    private readonly ILogger<AuthenticationService> _logger;
    private readonly IConfiguration _config;

    public async Task<AuthenticatedUser?> AuthenticateAsync(HttpRequestData request)
    {
        try
        {
            if (!request.Headers.TryGetValues("Authorization", out var authHeaders))
            {
                return null;
            }

            var authHeader = authHeaders.FirstOrDefault();
            if (string.IsNullOrEmpty(authHeader) || !authHeader.StartsWith("Bearer "))
            {
                return null;
            }

            var token = authHeader.Substring(7);
            
            // Validate JWT token with Azure AD B2C
            var tokenValidationResult = await ValidateTokenAsync(token);
            if (!tokenValidationResult.IsValid)
            {
                return null;
            }

            // Extract claims
            var username = tokenValidationResult.Claims.GetValueOrDefault("name") ?? 
                          tokenValidationResult.Claims.GetValueOrDefault("preferred_username") ?? 
                          "unknown";
            
            var groups = ExtractGroupsFromClaims(tokenValidationResult.Claims);
            var isAdmin = groups.Contains("ADMIN", StringComparer.OrdinalIgnoreCase);

            return new AuthenticatedUser
            {
                Username = username,
                Email = tokenValidationResult.Claims.GetValueOrDefault("email") ?? string.Empty,
                Groups = groups,
                IsAdmin = isAdmin,
                Token = token
            };
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Authentication failed");
            return null;
        }
    }

    private List<string> ExtractGroupsFromClaims(Dictionary<string, string> claims)
    {
        var groups = new List<string>();
        
        // Azure AD B2C groups claim
        if (claims.TryGetValue("groups", out var groupsClaim))
        {
            // Groups might be JSON array or single value
            try
            {
                var groupArray = JsonSerializer.Deserialize<string[]>(groupsClaim);
                if (groupArray != null)
                {
                    groups.AddRange(groupArray);
                }
            }
            catch
            {
                groups.Add(groupsClaim);
            }
        }
        
        return groups;
    }
}
```

## 5. Configuration Updates

### 5.1 Update Program.cs
```csharp
// Program.cs
public static void Main(string[] args)
{
    var host = new HostBuilder()
        .ConfigureFunctionsWorkerDefaults()
        .ConfigureServices(services => {
            // Register existing services...
            
            // Register authentication services
            services.AddSingleton<IAuthenticationService, AuthenticationService>();
            
            // Register admin services
            services.AddSingleton<IAdminService, AdminService>();
            
            // Register GraphServiceClient
            services.AddScoped<GraphServiceClient>(provider =>
            {
                var config = provider.GetRequiredService<IConfiguration>();
                var scopes = config.GetValue<string>("MicrosoftGraph:Scopes")?.Split(' ') ?? 
                            new[] { "User.Read.All", "GroupMember.ReadWrite.All" };
                
                var credentials = new DefaultAzureCredential();
                return new GraphServiceClient(credentials, scopes);
            });
        })
        .Build();

    host.Run();
}
```

### 5.2 Update appsettings.json
```json
{
  "AzureAdB2C": {
    "Instance": "https://<tenant-name>.b2clogin.com/",
    "TenantId": "<tenant-id>",
    "ClientId": "<client-id>",
    "Domain": "<tenant-name>.b2clogin.com",
    "PolicyId": "B2C_1_sign-up-sign-in"
  },
  "MicrosoftGraph": {
    "BaseUrl": "https://graph.microsoft.com/v1.0",
    "Scopes": "User.Read.All GroupMember.ReadWrite.All User.ReadWrite.All"
  }
}
```

## 6. Testing Strategy

### 6.1 Unit Tests
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
    public async Task AddUserToGroup_ValidInput_Success()
    {
        // Arrange
        var adminService = CreateAdminService();
        
        // Act
        await adminService.AddUserToGroupAsync("testuser", "USER", "admin");
        
        // Assert
        // Verify user was added to group
    }
}
```

### 6.2 Integration Tests
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

## 7. Deployment Considerations

### 7.1 Azure Function App Configuration
- Enable App Service Authentication
- Configure Azure AD B2C as provider
- Set authentication level to "Require authentication"
- Configure CORS for admin frontend

### 7.2 Permissions Required
- Microsoft Graph API permissions:
  - User.Read.All
  - User.ReadWrite.All
  - GroupMember.ReadWrite.All
  - Directory.Read.All

### 7.3 Monitoring & Logging
- Enable Application Insights
- Log all admin operations
- Set up alerts for failed admin operations

## 8. Migration Steps

1. **Deploy authentication layer** with optional mode
2. **Create admin endpoints** alongside existing ones
3. **Test admin functionality** with Azure AD B2C users
4. **Enable authentication** for admin endpoints
5. **Gradually migrate** existing endpoints
6. **Remove old authentication** if any

## Notes

1. **Azure AD B2C vs Cognito**: The main difference is using Microsoft Graph API instead of AWS Cognito SDK
2. **Group Claims**: Configure Azure AD B2C to include groups in JWT tokens
3. **Audit Logging**: Maintain same audit structure as Java implementation
4. **Error Handling**: Ensure error responses match expected format
5. **Rate Limiting**: Consider implementing for expensive operations like user listing
