using Microsoft.Azure.Functions.Worker;
using Microsoft.Azure.Functions.Worker.Http;
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models.Auth;
using QuoteAzureBackend.Services;
using QuoteAzureBackend.Middleware;
using System.Net;
using System.Text.Json;
using System.ComponentModel.DataAnnotations;

namespace QuoteAzureBackend.Handlers
{
    public class AuthHandler
    {
        private readonly IUserService _userService;
        private readonly JwtAuthenticationMiddleware _authMiddleware;
        private readonly ILogger<AuthHandler> _logger;

        public AuthHandler(IUserService userService, JwtAuthenticationMiddleware authMiddleware, ILogger<AuthHandler> logger)
        {
            _userService = userService;
            _authMiddleware = authMiddleware;
            _logger = logger;
        }

        [Function("Register")]
        public async Task<HttpResponseData> Register(
            [HttpTrigger(AuthorizationLevel.Anonymous, "post", Route = "auth/register")] HttpRequestData req,
            FunctionContext context)
        {
            _logger.LogInformation("Processing user registration request");

            try
            {
                var requestBody = await new StreamReader(req.Body).ReadToEndAsync();
                var registerRequest = JsonSerializer.Deserialize<RegisterRequest>(requestBody, new JsonSerializerOptions
                {
                    PropertyNameCaseInsensitive = true
                });

                if (registerRequest == null)
                {
                    return CreateBadRequestResponse(req, "Invalid request body");
                }

                // Validate request
                if (!IsValid(registerRequest))
                {
                    return CreateBadRequestResponse(req, "Validation failed: " + string.Join(", ", GetValidationErrors(registerRequest)));
                }

                // Register user
                var user = await _userService.RegisterAsync(registerRequest);

                var response = req.CreateResponse(HttpStatusCode.Created);
                await response.WriteStringAsync(JsonSerializer.Serialize(new
                {
                    message = "User registered successfully",
                    userId = user.Id,
                    email = user.Email,
                    username = user.Username,
                    role = user.Role
                }));

                return response;
            }
            catch (InvalidOperationException ex)
            {
                _logger.LogWarning(ex, "Registration failed: {Message}", ex.Message);
                return CreateBadRequestResponse(req, ex.Message);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error during user registration");
                return CreateErrorResponse(req, "An error occurred during registration");
            }
        }

        [Function("Login")]
        public async Task<HttpResponseData> Login(
            [HttpTrigger(AuthorizationLevel.Anonymous, "post", Route = "auth/login")] HttpRequestData req,
            FunctionContext context)
        {
            _logger.LogInformation("Processing user login request");

            try
            {
                var requestBody = await new StreamReader(req.Body).ReadToEndAsync();
                var loginRequest = JsonSerializer.Deserialize<LoginRequest>(requestBody, new JsonSerializerOptions
                {
                    PropertyNameCaseInsensitive = true
                });

                if (loginRequest == null)
                {
                    return CreateBadRequestResponse(req, "Invalid request body");
                }

                // Validate request
                if (!IsValid(loginRequest))
                {
                    return CreateBadRequestResponse(req, "Validation failed: " + string.Join(", ", GetValidationErrors(loginRequest)));
                }

                // Login user
                var token = await _userService.LoginAsync(loginRequest);

                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteStringAsync(JsonSerializer.Serialize(new
                {
                    token = token,
                    tokenType = "Bearer",
                    expiresIn = 86400 // 24 hours in seconds
                }));

                return response;
            }
            catch (InvalidOperationException ex)
            {
                _logger.LogWarning(ex, "Login failed: {Message}", ex.Message);
                return CreateUnauthorizedResponse(req, ex.Message);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error during user login");
                return CreateErrorResponse(req, "An error occurred during login");
            }
        }

        [Function("ChangePassword")]
        public async Task<HttpResponseData> ChangePassword(
            [HttpTrigger(AuthorizationLevel.Anonymous, "post", Route = "auth/change-password")] HttpRequestData req,
            FunctionContext context)
        {
            _logger.LogInformation("Processing password change request");

            try
            {
                // Authenticate user
                var user = await _authMiddleware.GetUserFromRequestAsync(req);
                if (user == null)
                {
                    return _authMiddleware.CreateUnauthorizedResponse(req);
                }

                var requestBody = await new StreamReader(req.Body).ReadToEndAsync();
                var changePasswordRequest = JsonSerializer.Deserialize<ChangePasswordRequest>(requestBody, new JsonSerializerOptions
                {
                    PropertyNameCaseInsensitive = true
                });

                if (changePasswordRequest == null)
                {
                    return CreateBadRequestResponse(req, "Invalid request body");
                }

                // Validate request
                if (!IsValid(changePasswordRequest))
                {
                    return CreateBadRequestResponse(req, "Validation failed: " + string.Join(", ", GetValidationErrors(changePasswordRequest)));
                }

                // Change password
                var result = await _userService.ChangePasswordAsync(user.Id, changePasswordRequest);

                if (result)
                {
                    var response = req.CreateResponse(HttpStatusCode.OK);
                    await response.WriteStringAsync(JsonSerializer.Serialize(new
                    {
                        message = "Password changed successfully"
                    }));
                    return response;
                }
                else
                {
                    return CreateErrorResponse(req, "Failed to change password");
                }
            }
            catch (InvalidOperationException ex)
            {
                _logger.LogWarning(ex, "Password change failed: {Message}", ex.Message);
                return CreateBadRequestResponse(req, ex.Message);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error during password change");
                return CreateErrorResponse(req, "An error occurred while changing password");
            }
        }

        private static HttpResponseData CreateBadRequestResponse(HttpRequestData req, string message)
        {
            var response = req.CreateResponse(HttpStatusCode.BadRequest);
            response.Headers.Add("Content-Type", "application/json");
            response.WriteStringAsync(JsonSerializer.Serialize(new { error = message })).Wait();
            return response;
        }

        private static HttpResponseData CreateUnauthorizedResponse(HttpRequestData req, string message)
        {
            var response = req.CreateResponse(HttpStatusCode.Unauthorized);
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

        private static bool IsValid(object model)
        {
            // Simple validation - in a real app, use DataAnnotations validation
            return model != null;
        }

        private static List<string> GetValidationErrors(object model)
        {
            // Simple validation - in a real app, use DataAnnotations validation
            return new List<string>();
        }

        [Function("Unregister")]
        public async Task<HttpResponseData> Unregister(
            [HttpTrigger(AuthorizationLevel.Function, "delete", Route = "auth/unregister")] HttpRequestData req)
        {
            try
            {
                // Authenticate user
                var user = await _authMiddleware.GetUserFromRequestAsync(req);
                if (user == null)
                {
                    return _authMiddleware.CreateUnauthorizedResponse(req);
                }

                var requestBody = await new StreamReader(req.Body).ReadToEndAsync();
                var unregisterRequest = JsonSerializer.Deserialize<UnregisterRequest>(requestBody, new JsonSerializerOptions
                {
                    PropertyNameCaseInsensitive = true
                });

                if (unregisterRequest == null)
                {
                    return CreateBadRequestResponse(req, "Invalid request body");
                }

                // Validate request
                var errors = GetValidationErrors(unregisterRequest);
                if (errors.Any())
                {
                    return CreateBadRequestResponse(req, string.Join(", ", errors));
                }

                // Unregister user (this will delete the user and all their data)
                var success = await _userService.UnregisterAsync(user.Id, unregisterRequest.Password);

                if (success)
                {
                    var response = req.CreateResponse(HttpStatusCode.OK);
                    await response.WriteStringAsync("User unregistered successfully");
                    return response;
                }
                else
                {
                    return CreateErrorResponse(req, "Failed to unregister user");
                }
            }
            catch (InvalidOperationException ex)
            {
                _logger.LogWarning(ex, "Unregister failed: {Message}", ex.Message);
                return CreateBadRequestResponse(req, ex.Message);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error unregistering user");
                return CreateErrorResponse(req, "An error occurred while unregistering user");
            }
        }
    }
}
