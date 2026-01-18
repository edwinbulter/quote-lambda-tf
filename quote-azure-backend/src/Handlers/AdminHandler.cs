using Microsoft.Azure.Functions.Worker;
using Microsoft.Azure.Functions.Worker.Http;
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Services;
using QuoteAzureBackend.Data;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Middleware;
using System.Net;
using System.Text.Json;

namespace QuoteAzureBackend.Handlers
{
    public class AdminHandler
    {
        private readonly IAdminService _adminService;
        private readonly IUserService _userService;
        private readonly JwtAuthenticationMiddleware _authMiddleware;
        private readonly ILogger<AdminHandler> _logger;

        public AdminHandler(IAdminService adminService, IUserService userService, JwtAuthenticationMiddleware authMiddleware, ILogger<AdminHandler> logger)
        {
            _adminService = adminService;
            _userService = userService;
            _authMiddleware = authMiddleware;
            _logger = logger;
        }

        private async Task<bool> IsCurrentUserAdminViaMiddleware(HttpRequestData req)
        {
            var user = await _authMiddleware.GetUserFromRequestAsync(req);
            
            if (user == null)
            {
                return false;
            }

            return await _userService.IsAdminAsync(user.Id);
        }

        
        [Function("AdminGetQuotes")]
        public async Task<HttpResponseData> AdminGetQuotesAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", Route = "manage/quotes")] HttpRequestData req)
        {
            try
            {
                if (!await IsCurrentUserAdminViaMiddleware(req))
                {
                    var forbiddenResponse = req.CreateResponse(HttpStatusCode.Forbidden);
                    await forbiddenResponse.WriteStringAsync("Admin access required");
                    return forbiddenResponse;
                }

                // Parse query parameters
                var query = System.Web.HttpUtility.ParseQueryString(req.Url.Query);
                var page = int.TryParse(query["page"] ?? "1", out var p) ? p : 1;
                var pageSize = int.TryParse(query["pageSize"] ?? "10", out var ps) ? ps : 10;
                var quoteText = query["quoteText"];
                var author = query["author"];
                var sortBy = query["sortBy"];
                var sortOrder = query["sortOrder"];

                var quotes = await _adminService.GetQuotesAsync(page, pageSize, quoteText, author, sortBy, sortOrder);
                
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(quotes);
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting quotes");
                var errorResponse = req.CreateResponse(HttpStatusCode.InternalServerError);
                await errorResponse.WriteStringAsync("Internal server error");
                return errorResponse;
            }
        }

        [Function("AdminAddQuotes")]
        public async Task<HttpResponseData> AdminAddQuotesAsync(
            [HttpTrigger(AuthorizationLevel.Function, "post", Route = "manage/quotes/fetch")] HttpRequestData req)
        {
            try
            {
                if (!await IsCurrentUserAdminViaMiddleware(req))
                {
                    var forbiddenResponse = req.CreateResponse(HttpStatusCode.Forbidden);
                    await forbiddenResponse.WriteStringAsync("Admin access required");
                    return forbiddenResponse;
                }

                var currentUserId = req.Headers.TryGetValues("X-User-ObjectId", out var values) 
                    ? values.FirstOrDefault() ?? "system"
                    : "system";

                var result = await _adminService.FetchAndAddNewQuotesAsync(currentUserId);
                
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(result);
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error adding quotes");
                var errorResponse = req.CreateResponse(HttpStatusCode.InternalServerError);
                await errorResponse.WriteStringAsync("Internal server error");
                return errorResponse;
            }
        }

        [Function("AdminGetStats")]
        public async Task<HttpResponseData> AdminGetStatsAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", Route = "manage/stats")] HttpRequestData req)
        {
            try
            {
                if (!await IsCurrentUserAdminViaMiddleware(req))
                {
                    var forbiddenResponse = req.CreateResponse(HttpStatusCode.Forbidden);
                    await forbiddenResponse.WriteStringAsync("Admin access required");
                    return forbiddenResponse;
                }

                var totalLikes = await _adminService.GetTotalLikesAsync();
                
                var stats = new
                {
                    TotalLikes = totalLikes,
                    Timestamp = DateTime.UtcNow
                };
                
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(stats);
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting stats");
                var errorResponse = req.CreateResponse(HttpStatusCode.InternalServerError);
                await errorResponse.WriteStringAsync("Internal server error");
                return errorResponse;
            }
        }

        [Function("AdminDeleteQuote")]
        public async Task<HttpResponseData> AdminDeleteQuoteAsync(
            [HttpTrigger(AuthorizationLevel.Function, "delete", Route = "manage/quotes/{id}")] HttpRequestData req,
            int id)
        {
            try
            {
                if (!await IsCurrentUserAdminViaMiddleware(req))
                {
                    var forbiddenResponse = req.CreateResponse(HttpStatusCode.Forbidden);
                    await forbiddenResponse.WriteStringAsync("Admin access required");
                    return forbiddenResponse;
                }

                var currentUserId = req.Headers.TryGetValues("X-User-ObjectId", out var values) 
                    ? values.FirstOrDefault() ?? "system"
                    : "system";

                var success = await _adminService.DeleteQuoteAsync(id, currentUserId);
                
                if (success)
                {
                    var response = req.CreateResponse(HttpStatusCode.OK);
                    await response.WriteAsJsonAsync(new { message = "Quote deleted successfully" });
                    return response;
                }

                var notFoundResponse = req.CreateResponse(HttpStatusCode.NotFound);
                await notFoundResponse.WriteStringAsync("Quote not found");
                return notFoundResponse;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error deleting quote");
                var errorResponse = req.CreateResponse(HttpStatusCode.InternalServerError);
                await errorResponse.WriteStringAsync("Internal server error");
                return errorResponse;
            }
        }

        [Function("AdminUpdateQuote")]
        public async Task<HttpResponseData> AdminUpdateQuoteAsync(
            [HttpTrigger(AuthorizationLevel.Function, "put", Route = "manage/quotes/{id}")] HttpRequestData req,
            int id)
        {
            try
            {
                if (!await IsCurrentUserAdminViaMiddleware(req))
                {
                    var forbiddenResponse = req.CreateResponse(HttpStatusCode.Forbidden);
                    await forbiddenResponse.WriteStringAsync("Admin access required");
                    return forbiddenResponse;
                }

                var requestBody = await req.ReadAsStringAsync();
                var quoteUpdate = JsonSerializer.Deserialize<Quote>(requestBody ?? "{}");
                
                if (quoteUpdate == null)
                {
                    var badRequestResponse = req.CreateResponse(HttpStatusCode.BadRequest);
                    await badRequestResponse.WriteStringAsync("Invalid quote data");
                    return badRequestResponse;
                }

                var currentUserId = req.Headers.TryGetValues("X-User-ObjectId", out var values) 
                    ? values.FirstOrDefault() ?? "system"
                    : "system";

                var updatedQuote = await _adminService.UpdateQuoteAsync(id, quoteUpdate, currentUserId);
                
                if (updatedQuote != null)
                {
                    var response = req.CreateResponse(HttpStatusCode.OK);
                    await response.WriteAsJsonAsync(updatedQuote);
                    return response;
                }

                var notFoundResponse = req.CreateResponse(HttpStatusCode.NotFound);
                await notFoundResponse.WriteStringAsync("Quote not found");
                return notFoundResponse;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error updating quote");
                var errorResponse = req.CreateResponse(HttpStatusCode.InternalServerError);
                await errorResponse.WriteStringAsync("Internal server error");
                return errorResponse;
            }
        }
    }
}
