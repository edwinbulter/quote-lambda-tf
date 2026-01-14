using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;

namespace QuoteAzureBackend.Data
{
    public class QuoteRepository : IQuoteRepository
    {
        private readonly ILogger<QuoteRepository> _logger;
        private static readonly List<Quote> _quotes = new List<Quote>();

        public QuoteRepository(ILogger<QuoteRepository> logger)
        {
            _logger = logger;
            // Initialize with sample data if empty
            if (!_quotes.Any())
            {
                InitializeSampleQuotes();
            }
        }

        public async Task<Quote> GetQuoteByIdAsync(int id)
        {
            return await Task.FromResult(_quotes.FirstOrDefault(q => q.Id == id));
        }

        public async Task<List<Quote>> GetAllQuotesAsync()
        {
            return await Task.FromResult(_quotes.ToList());
        }

        public async Task<Quote> AddQuoteAsync(Quote quote)
        {
            if (quote.Id == 0)
            {
                quote.Id = Math.Abs(DateTime.UtcNow.GetHashCode());
            }
            
            _quotes.Add(quote);
            _logger.LogInformation("Added quote with ID: {QuoteId}", quote.Id);
            
            return await Task.FromResult(quote);
        }

        public async Task<bool> DeleteQuoteAsync(int id)
        {
            var quote = _quotes.FirstOrDefault(q => q.Id == id);
            if (quote != null)
            {
                _quotes.Remove(quote);
                _logger.LogInformation("Deleted quote with ID: {QuoteId}", id);
                return await Task.FromResult(true);
            }
            
            return await Task.FromResult(false);
        }

        private void InitializeSampleQuotes()
        {
            var sampleQuotes = new[]
            {
                new Quote { Id = 1, QuoteText = "The only way to do great work is to love what you do.", Author = "Steve Jobs", LikeCount = 0, CreatedAt = DateTime.UtcNow, Source = "Sample" },
                new Quote { Id = 2, QuoteText = "Innovation distinguishes between a leader and a follower.", Author = "Steve Jobs", LikeCount = 0, CreatedAt = DateTime.UtcNow, Source = "Sample" },
                new Quote { Id = 3, QuoteText = "Life is what happens when you're busy making other plans.", Author = "John Lennon", LikeCount = 0, CreatedAt = DateTime.UtcNow, Source = "Sample" },
                new Quote { Id = 4, QuoteText = "The future belongs to those who believe in the beauty of their dreams.", Author = "Eleanor Roosevelt", LikeCount = 0, CreatedAt = DateTime.UtcNow, Source = "Sample" },
                new Quote { Id = 5, QuoteText = "It is during our darkest moments that we must focus to see the light.", Author = "Aristotle", LikeCount = 0, CreatedAt = DateTime.UtcNow, Source = "Sample" }
            };

            _quotes.AddRange(sampleQuotes);
        }
    }
}
