using Microsoft.Azure.Functions.Worker;
using Microsoft.Azure.Functions.Worker.Http;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.DependencyInjection;
using QuoteAzureBackend.Data;
using QuoteAzureBackend.Models;
using System.Net;

namespace QuoteAzureBackend.Handlers
{
    public class TestHandler
    {
        private readonly ILogger<TestHandler> _logger;
        private readonly IQuoteRepository _quoteRepository;

        public TestHandler(ILogger<TestHandler> logger, IQuoteRepository quoteRepository)
        {
            _logger = logger;
            _quoteRepository = quoteRepository;
        }

        [Function("TestTableStorage")]
        public async Task<HttpResponseData> TestTableStorageAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", Route = "test/table")] HttpRequestData req,
            FunctionContext executionContext)
        {
            try
            {
                _logger.LogInformation("Testing table storage operations");

                // Test 1: Get all quotes
                var allQuotes = await _quoteRepository.GetAllQuotesAsync();
                _logger.LogInformation("Current quote count: {Count}", allQuotes.Count);

                // Test 2: Add a test quote
                var testQuote = new Quote
                {
                    Id = DateTime.UtcNow.Millisecond,
                    QuoteText = $"Test quote at {DateTime.UtcNow}",
                    Author = "Test Author",
                    LikeCount = 0,
                    CreatedAt = DateTime.UtcNow,
                    Source = "Test"
                };

                _logger.LogInformation("Adding test quote with ID: {Id}", testQuote.Id);
                var addedQuote = await _quoteRepository.AddQuoteAsync(testQuote);
                _logger.LogInformation("Successfully added test quote");

                // Test 3: Retrieve the added quote
                var retrievedQuote = await _quoteRepository.GetQuoteByIdAsync(testQuote.Id);
                _logger.LogInformation("Retrieved quote: {Text}", retrievedQuote?.QuoteText);

                // Test 4: Get all quotes again
                var allQuotesAfter = await _quoteRepository.GetAllQuotesAsync();
                _logger.LogInformation("Quote count after adding: {Count}", allQuotesAfter.Count);

                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(new
                {
                    initialCount = allQuotes.Count,
                    addedQuoteId = addedQuote.Id,
                    retrievedQuoteText = retrievedQuote?.QuoteText,
                    finalCount = allQuotesAfter.Count,
                    connectionTest = "Success"
                });
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error testing table storage");
                var response = req.CreateResponse(HttpStatusCode.InternalServerError);
                await response.WriteStringAsync($"Error: {ex.Message}");
                return response;
            }
        }

        [Function("ListTables")]
        public async Task<HttpResponseData> ListTablesAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", Route = "test/tables")] HttpRequestData req,
            FunctionContext executionContext)
        {
            try
            {
                _logger.LogInformation("Testing table listing");
                
                // Get the connection string from the repository
                var configuration = executionContext.InstanceServices.GetRequiredService<Microsoft.Extensions.Configuration.IConfiguration>();
                var connectionString = configuration["TableStorageConnectionString"];
                
                var serviceClient = new Azure.Data.Tables.TableServiceClient(connectionString);
                var tables = new List<string>();
                
                await foreach (var table in serviceClient.QueryAsync())
                {
                    tables.Add(table.Name);
                }
                
                _logger.LogInformation("Found {Count} tables: {Tables}", tables.Count, string.Join(", ", tables));
                
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(new
                {
                    tableCount = tables.Count,
                    tables = tables,
                    connectionTest = connectionString?.Contains("qbtst") == true ? "Correct storage account" : "Wrong storage account"
                });
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error listing tables");
                var response = req.CreateResponse(HttpStatusCode.InternalServerError);
                await response.WriteStringAsync($"Error: {ex.Message}");
                return response;
            }
        }
    }
}
