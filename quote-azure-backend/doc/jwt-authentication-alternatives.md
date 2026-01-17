# JWT Authentication Alternatives to Azure AD in C#

## Overview

Azure AD can be complex and has various limitations. Here are several alternatives for implementing JWT-based authentication, authorization, and user management in C# applications.

## 1. Self-Hosted JWT Authentication (Recommended)

### Description
Implement your own JWT authentication system using ASP.NET Core's built-in authentication middleware.

### Pros
- Full control over authentication flow
- No external dependencies
- Custom user registration and management
- Works offline
- Free and open source

### Cons
- Need to handle security yourself
- User management database required
- Password reset flows to implement

### Implementation

#### 1.1 Basic Setup

```csharp
// Program.cs
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidateAudience = true,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            ValidIssuer = builder.Configuration["Jwt:Issuer"],
            ValidAudience = builder.Configuration["Jwt:Audience"],
            IssuerSigningKey = new SymmetricSecurityKey(
                Encoding.UTF8.GetBytes(builder.Configuration["Jwt:Key"]))
        };
    });

builder.Services.AddAuthorization();
builder.Services.AddScoped<IUserService, UserService>();
builder.Services.AddScoped<IJwtService, JwtService>();
```

#### 1.2 JWT Service

```csharp
public interface IJwtService
{
    string GenerateToken(User user);
    ClaimsPrincipal ValidateToken(string token);
}

public class JwtService : IJwtService
{
    private readonly IConfiguration _config;
    private readonly SymmetricSecurityKey _key;

    public JwtService(IConfiguration config)
    {
        _config = config;
        _key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_config["Jwt:Key"]));
    }

    public string GenerateToken(User user)
    {
        var claims = new[]
        {
            new Claim(ClaimTypes.NameIdentifier, user.Id.ToString()),
            new Claim(ClaimTypes.Email, user.Email),
            new Claim(ClaimTypes.Name, user.Username),
            new Claim(ClaimTypes.Role, user.Role)
        };

        var credentials = new SigningCredentials(_key, SecurityAlgorithms.HmacSha256);
        var token = new JwtSecurityToken(
            issuer: _config["Jwt:Issuer"],
            audience: _config["Jwt:Audience"],
            claims: claims,
            expires: DateTime.UtcNow.AddHours(24),
            signingCredentials: credentials
        );

        return new JwtSecurityTokenHandler().WriteToken(token);
    }

    public ClaimsPrincipal ValidateToken(string token)
    {
        var tokenHandler = new JwtSecurityTokenHandler();
        var validationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidateAudience = true,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            ValidIssuer = _config["Jwt:Issuer"],
            ValidAudience = _config["Jwt:Audience"],
            IssuerSigningKey = _key
        };

        return tokenHandler.ValidateToken(token, validationParameters, out _);
    }
}
```

#### 1.3 User Service

```csharp
public interface IUserService
{
    Task<User> RegisterAsync(RegisterRequest request);
    Task<string> AuthenticateAsync(LoginRequest request);
    Task<User> GetUserByIdAsync(int id);
    Task<bool> IsInRoleAsync(int userId, string role);
}

public class UserService : IUserService
{
    private readonly IUserRepository _userRepository;
    private readonly IPasswordHasher<User> _passwordHasher;
    private readonly IJwtService _jwtService;

    public async Task<User> RegisterAsync(RegisterRequest request)
    {
        var existingUser = await _userRepository.GetByEmailAsync(request.Email);
        if (existingUser != null)
            throw new Exception("User already exists");

        var user = new User
        {
            Username = request.Username,
            Email = request.Email,
            Role = "User", // Default role
            CreatedAt = DateTime.UtcNow
        };

        user.PasswordHash = _passwordHasher.HashPassword(user, request.Password);

        return await _userRepository.CreateAsync(user);
    }

    public async Task<string> AuthenticateAsync(LoginRequest request)
    {
        var user = await _userRepository.GetByEmailAsync(request.Email);
        if (user == null)
            throw new Exception("Invalid credentials");

        var result = _passwordHasher.VerifyHashedPassword(user, user.PasswordHash, request.Password);
        if (result == PasswordVerificationResult.Failed)
            throw new Exception("Invalid credentials");

        return _jwtService.GenerateToken(user);
    }
}
```

#### 1.4 Authentication Controller

```csharp
[ApiController]
[Route("api/[controller]")]
public class AuthController : ControllerBase
{
    private readonly IUserService _userService;

    [HttpPost("register")]
    public async Task<IActionResult> Register([FromBody] RegisterRequest request)
    {
        var user = await _userService.RegisterAsync(request);
        return Ok(new { user.Id, user.Email, user.Username });
    }

    [HttpPost("login")]
    public async Task<IActionResult> Login([FromBody] LoginRequest request)
    {
        var token = await _userService.AuthenticateAsync(request);
        return Ok(new { token });
    }
}
```

## 2. IdentityServer4 (Recommended for Enterprise)

### Description
OpenID Connect and OAuth 2.0 framework for ASP.NET Core.

### Pros
- Industry standards compliant
- Supports multiple grant types
- Client management
- Scope management
- Token revocation
- Introspection endpoints

### Cons
- Learning curve
- More configuration needed
- Additional service to maintain

### Implementation

```csharp
// Program.cs
builder.Services.AddIdentityServer()
    .AddInMemoryClients(Config.GetClients())
    .AddInMemoryApiResources(Config.GetApiResources())
    .AddInMemoryIdentityResources(Config.GetIdentityResources())
    .AddTestUsers(Config.GetUsers())
    .AddDeveloperSigningCredential();

// Config.cs
public static class Config
{
    public static IEnumerable<IdentityResource> GetIdentityResources()
    {
        return new List<IdentityResource>
        {
            new IdentityResources.OpenId(),
            new IdentityResources.Profile(),
            new IdentityResources.Email()
        };
    }

    public static IEnumerable<ApiResource> GetApiResources()
    {
        return new List<ApiResource>
        {
            new ApiResource("api1", "My API")
        };
    }

    public static IEnumerable<Client> GetClients()
    {
        return new List<Client>
        {
            new Client
            {
                ClientId = "client",
                AllowedGrantTypes = GrantTypes.ResourceOwnerPassword,
                ClientSecrets = { new Secret("secret".Sha256()) },
                AllowedScopes = { "api1" }
            }
        };
    }
}
```

## 3. ASP.NET Core Identity with JWT

### Description
Built-in ASP.NET Core Identity with JWT token generation.

### Pros
- Complete user management system
- Password policies
- Two-factor authentication
- Account lockout
- External login providers

### Cons
- Database required
- More complex setup
- Entity Framework dependency

### Implementation

```csharp
// Program.cs
builder.Services.AddDbContext<ApplicationDbContext>(options =>
    options.UseSqlServer(builder.Configuration.GetConnectionString("DefaultConnection")));

builder.Services.AddIdentity<ApplicationUser, IdentityRole>()
    .AddEntityFrameworkStores<ApplicationDbContext>()
    .AddDefaultTokenProviders();

builder.Services.AddAuthentication(options =>
{
    options.DefaultAuthenticateScheme = JwtBearerDefaults.AuthenticationScheme;
    options.DefaultChallengeScheme = JwtBearerDefaults.AuthenticationScheme;
})
.AddJwtBearer(options =>
{
    options.TokenValidationParameters = new TokenValidationParameters
    {
        ValidateIssuer = true,
        ValidateAudience = true,
        ValidateLifetime = true,
        ValidateIssuerSigningKey = true,
        ValidIssuer = builder.Configuration["Jwt:Issuer"],
        ValidAudience = builder.Configuration["Jwt:Audience"],
        IssuerSigningKey = new SymmetricSecurityKey(
            Encoding.UTF8.GetBytes(builder.Configuration["Jwt:Key"]))
    };
});
```

## 4. OpenIddict

### Description
OpenID Connect server framework for ASP.NET Core.

### Pros
- Open source
- Flexible configuration
- Supports multiple databases
- No external dependencies

### Cons
- Less documentation than IdentityServer
- Smaller community

### Implementation

```csharp
// Program.cs
builder.Services.AddOpenIddict()
    .AddCore(options =>
    {
        options.UseEntityFrameworkCore()
            .UseDbContext<ApplicationDbContext>();
    })
    .AddServer(options =>
    {
        options.SetTokenEndpointUris("/connect/token");
        options.AllowPasswordFlow();
        options.AcceptAnonymousClients();
        
        options.AddDevelopmentEncryptionCertificate();
        options.AddDevelopmentSigningCertificate();
    })
    .AddValidation(options =>
    {
        options.UseLocalServer();
        options.UseAspNetCore();
    });
```

## 5. Custom Middleware Approach

### Description
Simple custom authentication middleware for basic JWT validation.

### Pros
- Minimal setup
- Full control
- No external packages needed

### Cons
- Reinventing the wheel
- Security risks if not done properly

### Implementation

```csharp
public class JwtMiddleware
{
    private readonly RequestDelegate _next;
    private readonly IConfiguration _config;

    public JwtMiddleware(RequestDelegate next, IConfiguration config)
    {
        _next = next;
        _config = config;
    }

    public async Task Invoke(HttpContext context)
    {
        var token = context.Request.Headers["Authorization"]
            .FirstOrDefault()?.Split(" ").Last();

        if (token != null)
        {
            AttachUserToContext(context, token);
        }

        await _next(context);
    }

    private void AttachUserToContext(HttpContext context, string token)
    {
        try
        {
            var tokenHandler = new JwtSecurityTokenHandler();
            var key = Encoding.ASCII.GetBytes(_config["Jwt:Key"]);
            
            tokenHandler.ValidateToken(token, new TokenValidationParameters
            {
                ValidateIssuerSigningKey = true,
                IssuerSigningKey = new SymmetricSecurityKey(key),
                ValidateIssuer = true,
                ValidateAudience = true,
                ClockSkew = TimeSpan.Zero
            }, out SecurityToken validatedToken);

            var jwtToken = (JwtSecurityToken)validatedToken;
            var userId = int.Parse(jwtToken.Claims.First(x => x.Type == "nameid").Value);
            
            // Attach user to context
            context.Items["User"] = userId;
        }
        catch
        {
            // Do nothing if token validation fails
        }
    }
}
```

## 6. Third-Party Solutions

### 6.1 Auth0
- Cloud-based authentication
- Free tier available
- Multiple providers supported
- Good documentation

### 6.2 Firebase Authentication
- Google's solution
- Free tier
- Multiple auth methods
- Real-time database integration

### 6.3 Okta
- Enterprise-focused
- Free developer tier
- Good documentation
- Advanced features

## Comparison Table

| Solution | Setup Complexity | Maintenance | Cost | Control | Scalability |
|----------|------------------|-------------|------|---------|-------------|
| Self-Hosted JWT | Medium | Medium | Free | Full | High |
| IdentityServer4 | High | Medium | Free | High | High |
| ASP.NET Core Identity | High | Low | Free | High | High |
| OpenIddict | Medium | Medium | Free | High | High |
| Custom Middleware | Low | High | Free | Full | Medium |
| Auth0 | Low | None | Freemium | Low | Very High |
| Firebase | Low | None | Freemium | Low | Very High |
| Okta | Low | None | Freemium | Low | Very High |

## Recommendation for Your Project

Given your current setup and requirements, I recommend **Self-Hosted JWT Authentication** because:

1. **No external dependencies** - Works offline
2. **Full control** - Customize as needed
3. **Easy migration** - Can replace Azure AD without major changes
4. **Cost-effective** - Completely free
5. **Scalable** - Works for small to large applications

## Migration Steps

1. **Create User model and database**
2. **Implement JWT service**
3. **Add authentication middleware**
4. **Create auth endpoints (register/login)**
5. **Update existing controllers to use new auth**
6. **Remove Azure AD dependencies**
7. **Update configuration**

## Security Considerations

1. **Use strong JWT secrets** (256-bit keys)
2. **Implement token expiration**
3. **Use HTTPS everywhere**
4. **Hash passwords properly**
5. **Implement rate limiting**
6. **Add refresh tokens**
7. **Consider token blacklisting**
8. **Validate all inputs**

## Next Steps

Would you like me to implement any of these solutions? I can start with the self-hosted JWT approach and create a complete authentication system for your project.
