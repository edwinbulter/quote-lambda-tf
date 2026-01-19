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
        private readonly IUserRoleRepository _userRoleRepository;
        private readonly IUserActivityRepository _userActivityRepository;
        private readonly ILogger<UserService> _logger;

        public UserService(
            IUserRepository userRepository,
            IJwtService jwtService,
            IPasswordHasher<Models.User> passwordHasher,
            IUserRoleRepository userRoleRepository,
            IUserActivityRepository userActivityRepository,
            ILogger<UserService> logger)
        {
            _userRepository = userRepository;
            _jwtService = jwtService;
            _passwordHasher = passwordHasher;
            _userRoleRepository = userRoleRepository;
            _userActivityRepository = userActivityRepository;
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
                IsActive = true
            };

            // Hash password
            user.PasswordHash = _passwordHasher.HashPassword(user, request.Password);

            try
            {
                var createdUser = await _userRepository.CreateAsync(user);
                
                // Assign default USER role
                await _userRoleRepository.AssignRoleAsync(user.Username, "USER", "system");
                
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
            try
            {
                // Verify admin user
                var admin = await _userRepository.GetByIdAsync(adminId);
                if (admin == null || !await IsAdminAsync(adminId))
                {
                    throw new UnauthorizedAccessException("Only administrators can update user roles");
                }

                // Find user to update - try by ID first, then by username
                var user = await _userRepository.GetByIdAsync(request.UserId);
                if (user == null)
                {
                    // If not found by ID, try by username
                    user = await _userRepository.GetByUsernameAsync(request.UserId);
                }
                
                if (user == null)
                {
                    throw new InvalidOperationException("User not found");
                }

                // Update role in userroles table
                var success = await _userRoleRepository.AssignRoleAsync(
                    user.Username, 
                    request.NewRole, 
                    adminId
                );
                
                if (!success)
                {
                    throw new InvalidOperationException("Failed to update user role");
                }

                // Update user repository timestamp
                user.UpdatedAt = DateTime.UtcNow;
                await _userRepository.UpdateAsync(user);

                _logger.LogInformation("User role updated successfully. Username: {Username}, New Role: {Role}, Updated by: {AdminId}", 
                    user.Username, request.NewRole, adminId);
                return true;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error updating user role for user ID: {UserId}", request.UserId);
                throw;
            }
        }

        public async Task<bool> RemoveUserRoleAsync(string adminId, UpdateRoleRequest request)
        {
            try
            {
                // Verify admin user
                var admin = await _userRepository.GetByIdAsync(adminId);
                if (admin == null || !await IsAdminAsync(adminId))
                {
                    throw new UnauthorizedAccessException("Only administrators can remove user roles");
                }

                // Find user to update - try by ID first, then by username
                var user = await _userRepository.GetByIdAsync(request.UserId);
                if (user == null)
                {
                    // If not found by ID, try by username
                    user = await _userRepository.GetByUsernameAsync(request.UserId);
                }
                
                if (user == null)
                {
                    throw new InvalidOperationException("User not found");
                }

                // Remove role in userroles table
                var success = await _userRoleRepository.RemoveRoleAsync(
                    user.Username, 
                    request.NewRole
                );
                
                if (!success)
                {
                    throw new InvalidOperationException("Failed to remove user role");
                }

                // Update user repository timestamp
                user.UpdatedAt = DateTime.UtcNow;
                await _userRepository.UpdateAsync(user);

                _logger.LogInformation("User role removed successfully. Username: {Username}, Removed Role: {Role}, Removed by: {AdminId}", 
                    user.Username, request.NewRole, adminId);
                return true;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error removing user role for user ID: {UserId}", request.UserId);
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
            if (!await IsAdminAsync(adminId))
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
            // Get user to find username
            var user = await _userRepository.GetByIdAsync(userId);
            if (user == null)
            {
                return false;
            }
            
            return await _userRoleRepository.IsUserInRoleAsync(user.Username, role);
        }

        public async Task<bool> IsAdminAsync(string userId)
        {
            return await IsUserInRoleAsync(userId, "ADMIN");
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
                if (await _userRoleRepository.IsUserInRoleAsync(user.Username, "ADMIN"))
                {
                    throw new InvalidOperationException("Cannot delete admin users");
                }

                // Delete user from repository (this will delete the user entity)
                var userDeleted = await _userRepository.DeleteAsync(userId);
                if (!userDeleted)
                {
                    throw new InvalidOperationException("Failed to delete user");
                }

                // Clean up user roles
                await _userRoleRepository.RemoveAllRolesAsync(user.Username);

                // Clean up user likes
                await _userActivityRepository.RemoveAllUserLikesAsync(user.Username);

                // Clean up user progress
                await _userActivityRepository.RemoveUserProgressAsync(user.Username);

                _logger.LogInformation("User unregistered successfully: {Username}", user.Username);
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
