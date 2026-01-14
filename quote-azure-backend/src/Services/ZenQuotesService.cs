using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using System.Net.Http.Json;
using System.Text.Json;

namespace QuoteAzureBackend.Services
{
    public interface IZenQuotesService
    {
        Task<Quote> GetRandomQuoteAsync();
        Task<List<Quote>> GetMultipleQuotesAsync(int count = 5);
    }

    public class ZenQuotesService : IZenQuotesService
    {
        private readonly HttpClient _httpClient;
        private readonly ILogger<ZenQuotesService> _logger;

        public ZenQuotesService(HttpClient httpClient, ILogger<ZenQuotesService> logger)
        {
            _httpClient = httpClient;
            _logger = logger;
            _httpClient.BaseAddress = new Uri("https://zenquotes.io/api/");
        }

        public async Task<Quote> GetRandomQuoteAsync()
        {
            try
            {
                _logger.LogInformation("Fetching random quote from ZenQuotes API");
                var response = await _httpClient.GetAsync("random");
                response.EnsureSuccessStatusCode();
                
                var content = await response.Content.ReadAsStringAsync();
                _logger.LogInformation("ZenQuotes API response: {Content}", content);
                
                var zenQuotes = JsonSerializer.Deserialize<List<ZenQuoteResponse>>(content);
                
                if (zenQuotes?.Any() == true)
                {
                    var zenQuote = zenQuotes.First();
                    return new Quote
                    {
                        Id = Guid.NewGuid().GetHashCode(),
                        QuoteText = zenQuote.q,
                        Author = zenQuote.a,
                        LikeCount = 0,
                        CreatedAt = DateTime.UtcNow,
                        Source = "ZenQuotes"
                    };
                }
                
                _logger.LogError("No quotes returned from ZenQuotes API. Response: {Content}", content);
                throw new InvalidOperationException("No quotes returned from ZenQuotes API");
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error fetching quote from ZenQuotes API");
                throw;
            }
        }

        public async Task<List<Quote>> GetMultipleQuotesAsync(int count = 5)
        {
            try
            {
                var response = await _httpClient.GetAsync("quotes");
                response.EnsureSuccessStatusCode();
                
                var content = await response.Content.ReadAsStringAsync();
                var zenQuotes = JsonSerializer.Deserialize<List<ZenQuoteResponse>>(content);
                
                return zenQuotes?.Select(zq => new Quote
                {
                    Id = Guid.NewGuid().GetHashCode(),
                    QuoteText = zq.q,
                    Author = zq.a,
                    LikeCount = 0,
                    CreatedAt = DateTime.UtcNow,
                    Source = "ZenQuotes"
                }).ToList() ?? new List<Quote>();
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error fetching multiple quotes from ZenQuotes API");
                throw;
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
