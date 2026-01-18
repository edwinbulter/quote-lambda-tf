using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Data;
using System.Net.Http.Json;
using System.Text.Json;

namespace QuoteAzureBackend.Services
{
    public interface IZenQuotesService
    {
        Task<Quote> GetRandomQuoteAsync();
        Task<List<Quote>> GetMultipleQuotesAsync();
    }

    public class ZenQuotesService : IZenQuotesService
    {
        private readonly HttpClient _httpClient;
        private readonly ILogger<ZenQuotesService> _logger;
        private readonly IQuoteRepository _quoteRepository;

        public ZenQuotesService(HttpClient httpClient, ILogger<ZenQuotesService> logger, IQuoteRepository quoteRepository)
        {
            _httpClient = httpClient;
            _logger = logger;
            _quoteRepository = quoteRepository;
            _httpClient.BaseAddress = new Uri("https://zenquotes.io/api/");
        }

        public async Task<Quote> GetRandomQuoteAsync()
        {
            try
            {
                var response = await _httpClient.GetAsync("random");
                response.EnsureSuccessStatusCode();
                
                var content = await response.Content.ReadAsStringAsync();
                var zenQuotes = JsonSerializer.Deserialize<List<ZenQuoteResponse>>(content);
                
                if (zenQuotes?.Any() == true)
                {
                    var zenQuote = zenQuotes.First();
                    var nextId = await GetNextAvailableIdAsync();
                    return new Quote
                    {
                        Id = nextId,
                        QuoteText = zenQuote.q,
                        Author = zenQuote.a,
                        LikeCount = 0,
                        CreatedAt = DateTime.UtcNow,
                        Source = "ZenQuotes"
                    };
                }
                
                throw new InvalidOperationException("No quotes returned from ZenQuotes API");
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error fetching quote from ZenQuotes API");
                throw;
            }
        }

        public async Task<List<Quote>> GetMultipleQuotesAsync()
        {
            try
            {
                var response = await _httpClient.GetAsync("quotes");
                response.EnsureSuccessStatusCode();
                
                var content = await response.Content.ReadAsStringAsync();
                var zenQuotes = JsonSerializer.Deserialize<List<ZenQuoteResponse>>(content);
                
                var quotes = new List<Quote>();
                if (zenQuotes != null)
                {
                    var nextId = await GetNextAvailableIdAsync();
                    
                    for (int i = 0; i < zenQuotes.Count; i++)
                    {
                        quotes.Add(new Quote
                        {
                            Id = nextId + i,
                            QuoteText = zenQuotes[i].q,
                            Author = zenQuotes[i].a,
                            LikeCount = 0,
                            CreatedAt = DateTime.UtcNow,
                            Source = "ZenQuotes"
                        });
                    }
                }
                
                return quotes;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error fetching multiple quotes from ZenQuotes API");
                throw;
            }
        }
        
        private async Task<int> GetNextAvailableIdAsync()
        {
            try
            {
                var existingQuotes = await _quoteRepository.GetAllQuotesAsync();
                if (!existingQuotes.Any())
                {
                    return 1; // Start with ID 1 if no quotes exist
                }
                
                var maxId = existingQuotes.Max(q => q.Id);
                return maxId + 1;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting next available ID");
                // Fallback to a timestamp-based ID if there's an error
                return (int)(DateTime.UtcNow.Ticks % int.MaxValue);
            }
        }
    }

    public class ZenQuoteResponse
    {
        public string q { get; set; } = string.Empty;
        public string a { get; set; } = string.Empty;
        public string h { get; set; } = string.Empty;
    }
}
