using Microsoft.Azure.Functions.Worker;
using Microsoft.Azure.Functions.Worker.Http;
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Models.Auth;
using QuoteAzureBackend.Services;
using QuoteAzureBackend.Middleware;
using System.Net;

namespace QuoteAzureBackend.Handlers
{
    public class QuoteHandler(
        ILogger<QuoteHandler> logger,
        IQuoteService quoteService,
        JwtAuthenticationMiddleware authMiddleware)
    {
        [Function("GetRandomQuote")]
        public async Task<HttpResponseData> GetRandomQuoteAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", Route = "quotes/random")] HttpRequestData req,
            FunctionContext executionContext)
        {
            try
            {
                logger.LogInformation("Getting random quote");

                try
                {
                    var userId = await GetUserFromRequestAsync(req);
                    var quote = await quoteService.GetQuoteAsync(userId, new HashSet<int>());
                    
                    if (quote == null)
                    {
                        logger.LogWarning("No quotes available in database");
                        return req.CreateResponse(HttpStatusCode.NotFound);
                    }
                    
                    // Record view if user is authenticated
                    if (!string.IsNullOrEmpty(userId))
                    {
                        await quoteService.RecordViewAsync(userId, quote.Id);
                    }

                    var response = req.CreateResponse(HttpStatusCode.OK);
                    await response.WriteAsJsonAsync(quote);
                    return response;
                }
                catch (Exception ex)
                {
                    logger.LogError(ex, "Error getting random quote");
                    return req.CreateResponse(HttpStatusCode.InternalServerError);
                }
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Critical error in GetRandomQuote function");
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        [Function("GetViewedQuotes")]
        public async Task<HttpResponseData> GetViewedQuotesAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", Route = "quote/viewed")] HttpRequestData req,
            FunctionContext executionContext)
        {
            try
            {
                var userId = await GetUserFromRequestAsync(req);
                if (string.IsNullOrEmpty(userId))
                {
                    var unauthorizedResponse = req.CreateResponse(HttpStatusCode.Unauthorized);
                    await unauthorizedResponse.WriteStringAsync("Authentication required");
                    return unauthorizedResponse;
                }

                logger.LogInformation("Getting viewed quotes for user {UserId}", userId);

                var viewedQuotes = await quoteService.GetViewedQuotesAsync(userId);
                
                var successResponse = req.CreateResponse(HttpStatusCode.OK);
                await successResponse.WriteAsJsonAsync(viewedQuotes);
                return successResponse;
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Error getting viewed quotes");
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        [Function("GetQuoteById")]
        public async Task<HttpResponseData> GetQuoteByIdAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", Route = "quote/{id}")] HttpRequestData req,
            FunctionContext executionContext, int id)
        {
            try
            {
                var userId = await GetUserFromRequestAsync(req);
                logger.LogInformation("Getting quote by ID: {Id} for user {UserId}", id, userId);

                var quote = await quoteService.GetQuoteByIdAsync(userId, id);
                if (quote == null)
                {
                    var response = req.CreateResponse(HttpStatusCode.NotFound);
                    await response.WriteStringAsync("Quote not found");
                    return response;
                }

                var successResponse = req.CreateResponse(HttpStatusCode.OK);
                await successResponse.WriteAsJsonAsync(quote);
                return successResponse;
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Error getting quote by ID: {Id}", id);
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        [Function("LikeQuote")]
        public async Task<HttpResponseData> LikeQuoteAsync(
            [HttpTrigger(AuthorizationLevel.Function, "post", Route = "quote/{id}/like")] HttpRequestData req,
            FunctionContext executionContext, int id)
        {
            try
            {
                var userId = await GetUserFromRequestAsync(req);
                if (string.IsNullOrEmpty(userId))
                {
                    var response = req.CreateResponse(HttpStatusCode.Unauthorized);
                    await response.WriteStringAsync("Authentication required");
                    return response;
                }

                logger.LogInformation("User {UserId} liking quote {QuoteId}", userId, id);

                var quote = await quoteService.LikeQuoteAsync(userId, id);
                if (quote == null)
                {
                    var response = req.CreateResponse(HttpStatusCode.NotFound);
                    await response.WriteStringAsync("Quote not found");
                    return response;
                }

                var successResponse = req.CreateResponse(HttpStatusCode.OK);
                await successResponse.WriteAsJsonAsync(quote);
                return successResponse;
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Error liking quote {QuoteId}", id);
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        [Function("UnlikeQuote")]
        public async Task<HttpResponseData> UnlikeQuoteAsync(
            [HttpTrigger(AuthorizationLevel.Function, "delete", Route = "quote/{id}/unlike")] HttpRequestData req,
            FunctionContext executionContext, int id)
        {
            try
            {
                var userId = await GetUserFromRequestAsync(req);
                if (string.IsNullOrEmpty(userId))
                {
                    var response = req.CreateResponse(HttpStatusCode.Unauthorized);
                    await response.WriteStringAsync("Authentication required");
                    return response;
                }

                logger.LogInformation("User {UserId} unliking quote {QuoteId}", userId, id);

                await quoteService.UnlikeQuoteAsync(userId, id);
                
                return req.CreateResponse(HttpStatusCode.NoContent);;
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Error unliking quote {QuoteId}", id);
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        [Function("GetLikedQuotes")]
        public async Task<HttpResponseData> GetLikedQuotesAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", Route = "quote/liked")] HttpRequestData req,
            FunctionContext executionContext)
        {
            try
            {
                var userId = await GetUserFromRequestAsync(req);
                if (string.IsNullOrEmpty(userId))
                {
                    var response = req.CreateResponse(HttpStatusCode.Unauthorized);
                    await response.WriteStringAsync("Authentication required");
                    return response;
                }

                logger.LogInformation("Getting liked quotes for user {UserId}", userId);

                var quotes = await quoteService.GetLikedQuotesByUserAsync(userId);

                var successResponse = req.CreateResponse(HttpStatusCode.OK);
                await successResponse.WriteAsJsonAsync(quotes);
                return successResponse;
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Error getting liked quotes");
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        [Function("GetQuote")]
        public async Task<HttpResponseData> GetQuoteAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", Route = "quote")] HttpRequestData req,
            FunctionContext executionContext)
        {
            try
            {
                var userId = await GetUserFromRequestAsync(req);
                logger.LogInformation("Getting quote for user {UserId}", userId);

                var quote = await quoteService.GetQuoteAsync(userId, new HashSet<int>());
                
                if (quote == null)
                {
                    logger.LogWarning("No quotes available in database");
                    return req.CreateResponse(HttpStatusCode.NotFound);
                }

                // View is automatically tracked in GetNextSequentialQuoteAsync for authenticated users

                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(quote);
                return response;
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Error getting quote");
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        [Function("GetUniqueQuote")]
        public async Task<HttpResponseData> GetUniqueQuoteAsync(
            [HttpTrigger(AuthorizationLevel.Function, "post", Route = "quote")] HttpRequestData req,
            FunctionContext executionContext)
        {
            try
            {
                var userId = await GetUserFromRequestAsync(req);
                var requestBody = await req.ReadFromJsonAsync<int[]>();
                var idsToExclude = new HashSet<int>(requestBody ?? Array.Empty<int>());

                logger.LogInformation("Getting unique quote for user {UserId}, excluding {Count} IDs", userId, idsToExclude.Count);

                var quote = await quoteService.GetQuoteAsync(userId, idsToExclude);
                
                if (quote == null)
                {
                    logger.LogWarning("No quotes available in database");
                    return req.CreateResponse(HttpStatusCode.NotFound);
                }

                // Record view if user is authenticated
                if (!string.IsNullOrEmpty(userId))
                {
                    await quoteService.RecordViewAsync(userId, quote.Id);
                }

                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(quote);
                return response;
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Error getting unique quote");
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        private async Task<string> GetUserFromRequestAsync(HttpRequestData req)
        {
            try
            {
                var userInfo = await authMiddleware.AuthenticateAsync(req);
                return userInfo?.DisplayName ?? string.Empty; // DisplayName contains the username
            }
            catch
            {
                return string.Empty;
            }
        }
    }
}
