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

        public UserManagementHandler(IUserService userService, JwtAuthenticationMiddleware authMiddleware, ILogger<UserManagementHandler> logger)
        {
            _userService = userService;
            _authMiddleware = authMiddleware;
            _logger = logger;
        }

        [Function("GetAllUsers")]
        public async Task<HttpResponseData> GetAllUsers(
            [HttpTrigger(AuthorizationLevel.Anonymous, "get", Route = "admin/users")] HttpRequestData req,
            FunctionContext context)
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

                // Get all users
                var users = await _userService.GetAllUsersAsync(user.Id);

                var response = req.CreateResponse(HttpStatusCode.OK);
                response.Headers.Add("Content-Type", "application/json");
                
                var usersResponse = users.Select(u => new
                {
                    id = u.Id,
                    email = u.Email,
                    username = u.Username,
                    role = u.Role,
                    isActive = u.IsActive,
                    createdAt = u.CreatedAt,
                    updatedAt = u.UpdatedAt
                });

                await response.WriteStringAsync(JsonSerializer.Serialize(usersResponse));
                return response;
            }
            catch (UnauthorizedAccessException ex)
            {
                _logger.LogWarning(ex, "Access denied to get all users");
                return _authMiddleware.CreateForbiddenResponse(req);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error retrieving all users");
                return CreateErrorResponse(req, "An error occurred while retrieving users");
            }
        }

        [Function("UpdateUserRole")]
        public async Task<HttpResponseData> UpdateUserRole(
            [HttpTrigger(AuthorizationLevel.Anonymous, "put", Route = "admin/users/role")] HttpRequestData req,
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

        [Function("GetUserById")]
        public async Task<HttpResponseData> GetUserById(
            [HttpTrigger(AuthorizationLevel.Anonymous, "get", Route = "admin/users/{userId}")] HttpRequestData req,
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
                    role = targetUser.Role,
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
    }
}
