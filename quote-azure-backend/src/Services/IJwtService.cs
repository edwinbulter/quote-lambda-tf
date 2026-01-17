using QuoteAzureBackend.Models;
using System.Security.Claims;

namespace QuoteAzureBackend.Services
{
    public interface IJwtService
    {
        string GenerateToken(User user);
        string GenerateRefreshToken(User user);
        ClaimsPrincipal? ValidateToken(string token);
        string? GetUserIdFromToken(string token);
    }
}
