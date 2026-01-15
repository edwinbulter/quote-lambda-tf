using Azure;
using Azure.Data.Tables;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Data.Entities;

namespace QuoteAzureBackend.Data
{
    public class QuoteRepository : IQuoteRepository
    {
        private readonly TableClient _tableClient;
        private readonly ILogger<QuoteRepository> _logger;

        public QuoteRepository(IConfiguration configuration, ILogger<QuoteRepository> logger)
        {
            var connectionString = configuration["TableStorageConnectionString"];
            var tableClient = new TableClient(connectionString, "quotes");
            _tableClient = tableClient;
            _logger = logger;
            
            // Create table if it doesn't exist
            _tableClient.CreateIfNotExists();
        }

        public async Task<Quote?> GetQuoteByIdAsync(int id)
        {
            try
            {
                var response = await _tableClient.GetEntityAsync<QuoteEntity>("quotes", id.ToString());
                return response.Value?.ToQuote();
            }
            catch (RequestFailedException ex) when (ex.Status == 404)
            {
                return null;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting quote by ID: {Id}", id);
                throw;
            }
        }

        public async Task<List<Quote>> GetAllQuotesAsync()
        {
            try
            {
                var quotes = new List<Quote>();
                await foreach (var entity in _tableClient.QueryAsync<QuoteEntity>(filter: $"PartitionKey eq 'quotes'"))
                {
                    quotes.Add(entity.ToQuote());
                }
                return quotes;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting all quotes");
                throw;
            }
        }

        public async Task<Quote> AddQuoteAsync(Quote quote)
        {
            try
            {
                var entity = new QuoteEntity(quote);
                await _tableClient.AddEntityAsync(entity);
                return entity.ToQuote();
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error adding quote");
                throw;
            }
        }

        public async Task<bool> DeleteQuoteAsync(int id)
        {
            try
            {
                await _tableClient.DeleteEntityAsync("quotes", id.ToString());
                return true;
            }
            catch (RequestFailedException ex) when (ex.Status == 404)
            {
                return false;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error deleting quote: {Id}", id);
                throw;
            }
        }

        public async Task<bool> UpdateQuoteAsync(Quote quote)
        {
            try
            {
                var entity = new QuoteEntity(quote);
                await _tableClient.UpdateEntityAsync(entity, ETag.All);
                return true;
            }
            catch (RequestFailedException ex) when (ex.Status == 404)
            {
                return false;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error updating quote: {Id}", quote.Id);
                throw;
            }
        }
    }
}
