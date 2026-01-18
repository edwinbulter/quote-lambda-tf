using QuoteAzureBackend.Models;
using QuoteAzureBackend.Data.Entities;

namespace QuoteAzureBackend.Data
{
    public interface IUserActivityRepository
    {
        Task<bool> UpdateUserPreferencesAsync(UserProgress preferences);
        
        // UserProgress-like methods to match Java implementation
        Task<UserProgress?> GetUserProgressAsync(string userId);
        Task<bool> UpdateLastQuoteIdAsync(string userId, int quoteId);
        
        // New methods for Table Storage implementation
        Task<bool> AddUserLikeAsync(string userId, int quoteId);
        Task<bool> RemoveUserLikeAsync(string userId, int quoteId);
        Task<List<int>> GetUserLikedQuoteIdsAsync(string userId);
        Task<List<UserLikeEntity>> GetAllUserLikesAsync(string userId);
        Task<bool> UpdateUserLikeOrderAsync(string userId, int quoteId, int newOrder);
        Task<int> GetTotalLikesCountAsync();
    }
}
