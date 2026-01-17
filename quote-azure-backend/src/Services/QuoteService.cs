using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Data;

namespace QuoteAzureBackend.Services
{
    public interface IQuoteService
    {
        Task<Quote?> GetQuoteAsync(string? username, HashSet<int> idsToExclude);
        Task<Quote?> GetQuoteByIdAsync(string? username, int quoteId);
        Task<Quote?> LikeQuoteAsync(string username, int quoteId);
        Task UnlikeQuoteAsync(string username, int quoteId);
        Task<List<Quote>> GetLikedQuotesByUserAsync(string username);
        Task<int> GetLikeCountAsync(int quoteId);
        Task<bool> HasUserLikedQuoteAsync(string username, int quoteId);
        Task<Quote?> GetPreviousQuoteAsync(string username, int currentQuoteId);
        Task<Quote?> GetNextQuoteAsync(string username, int currentQuoteId);
        Task<UserProgress?> GetUserProgressAsync(string username);
        Task<List<Quote>> GetViewedQuotesAsync(string username);
        Task<bool> RecordViewAsync(string username, int quoteId);
        Task ReorderLikedQuoteAsync(string username, int quoteId, int newOrder);
        Task ResetUserProgressAsync(string username);
    }

    public class QuoteService : IQuoteService
    {
        private readonly IQuoteRepository _quoteRepository;
        private readonly IUserActivityRepository _userActivityRepository;
        private readonly IZenQuotesService _zenQuotesService;
        private readonly ILogger<QuoteService> _logger;

        public QuoteService(
            IQuoteRepository quoteRepository,
            IUserActivityRepository userActivityRepository,
            IZenQuotesService zenQuotesService,
            ILogger<QuoteService> logger)
        {
            _quoteRepository = quoteRepository;
            _userActivityRepository = userActivityRepository;
            _zenQuotesService = zenQuotesService;
            _logger = logger;
        }

        public async Task<Quote?> GetQuoteAsync(string? username, HashSet<int> idsToExclude)
        {
            if (!string.IsNullOrEmpty(username))
            {
                return await GetNextSequentialQuoteAsync(username);
            }
            return await GetRandomQuoteForUnauthenticatedUserAsync(idsToExclude);
        }

        private async Task<Quote?> GetNextSequentialQuoteAsync(string username)
        {
            _logger.LogInformation("Getting next sequential quote for user: {Username}", username);
            
            // Get user's current progress (matching Java implementation)
            var userProgress = await _userActivityRepository.GetUserProgressAsync(username);
            int nextQuoteId;
            
            if (userProgress == null)
            {
                // New user - start with quote ID 1
                nextQuoteId = 1;
                _logger.LogInformation("New user {Username} starting with quote ID: {NextQuoteId}", username, nextQuoteId);
            }
            else
            {
                // Existing user - get next quote
                nextQuoteId = userProgress.LastQuoteId + 1;
                _logger.LogInformation("User {Username} progress: lastQuoteId={LastQuoteId}, nextQuoteId={NextQuoteId}", 
                    username, userProgress.LastQuoteId, nextQuoteId);
            }
            
            // Check if we need to fetch more quotes
            var allQuotes = await _quoteRepository.GetAllQuotesAsync();
            int maxId = allQuotes.Any() ? allQuotes.Max(q => q.Id) : 0;
            
            if (nextQuoteId > maxId)
            {
                _logger.LogInformation("Next quote ID {NextQuoteId} exceeds max ID {MaxId}, fetching more quotes", 
                    nextQuoteId, maxId);
                await FetchMoreQuotesIfNeededAsync();
                allQuotes = await _quoteRepository.GetAllQuotesAsync();
                maxId = allQuotes.Any() ? allQuotes.Max(q => q.Id) : 0;
            }
            
            // Get the quote
            var quote = await _quoteRepository.GetQuoteByIdAsync(nextQuoteId);
            if (quote == null)
            {
                _logger.LogWarning("Quote with ID {NextQuoteId} not found, finding next available quote", nextQuoteId);
                quote = await FindNextAvailableQuoteAsync(nextQuoteId);
            }
            
            if (quote != null)
            {
                // Update user progress (matching Java implementation)
                await _userActivityRepository.UpdateLastQuoteIdAsync(username, quote.Id);
                _logger.LogInformation("Updated user {Username} progress to lastQuoteId={LastQuoteId}", 
                    username, quote.Id);
            }
            
            return quote;
        }

        private async Task<Quote?> GetRandomQuoteForUnauthenticatedUserAsync(HashSet<int> idsToExclude)
        {
            _logger.LogInformation("Getting random quote for unauthenticated user, excluding {Count} IDs", idsToExclude.Count);
            
            var allQuotes = await _quoteRepository.GetAllQuotesAsync();
            int maxId = allQuotes.Any() ? allQuotes.Max(q => q.Id) : 0;
            _logger.LogInformation("Max quote ID in database: {MaxId}", maxId);
            
            if (maxId < 5 || maxId <= idsToExclude.Count)
            {
                _logger.LogInformation("Need to fetch more quotes (maxId={MaxId}, excludeCount={ExcludeCount})", maxId, idsToExclude.Count);
                await FetchMoreQuotesIfNeededAsync();
                allQuotes = await _quoteRepository.GetAllQuotesAsync();
                maxId = allQuotes.Any() ? allQuotes.Max(q => q.Id) : 0;
                _logger.LogInformation("After fetching, new max ID: {MaxId}", maxId);
            }
            
            var random = new Random();
            int maxAttempts = Math.Min(100, maxId);
            var attemptedIds = new HashSet<int>();
            
            for (int attempt = 0; attempt < maxAttempts; attempt++)
            {
                int candidateId = random.Next(maxId) + 1;
                if (idsToExclude.Contains(candidateId) || attemptedIds.Contains(candidateId))
                    continue;
                
                attemptedIds.Add(candidateId);
                var quote = await _quoteRepository.GetQuoteByIdAsync(candidateId);
                if (quote != null)
                    return quote;
            }
            
            var filteredQuotes = allQuotes.Where(q => !idsToExclude.Contains(q.Id)).ToList();
            if (!filteredQuotes.Any())
            {
                _logger.LogWarning("No available quotes after excluding {Count} IDs", idsToExclude.Count);
                return null;
            }
            
            return filteredQuotes[random.Next(filteredQuotes.Count)];
        }

        private async Task FetchMoreQuotesIfNeededAsync()
        {
            try
            {
                _logger.LogInformation("Fetching quotes from ZenQuotes API");
                var fetchedQuotes = await _zenQuotesService.GetMultipleQuotesAsync(50);
                _logger.LogInformation("Fetched {Count} quotes from ZenQuotes", fetchedQuotes?.Count ?? 0);
                
                var currentDatabaseQuotes = await _quoteRepository.GetAllQuotesAsync();
                _logger.LogInformation("Current database has {Count} quotes", currentDatabaseQuotes.Count);
                
                var existingTexts = new HashSet<string>(currentDatabaseQuotes.Select(q => q.QuoteText));
                int nextId = currentDatabaseQuotes.Any() ? currentDatabaseQuotes.Max(q => q.Id) + 1 : 1;
                
                int addedCount = 0;
                if (fetchedQuotes != null)
                {
                    foreach (var quote in fetchedQuotes)
                {
                    if (!existingTexts.Contains(quote.QuoteText))
                    {
                        var originalId = quote.Id;
                        quote.Id = nextId++;
                        _logger.LogInformation("Assigning new ID: {NewId} to quote (original ID: {OriginalId})", quote.Id, originalId);
                        await _quoteRepository.AddQuoteAsync(quote);
                        existingTexts.Add(quote.QuoteText);
                        addedCount++;
                    }
                }
                }
                
                _logger.LogInformation("Added {Count} new quotes to database", addedCount);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to fetch quotes from ZenQuotes");
            }
        }

        private async Task<Quote?> FindNextAvailableQuoteAsync(int startId)
        {
            var allQuotes = await _quoteRepository.GetAllQuotesAsync();
            int maxId = allQuotes.Any() ? allQuotes.Max(q => q.Id) : 0;
            
            for (int id = startId; id <= maxId; id++)
            {
                var quote = await _quoteRepository.GetQuoteByIdAsync(id);
                if (quote != null)
                    return quote;
            }
            return null;
        }

        public async Task<Quote?> GetQuoteByIdAsync(string? username, int quoteId)
        {
            var quote = await _quoteRepository.GetQuoteByIdAsync(quoteId);
            if (quote != null && !string.IsNullOrEmpty(username))
            {
                await _userActivityRepository.UpdateUserPreferencesAsync(new UserPreferences
                {
                    UserId = username,
                    LastQuoteId = quoteId,
                    UpdatedAt = DateTime.UtcNow
                });
            }
            return quote;
        }

        public async Task<Quote?> LikeQuoteAsync(string username, int quoteId)
        {
            var quote = await _quoteRepository.GetQuoteByIdAsync(quoteId);
            if (quote != null)
            {
                await _userActivityRepository.AddUserLikeAsync(username, quoteId);
            }
            return quote;
        }

        public async Task UnlikeQuoteAsync(string username, int quoteId)
        {
            await _userActivityRepository.RemoveUserLikeAsync(username, quoteId);
        }

        public async Task<List<Quote>> GetLikedQuotesByUserAsync(string username)
        {
            var allLikes = await _userActivityRepository.GetAllUserLikesAsync(username);
            var likedQuotes = new List<Quote>();
            foreach (var like in allLikes.OrderBy(l => l.Order))
            {
                var quote = await _quoteRepository.GetQuoteByIdAsync(like.QuoteId);
                if (quote != null)
                    likedQuotes.Add(quote);
            }
            return likedQuotes;
        }

        public Task<int> GetLikeCountAsync(int quoteId) => Task.FromResult(0);

        public async Task<bool> HasUserLikedQuoteAsync(string username, int quoteId)
        {
            var likedIds = await _userActivityRepository.GetUserLikedQuoteIdsAsync(username);
            return likedIds.Contains(quoteId);
        }

        public async Task<Quote?> GetPreviousQuoteAsync(string username, int currentQuoteId)
        {
            if (string.IsNullOrEmpty(username)) return null;
            
            for (int id = currentQuoteId - 1; id >= 1; id--)
            {
                var quote = await _quoteRepository.GetQuoteByIdAsync(id);
                if (quote != null)
                {
                    await _userActivityRepository.UpdateUserPreferencesAsync(new UserPreferences
                    {
                        UserId = username,
                        LastQuoteId = id,
                        UpdatedAt = DateTime.UtcNow
                    });
                    return quote;
                }
            }
            return null;
        }

        public async Task<Quote?> GetNextQuoteAsync(string username, int currentQuoteId)
        {
            if (string.IsNullOrEmpty(username)) return null;
            
            var allQuotes = await _quoteRepository.GetAllQuotesAsync();
            int maxId = allQuotes.Any() ? allQuotes.Max(q => q.Id) : 0;
            
            for (int id = currentQuoteId + 1; id <= maxId; id++)
            {
                var quote = await _quoteRepository.GetQuoteByIdAsync(id);
                if (quote != null)
                {
                    await _userActivityRepository.UpdateUserPreferencesAsync(new UserPreferences
                    {
                        UserId = username,
                        LastQuoteId = id,
                        UpdatedAt = DateTime.UtcNow
                    });
                    return quote;
                }
            }
            return null;
        }

        public async Task<UserProgress?> GetUserProgressAsync(string username)
        {
            if (string.IsNullOrEmpty(username)) return null;
            var preferences = await _userActivityRepository.GetUserPreferencesAsync(username);
            if (preferences == null) return null;
            return new UserProgress
            {
                Username = username,
                LastQuoteId = preferences.LastQuoteId,
                UpdatedAt = preferences.UpdatedAt ?? DateTime.UtcNow
            };
        }

        public async Task<List<Quote>> GetViewedQuotesAsync(string username)
        {
            // Return quotes 1 to lastQuoteId using the sequential system (matching Java implementation)
            _logger.LogInformation("Getting viewed quotes for user: {Username}", username);
            
            if (string.IsNullOrEmpty(username))
            {
                return new List<Quote>();
            }
            
            var progress = await _userActivityRepository.GetUserProgressAsync(username);
            if (progress == null || progress.LastQuoteId <= 0)
            {
                _logger.LogInformation("User {Username} has no progress or hasn't viewed any quotes", username);
                return new List<Quote>();
            }
            
            var viewedQuotes = new List<Quote>();
            for (int i = 1; i <= progress.LastQuoteId; i++)
            {
                var quote = await _quoteRepository.GetQuoteByIdAsync(i);
                if (quote != null)
                {
                    viewedQuotes.Add(quote);
                }
                else
                {
                    _logger.LogWarning("Quote with ID {QuoteId} not found while getting viewed quotes for user {Username}", i, username);
                }
            }
            
            _logger.LogInformation("Retrieved {Count} viewed quotes for user {Username}", viewedQuotes.Count, username);
            return viewedQuotes;
        }

        public Task<bool> RecordViewAsync(string userId, int quoteId)
        {
            try
            {
                // In the Java version, this updates the user progress
                // But in C#, the progress is already updated in GetNextSequentialQuoteAsync
                // So we don't need to do anything here, but we keep the method for compatibility
                _logger.LogDebug("RecordViewAsync called for user {UserId}, quote {QuoteId} - progress already tracked", userId, quoteId);
                return Task.FromResult(true);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error recording view for user {UserId}, quote {QuoteId}", userId, quoteId);
                return Task.FromResult(false);
            }
        }

        
        public async Task ReorderLikedQuoteAsync(string username, int quoteId, int newOrder)
        {
            var allLikes = await _userActivityRepository.GetAllUserLikesAsync(username);
            
            // Find the like to move
            var likeToMove = allLikes.FirstOrDefault(l => l.QuoteId == quoteId);
            
            if (likeToMove == null)
            {
                _logger.LogWarning("Quote {QuoteId} not found in user {Username}'s likes", quoteId, username);
                return;
            }
            
            int oldOrder = likeToMove.Order > 0 ? likeToMove.Order : allLikes.IndexOf(likeToMove) + 1;
            
            if (oldOrder == newOrder)
            {
                return; // No change needed
            }
            
            // Update orders for affected likes
            if (newOrder > oldOrder)
            {
                // Moving down: decrement orders between oldOrder and newOrder
                var likesToUpdate = allLikes.Where(l => l.Order > oldOrder && l.Order <= newOrder);
                foreach (var like in likesToUpdate)
                {
                    await _userActivityRepository.UpdateUserLikeOrderAsync(username, like.QuoteId, like.Order - 1);
                }
            }
            else
            {
                // Moving up: increment orders between newOrder and oldOrder
                var likesToUpdate = allLikes.Where(l => l.Order >= newOrder && l.Order < oldOrder);
                foreach (var like in likesToUpdate)
                {
                    await _userActivityRepository.UpdateUserLikeOrderAsync(username, like.QuoteId, like.Order + 1);
                }
            }
            
            // Set the moved item to new order
            await _userActivityRepository.UpdateUserLikeOrderAsync(username, quoteId, newOrder);
            
            _logger.LogInformation("Reordered quote {QuoteId} for user {Username} from order {OldOrder} to {NewOrder}", 
                quoteId, username, oldOrder, newOrder);
        }

        public async Task ResetUserProgressAsync(string username)
        {
            if (string.IsNullOrEmpty(username)) return;
            await _userActivityRepository.UpdateUserPreferencesAsync(new UserPreferences
            {
                UserId = username,
                LastQuoteId = 0,
                UpdatedAt = DateTime.UtcNow
            });
        }
    }
}
