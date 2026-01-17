using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Services;
using QuoteAzureBackend.Data;
using Microsoft.AspNetCore.Identity;

namespace QuoteAzureBackend.Services
{
    public class AdminUserSeeder
    {
        private readonly IUserRepository _userRepository;
        private readonly IPasswordHasher<User> _passwordHasher;
        private readonly IUserRoleRepository _userRoleRepository;
        private readonly ILogger<AdminUserSeeder> _logger;

        public AdminUserSeeder(
            IUserRepository userRepository,
            IPasswordHasher<User> passwordHasher,
            IUserRoleRepository userRoleRepository,
            ILogger<AdminUserSeeder> logger)
        {
            _userRepository = userRepository;
            _passwordHasher = passwordHasher;
            _userRoleRepository = userRoleRepository;
            _logger = logger;
        }

        public async Task SeedAdminUserAsync()
        {
            try
            {
                // Check if admin user already exists
                var existingAdmin = await _userRepository.GetByEmailAsync("admin@quote-backend.local");
                
                if (existingAdmin != null)
                {
                    _logger.LogInformation("Admin user already exists");
                    return;
                }

                // Create admin user
                var adminUser = new User
                {
                    Id = Guid.NewGuid().ToString(),
                    Email = "admin@quote-backend.local",
                    Username = "admin",
                    Role = "Admin",
                    IsActive = true,
                    CreatedAt = DateTime.UtcNow,
                    UpdatedAt = DateTime.UtcNow
                };

                // Hash password
                adminUser.PasswordHash = _passwordHasher.HashPassword(adminUser, "Admin123!");

                // Save admin user
                await _userRepository.CreateAsync(adminUser);

                // Assign ADMIN role in userroles table
                await _userRoleRepository.AssignRoleAsync(
                    adminUser.Id,
                    adminUser.Email,
                    "ADMIN",
                    "system"
                );

                _logger.LogInformation("Admin user created successfully with email: admin@quote-backend.local");
                _logger.LogInformation("Admin role assigned in userroles table");
                _logger.LogInformation("Default admin password: Admin123!");
                _logger.LogWarning("Please change the default admin password after first login!");
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error creating admin user");
                throw;
            }
        }

        public async Task SeedTestUserAsync()
        {
            try
            {
                // Check if test user already exists
                var existingUser = await _userRepository.GetByEmailAsync("user@example.com");
                
                if (existingUser != null)
                {
                    _logger.LogInformation("Test user already exists");
                    return;
                }

                // Create test user
                var testUser = new User
                {
                    Id = Guid.NewGuid().ToString(),
                    Email = "user@example.com",
                    Username = "testuser",
                    Role = "User",
                    IsActive = true,
                    CreatedAt = DateTime.UtcNow,
                    UpdatedAt = DateTime.UtcNow
                };

                // Hash password
                testUser.PasswordHash = _passwordHasher.HashPassword(testUser, "User123!");

                // Save test user
                await _userRepository.CreateAsync(testUser);

                // Assign USER role in userroles table
                await _userRoleRepository.AssignRoleAsync(
                    testUser.Id,
                    testUser.Email,
                    "USER",
                    "system"
                );

                _logger.LogInformation("Test user created successfully with email: user@example.com");
                _logger.LogInformation("User role assigned in userroles table");
                _logger.LogInformation("Default test password: User123!");
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error creating test user");
                throw;
            }
        }
    }
}
