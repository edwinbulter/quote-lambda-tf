using Microsoft.Azure.Functions.Worker;
using Microsoft.Azure.Functions.Worker.Http;
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models.Admin;
using QuoteAzureBackend.Services;
using QuoteAzureBackend.Data;
using System.Net;

namespace QuoteAzureBackend.Handlers
{
    public class AdminHandler
    {
        private readonly IAdminService _adminService;
        private readonly IAuthenticationService _authService;
        private readonly ILogger<AdminHandler> _logger;

        public AdminHandler(
            IAdminService adminService,
            IAuthenticationService authService,
            ILogger<AdminHandler> logger)
        {
            _adminService = adminService;
            _authService = authService;
            _logger = logger;
        }

        private async Task<bool> IsCurrentUserAdmin(HttpRequestData req)
        {
            var objectId = req.Headers.TryGetValues("X-User-ObjectId", out var values) 
                ? values.FirstOrDefault() 
                : null;
            
            if (string.IsNullOrEmpty(objectId))
            {
                return false;
            }

            return await _authService.IsAdminAsync(objectId);
        }

        [Function("AdminListUsers")]
        public async Task<HttpResponseData> AdminListUsersAsync(
            [HttpTrigger(AuthorizationLevel.Anonymous, "get", Route = "admin/users")] HttpRequestData req)
        {
            try
            {
                if (!await IsCurrentUserAdmin(req))
                {
                    var forbiddenResponse = req.CreateResponse(HttpStatusCode.Forbidden);
                    await forbiddenResponse.WriteStringAsync("Admin access required");
                    return forbiddenResponse;
                }

                var users = await _adminService.ListAllUsersAsync();
                
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(users);
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error listing users");
                var errorResponse = req.CreateResponse(HttpStatusCode.InternalServerError);
                await errorResponse.WriteStringAsync("Internal server error");
                return errorResponse;
            }
        }

        [Function("AdminGetQuotes")]
        public async Task<HttpResponseData> AdminGetQuotesAsync(
            [HttpTrigger(AuthorizationLevel.Anonymous, "get", Route = "admin/quotes")] HttpRequestData req)
        {
            try
            {
                if (!await IsCurrentUserAdmin(req))
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
            [HttpTrigger(AuthorizationLevel.Anonymous, "post", Route = "admin/quotes/fetch")] HttpRequestData req)
        {
            try
            {
                if (!await IsCurrentUserAdmin(req))
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
            [HttpTrigger(AuthorizationLevel.Anonymous, "get", Route = "admin/stats")] HttpRequestData req)
        {
            try
            {
                if (!await IsCurrentUserAdmin(req))
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
    }
}
