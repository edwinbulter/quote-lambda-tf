using Microsoft.Azure.Functions.Worker;
using Microsoft.Azure.Functions.Worker.Http;
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models.Auth;
using QuoteAzureBackend.Services;
using System.Net;

namespace QuoteAzureBackend.Middleware
{
    public class JwtAuthenticationMiddleware
    {
        private readonly IAuthenticationService _authService;
        private readonly ILogger<JwtAuthenticationMiddleware> _logger;

        public JwtAuthenticationMiddleware(IAuthenticationService authService, ILogger<JwtAuthenticationMiddleware> logger)
        {
            _authService = authService;
            _logger = logger;
        }

        public async Task<UserInfo?> AuthenticateAsync(HttpRequestData req)
        {
            try
            {
                // Try to get token from Authorization header
                var authHeader = req.Headers.FirstOrDefault(h => h.Key.Equals("Authorization", StringComparison.OrdinalIgnoreCase));
                
                if (!authHeader.Value.Any() || !authHeader.Value.Any(v => v.StartsWith("Bearer ")))
                {
                    _logger.LogWarning("No valid Authorization header found");
                    return null;
                }

                var token = authHeader.Value.First(v => v.StartsWith("Bearer ")).Substring("Bearer ".Length).Trim();
                var userInfo = await _authService.ValidateTokenAsync(token);
                
                if (!userInfo.IsAuthenticated)
                {
                    _logger.LogWarning("Token validation failed");
                    return null;
                }

                return userInfo;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error during authentication");
                return null;
            }
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
