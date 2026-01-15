using QuoteAzureBackend.Models.Auth;

namespace QuoteAzureBackend.Services
{
    public interface IAuthenticationService
    {
        Task<UserInfo> ValidateTokenAsync(string token);
        Task<bool> IsUserInGroupAsync(string objectId, string groupName);
        Task<bool> IsAdminAsync(string objectId);
    }
}
