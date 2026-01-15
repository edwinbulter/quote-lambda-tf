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
            logger?.LogInformation("Table storage connection string: {ConnectionString}", 
                string.IsNullOrEmpty(connectionString) ? "NULL or EMPTY" : "PRESENT");
            
            var tableClient = new TableClient(connectionString, "quotes");
            _tableClient = tableClient;
            _logger = logger ?? throw new ArgumentNullException(nameof(logger));
            
            // Create table if it doesn't exist
            _logger.LogInformation("Creating 'quotes' table if not exists");
            try
            {
                var response = _tableClient.CreateIfNotExists();
                _logger.LogInformation("Table creation response: {Response}", response?.Value?.Name ?? "null");
                _logger.LogInformation("Table client initialized successfully");
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to create table");
                throw;
            }
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
                _logger.LogInformation("Querying quotes table with PartitionKey 'quotes'");
                await foreach (var entity in _tableClient.QueryAsync<QuoteEntity>(filter: $"PartitionKey eq 'quotes'"))
                {
                    quotes.Add(entity.ToQuote());
                    _logger.LogDebug("Found quote with ID: {Id}", entity.RowKey);
                }
                _logger.LogInformation("Retrieved {Count} quotes from table", quotes.Count);
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
                _logger.LogInformation("Adding quote with ID: {Id} and text: {Text}", quote.Id, quote.QuoteText.Substring(0, Math.Min(50, quote.QuoteText.Length)));
                var entity = new QuoteEntity(quote);
                await _tableClient.AddEntityAsync(entity);
                _logger.LogInformation("Successfully added quote with ID: {Id}", quote.Id);
                return entity.ToQuote();
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error adding quote with ID: {Id}", quote.Id);
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
