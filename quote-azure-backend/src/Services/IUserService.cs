using QuoteAzureBackend.Models;
using QuoteAzureBackend.Models.Auth;

namespace QuoteAzureBackend.Services
{
    public interface IUserService
    {
        Task<User> RegisterAsync(RegisterRequest request);
        Task<string> LoginAsync(LoginRequest request);
        Task<bool> ChangePasswordAsync(string userId, ChangePasswordRequest request);
        Task<bool> UpdateUserRoleAsync(string adminId, UpdateRoleRequest request);
        Task<User?> GetUserByIdAsync(string id);
        Task<IEnumerable<User>> GetAllUsersAsync(string adminId);
        Task<bool> IsUserInRoleAsync(string userId, string role);
        Task<bool> IsAdminAsync(string userId);
        Task<bool> UnregisterAsync(string userId, string password);
    }
}
