using Microsoft.Azure.Functions.Worker;
using Microsoft.Azure.Functions.Worker.Http;
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Services;
using System.Net;

namespace QuoteAzureBackend.Handlers
{
    public class QuoteHandler
    {
        private readonly ILogger<QuoteHandler> _logger;
        private readonly IQuoteService _quoteService;
        private readonly IUserActivityService _userActivityService;

        public QuoteHandler(ILogger<QuoteHandler> logger, IQuoteService quoteService, IUserActivityService userActivityService)
        {
            _logger = logger;
            _quoteService = quoteService;
            _userActivityService = userActivityService;
        }

        [Function("quotes")]
        public async Task<HttpResponseData> GetQuotesAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", Route = "quotes")] HttpRequestData req,
            FunctionContext executionContext)
        {
            _logger.LogInformation("Getting all quotes");

            try
            {
                var quotes = await _quoteService.GetAllQuotesAsync();
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(quotes);
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting quotes");
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        [Function("quote/random")]
        public async Task<HttpResponseData> GetRandomQuoteAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", Route = "quote/random")] HttpRequestData req,
            FunctionContext executionContext)
        {
            _logger.LogInformation("Getting random quote");

            try
            {
                var quote = await _quoteService.GetRandomQuoteAsync();
                
                // Record view if user is authenticated
                var userId = GetUserFromRequest(req);
                if (!string.IsNullOrEmpty(userId))
                {
                    await _userActivityService.RecordViewAsync(userId, quote.Id);
                }

                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(quote);
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting random quote");
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        [Function("quotes/zen")]
        public async Task<HttpResponseData> GetZenQuotesAsync(
            [HttpTrigger(AuthorizationLevel.Function, "post", Route = "quotes/zen")] HttpRequestData req,
            FunctionContext executionContext)
        {
            _logger.LogInformation("Fetching quotes from ZenQuotes API");

            try
            {
                var requestBody = await new StreamReader(req.Body).ReadToEndAsync();
                var count = string.IsNullOrEmpty(requestBody) ? 5 : int.Parse(requestBody);
                
                var quotes = await _quoteService.GetQuotesFromZenQuotesAsync(count);
                
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(new { AddedQuotes = quotes.Count, Quotes = quotes });
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error fetching ZenQuotes");
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        [Function("quote/{id}")]
        public async Task<HttpResponseData> GetQuoteByIdAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", Route = "quote/{id}")] HttpRequestData req,
            int id,
            FunctionContext executionContext)
        {
            _logger.LogInformation("Getting quote by ID: {QuoteId}", id);

            try
            {
                var quote = await _quoteService.GetQuoteByIdAsync(id);
                
                if (quote == null)
                {
                    return req.CreateResponse(HttpStatusCode.NotFound);
                }

                // Record view if user is authenticated
                var userId = GetUserFromRequest(req);
                if (!string.IsNullOrEmpty(userId))
                {
                    await _userActivityService.RecordViewAsync(userId, quote.Id);
                }

                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(quote);
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting quote by ID");
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        private string GetUserFromRequest(HttpRequestData req)
        {
            // Extract user ID from JWT token or headers
            // This is a simplified version - implement proper JWT validation
            if (req.Headers.TryGetValues("X-User-Id", out var userIdValues))
            {
                return userIdValues.FirstOrDefault();
            }
            return string.Empty;
        }
    }
}
