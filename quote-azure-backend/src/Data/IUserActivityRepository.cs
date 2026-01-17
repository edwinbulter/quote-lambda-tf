using QuoteAzureBackend.Models;

namespace QuoteAzureBackend.Data
{
    public interface IUserActivityRepository
    {
        Task<bool> AddFavoriteAsync(UserFavorite favorite);
        Task<bool> RemoveFavoriteAsync(string userId, int quoteId);
        Task<List<int>> GetUserFavoriteQuoteIdsAsync(string userId);
        Task<bool> RecordViewAsync(UserViewHistory viewHistory);
        Task<List<int>> GetUserViewHistoryQuoteIdsAsync(string userId, int limit);
        Task<UserPreferences?> GetUserPreferencesAsync(string userId);
        Task<bool> UpdateUserPreferencesAsync(UserPreferences preferences);
        
        // UserProgress-like methods to match Java implementation
        Task<UserPreferences?> GetUserProgressAsync(string userId);
        Task<bool> UpdateLastQuoteIdAsync(string userId, int quoteId);
        
        // New methods for Table Storage implementation
        Task<bool> AddUserLikeAsync(string userId, int quoteId);
        Task<bool> RemoveUserLikeAsync(string userId, int quoteId);
        Task<List<int>> GetUserLikedQuoteIdsAsync(string userId);
    }
}
