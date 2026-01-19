using Azure;
using Azure.Data.Tables;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Data.Entities;
using System.Linq;

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
                // Get current max order for this user
                var allLikes = await GetAllUserLikesAsync(userId);
                int maxOrder = allLikes.Any() ? allLikes.Max(l => l.Order) : 0;
                int nextOrder = maxOrder + 1;
                
                var entity = new UserLikeEntity(userId, quoteId)
                {
                    Order = nextOrder
                };
                await _likesTableClient.AddEntityAsync(entity);
                _logger.LogInformation("Added like for user {UserId}, quote {QuoteId} with order {Order}", userId, quoteId, nextOrder);
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
                _logger.LogError(ex, "Error getting user liked quote IDs");
                return new List<int>();
            }
        }

        public async Task<List<UserLikeEntity>> GetAllUserLikesAsync(string userId)
        {
            try
            {
                var likes = new List<UserLikeEntity>();
                await foreach (var entity in _likesTableClient.QueryAsync<UserLikeEntity>(filter: $"PartitionKey eq '{userId}'"))
                {
                    likes.Add(entity);
                }
                
                // First, fix any likes that don't have an order (Order = 0)
                var likesWithoutOrder = likes.Where(l => l.Order == 0).ToList();
                if (likesWithoutOrder.Any())
                {
                    var maxOrder = likes.Where(l => l.Order > 0).DefaultIfEmpty().Max(l => l?.Order ?? 0);
                    foreach (var like in likesWithoutOrder)
                    {
                        maxOrder++;
                        like.Order = maxOrder;
                        await UpdateUserLikeOrderAsync(userId, like.QuoteId, maxOrder);
                        _logger.LogInformation("Fixed missing order for user {UserId}, quote {QuoteId} to {Order}", userId, like.QuoteId, maxOrder);
                    }
                    // Refresh the list
                    likes.Clear();
                    await foreach (var entity in _likesTableClient.QueryAsync<UserLikeEntity>(filter: $"PartitionKey eq '{userId}'"))
                    {
                        likes.Add(entity);
                    }
                }
                
                return likes.OrderBy(l => l.Order).ToList();
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting all user likes");
                return new List<UserLikeEntity>();
            }
        }

        public async Task<bool> RemoveAllUserLikesAsync(string userId)
        {
            try
            {
                await foreach (var entity in _likesTableClient.QueryAsync<UserLikeEntity>(filter: $"PartitionKey eq '{userId}'"))
                {
                    await _likesTableClient.DeleteEntityAsync(entity.PartitionKey, entity.RowKey);
                }
                _logger.LogInformation("Removed all likes for user {UserId}", userId);
                return true;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error removing all user likes");
                return false;
            }
        }

        public async Task<bool> RemoveUserProgressAsync(string userId)
        {
            try
            {
                await _progressTableClient.DeleteEntityAsync(userId, userId);
                _logger.LogInformation("Removed progress for user {UserId}", userId);
                return true;
            }
            catch (RequestFailedException ex) when (ex.Status == 404)
            {
                return true; // Progress didn't exist, that's ok
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error removing user progress");
                return false;
            }
        }

        public async Task<bool> UpdateUserLikeOrderAsync(string userId, int quoteId, int newOrder)
        {
            try
            {
                var entity = new UserLikeEntity(userId, quoteId)
                {
                    Order = newOrder,
                    ETag = ETag.All
                };
                await _likesTableClient.UpdateEntityAsync(entity, new ETag("*"), TableUpdateMode.Replace);
                _logger.LogInformation("Updated order for user {UserId}, quote {QuoteId} to {Order}", userId, quoteId, newOrder);
                return true;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error updating user like order");
                return false;
            }
        }

        public async Task<UserProgress?> GetUserProgressAsync(string userId)
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
                
                return new UserProgress
                {
                    Username = entity.PartitionKey,
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

        public async Task<bool> UpdateUserPreferencesAsync(UserProgress preferences)
        {
            try
            {
                var entity = new TableEntity(preferences.Username, preferences.Username)
                {
                    ["LastQuoteId"] = preferences.LastQuoteId,
                    ["UpdatedAt"] = preferences.UpdatedAt
                };
                
                await _progressTableClient.UpsertEntityAsync(entity);
                _logger.LogInformation("Updated user preferences for {Username}, LastQuoteId: {LastQuoteId}", preferences.Username, preferences.LastQuoteId);
                return true;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error updating user preferences for {Username}", preferences.Username);
                return false;
            }
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
        
        public async Task<int> GetTotalLikesCountAsync()
        {
            try
            {
                var totalCount = 0;
                await foreach (var entity in _likesTableClient.QueryAsync<UserLikeEntity>())
                {
                    totalCount++;
                }
                _logger.LogInformation("Total likes count: {Count}", totalCount);
                return totalCount;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting total likes count");
                return 0;
            }
        }
    }
}
