using Microsoft.Azure.Functions.Worker;
using Microsoft.Azure.Functions.Worker.Http;
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Services;
using System.Net;

namespace QuoteAzureBackend
{
    public class QuoteHandler
    {
        private readonly ILogger<QuoteHandler> _logger;
        private readonly IQuoteService _quoteService;

        public QuoteHandler(ILogger<QuoteHandler> logger, IQuoteService quoteService)
        {
            _logger = logger;
            _quoteService = quoteService;
        }

        [Function("QuoteHandler")]
        public async Task<HttpResponseData> RunAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", "post", Route = "quote")] HttpRequestData req,
            FunctionContext executionContext)
        {
            _logger.LogInformation("QuoteHandler function processed a request.");

            try
            {
                if (req.Method.Equals("GET", StringComparison.OrdinalIgnoreCase))
                {
                    var quote = await _quoteService.GetRandomQuoteAsync();
                    var response = req.CreateResponse(HttpStatusCode.OK);
                    await response.WriteAsJsonAsync(quote);
                    return response;
                }
                else if (req.Method.Equals("POST", StringComparison.OrdinalIgnoreCase))
                {
                    var requestBody = await req.ReadAsStringAsync();
                    if (!string.IsNullOrEmpty(requestBody))
                    {
                        var excludeIds = System.Text.Json.JsonSerializer.Deserialize<List<int>>(requestBody);
                        var quote = await _quoteService.GetRandomQuoteAsync(excludeIds);
                        var response = req.CreateResponse(HttpStatusCode.OK);
                        await response.WriteAsJsonAsync(quote);
                        return response;
                    }
                    else
                    {
                        var quote = await _quoteService.GetRandomQuoteAsync();
                        var response = req.CreateResponse(HttpStatusCode.OK);
                        await response.WriteAsJsonAsync(quote);
                        return response;
                    }
                }
                else
                {
                    var response = req.CreateResponse(HttpStatusCode.MethodNotAllowed);
                    return response;
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error processing quote request");
                var response = req.CreateResponse(HttpStatusCode.InternalServerError);
                return response;
            }
        }

        [Function("LikeQuote")]
        public async Task<HttpResponseData> LikeQuoteAsync(
            [HttpTrigger(AuthorizationLevel.Function, "post", Route = "quote/{id}/like")] HttpRequestData req,
            int id)
        {
            _logger.LogInformation($"LikeQuote function processed a request for quote {id}.");

            try
            {
                // Extract user ID from headers (mock for local testing)
                var userId = req.Headers.Contains("Authorization") ? "test-user" : "anonymous";
                
                await _quoteService.LikeQuoteAsync(userId, id);
                
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteStringAsync("Quote liked successfully");
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, $"Error liking quote {id}");
                var response = req.CreateResponse(HttpStatusCode.InternalServerError);
                return response;
            }
        }

        [Function("GetLikedQuotes")]
        public async Task<HttpResponseData> GetLikedQuotesAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", Route = "quote/liked")] HttpRequestData req)
        {
            _logger.LogInformation("GetLikedQuotes function processed a request.");

            try
            {
                var userId = req.Headers.Contains("Authorization") ? "test-user" : "anonymous";
                var likedQuotes = await _quoteService.GetLikedQuotesAsync(userId);
                
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(likedQuotes);
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting liked quotes");
                var response = req.CreateResponse(HttpStatusCode.InternalServerError);
                return response;
            }
        }
    }
}
