using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;

namespace QuoteAzureBackend.Data
{
    public class UserActivityRepository : IUserActivityRepository
    {
        private readonly ILogger<UserActivityRepository> _logger;
        private static readonly List<UserFavorite> _favorites = new List<UserFavorite>();
        private static readonly List<UserViewHistory> _viewHistory = new List<UserViewHistory>();
        private static readonly List<UserPreferences> _preferences = new List<UserPreferences>();

        public UserActivityRepository(ILogger<UserActivityRepository> logger)
        {
            _logger = logger;
        }

        public async Task<bool> AddFavoriteAsync(UserFavorite favorite)
        {
            try
            {
                // Remove existing favorite if it exists
                _favorites.RemoveAll(f => f.UserId == favorite.UserId && f.QuoteId == favorite.QuoteId);
                _favorites.Add(favorite);
                
                _logger.LogInformation("Added favorite for user {UserId}, quote {QuoteId}", favorite.UserId, favorite.QuoteId);
                return await Task.FromResult(true);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error adding favorite");
                return await Task.FromResult(false);
            }
        }

        public async Task<bool> RemoveFavoriteAsync(string userId, int quoteId)
        {
            try
            {
                var removed = _favorites.RemoveAll(f => f.UserId == userId && f.QuoteId == quoteId) > 0;
                
                if (removed)
                {
                    _logger.LogInformation("Removed favorite for user {UserId}, quote {QuoteId}", userId, quoteId);
                }
                
                return await Task.FromResult(removed);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error removing favorite");
                return await Task.FromResult(false);
            }
        }

        public async Task<List<int>> GetUserFavoriteQuoteIdsAsync(string userId)
        {
            try
            {
                var favoriteQuoteIds = _favorites
                    .Where(f => f.UserId == userId)
                    .Select(f => f.QuoteId)
                    .ToList();
                
                return await Task.FromResult(favoriteQuoteIds);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting user favorites");
                return await Task.FromResult(new List<int>());
            }
        }

        public async Task<bool> RecordViewAsync(UserViewHistory viewHistory)
        {
            try
            {
                _viewHistory.Add(viewHistory);
                
                // Keep only last 100 views per user to prevent unlimited growth
                var userViews = _viewHistory.Where(v => v.UserId == viewHistory.UserId).ToList();
                if (userViews.Count > 100)
                {
                    var viewsToRemove = userViews.OrderBy(v => v.ViewedAt).Take(userViews.Count - 100);
                    _viewHistory.RemoveAll(v => viewsToRemove.Contains(v));
                }
                
                _logger.LogInformation("Recorded view for user {UserId}, quote {QuoteId}", viewHistory.UserId, viewHistory.QuoteId);
                return await Task.FromResult(true);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error recording view");
                return await Task.FromResult(false);
            }
        }

        public async Task<List<int>> GetUserViewHistoryQuoteIdsAsync(string userId, int limit)
        {
            try
            {
                var viewedQuoteIds = _viewHistory
                    .Where(v => v.UserId == userId)
                    .OrderByDescending(v => v.ViewedAt)
                    .Take(limit)
                    .Select(v => v.QuoteId)
                    .ToList();
                
                return await Task.FromResult(viewedQuoteIds);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting user view history");
                return await Task.FromResult(new List<int>());
            }
        }

        public async Task<UserPreferences?> GetUserPreferencesAsync(string userId)
        {
            try
            {
                var preferences = _preferences.FirstOrDefault(p => p.UserId == userId);
                return await Task.FromResult(preferences);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting user preferences");
                return await Task.FromResult<UserPreferences?>(null);
            }
        }

        public async Task<bool> UpdateUserPreferencesAsync(UserPreferences preferences)
        {
            try
            {
                // Remove existing preferences for this user
                _preferences.RemoveAll(p => p.UserId == preferences.UserId);
                
                // Add updated preferences
                _preferences.Add(preferences);
                
                _logger.LogInformation("Updated preferences for user {UserId}", preferences.UserId);
                return await Task.FromResult(true);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error updating user preferences");
                return await Task.FromResult(false);
            }
        }
    }
}
