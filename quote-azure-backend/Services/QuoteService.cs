using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;

namespace QuoteAzureBackend.Services
{
    public interface IQuoteService
    {
        Task<Quote> GetRandomQuoteAsync(List<int>? excludeIds = null);
        Task<List<Quote>> GetLikedQuotesAsync(string userId);
        Task LikeQuoteAsync(string userId, int quoteId);
        Task UnlikeQuoteAsync(string userId, int quoteId);
        Task<List<Quote>> GetViewHistoryAsync(string userId);
    }

    public class QuoteService : IQuoteService
    {
        private readonly ILogger<QuoteService> _logger;
        private readonly List<Quote> _quotes; // In-memory for local testing

        public QuoteService(ILogger<QuoteService> logger)
        {
            _logger = logger;
            _quotes = GenerateSampleQuotes();
        }

        public async Task<Quote> GetRandomQuoteAsync(List<int>? excludeIds = null)
        {
            var availableQuotes = excludeIds != null 
                ? _quotes.Where(q => !excludeIds.Contains(q.Id)).ToList()
                : _quotes;

            if (!availableQuotes.Any())
                throw new InvalidOperationException("No quotes available");

            var random = new Random();
            var selectedQuote = availableQuotes[random.Next(availableQuotes.Count)];
            
            return await Task.FromResult(selectedQuote);
        }

        public async Task<List<Quote>> GetLikedQuotesAsync(string userId)
        {
            // Mock implementation - in real app, query database
            return await Task.FromResult(new List<Quote>());
        }

        public async Task LikeQuoteAsync(string userId, int quoteId)
        {
            // Mock implementation - in real app, save to database
            await Task.CompletedTask;
        }

        public async Task UnlikeQuoteAsync(string userId, int quoteId)
        {
            // Mock implementation - in real app, remove from database
            await Task.CompletedTask;
        }

        public async Task<List<Quote>> GetViewHistoryAsync(string userId)
        {
            // Mock implementation - in real app, query database
            return await Task.FromResult(new List<Quote>());
        }

        private List<Quote> GenerateSampleQuotes()
        {
            return new List<Quote>
            {
                new Quote { Id = 1, QuoteText = "The only way to do great work is to love what you do.", Author = "Steve Jobs", LikeCount = 15, CreatedAt = DateTime.UtcNow },
                new Quote { Id = 2, QuoteText = "Innovation distinguishes between a leader and a follower.", Author = "Steve Jobs", LikeCount = 12, CreatedAt = DateTime.UtcNow },
                new Quote { Id = 3, QuoteText = "Life is what happens when you're busy making other plans.", Author = "John Lennon", LikeCount = 8, CreatedAt = DateTime.UtcNow },
                new Quote { Id = 4, QuoteText = "The future belongs to those who believe in the beauty of their dreams.", Author = "Eleanor Roosevelt", LikeCount = 10, CreatedAt = DateTime.UtcNow },
                new Quote { Id = 5, QuoteText = "It is during our darkest moments that we must focus to see the light.", Author = "Aristotle", LikeCount = 6, CreatedAt = DateTime.UtcNow }
            };
        }
    }
}
