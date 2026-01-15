using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Data;

namespace QuoteAzureBackend.Services
{
    public interface IQuoteService
    {
        Task<Quote> GetQuoteAsync(string? username, HashSet<int> idsToExclude);
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
        Task RecordViewAsync(string username, int quoteId);
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

        public async Task<Quote> GetQuoteAsync(string? username, HashSet<int> idsToExclude)
        {
            if (!string.IsNullOrEmpty(username))
            {
                return await GetNextSequentialQuoteAsync(username);
            }
            return await GetRandomQuoteForUnauthenticatedUserAsync(idsToExclude);
        }

        private async Task<Quote> GetNextSequentialQuoteAsync(string username)
        {
            _logger.LogInformation("Getting next sequential quote for user: {Username}", username);
            var userProgress = await _userActivityRepository.GetUserPreferencesAsync(username);
            int nextQuoteId = (userProgress == null || userProgress.LastQuoteId == 0) ? 1 : userProgress.LastQuoteId + 1;
            
            var allQuotes = await _quoteRepository.GetAllQuotesAsync();
            int maxId = allQuotes.Any() ? allQuotes.Max(q => q.Id) : 0;
            
            if (nextQuoteId > maxId)
            {
                await FetchMoreQuotesIfNeededAsync();
                allQuotes = await _quoteRepository.GetAllQuotesAsync();
            }
            
            var quote = await _quoteRepository.GetQuoteByIdAsync(nextQuoteId);
            if (quote == null)
            {
                quote = await FindNextAvailableQuoteAsync(nextQuoteId);
            }
            
            if (quote != null)
            {
                await _userActivityRepository.UpdateUserPreferencesAsync(new UserPreferences
                {
                    UserId = username,
                    LastQuoteId = quote.Id,
                    UpdatedAt = DateTime.UtcNow
                });
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
                foreach (var quote in fetchedQuotes)
                {
                    if (!existingTexts.Contains(quote.QuoteText))
                    {
                        quote.Id = nextId++;
                        await _quoteRepository.AddQuoteAsync(quote);
                        existingTexts.Add(quote.QuoteText);
                        addedCount++;
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
            var likedQuoteIds = await _userActivityRepository.GetUserLikedQuoteIdsAsync(username);
            var likedQuotes = new List<Quote>();
            foreach (var quoteId in likedQuoteIds)
            {
                var quote = await _quoteRepository.GetQuoteByIdAsync(quoteId);
                if (quote != null)
                    likedQuotes.Add(quote);
            }
            return likedQuotes;
        }

        public async Task<int> GetLikeCountAsync(int quoteId) => 0;

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
            if (string.IsNullOrEmpty(username)) return new List<Quote>();
            var userProgress = await _userActivityRepository.GetUserPreferencesAsync(username);
            if (userProgress == null || userProgress.LastQuoteId == 0) return new List<Quote>();
            
            var viewedQuotes = new List<Quote>();
            for (int id = 1; id <= userProgress.LastQuoteId; id++)
            {
                var quote = await _quoteRepository.GetQuoteByIdAsync(id);
                if (quote != null)
                    viewedQuotes.Add(quote);
            }
            return viewedQuotes;
        }

        public async Task RecordViewAsync(string username, int quoteId)
        {
            if (!string.IsNullOrEmpty(username))
            {
                await _userActivityRepository.UpdateUserPreferencesAsync(new UserPreferences
                {
                    UserId = username,
                    LastQuoteId = quoteId,
                    UpdatedAt = DateTime.UtcNow
                });
            }
        }

        public async Task ReorderLikedQuoteAsync(string username, int quoteId, int newOrder)
        {
            await Task.CompletedTask;
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
