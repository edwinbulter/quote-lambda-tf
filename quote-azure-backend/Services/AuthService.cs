using Microsoft.Extensions.Logging;

namespace QuoteAzureBackend.Services
{
    public interface IAuthService
    {
        Task<bool> ValidateUserAsync(string userId);
        Task<string> GetUserIdFromTokenAsync(string token);
    }

    public class AuthService : IAuthService
    {
        private readonly ILogger<AuthService> _logger;

        public AuthService(ILogger<AuthService> logger)
        {
            _logger = logger;
        }

        public async Task<bool> ValidateUserAsync(string userId)
        {
            // Mock implementation - in real app, validate against user database
            if (string.IsNullOrEmpty(userId))
                return false;

            // For local testing, accept any non-empty user ID
            return await Task.FromResult(true);
        }

        public async Task<string> GetUserIdFromTokenAsync(string token)
        {
            // Mock implementation - in real app, decode JWT token
            if (string.IsNullOrEmpty(token))
                return "anonymous";

            // For local testing, return a test user ID
            return await Task.FromResult("test-user");
        }
    }
}
