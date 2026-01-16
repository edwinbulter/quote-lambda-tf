using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using Microsoft.IdentityModel.Tokens;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Models.Auth;
using QuoteAzureBackend.Data;
using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text.Json;

namespace QuoteAzureBackend.Services
{
    public class AuthenticationService : IAuthenticationService
    {
        private readonly IConfiguration _configuration;
        private readonly ILogger<AuthenticationService> _logger;
        private readonly TokenValidationParameters _tokenValidationParameters;
        private readonly IUserRoleRepository _userRoleRepository;

        public AuthenticationService(IConfiguration configuration, ILogger<AuthenticationService> logger, IUserRoleRepository userRoleRepository)
        {
            _configuration = configuration;
            _logger = logger;
            _userRoleRepository = userRoleRepository;
            
            var instance = _configuration["AzureAd:Instance"];
            var domain = _configuration["AzureAd:Domain"];
            var tenantId = _configuration["AzureAd:TenantId"];
            var clientId = _configuration["AzureAd:ClientId"];
            
            _tokenValidationParameters = new TokenValidationParameters
            {
                ValidIssuer = $"{instance}{tenantId}/v2.0",
                ValidAudiences = new[] { clientId },
                ValidateIssuer = true,
                ValidateAudience = true,
                ValidateLifetime = true,
                ClockSkew = TimeSpan.Zero
            };
        }

        public Task<UserInfo> ValidateTokenAsync(string token)
        {
            try
            {
                var tokenHandler = new JwtSecurityTokenHandler();
                var principal = tokenHandler.ValidateToken(token, _tokenValidationParameters, out var validatedToken);
                
                var jwtToken = (JwtSecurityToken)validatedToken;
                
                var userInfo = new UserInfo
                {
                    ObjectId = jwtToken.Claims.FirstOrDefault(c => c.Type == "oid")?.Value ?? "",
                    Email = jwtToken.Claims.FirstOrDefault(c => c.Type == "email")?.Value ?? "",
                    DisplayName = jwtToken.Claims.FirstOrDefault(c => c.Type == "name")?.Value ?? "",
                    IsAuthenticated = true
                };

                // Note: Groups not available in Azure AD Free plan
                // Using individual user assignments instead
                var groupsClaim = jwtToken.Claims.FirstOrDefault(c => c.Type == "groups");
                if (groupsClaim != null)
                {
                    userInfo.Groups = JsonSerializer.Deserialize<List<string>>(groupsClaim.Value) ?? new List<string>();
                }
                else
                {
                    userInfo.Groups = new List<string>(); // No groups in free plan
                }

                _logger.LogInformation("Successfully validated token for user: {ObjectId}", userInfo.ObjectId);
                return Task.FromResult(userInfo);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to validate token");
                return Task.FromResult(new UserInfo { IsAuthenticated = false });
            }
        }

        public Task<bool> IsUserInGroupAsync(string objectId, string groupName)
        {
            // Note: Azure AD Free plan doesn't support group claims
            // Use database-based role management instead
            // See: doc/database-user-roles.md for implementation
            
            _logger.LogWarning("Group membership check called but database roles not implemented. User: {ObjectId}, Group: {GroupName}", 
                objectId, groupName);
            
            return Task.FromResult(false);
        }

        public Task<bool> IsAdminAsync(string objectId)
        {
            return IsUserInGroupAsync(objectId, "ADMIN");
        }
    }
}
