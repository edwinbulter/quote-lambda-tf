using Azure;
using Azure.Data.Tables;
using QuoteAzureBackend.Models;
using System.Text.Json;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Configuration;
using System.Linq;
using System.Threading.Tasks;

namespace QuoteAzureBackend.Data
{
    public class UserRepository : IUserRepository
    {
        private readonly TableClient _tableClient;
        private readonly ILogger<UserRepository> _logger;

        public UserRepository(IConfiguration config, ILogger<UserRepository> logger)
        {
            // Uses the SAME storage account as other tables (qbtstk9asli)
            var connectionString = config["TableStorageConnectionString"];
            var tableName = "Users"; // New table for JWT authentication
            
            _tableClient = new TableClient(connectionString, tableName);
            _tableClient.CreateIfNotExists(); // Auto-creates the Users table
            _logger = logger;
        }

        public async Task<User> CreateAsync(User user)
        {
            var entity = new TableEntity(user.Id, user.Username)
            {
                ["Email"] = user.Email,
                ["PasswordHash"] = user.PasswordHash,
                ["Role"] = user.Role,
                ["CreatedAt"] = user.CreatedAt,
                ["UpdatedAt"] = user.UpdatedAt,
                ["IsActive"] = user.IsActive,
                ["PasswordResetToken"] = user.PasswordResetToken,
                ["PasswordResetExpires"] = user.PasswordResetExpires
            };

            try
            {
                await _tableClient.AddEntityAsync(entity);
                _logger.LogInformation("User created successfully with ID: {UserId}", user.Id);
                return user;
            }
            catch (RequestFailedException ex)
            {
                _logger.LogError(ex, "Error creating user with email: {Email}", user.Email);
                throw;
            }
        }

        public async Task<bool> DeleteAsync(string id)
        {
            try
            {
                // First find the entity by partition key
                TableEntity? entity = null;
                await foreach (var e in _tableClient.QueryAsync<TableEntity>(filter: $"PartitionKey eq '{id}'"))
                {
                    entity = e;
                    break;
                }

                if (entity != null)
                {
                    await _tableClient.DeleteEntityAsync(entity.PartitionKey, entity.RowKey);
                    _logger.LogInformation("User deleted successfully with ID: {UserId}", id);
                    return true;
                }

                return false;
            }
            catch (RequestFailedException ex)
            {
                _logger.LogError(ex, "Error deleting user with ID: {UserId}", id);
                throw;
            }
        }

        public async Task<bool> EmailExistsAsync(string email)
        {
            try
            {
                TableEntity? entity = null;
                await foreach (var e in _tableClient.QueryAsync<TableEntity>(filter: $"Email eq '{email}'"))
                {
                    entity = e;
                    break;
                }
                return entity != null;
            }
            catch (RequestFailedException ex)
            {
                _logger.LogError(ex, "Error checking if email exists: {Email}", email);
                throw;
            }
        }

        public async Task<IEnumerable<User>> GetAllAsync()
        {
            try
            {
                var users = new List<User>();
                await foreach (var entity in _tableClient.QueryAsync<TableEntity>())
                {
                    users.Add(MapTableEntityToUser(entity));
                }
                return users;
            }
            catch (RequestFailedException ex)
            {
                _logger.LogError(ex, "Error retrieving all users");
                throw;
            }
        }

        public async Task<User?> GetByIdAsync(string id)
        {
            try
            {
                TableEntity? entity = null;
                await foreach (var e in _tableClient.QueryAsync<TableEntity>(filter: $"PartitionKey eq '{id}'"))
                {
                    entity = e;
                    break;
                }
                
                return entity != null ? MapTableEntityToUser(entity) : null;
            }
            catch (RequestFailedException ex)
            {
                _logger.LogError(ex, "Error retrieving user with ID: {UserId}", id);
                throw;
            }
        }

        public async Task<User?> GetByEmailAsync(string email)
        {
            try
            {
                TableEntity? entity = null;
                await foreach (var e in _tableClient.QueryAsync<TableEntity>(filter: $"Email eq '{email}'"))
                {
                    entity = e;
                    break;
                }
                
                return entity != null ? MapTableEntityToUser(entity) : null;
            }
            catch (RequestFailedException ex)
            {
                _logger.LogError(ex, "Error retrieving user with email: {Email}", email);
                throw;
            }
        }

        public async Task<User?> GetByUsernameAsync(string username)
        {
            try
            {
                TableEntity? entity = null;
                await foreach (var e in _tableClient.QueryAsync<TableEntity>(filter: $"RowKey eq '{username}'"))
                {
                    entity = e;
                    break;
                }
                
                return entity != null ? MapTableEntityToUser(entity) : null;
            }
            catch (RequestFailedException ex)
            {
                _logger.LogError(ex, "Error retrieving user with username: {Username}", username);
                throw;
            }
        }

        public async Task<User> UpdateAsync(User user)
        {
            var entity = new TableEntity(user.Id, user.Username)
            {
                ["Email"] = user.Email,
                ["PasswordHash"] = user.PasswordHash,
                ["Role"] = user.Role,
                ["CreatedAt"] = user.CreatedAt,
                ["UpdatedAt"] = DateTime.UtcNow, // Always update the timestamp
                ["IsActive"] = user.IsActive,
                ["PasswordResetToken"] = user.PasswordResetToken,
                ["PasswordResetExpires"] = user.PasswordResetExpires
            };

            try
            {
                await _tableClient.UpdateEntityAsync(entity, new ETag("*"), TableUpdateMode.Replace);
                _logger.LogInformation("User updated successfully with ID: {UserId}", user.Id);
                user.UpdatedAt = DateTime.UtcNow; // Update the user object as well
                return user;
            }
            catch (RequestFailedException ex)
            {
                _logger.LogError(ex, "Error updating user with ID: {UserId}", user.Id);
                throw;
            }
        }

        public async Task<bool> UsernameExistsAsync(string username)
        {
            try
            {
                TableEntity? entity = null;
                await foreach (var e in _tableClient.QueryAsync<TableEntity>(filter: $"RowKey eq '{username}'"))
                {
                    entity = e;
                    break;
                }
                return entity != null;
            }
            catch (RequestFailedException ex)
            {
                _logger.LogError(ex, "Error checking if username exists: {Username}", username);
                throw;
            }
        }

        private static User MapTableEntityToUser(TableEntity entity)
        {
            return new User
            {
                Id = entity.PartitionKey,
                Username = entity.RowKey,
                Email = entity.GetString("Email") ?? string.Empty,
                PasswordHash = entity.GetString("PasswordHash") ?? string.Empty,
                Role = entity.GetString("Role") ?? "User",
                CreatedAt = entity.GetDateTime("CreatedAt") ?? DateTime.UtcNow,
                UpdatedAt = entity.GetDateTime("UpdatedAt") ?? DateTime.UtcNow,
                IsActive = entity.GetBoolean("IsActive") ?? false,
                PasswordResetToken = entity.GetString("PasswordResetToken"),
                PasswordResetExpires = entity.ContainsKey("PasswordResetExpires") ? entity.GetDateTime("PasswordResetExpires") : null
            };
        }
    }
}
