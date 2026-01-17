using Microsoft.Azure.Functions.Worker;
using Microsoft.Azure.Functions.Worker.Http;
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Models.Auth;
using QuoteAzureBackend.Services;
using System.Net;
using System.Security.Claims;

namespace QuoteAzureBackend.Middleware
{
    public class JwtAuthenticationMiddleware
    {
        private readonly IJwtService _jwtService;
        private readonly ILogger<JwtAuthenticationMiddleware> _logger;

        public JwtAuthenticationMiddleware(IJwtService jwtService, ILogger<JwtAuthenticationMiddleware> logger)
        {
            _jwtService = jwtService;
            _logger = logger;
        }

        public Task<UserInfo?> AuthenticateAsync(HttpRequestData req)
        {
            try
            {
                // Try to get token from Authorization header
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

                // Convert to UserInfo for compatibility with existing code
                var userInfo = new UserInfo
                {
                    ObjectId = principal.FindFirst(ClaimTypes.NameIdentifier)?.Value ?? string.Empty,
                    Email = principal.FindFirst(ClaimTypes.Email)?.Value ?? string.Empty,
                    DisplayName = principal.FindFirst(ClaimTypes.Name)?.Value ?? string.Empty,
                    IsAuthenticated = true,
                    Role = principal.FindFirst(ClaimTypes.Role)?.Value ?? "User"
                };

                return Task.FromResult<UserInfo?>(userInfo);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error during authentication");
                return Task.FromResult<UserInfo?>(null);
            }
        }

        public async Task<User?> GetUserFromRequestAsync(HttpRequestData req)
        {
            var userInfo = await AuthenticateAsync(req);
            if (userInfo == null || !userInfo.IsAuthenticated)
            {
                return null;
            }

            // Create a User object from the claims
            return new User
            {
                Id = userInfo.ObjectId ?? string.Empty,
                Email = userInfo.Email ?? string.Empty,
                Username = userInfo.DisplayName ?? string.Empty,
                Role = userInfo.Role ?? "User"
            };
        }

        public HttpResponseData CreateUnauthorizedResponse(HttpRequestData req)
        {
            var response = req.CreateResponse(HttpStatusCode.Unauthorized);
            response.Headers.Add("WWW-Authenticate", "Bearer");
            return response;
        }

        public HttpResponseData CreateForbiddenResponse(HttpRequestData req)
        {
            return req.CreateResponse(HttpStatusCode.Forbidden);
        }
    }
}
