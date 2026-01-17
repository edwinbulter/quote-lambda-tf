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
        private readonly TableClient _progressTableClient;
        private readonly ILogger<UserActivityRepository> _logger;

        public UserActivityRepository(IConfiguration configuration, ILogger<UserActivityRepository> logger)
        {
            var connectionString = configuration["TableStorageConnectionString"];
            _likesTableClient = new TableClient(connectionString, "userlikes");
            _progressTableClient = new TableClient(connectionString, "userprogress");
            _logger = logger;
            
            // Create tables if they don't exist
            _likesTableClient.CreateIfNotExists();
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

        public Task<bool> RecordViewAsync(UserViewHistory viewHistory)
        {
            // No longer needed - view tracking is handled by the progress table
            // Keeping method for interface compatibility
            _logger.LogDebug("RecordViewAsync called - views are tracked via progress table");
            return Task.FromResult(true);
        }

        public async Task<List<int>> GetUserViewHistoryQuoteIdsAsync(string userId, int limit)
        {
            try
            {
                // Get viewed quotes from progress table (quotes 1 to lastQuoteId)
                var viewedQuoteIds = new List<int>();
                var progress = await GetUserPreferencesAsync(userId);
                
                if (progress != null && progress.LastQuoteId > 0)
                {
                    // Return quotes 1 through lastQuoteId
                    var startId = Math.Max(1, progress.LastQuoteId - limit + 1);
                    for (int i = startId; i <= progress.LastQuoteId; i++)
                    {
                        viewedQuoteIds.Add(i);
                    }
                }
                
                return viewedQuoteIds;
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
            try
            {
                TableEntity? entity = null;
                
                await foreach (var e in _progressTableClient.QueryAsync<TableEntity>(filter: $"PartitionKey eq '{userId}'"))
                {
                    entity = e;
                    break;
                }
                
                if (entity == null)
                {
                    return null;
                }
                
                return new UserPreferences
                {
                    UserId = entity.PartitionKey,
                    LastQuoteId = entity.ContainsKey("LastQuoteId") ? entity.GetInt32("LastQuoteId") ?? 0 : 0,
                    UpdatedAt = entity.ContainsKey("UpdatedAt") ? entity.GetDateTime("UpdatedAt") ?? DateTime.UtcNow : DateTime.UtcNow
                };
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting user preferences for {UserId}", userId);
                return null;
            }
        }

        public async Task<bool> UpdateUserPreferencesAsync(UserPreferences preferences)
        {
            try
            {
                var entity = new TableEntity(preferences.UserId, preferences.UserId)
                {
                    ["LastQuoteId"] = preferences.LastQuoteId,
                    ["UpdatedAt"] = preferences.UpdatedAt
                };
                
                await _progressTableClient.UpsertEntityAsync(entity);
                _logger.LogInformation("Updated user preferences for {UserId}, LastQuoteId: {LastQuoteId}", preferences.UserId, preferences.LastQuoteId);
                return true;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error updating user preferences for {UserId}", preferences.UserId);
                return false;
            }
        }

        // UserProgress-like methods to match Java implementation
        public async Task<UserPreferences?> GetUserProgressAsync(string userId)
        {
            // Same as GetUserPreferencesAsync - just a different name for clarity
            return await GetUserPreferencesAsync(userId);
        }

        public async Task<bool> UpdateLastQuoteIdAsync(string userId, int quoteId)
        {
            try
            {
                var entity = new TableEntity(userId, userId)
                {
                    ["LastQuoteId"] = quoteId,
                    ["UpdatedAt"] = DateTime.UtcNow
                };
                
                await _progressTableClient.UpsertEntityAsync(entity);
                _logger.LogInformation("Updated user {UserId} progress to lastQuoteId={LastQuoteId}", userId, quoteId);
                return true;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error updating last quote ID for {UserId}", userId);
                return false;
            }
        }
    }
}
