using Azure;
using Azure.Data.Tables;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Data.Entities;

namespace QuoteAzureBackend.Data
{
    public class UserActivityRepository : IUserActivityRepository
    {
        private readonly TableClient _likesTableClient;
        private readonly TableClient _viewHistoryTableClient;
        private readonly TableClient _progressTableClient;
        private readonly ILogger<UserActivityRepository> _logger;

        public UserActivityRepository(IConfiguration configuration, ILogger<UserActivityRepository> logger)
        {
            var connectionString = configuration["TableStorageConnectionString"];
            _likesTableClient = new TableClient(connectionString, "userlikes");
            _viewHistoryTableClient = new TableClient(connectionString, "userviewhistory");
            _progressTableClient = new TableClient(connectionString, "userprogress");
            _logger = logger;
            
            // Create tables if they don't exist
            _likesTableClient.CreateIfNotExists();
            _viewHistoryTableClient.CreateIfNotExists();
            _progressTableClient.CreateIfNotExists();
        }

        public async Task<bool> AddUserLikeAsync(string userId, int quoteId)
        {
            try
            {
                var entity = new UserLikeEntity(userId, quoteId);
                await _likesTableClient.AddEntityAsync(entity);
                _logger.LogInformation("Added like for user {UserId}, quote {QuoteId}", userId, quoteId);
                return true;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error adding user like");
                return false;
            }
        }

        public async Task<bool> RemoveUserLikeAsync(string userId, int quoteId)
        {
            try
            {
                await _likesTableClient.DeleteEntityAsync(userId, $"{userId}_{quoteId}");
                _logger.LogInformation("Removed like for user {UserId}, quote {QuoteId}", userId, quoteId);
                return true;
            }
            catch (RequestFailedException ex) when (ex.Status == 404)
            {
                return false; // Like didn't exist
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error removing user like");
                return false;
            }
        }

        public async Task<List<int>> GetUserLikedQuoteIdsAsync(string userId)
        {
            try
            {
                var likedQuoteIds = new List<int>();
                await foreach (var entity in _likesTableClient.QueryAsync<UserLikeEntity>(filter: $"PartitionKey eq '{userId}'"))
                {
                    likedQuoteIds.Add(entity.QuoteId);
                }
                return likedQuoteIds;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting user liked quotes");
                return new List<int>();
            }
        }

        public async Task<bool> RecordViewAsync(UserViewHistory viewHistory)
        {
            try
            {
                var entity = new UserViewHistoryEntity(viewHistory.UserId, viewHistory.QuoteId);
                await _viewHistoryTableClient.AddEntityAsync(entity);
                _logger.LogInformation("Recorded view for user {UserId}, quote {QuoteId}", viewHistory.UserId, viewHistory.QuoteId);
                return true;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error recording view");
                return false;
            }
        }

        public async Task<List<int>> GetUserViewHistoryQuoteIdsAsync(string userId, int limit)
        {
            try
            {
                var viewedQuoteIds = new List<int>();
                var query = _viewHistoryTableClient.QueryAsync<UserViewHistoryEntity>(filter: $"PartitionKey eq '{userId}'");
                
                var count = 0;
                await foreach (var entity in query)
                {
                    if (count >= limit) break;
                    viewedQuoteIds.Add(entity.QuoteId);
                    count++;
                }
                
                return viewedQuoteIds.OrderByDescending(id => id).ToList();
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting user view history");
                return new List<int>();
            }
        }

        // Placeholder implementations for existing interface methods
        public async Task<bool> AddFavoriteAsync(UserFavorite favorite)
        {
            // For now, treat favorites as likes
            return await AddUserLikeAsync(favorite.UserId, favorite.QuoteId);
        }

        public async Task<bool> RemoveFavoriteAsync(string userId, int quoteId)
        {
            // For now, treat favorites as likes
            return await RemoveUserLikeAsync(userId, quoteId);
        }

        public async Task<List<int>> GetUserFavoriteQuoteIdsAsync(string userId)
        {
            // For now, treat favorites as likes
            return await GetUserLikedQuoteIdsAsync(userId);
        }

        public async Task<UserPreferences?> GetUserPreferencesAsync(string userId)
        {
            // Not implemented in Table Storage version yet
            return await Task.FromResult<UserPreferences?>(null);
        }

        public async Task<bool> UpdateUserPreferencesAsync(UserPreferences preferences)
        {
            // Not implemented in Table Storage version yet
            return await Task.FromResult(false);
        }
    }
}
