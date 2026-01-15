using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using Microsoft.IdentityModel.Tokens;
using QuoteAzureBackend.Models.Auth;
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

        public AuthenticationService(IConfiguration configuration, ILogger<AuthenticationService> logger)
        {
            _configuration = configuration;
            _logger = logger;
            
            var tenantId = _configuration["AzureAdB2C:TenantId"];
            var clientId = _configuration["AzureAdB2C:ClientId"];
            
            _tokenValidationParameters = new TokenValidationParameters
            {
                ValidIssuer = $"https://sts.windows.net/{tenantId}/",
                ValidAudiences = new[] { clientId },
                ValidateIssuer = true,
                ValidateAudience = true,
                ValidateLifetime = true,
                ClockSkew = TimeSpan.Zero
            };
        }

        public async Task<UserInfo> ValidateTokenAsync(string token)
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

                // Parse groups from token
                var groupsClaim = jwtToken.Claims.FirstOrDefault(c => c.Type == "groups");
                if (groupsClaim != null)
                {
                    userInfo.Groups = JsonSerializer.Deserialize<List<string>>(groupsClaim.Value) ?? new List<string>();
                }

                _logger.LogInformation("Successfully validated token for user: {ObjectId}", userInfo.ObjectId);
                return userInfo;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to validate token");
                return new UserInfo { IsAuthenticated = false };
            }
        }

        public Task<bool> IsUserInGroupAsync(string objectId, string groupName)
        {
            // TODO: Implement Microsoft Graph API call to check group membership
            // For now, return false
            _logger.LogWarning("Group membership check not implemented for user: {ObjectId}, group: {GroupName}", objectId, groupName);
            return Task.FromResult(false);
        }

        public Task<bool> IsAdminAsync(string objectId)
        {
            return IsUserInGroupAsync(objectId, "ADMIN");
        }
    }
}
