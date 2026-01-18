using Microsoft.Azure.Functions.Worker;
using Microsoft.Azure.Functions.Worker.Http;
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models.Auth;
using QuoteAzureBackend.Services;
using QuoteAzureBackend.Middleware;
using System.Net;
using System.Text.Json;

namespace QuoteAzureBackend.Handlers
{
    public class UserManagementHandler
    {
        private readonly IUserService _userService;
        private readonly JwtAuthenticationMiddleware _authMiddleware;
        private readonly ILogger<UserManagementHandler> _logger;
        private readonly AdminUserSeeder _adminUserSeeder;

        public UserManagementHandler(IUserService userService, JwtAuthenticationMiddleware authMiddleware, ILogger<UserManagementHandler> logger, AdminUserSeeder adminUserSeeder)
        {
            _userService = userService;
            _authMiddleware = authMiddleware;
            _logger = logger;
            _adminUserSeeder = adminUserSeeder;
        }

        
        [Function("UpdateUserRole")]
        public async Task<HttpResponseData> UpdateUserRole(
            [HttpTrigger(AuthorizationLevel.Function, "put", Route = "manage/users/role")] HttpRequestData req,
            FunctionContext context)
        {
            _logger.LogInformation("Processing update user role request");

            try
            {
                // Authenticate and authorize user
                var user = await _authMiddleware.GetUserFromRequestAsync(req);
                if (user == null)
                {
                    return _authMiddleware.CreateUnauthorizedResponse(req);
                }

                // Check if user is admin
                if (!await _userService.IsAdminAsync(user.Id))
                {
                    return _authMiddleware.CreateForbiddenResponse(req);
                }

                var requestBody = await new StreamReader(req.Body).ReadToEndAsync();
                var updateRoleRequest = JsonSerializer.Deserialize<UpdateRoleRequest>(requestBody, new JsonSerializerOptions
                {
                    PropertyNameCaseInsensitive = true
                });

                if (updateRoleRequest == null)
                {
                    return CreateBadRequestResponse(req, "Invalid request body");
                }

                // Validate request
                if (string.IsNullOrEmpty(updateRoleRequest.UserId))
                {
                    return CreateBadRequestResponse(req, "User ID is required");
                }

                if (string.IsNullOrEmpty(updateRoleRequest.NewRole) || 
                    !IsValidRole(updateRoleRequest.NewRole))
                {
                    return CreateBadRequestResponse(req, "Invalid role. Valid roles are: User, Admin");
                }

                // Update user role
                var result = await _userService.UpdateUserRoleAsync(user.Id, updateRoleRequest);

                if (result)
                {
                    var response = req.CreateResponse(HttpStatusCode.OK);
                    response.Headers.Add("Content-Type", "application/json");
                    await response.WriteStringAsync(JsonSerializer.Serialize(new
                    {
                        message = "User role updated successfully",
                        userId = updateRoleRequest.UserId,
                        newRole = updateRoleRequest.NewRole
                    }));
                    return response;
                }
                else
                {
                    return CreateErrorResponse(req, "Failed to update user role");
                }
            }
            catch (UnauthorizedAccessException ex)
            {
                _logger.LogWarning(ex, "Access denied to update user role");
                return _authMiddleware.CreateForbiddenResponse(req);
            }
            catch (InvalidOperationException ex)
            {
                _logger.LogWarning(ex, "Update user role failed: {Message}", ex.Message);
                return CreateBadRequestResponse(req, ex.Message);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error updating user role");
                return CreateErrorResponse(req, "An error occurred while updating user role");
            }
        }

        [Function("RemoveUserRole")]
        public async Task<HttpResponseData> RemoveUserRole(
            [HttpTrigger(AuthorizationLevel.Function, "delete", Route = "manage/users/role")] HttpRequestData req,
            FunctionContext context)
        {
            _logger.LogInformation("Processing remove user role request");

            try
            {
                // Authenticate and authorize user
                var user = await _authMiddleware.GetUserFromRequestAsync(req);
                if (user == null)
                {
                    return _authMiddleware.CreateUnauthorizedResponse(req);
                }

                // Check if user is admin
                if (!await _userService.IsAdminAsync(user.Id))
                {
                    return _authMiddleware.CreateForbiddenResponse(req);
                }

                var requestBody = await new StreamReader(req.Body).ReadToEndAsync();
                var updateRoleRequest = JsonSerializer.Deserialize<UpdateRoleRequest>(requestBody, new JsonSerializerOptions
                {
                    PropertyNameCaseInsensitive = true
                });

                if (updateRoleRequest == null)
                {
                    return CreateBadRequestResponse(req, "Invalid request body");
                }

                // Validate request
                if (string.IsNullOrEmpty(updateRoleRequest.UserId))
                {
                    return CreateBadRequestResponse(req, "User ID is required");
                }

                if (string.IsNullOrEmpty(updateRoleRequest.NewRole) || 
                    !IsValidRole(updateRoleRequest.NewRole))
                {
                    return CreateBadRequestResponse(req, "Invalid role. Valid roles are: User, Admin");
                }

                // Remove user role
                var result = await _userService.RemoveUserRoleAsync(user.Id, updateRoleRequest);

                if (result)
                {
                    var response = req.CreateResponse(HttpStatusCode.OK);
                    response.Headers.Add("Content-Type", "application/json");
                    await response.WriteStringAsync(JsonSerializer.Serialize(new
                    {
                        message = "User role removed successfully",
                        userId = updateRoleRequest.UserId,
                        removedRole = updateRoleRequest.NewRole
                    }));
                    return response;
                }
                else
                {
                    return CreateErrorResponse(req, "Failed to remove user role");
                }
            }
            catch (UnauthorizedAccessException ex)
            {
                _logger.LogWarning(ex, "Access denied to remove user role");
                return _authMiddleware.CreateForbiddenResponse(req);
            }
            catch (InvalidOperationException ex)
            {
                _logger.LogWarning(ex, "Remove user role failed: {Message}", ex.Message);
                return CreateBadRequestResponse(req, ex.Message);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error removing user role");
                return CreateErrorResponse(req, "An error occurred while removing user role");
            }
        }

        [Function("GetUserById")]
        public async Task<HttpResponseData> GetUserById(
            [HttpTrigger(AuthorizationLevel.Function, "get", Route = "admin/users/{userId}")] HttpRequestData req,
            string userId,
            FunctionContext context)
        {
            _logger.LogInformation("Processing get user by ID request");

            try
            {
                // Authenticate and authorize user
                var user = await _authMiddleware.GetUserFromRequestAsync(req);
                if (user == null)
                {
                    return _authMiddleware.CreateUnauthorizedResponse(req);
                }

                // Check if user is admin or requesting their own info
                if (!await _userService.IsAdminAsync(user.Id) && user.Id != userId)
                {
                    return _authMiddleware.CreateForbiddenResponse(req);
                }

                // Get user by ID
                var targetUser = await _userService.GetUserByIdAsync(userId);
                
                if (targetUser == null)
                {
                    return CreateNotFoundResponse(req, "User not found");
                }

                var response = req.CreateResponse(HttpStatusCode.OK);
                response.Headers.Add("Content-Type", "application/json");
                
                var userResponse = new
                {
                    id = targetUser.Id,
                    email = targetUser.Email,
                    username = targetUser.Username,
                    isActive = targetUser.IsActive,
                    createdAt = targetUser.CreatedAt,
                    updatedAt = targetUser.UpdatedAt
                };

                await response.WriteStringAsync(JsonSerializer.Serialize(userResponse));
                return response;
            }
            catch (UnauthorizedAccessException ex)
            {
                _logger.LogWarning(ex, "Access denied to get user by ID");
                return _authMiddleware.CreateForbiddenResponse(req);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error retrieving user by ID");
                return CreateErrorResponse(req, "An error occurred while retrieving user");
            }
        }

        [Function("GetAllUsers")]
        public async Task<HttpResponseData> GetAllUsers(
            [HttpTrigger(AuthorizationLevel.Function, "get", Route = "manage/users")] HttpRequestData req)
        {
            _logger.LogInformation("Processing get all users request");

            try
            {
                // Authenticate and authorize user
                var user = await _authMiddleware.GetUserFromRequestAsync(req);
                if (user == null)
                {
                    return _authMiddleware.CreateUnauthorizedResponse(req);
                }

                // Check if user is admin
                if (!await _userService.IsAdminAsync(user.Id))
                {
                    return _authMiddleware.CreateForbiddenResponse(req);
                }

                var users = await _userService.GetAllUsersAsync(user.Id);
                
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(users);
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error listing users");
                return CreateErrorResponse(req, "Internal server error");
            }
        }

        private static bool IsValidRole(string role)
        {
            return role.Equals("User", StringComparison.OrdinalIgnoreCase) ||
                   role.Equals("Admin", StringComparison.OrdinalIgnoreCase);
        }

        private static HttpResponseData CreateBadRequestResponse(HttpRequestData req, string message)
        {
            var response = req.CreateResponse(HttpStatusCode.BadRequest);
            response.Headers.Add("Content-Type", "application/json");
            response.WriteStringAsync(JsonSerializer.Serialize(new { error = message })).Wait();
            return response;
        }

        private static HttpResponseData CreateNotFoundResponse(HttpRequestData req, string message)
        {
            var response = req.CreateResponse(HttpStatusCode.NotFound);
            response.Headers.Add("Content-Type", "application/json");
            response.WriteStringAsync(JsonSerializer.Serialize(new { error = message })).Wait();
            return response;
        }

        private static HttpResponseData CreateErrorResponse(HttpRequestData req, string message)
        {
            var response = req.CreateResponse(HttpStatusCode.InternalServerError);
            response.Headers.Add("Content-Type", "application/json");
            response.WriteStringAsync(JsonSerializer.Serialize(new { error = message })).Wait();
            return response;
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
}
