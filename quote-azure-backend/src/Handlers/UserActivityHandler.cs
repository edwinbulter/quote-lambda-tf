using Microsoft.Azure.Functions.Worker;
using Microsoft.Azure.Functions.Worker.Http;
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Services;
using System.Net;

namespace QuoteAzureBackend.Handlers
{
    public class UserActivityHandler
    {
        private readonly ILogger<UserActivityHandler> _logger;
        private readonly IUserActivityService _userActivityService;

        public UserActivityHandler(ILogger<UserActivityHandler> logger, IUserActivityService userActivityService)
        {
            _logger = logger;
            _userActivityService = userActivityService;
        }

        [Function("user/favorites")]
        public async Task<HttpResponseData> GetUserFavoritesAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", "post", "delete", Route = "user/favorites")] HttpRequestData req,
            FunctionContext executionContext)
        {
            var userId = GetUserFromRequest(req);
            if (string.IsNullOrEmpty(userId))
            {
                return req.CreateResponse(HttpStatusCode.Unauthorized);
            }

            try
            {
                if (req.Method.Equals("GET", StringComparison.OrdinalIgnoreCase))
                {
                    var favorites = await _userActivityService.GetUserFavoritesAsync(userId);
                    var response = req.CreateResponse(HttpStatusCode.OK);
                    await response.WriteAsJsonAsync(favorites);
                    return response;
                }
                else if (req.Method.Equals("POST", StringComparison.OrdinalIgnoreCase))
                {
                    var requestBody = await new StreamReader(req.Body).ReadToEndAsync();
                    var quoteId = int.Parse(requestBody);
                    
                    var success = await _userActivityService.AddFavoriteAsync(userId, quoteId);
                    return success ? req.CreateResponse(HttpStatusCode.OK) : req.CreateResponse(HttpStatusCode.BadRequest);
                }
                else if (req.Method.Equals("DELETE", StringComparison.OrdinalIgnoreCase))
                {
                    var requestBody = await new StreamReader(req.Body).ReadToEndAsync();
                    var quoteId = int.Parse(requestBody);
                    
                    var success = await _userActivityService.RemoveFavoriteAsync(userId, quoteId);
                    return success ? req.CreateResponse(HttpStatusCode.OK) : req.CreateResponse(HttpStatusCode.BadRequest);
                }

                return req.CreateResponse(HttpStatusCode.MethodNotAllowed);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error handling user favorites request");
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        [Function("user/history")]
        public async Task<HttpResponseData> GetUserViewHistoryAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", Route = "user/history")] HttpRequestData req,
            FunctionContext executionContext)
        {
            var userId = GetUserFromRequest(req);
            if (string.IsNullOrEmpty(userId))
            {
                return req.CreateResponse(HttpStatusCode.Unauthorized);
            }

            try
            {
                var queryParams = System.Web.HttpUtility.ParseQueryString(req.Url.Query);
                var limit = int.TryParse(queryParams["limit"], out var l) ? l : 50;
                
                var history = await _userActivityService.GetUserViewHistoryAsync(userId, limit);
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(history);
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting user view history");
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        [Function("user/preferences")]
        public async Task<HttpResponseData> GetUserPreferencesAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", "put", Route = "user/preferences")] HttpRequestData req,
            FunctionContext executionContext)
        {
            var userId = GetUserFromRequest(req);
            if (string.IsNullOrEmpty(userId))
            {
                return req.CreateResponse(HttpStatusCode.Unauthorized);
            }

            try
            {
                if (req.Method.Equals("GET", StringComparison.OrdinalIgnoreCase))
                {
                    var preferences = await _userActivityService.GetUserPreferencesAsync(userId);
                    var response = req.CreateResponse(HttpStatusCode.OK);
                    await response.WriteAsJsonAsync(preferences);
                    return response;
                }
                else if (req.Method.Equals("PUT", StringComparison.OrdinalIgnoreCase))
                {
                    var preferences = await req.ReadFromJsonAsync<UserPreferences>();
                    if (preferences == null)
                    {
                        return req.CreateResponse(HttpStatusCode.BadRequest);
                    }
                    
                    var success = await _userActivityService.UpdateUserPreferencesAsync(userId, preferences);
                    return success ? req.CreateResponse(HttpStatusCode.OK) : req.CreateResponse(HttpStatusCode.BadRequest);
                }

                return req.CreateResponse(HttpStatusCode.MethodNotAllowed);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error handling user preferences request");
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        private string GetUserFromRequest(HttpRequestData req)
        {
            // Extract user ID from JWT token or headers
            // This is a simplified version - implement proper JWT validation
            if (req.Headers.TryGetValues("X-User-Id", out var userIdValues))
            {
                return userIdValues.FirstOrDefault() ?? string.Empty;
            }
            return string.Empty;
        }
    }
}
