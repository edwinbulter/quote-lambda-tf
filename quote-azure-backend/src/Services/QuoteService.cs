using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Data;

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

        public QuoteService(IQuoteRepository repository, IZenQuotesService zenQuotesService, ILogger<QuoteService> logger)
        {
            _repository = repository;
            _zenQuotesService = zenQuotesService;
            _logger = logger;
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
            return await _repository.GetAllQuotesAsync();
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

            // In a real implementation, you would store this in a database
            // For now, we'll just increment the like count
            quote.LikeCount++;
            
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

            // In a real implementation, you would remove this from a database
            // For now, we'll just decrement the like count if it's greater than 0
            if (quote.LikeCount > 0)
            {
                quote.LikeCount--;
            }
            
            _logger.LogInformation("User {UserId} unliked quote {QuoteId}", userId, quoteId);
        }

        public async Task<List<Quote>> GetLikedQuotesAsync(string userId)
        {
            // In a real implementation, you would fetch this from a database
            // For now, we'll return quotes with likes as a placeholder
            var allQuotes = await _repository.GetAllQuotesAsync();
            var likedQuotes = allQuotes.Where(q => q.LikeCount > 0).ToList();
            
            _logger.LogInformation("Returning {Count} liked quotes for user {UserId}", likedQuotes.Count, userId);
            return likedQuotes;
        }
    }
}