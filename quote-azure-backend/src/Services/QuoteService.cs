using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Data;

namespace QuoteAzureBackend.Services
{
    public interface IQuoteService
    {
        Task<Quote> GetRandomQuoteAsync();
        Task<Quote> GetQuoteByIdAsync(int id);
        Task<List<Quote>> GetAllQuotesAsync();
        Task<Quote> AddQuoteAsync(Quote quote);
        Task<bool> DeleteQuoteAsync(int id);
        Task<List<Quote>> GetQuotesFromZenQuotesAsync(int count = 5);
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
            try
            {
                var quotes = await _repository.GetAllQuotesAsync();
                if (quotes.Any())
                {
                    var random = new Random();
                    var index = random.Next(quotes.Count);
                    _logger.LogInformation("Returning random quote from {Count} local quotes", quotes.Count);
                    return quotes[index];
                }
                
                _logger.LogWarning("No local quotes found, falling back to ZenQuotes API");
                // Fallback to ZenQuotes if no local quotes
                return await _zenQuotesService.GetRandomQuoteAsync();
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting random quote");
                throw;
            }
        }

        public async Task<Quote> GetQuoteByIdAsync(int id)
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
    }
}