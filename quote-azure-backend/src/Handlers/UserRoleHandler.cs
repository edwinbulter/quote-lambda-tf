using Microsoft.Azure.Functions.Worker;
using Microsoft.Azure.Functions.Worker.Http;
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Data;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Services;
using QuoteAzureBackend.Middleware;
using System.Net;
using System.Text.Json;

namespace QuoteAzureBackend.Handlers
{
    public class UserRoleHandler
    {
        private readonly IUserRoleRepository _userRoleRepository;
        private readonly IAuthenticationService _authService;
        private readonly JwtAuthenticationMiddleware _authMiddleware;
        private readonly IUserService _userService;
        private readonly AdminUserSeeder _adminUserSeeder;
        private readonly ILogger<UserRoleHandler> _logger;

        public UserRoleHandler(
            IUserRoleRepository userRoleRepository,
            IAuthenticationService authService,
            JwtAuthenticationMiddleware authMiddleware,
            IUserService userService,
            AdminUserSeeder adminUserSeeder,
            ILogger<UserRoleHandler> logger)
        {
            _userRoleRepository = userRoleRepository;
            _authService = authService;
            _authMiddleware = authMiddleware;
            _userService = userService;
            _adminUserSeeder = adminUserSeeder;
            _logger = logger;
        }

        private async Task<bool> IsCurrentUserAdmin(HttpRequestData req)
        {
            var user = await _authMiddleware.GetUserFromRequestAsync(req);
            
            if (user == null)
            {
                return false;
            }

            return await _userService.IsAdminAsync(user.Id);
        }

        [Function("GetAllUsers")]
        public async Task<HttpResponseData> GetAllUsers(
            [HttpTrigger(AuthorizationLevel.Anonymous, "get", Route = "admin/userrole")] HttpRequestData req)
        {
            if (!await IsCurrentUserAdmin(req))
            {
                var forbiddenResponse = req.CreateResponse(HttpStatusCode.Forbidden);
                await forbiddenResponse.WriteStringAsync("Admin access required");
                return forbiddenResponse;
            }

            try
            {
                var users = await _userRoleRepository.GetAllUsersAsync();
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(users);
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting all users");
                var errorResponse = req.CreateResponse(HttpStatusCode.InternalServerError);
                await errorResponse.WriteStringAsync("Internal server error");
                return errorResponse;
            }
        }

        [Function("AssignRole")]
        public async Task<HttpResponseData> AssignRole(
            [HttpTrigger(AuthorizationLevel.Anonymous, "post", Route = "admin/userrole/{objectId}/role")] HttpRequestData req,
            string objectId)
        {
            if (!await IsCurrentUserAdmin(req))
            {
                var forbiddenResponse = req.CreateResponse(HttpStatusCode.Forbidden);
                await forbiddenResponse.WriteStringAsync("Admin access required");
                return forbiddenResponse;
            }

            try
            {
                var requestBody = await req.ReadAsStringAsync();
                var request = JsonSerializer.Deserialize<AssignRoleRequest>(requestBody ?? "{}");

                if (request == null || string.IsNullOrWhiteSpace(request.Role) || 
                    !request.Role.Equals("USER", StringComparison.OrdinalIgnoreCase) && 
                    !request.Role.Equals("ADMIN", StringComparison.OrdinalIgnoreCase))
                {
                    var badRequestResponse = req.CreateResponse(HttpStatusCode.BadRequest);
                    await badRequestResponse.WriteStringAsync("Role must be 'USER' or 'ADMIN'");
                    return badRequestResponse;
                }

                var currentUserId = req.Headers.TryGetValues("X-User-ObjectId", out var values) 
                    ? values.FirstOrDefault() ?? "system"
                    : "system";

                var success = await _userRoleRepository.AssignRoleAsync(
                    objectId, 
                    request.Email ?? objectId, 
                    request.Role, 
                    currentUserId);

                if (success)
                {
                    var response = req.CreateResponse(HttpStatusCode.OK);
                    await response.WriteAsJsonAsync(new { message = $"Role {request.Role} assigned successfully" });
                    return response;
                }

                var failResponse = req.CreateResponse(HttpStatusCode.BadRequest);
                await failResponse.WriteStringAsync("Failed to assign role");
                return failResponse;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error assigning role {Role} to user {ObjectId}", "unknown", objectId);
                var errorResponse = req.CreateResponse(HttpStatusCode.InternalServerError);
                await errorResponse.WriteStringAsync("Internal server error");
                return errorResponse;
            }
        }

        [Function("RemoveRole")]
        public async Task<HttpResponseData> RemoveRole(
            [HttpTrigger(AuthorizationLevel.Anonymous, "delete", Route = "admin/userrole/{objectId}/role")] HttpRequestData req,
            string objectId)
        {
            if (!await IsCurrentUserAdmin(req))
            {
                var forbiddenResponse = req.CreateResponse(HttpStatusCode.Forbidden);
                await forbiddenResponse.WriteStringAsync("Admin access required");
                return forbiddenResponse;
            }

            try
            {
                var success = await _userRoleRepository.RemoveRoleAsync(objectId);
                if (success)
                {
                    var response = req.CreateResponse(HttpStatusCode.OK);
                    await response.WriteAsJsonAsync(new { message = "Role removed successfully" });
                    return response;
                }

                var failResponse = req.CreateResponse(HttpStatusCode.BadRequest);
                await failResponse.WriteStringAsync("Failed to remove role");
                return failResponse;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error removing role for user {ObjectId}", objectId);
                var errorResponse = req.CreateResponse(HttpStatusCode.InternalServerError);
                await errorResponse.WriteStringAsync("Internal server error");
                return errorResponse;
            }
        }

        [Function("GetUserRole")]
        public async Task<HttpResponseData> GetUserRole(
            [HttpTrigger(AuthorizationLevel.Anonymous, "get", Route = "admin/userrole/{objectId}/role")] HttpRequestData req,
            string objectId)
        {
            if (!await IsCurrentUserAdmin(req))
            {
                var forbiddenResponse = req.CreateResponse(HttpStatusCode.Forbidden);
                await forbiddenResponse.WriteStringAsync("Admin access required");
                return forbiddenResponse;
            }

            try
            {
                var userRole = await _userRoleRepository.GetUserRoleAsync(objectId);
                if (userRole == null)
                {
                    var notFoundResponse = req.CreateResponse(HttpStatusCode.NotFound);
                    await notFoundResponse.WriteStringAsync("User role not found");
                    return notFoundResponse;
                }

                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(userRole);
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting role for user {ObjectId}", objectId);
                var errorResponse = req.CreateResponse(HttpStatusCode.InternalServerError);
                await errorResponse.WriteStringAsync("Internal server error");
                return errorResponse;
            }
        }

        [Function("seed-users")]
        public async Task<HttpResponseData> SeedUsersAsync(
            [HttpTrigger(AuthorizationLevel.Function, "post", Route = "seed-users")] HttpRequestData req,
            FunctionContext executionContext)
        {
            try
            {
                _logger.LogInformation("Starting user seeding process");
                
                // Seed admin user
                await _adminUserSeeder.SeedAdminUserAsync();
                
                // Seed test user
                await _adminUserSeeder.SeedTestUserAsync();
                
                _logger.LogInformation("User seeding completed successfully");
                
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteStringAsync("Users seeded successfully");
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error seeding users");
                var errorResponse = req.CreateResponse(HttpStatusCode.InternalServerError);
                await errorResponse.WriteStringAsync("Error seeding users");
                return errorResponse;
            }
        }
    }

    public class AssignRoleRequest
    {
        public string Role { get; set; } = string.Empty;
        public string Email { get; set; } = string.Empty;
    }
}
