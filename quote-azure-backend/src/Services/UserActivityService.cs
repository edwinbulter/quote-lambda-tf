using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Data;

namespace QuoteAzureBackend.Services
{
    public interface IUserActivityService
    {
        Task<bool> AddFavoriteAsync(string userId, int quoteId);
        Task<bool> RemoveFavoriteAsync(string userId, int quoteId);
        Task<List<Quote>> GetUserFavoritesAsync(string userId);
        Task<bool> RecordViewAsync(string userId, int quoteId);
        Task<List<Quote>> GetUserViewHistoryAsync(string userId, int limit = 50);
        Task<UserPreferences> GetUserPreferencesAsync(string userId);
        Task<bool> UpdateUserPreferencesAsync(string userId, UserPreferences preferences);
    }

    public class UserActivityService : IUserActivityService
    {
        private readonly IUserActivityRepository _repository;
        private readonly IQuoteService _quoteService;
        private readonly ILogger<UserActivityService> _logger;

        public UserActivityService(IUserActivityRepository repository, IQuoteService quoteService, ILogger<UserActivityService> logger)
        {
            _repository = repository;
            _quoteService = quoteService;
            _logger = logger;
        }

        public async Task<bool> AddFavoriteAsync(string userId, int quoteId)
        {
            try
            {
                var favorite = new UserFavorite { UserId = userId, QuoteId = quoteId };
                return await _repository.AddFavoriteAsync(favorite);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error adding favorite for user {UserId}, quote {QuoteId}", userId, quoteId);
                return false;
            }
        }

        public async Task<bool> RemoveFavoriteAsync(string userId, int quoteId)
        {
            try
            {
                return await _repository.RemoveFavoriteAsync(userId, quoteId);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error removing favorite for user {UserId}, quote {QuoteId}", userId, quoteId);
                return false;
            }
        }

        public async Task<List<Quote>> GetUserFavoritesAsync(string userId)
        {
            try
            {
                var favoriteQuoteIds = await _repository.GetUserFavoriteQuoteIdsAsync(userId);
                var quotes = new List<Quote>();
                
                foreach (var quoteId in favoriteQuoteIds)
                {
                    var quote = await _quoteService.GetQuoteByIdAsync(userId, quoteId);
                    if (quote != null)
                    {
                        quotes.Add(quote);
                    }
                }
                
                return quotes;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting favorites for user {UserId}", userId);
                return new List<Quote>();
            }
        }

        public async Task<bool> RecordViewAsync(string userId, int quoteId)
        {
            try
            {
                var viewHistory = new UserViewHistory { UserId = userId, QuoteId = quoteId };
                return await _repository.RecordViewAsync(viewHistory);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error recording view for user {UserId}, quote {QuoteId}", userId, quoteId);
                return false;
            }
        }

        public async Task<List<Quote>> GetUserViewHistoryAsync(string userId, int limit = 50)
        {
            try
            {
                var viewedQuoteIds = await _repository.GetUserViewHistoryQuoteIdsAsync(userId, limit);
                var quotes = new List<Quote>();
                
                foreach (var quoteId in viewedQuoteIds)
                {
                    var quote = await _quoteService.GetQuoteByIdAsync(userId, quoteId);
                    if (quote != null)
                    {
                        quotes.Add(quote);
                    }
                }
                
                return quotes;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting view history for user {UserId}", userId);
                return new List<Quote>();
            }
        }

        public async Task<UserPreferences> GetUserPreferencesAsync(string userId)
        {
            try
            {
                return await _repository.GetUserPreferencesAsync(userId) ?? new UserPreferences { UserId = userId };
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting preferences for user {UserId}", userId);
                return new UserPreferences { UserId = userId };
            }
        }

        public async Task<bool> UpdateUserPreferencesAsync(string userId, UserPreferences preferences)
        {
            try
            {
                preferences.UserId = userId;
                preferences.UpdatedAt = DateTime.UtcNow;
                return await _repository.UpdateUserPreferencesAsync(preferences);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error updating preferences for user {UserId}", userId);
                return false;
            }
        }
    }
}
