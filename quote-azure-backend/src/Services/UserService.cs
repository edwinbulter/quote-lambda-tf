using Microsoft.AspNetCore.Identity;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Models.Auth;
using QuoteAzureBackend.Data;
using Microsoft.Extensions.Logging;

namespace QuoteAzureBackend.Services
{
    public class UserService : IUserService
    {
        private readonly IUserRepository _userRepository;
        private readonly IJwtService _jwtService;
        private readonly IPasswordHasher<Models.User> _passwordHasher;
        private readonly ILogger<UserService> _logger;

        public UserService(
            IUserRepository userRepository,
            IJwtService jwtService,
            IPasswordHasher<Models.User> passwordHasher,
            ILogger<UserService> logger)
        {
            _userRepository = userRepository;
            _jwtService = jwtService;
            _passwordHasher = passwordHasher;
            _logger = logger;
        }

        public async Task<User> RegisterAsync(RegisterRequest request)
        {
            // Check if email already exists
            if (await _userRepository.EmailExistsAsync(request.Email))
            {
                throw new InvalidOperationException("Email is already registered");
            }

            // Check if username already exists
            if (await _userRepository.UsernameExistsAsync(request.Username))
            {
                throw new InvalidOperationException("Username is already taken");
            }

            // Create new user
            var user = new User
            {
                Email = request.Email,
                Username = request.Username,
                Role = "User", // Default role
                IsActive = true
            };

            // Hash password
            user.PasswordHash = _passwordHasher.HashPassword(user, request.Password);

            try
            {
                var createdUser = await _userRepository.CreateAsync(user);
                _logger.LogInformation("User registered successfully with email: {Email}", request.Email);
                return createdUser;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error registering user with email: {Email}", request.Email);
                throw;
            }
        }

        public async Task<string> LoginAsync(LoginRequest request)
        {
            // Try to find user by email first, then by username
            User? user = null;
            bool isEmailLogin = false;
            
            // Check if the input looks like an email
            if (request.LoginIdentifier.Contains("@"))
            {
                user = await _userRepository.GetByEmailAsync(request.LoginIdentifier);
                isEmailLogin = true;
            }
            else
            {
                // Try to find by username
                user = await _userRepository.GetByUsernameAsync(request.LoginIdentifier);
            }
            
            if (user == null)
            {
                throw new InvalidOperationException("Invalid email/username or password");
            }

            // Check if user is active
            if (!user.IsActive)
            {
                throw new InvalidOperationException("Account is deactivated");
            }

            // Verify password
            var result = _passwordHasher.VerifyHashedPassword(user, user.PasswordHash, request.Password);
            
            if (result == PasswordVerificationResult.Failed)
            {
                throw new InvalidOperationException("Invalid email/username or password");
            }

            // Generate JWT token
            var token = _jwtService.GenerateToken(user);
            
            if (isEmailLogin)
            {
                _logger.LogInformation("User logged in successfully with email: {Email}", request.LoginIdentifier);
            }
            else
            {
                _logger.LogInformation("User logged in successfully with username: {Username}", request.LoginIdentifier);
            }
            
            return token;
        }

        public async Task<bool> ChangePasswordAsync(string userId, ChangePasswordRequest request)
        {
            // Find user
            var user = await _userRepository.GetByIdAsync(userId);
            
            if (user == null)
            {
                throw new InvalidOperationException("User not found");
            }

            // Verify current password
            var result = _passwordHasher.VerifyHashedPassword(user, user.PasswordHash, request.CurrentPassword);
            
            if (result == PasswordVerificationResult.Failed)
            {
                throw new InvalidOperationException("Current password is incorrect");
            }

            // Hash new password
            user.PasswordHash = _passwordHasher.HashPassword(user, request.NewPassword);
            user.UpdatedAt = DateTime.UtcNow;

            try
            {
                await _userRepository.UpdateAsync(user);
                _logger.LogInformation("Password changed successfully for user ID: {UserId}", userId);
                return true;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error changing password for user ID: {UserId}", userId);
                throw;
            }
        }

        public async Task<bool> UpdateUserRoleAsync(string adminId, UpdateRoleRequest request)
        {
            // Verify admin user
            var admin = await _userRepository.GetByIdAsync(adminId);
            if (admin == null || !IsAdminAsync(adminId).Result)
            {
                throw new UnauthorizedAccessException("Only administrators can update user roles");
            }

            // Find user to update
            var user = await _userRepository.GetByIdAsync(request.UserId);
            if (user == null)
            {
                throw new InvalidOperationException("User not found");
            }

            // Update role
            user.Role = request.NewRole;
            user.UpdatedAt = DateTime.UtcNow;

            try
            {
                await _userRepository.UpdateAsync(user);
                _logger.LogInformation("User role updated successfully. User ID: {UserId}, New Role: {Role}, Updated by: {AdminId}", 
                    request.UserId, request.NewRole, adminId);
                return true;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error updating user role for user ID: {UserId}", request.UserId);
                throw;
            }
        }

        public async Task<User?> GetUserByIdAsync(string id)
        {
            return await _userRepository.GetByIdAsync(id);
        }

        public async Task<IEnumerable<User>> GetAllUsersAsync(string adminId)
        {
            // Verify admin user
            if (!IsAdminAsync(adminId).Result)
            {
                throw new UnauthorizedAccessException("Only administrators can view all users");
            }

            try
            {
                var users = await _userRepository.GetAllAsync();
                _logger.LogInformation("Retrieved all users by admin ID: {AdminId}", adminId);
                return users;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error retrieving all users");
                throw;
            }
        }

        public async Task<bool> IsUserInRoleAsync(string userId, string role)
        {
            var user = await _userRepository.GetByIdAsync(userId);
            return user?.Role == role;
        }

        public async Task<bool> IsAdminAsync(string userId)
        {
            return await IsUserInRoleAsync(userId, "Admin");
        }

        public async Task<bool> UnregisterAsync(string userId, string password)
        {
            try
            {
                // Get user to verify password
                var user = await _userRepository.GetByIdAsync(userId);
                if (user == null)
                {
                    throw new InvalidOperationException("User not found");
                }

                // Verify password for security
                var result = _passwordHasher.VerifyHashedPassword(user, user.PasswordHash, password);
                if (result == PasswordVerificationResult.Failed)
                {
                    throw new InvalidOperationException("Invalid password");
                }

                // Prevent deletion of admin users (self-protection)
                if (user.Role == "Admin")
                {
                    throw new InvalidOperationException("Cannot delete admin users");
                }

                // Delete user from repository (this will delete the user entity)
                var userDeleted = await _userRepository.DeleteAsync(userId);
                if (!userDeleted)
                {
                    throw new InvalidOperationException("Failed to delete user");
                }

                _logger.LogInformation("User unregistered successfully: {UserId}", userId);
                return true;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error unregistering user: {UserId}", userId);
                throw;
            }
        }
    }
}
