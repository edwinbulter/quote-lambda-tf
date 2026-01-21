# Role-Based Authentication Implementation Guide

## Problem Statement

Currently, when users log in to the Azure backend, the JWT token does not include role information, even though the user roles are stored in the `userroles` table in Azure Table Storage. This causes issues where admin users cannot access admin-only endpoints because the token lacks the necessary role claims.

## Current Architecture

### 1. User Roles Storage
- User roles are stored in Azure Table Storage in the `userroles` table
- The `UserRoleRepository` provides methods to manage user roles:
  - `AssignRoleAsync(username, role, assignedBy)`
  - `IsUserInRoleAsync(username, role)`
  - `GetAllUsersAsync()`
  - `RemoveRoleAsync(username, role)`

### 2. JWT Token Generation
- The `JwtService.GenerateToken()` method creates JWT tokens without role claims
- Currently includes only basic claims: NameIdentifier, Email, Name, jti, iat
- The `JwtAuthenticationMiddleware` attempts to read role claims but defaults to "User" when not found

### 3. Authorization Check
- Admin endpoints check `await _userService.IsAdminAsync(userId)` which queries the database
- This requires an additional database lookup for every admin request

## Recommended Implementation

### Step 1: Modify JwtService to Include Roles

Update the `GenerateToken` method in `/src/Services/JwtService.cs` to include user roles:

```csharp
public string GenerateToken(User user)
{
    var tokenHandler = new JwtSecurityTokenHandler();
    var key = Encoding.ASCII.GetBytes(_key);
    
    // Get user roles from the database
    var userRoles = _userRoleRepository.GetUserRolesAsync(user.Username).GetAwaiter().GetResult();
    var roleClaims = userRoles.Select(role => new Claim(ClaimTypes.Role, role.Role));
    
    var claims = new List<Claim>
    {
        new Claim(ClaimTypes.NameIdentifier, user.Id),
        new Claim(ClaimTypes.Email, user.Email),
        new Claim(ClaimTypes.Name, user.Username),
        new Claim("jti", Guid.NewGuid().ToString()),
        new Claim("iat", DateTimeOffset.UtcNow.ToUnixTimeSeconds().ToString(), ClaimValueTypes.Integer64)
    };
    
    // Add role claims
    claims.AddRange(roleClaims);

    var tokenDescriptor = new SecurityTokenDescriptor
    {
        Subject = new ClaimsIdentity(claims),
        Expires = DateTime.UtcNow.AddHours(24),
        SigningCredentials = new SigningCredentials(new SymmetricSecurityKey(key), SecurityAlgorithms.HmacSha256Signature),
        Issuer = _issuer,
        Audience = _audience
    };

    var token = tokenHandler.CreateToken(tokenDescriptor);
    return tokenHandler.WriteToken(token);
}
```

### Step 2: Update JwtService Constructor

Inject `IUserRoleRepository` into the `JwtService`:

```csharp
public class JwtService : IJwtService
{
    private readonly IConfiguration _config;
    private readonly IUserRoleRepository _userRoleRepository;
    private readonly string _key;
    private readonly string _issuer;
    private readonly string _audience;

    public JwtService(IConfiguration config, IUserRoleRepository userRoleRepository)
    {
        _config = config;
        _userRoleRepository = userRoleRepository;
        _key = _config["Jwt:Key"] ?? throw new ArgumentNullException("Jwt:Key not configured");
        _issuer = _config["Jwt:Issuer"] ?? "quote-azure-backend";
        _audience = _config["Jwt:Audience"] ?? "quote-azure-backend-users";
    }
}
```

### Step 3: Update Program.cs Registration

Register the dependency injection properly:

```csharp
// Register UserRoleRepository before JwtService
services.AddSingleton<IUserRoleRepository>(provider => 
    new UserRoleRepository(
        provider.GetRequiredService<TableServiceClient>(),
        provider.GetRequiredService<ILogger<UserRoleRepository>>()
    )
);

services.AddSingleton<IJwtService, JwtService>();
```

### Step 4: Simplify Authorization Checks

With roles in the JWT token, we can optimize authorization checks. Update the `JwtAuthenticationMiddleware`:

```csharp
public Task<UserInfo?> AuthenticateAsync(HttpRequestData req)
{
    try
    {
        var authHeader = req.Headers.FirstOrDefault(h => h.Key.Equals("Authorization", StringComparison.OrdinalIgnoreCase));
        
        if (!authHeader.Value.Any() || !authHeader.Value.Any(v => v.StartsWith("Bearer ")))
        {
            _logger.LogWarning("No valid Authorization header found");
            return Task.FromResult<UserInfo?>(null);
        }

        var token = authHeader.Value.First(v => v.StartsWith("Bearer ")).Substring("Bearer ".Length).Trim();
        var principal = _jwtService.ValidateToken(token);
        
        if (principal == null)
        {
            _logger.LogWarning("Token validation failed");
            return Task.FromResult<UserInfo?>(null);
        }

        // Get all roles from the token
        var roles = principal.FindAll(ClaimTypes.Role).Select(c => c.Value).ToList();
        
        // Convert to UserInfo for compatibility with existing code
        var userInfo = new UserInfo
        {
            ObjectId = principal.FindFirst(ClaimTypes.NameIdentifier)?.Value ?? string.Empty,
            Email = principal.FindFirst(ClaimTypes.Email)?.Value ?? string.Empty,
            DisplayName = principal.FindFirst(ClaimTypes.Name)?.Value ?? string.Empty,
            IsAuthenticated = true,
            Role = roles.Contains("ADMIN") ? "ADMIN" : (roles.FirstOrDefault() ?? "User")
        };

        return Task.FromResult<UserInfo?>(userInfo);
    }
    catch (Exception ex)
    {
        _logger.LogError(ex, "Error during authentication");
        return Task.FromResult<UserInfo?>(null);
    }
}
```

### Step 5: Add Policy-Based Authorization (Optional Enhancement)

For better security, consider adding policy-based authorization in `Program.cs`:

```csharp
services.AddAuthorization(options =>
{
    options.AddPolicy("AdminOnly", policy => 
        policy.RequireClaim(ClaimTypes.Role, "ADMIN"));
});
```

Then update admin handlers to use the policy:

```csharp
[Function("SomeAdminFunction")]
public async Task<HttpResponseData> SomeAdminFunction(
    [HttpTrigger(AuthorizationLevel.Function, "post", Route = "admin/some-action")] 
    HttpRequestData req,
    FunctionContext context)
{
    // Authenticate user
    var userInfo = await _authMiddleware.AuthenticateAsync(req);
    if (userInfo == null || !userInfo.IsAuthenticated)
    {
        return _authMiddleware.CreateUnauthorizedResponse(req);
    }
    
    // Check admin role from token (no database lookup needed)
    if (!userInfo.Role.Equals("ADMIN", StringComparison.OrdinalIgnoreCase))
    {
        return _authMiddleware.CreateForbiddenResponse(req);
    }
    
    // Process admin request...
}
```

## Benefits of This Implementation

1. **Performance**: Eliminates database lookups for role validation on every request
2. **Stateless**: JWT tokens contain all necessary authorization information
3. **Scalability**: Reduces load on the database and improves response times
4. **Security**: Roles are cryptographically signed within the JWT token
5. **Flexibility**: Supports multiple roles per user if needed in the future

## Migration Strategy

1. Deploy the updated JWT service with role inclusion
2. Existing tokens will remain valid until they expire (24 hours)
3. Users will need to re-authenticate to get role claims in their tokens
4. Monitor logs to ensure admin users can access admin endpoints after re-authentication

## Testing

Verify the implementation with these steps:

1. Login as an admin user
2. Decode the JWT token (using jwt.ms or similar) to verify it contains role claims
3. Access admin endpoints to ensure they work without additional database lookups
4. Login as a regular user to verify they still have "User" role
5. Test role assignment/removal and verify new tokens reflect updated roles

## Security Considerations

1. JWT tokens with roles should have a reasonable expiration time (current 24 hours is appropriate)
2. If a user's role is revoked, their existing token will remain valid until expiration
3. For immediate role revocation, implement a token blacklist or shorter expiration times
4. Consider using refresh tokens with shorter access token lifetime for better security

## Related Files

- `/src/Services/JwtService.cs` - Main implementation file
- `/src/Services/IJwtService.cs` - Interface definition
- `/src/Middleware/JwtAuthenticationMiddleware.cs` - Authentication middleware
- `/src/Data/UserRoleRepository.cs` - Role management repository
- `/src/Program.cs` - Dependency injection configuration
