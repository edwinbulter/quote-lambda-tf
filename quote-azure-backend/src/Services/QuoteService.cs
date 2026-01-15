using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Data;
using QuoteAzureBackend.Data.Entities;

namespace QuoteAzureBackend.Services
{
    public interface IQuoteService
    {
        Task<Quote> GetRandomQuoteAsync();
        Task<Quote?> GetQuoteByIdAsync(int id);
        Task<List<Quote>> GetAllQuotesAsync();
        Task<Quote> AddQuoteAsync(Quote quote);
        Task<bool> DeleteQuoteAsync(int id);
        Task<List<Quote>> GetQuotesFromZenQuotesAsync(int count = 5);
        
        // New methods for frontend functionality
        Task<Quote> GetQuoteAsync(string? userId, HashSet<int> idsToExclude);
        Task<Quote?> LikeQuoteAsync(string userId, int quoteId);
        Task UnlikeQuoteAsync(string userId, int quoteId);
        Task<List<Quote>> GetLikedQuotesAsync(string userId);
    }

    public class QuoteService : IQuoteService
    {
        private readonly IQuoteRepository _repository;
        private readonly IZenQuotesService _zenQuotesService;
        private readonly ILogger<QuoteService> _logger;
        private readonly IServiceProvider _serviceProvider;

        public QuoteService(IQuoteRepository repository, IZenQuotesService zenQuotesService, ILogger<QuoteService> logger, IServiceProvider serviceProvider)
        {
            _repository = repository;
            _zenQuotesService = zenQuotesService;
            _logger = logger;
            _serviceProvider = serviceProvider;
        }

        public async Task<Quote> GetRandomQuoteAsync()
        {
            var quotes = await _repository.GetAllQuotesAsync();
            if (quotes.Any())
            {
                var random = new Random();
                var index = random.Next(quotes.Count);
                return quotes[index];
            }
            
            // Fallback to ZenQuotes if no local quotes
            return await _zenQuotesService.GetRandomQuoteAsync();
        }

        public async Task<Quote?> GetQuoteByIdAsync(int id)
        {
            return await _repository.GetQuoteByIdAsync(id);
        }

        public async Task<List<Quote>> GetAllQuotesAsync()
        {
            var quotes = await _repository.GetAllQuotesAsync();
            
            // If no quotes in repository, fetch from ZenQuotes and populate
            if (!quotes.Any())
            {
                _logger.LogInformation("No quotes found in repository, fetching from ZenQuotes API");
                try
                {
                    var zenQuotes = await _zenQuotesService.GetMultipleQuotesAsync(5);
                    foreach (var zenQuote in zenQuotes)
                    {
                        var addedQuote = await _repository.AddQuoteAsync(zenQuote);
                        quotes.Add(addedQuote);
                    }
                    _logger.LogInformation("Added {Count} quotes from ZenQuotes to repository", quotes.Count);
                }
                catch (Exception ex)
                {
                    _logger.LogError(ex, "Error fetching quotes from ZenQuotes API");
                    // Return empty list if ZenQuotes fails
                }
            }
            
            return quotes;
        }

        public async Task<Quote> AddQuoteAsync(Quote quote)
        {
            return await _repository.AddQuoteAsync(quote);
        }

        public async Task<bool> DeleteQuoteAsync(int id)
        {
            return await _repository.DeleteQuoteAsync(id);
        }

        public async Task<List<Quote>> GetQuotesFromZenQuotesAsync(int count = 5)
        {
            var zenQuotes = await _zenQuotesService.GetMultipleQuotesAsync(count);
            var addedQuotes = new List<Quote>();
            
            foreach (var quote in zenQuotes)
            {
                try
                {
                    var addedQuote = await _repository.AddQuoteAsync(quote);
                    addedQuotes.Add(addedQuote);
                }
                catch (Exception ex)
                {
                    _logger.LogWarning(ex, "Failed to add quote: {QuoteText}", quote.QuoteText);
                }
            }
            
            return addedQuotes;
        }

        // New methods for frontend functionality
        public async Task<Quote> GetQuoteAsync(string? userId, HashSet<int> idsToExclude)
        {
            var quotes = await _repository.GetAllQuotesAsync();
            
            // Filter out excluded IDs
            var availableQuotes = quotes.Where(q => !idsToExclude.Contains(q.Id)).ToList();
            
            if (!availableQuotes.Any())
            {
                // Fallback to ZenQuotes if no local quotes available
                return await _zenQuotesService.GetRandomQuoteAsync();
            }
            
            var random = new Random();
            var index = random.Next(availableQuotes.Count);
            return availableQuotes[index];
        }

        public async Task<Quote?> LikeQuoteAsync(string userId, int quoteId)
        {
            var quote = await _repository.GetQuoteByIdAsync(quoteId);
            if (quote == null)
            {
                return null;
            }

            // Add user like to Table Storage
            var userActivityRepo = _serviceProvider.GetRequiredService<IUserActivityRepository>();
            if (userActivityRepo != null)
            {
                await userActivityRepo.AddUserLikeAsync(userId, quoteId);
            }

            // Increment like count
            quote.LikeCount++;
            await _repository.UpdateQuoteAsync(quote);
            
            _logger.LogInformation("User {UserId} liked quote {QuoteId}", userId, quoteId);
            return quote;
        }

        public async Task UnlikeQuoteAsync(string userId, int quoteId)
        {
            var quote = await _repository.GetQuoteByIdAsync(quoteId);
            if (quote == null)
            {
                return;
            }

            // Remove user like from Table Storage
            var userActivityRepo = _serviceProvider.GetRequiredService<IUserActivityRepository>();
            if (userActivityRepo != null)
            {
                await userActivityRepo.RemoveUserLikeAsync(userId, quoteId);
            }

            // Decrement like count if it's greater than 0
            if (quote.LikeCount > 0)
            {
                quote.LikeCount--;
                await _repository.UpdateQuoteAsync(quote);
            }
            
            _logger.LogInformation("User {UserId} unliked quote {QuoteId}", userId, quoteId);
        }

        public async Task<List<Quote>> GetLikedQuotesAsync(string userId)
        {
            var userActivityRepo = _serviceProvider.GetRequiredService<IUserActivityRepository>();
            if (userActivityRepo == null)
            {
                return new List<Quote>();
            }

            var likedQuoteIds = await userActivityRepo.GetUserLikedQuoteIdsAsync(userId);
            var likedQuotes = new List<Quote>();

            foreach (var quoteId in likedQuoteIds)
            {
                var quote = await _repository.GetQuoteByIdAsync(quoteId);
                if (quote != null)
                {
                    likedQuotes.Add(quote);
                }
            }
            
            _logger.LogInformation("Returning {Count} liked quotes for user {UserId}", likedQuotes.Count, userId);
            return likedQuotes;
        }
    }
}