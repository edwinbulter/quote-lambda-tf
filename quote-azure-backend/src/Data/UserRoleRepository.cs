using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using System.Data;
using System.Net;
using Azure;
using Azure.Data.Tables;

namespace QuoteAzureBackend.Data
{
    public interface IUserRoleRepository
    {
        Task<bool> AssignRoleAsync(string username, string role, string assignedBy);
        Task<bool> RemoveRoleAsync(string username, string role);
        Task<IEnumerable<UserRole>> GetAllUsersAsync();
        Task<bool> IsUserInRoleAsync(string username, string role);
        Task<bool> RemoveAllRolesAsync(string username);
    }

    public class UserRoleRepository : IUserRoleRepository
    {
        private readonly TableClient _tableClient;
        private readonly ILogger<UserRoleRepository> _logger;
        private const string TableName = "userroles";

        public UserRoleRepository(TableServiceClient tableServiceClient, ILogger<UserRoleRepository> logger)
        {
            _tableClient = tableServiceClient.GetTableClient(TableName);
            _logger = logger;
        }

        private async Task<IEnumerable<UserRole>> GetUserRolesAsync(string username)
        {
            try
            {
                var roles = new List<UserRole>();
                await foreach (var entity in _tableClient.QueryAsync<UserRoleEntity>(filter: $"PartitionKey eq 'USER' and Username eq '{username}'"))
                {
                    roles.Add(new UserRole
                    {
                        Username = entity.Username,
                        Role = entity.Role,
                        CreatedAt = entity.CreatedAt,
                        UpdatedAt = entity.UpdatedAt,
                        CreatedBy = entity.CreatedBy,
                        UpdatedBy = entity.UpdatedBy
                    });
                }
                return roles;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting user roles for username: {Username}", username);
                return Enumerable.Empty<UserRole>();
            }
        }

        public async Task<bool> AssignRoleAsync(string username, string role, string assignedBy)
        {
            try
            {
                // Sanitize username for RowKey (replace invalid characters)
                var sanitizedUsername = username.Replace("@", "-at-").Replace(".", "-dot-");
                
                var entity = new UserRoleEntity
                {
                    PartitionKey = "USER",
                    RowKey = $"{sanitizedUsername}_{role.ToUpper()}", // Unique per username/role combination
                    Username = username, // Keep original username in the field
                    Role = role.ToUpper(),
                    CreatedAt = DateTime.UtcNow,
                    UpdatedAt = DateTime.UtcNow,
                    CreatedBy = assignedBy,
                    UpdatedBy = assignedBy
                };

                await _tableClient.UpsertEntityAsync(entity);
                _logger.LogInformation("Assigned role {Role} to user {Username} by {AssignedBy}", role, username, assignedBy);
                return true;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error assigning role {Role} to user {Username}", role, username);
                return false;
            }
        }

        public async Task<bool> RemoveRoleAsync(string username, string role)
        {
            try
            {
                // Sanitize username for RowKey (replace invalid characters)
                var sanitizedUsername = username.Replace("@", "-at-").Replace(".", "-dot-");
                
                await _tableClient.DeleteEntityAsync("USER", $"{sanitizedUsername}_{role.ToUpper()}");
                _logger.LogInformation("Removed role {Role} for user {Username}", role, username);
                return true;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error removing role {Role} for user {Username}", role, username);
                return false;
            }
        }

        public async Task<IEnumerable<UserRole>> GetAllUsersAsync()
        {
            try
            {
                var users = new List<UserRole>();
                await foreach (var entity in _tableClient.QueryAsync<UserRoleEntity>(filter: $"PartitionKey eq 'USER'"))
                {
                    users.Add(new UserRole
                    {
                        Username = entity.Username,
                        Role = entity.Role,
                        CreatedAt = entity.CreatedAt,
                        UpdatedAt = entity.UpdatedAt,
                        CreatedBy = entity.CreatedBy,
                        UpdatedBy = entity.UpdatedBy
                    });
                }
                return users;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting all user roles");
                return Enumerable.Empty<UserRole>();
            }
        }

        public async Task<bool> IsUserInRoleAsync(string username, string role)
        {
            var userRoles = await GetUserRolesAsync(username);
            return userRoles.Any(ur => ur.Role.Equals(role.ToUpper(), StringComparison.OrdinalIgnoreCase));
        }
        
        public async Task<bool> RemoveAllRolesAsync(string username)
        {
            try
            {
                var userRoles = await GetUserRolesAsync(username);
                // Sanitize username for RowKey (replace invalid characters)
                var sanitizedUsername = username.Replace("@", "-at-").Replace(".", "-dot-");
                
                foreach (var userRole in userRoles)
                {
                    await _tableClient.DeleteEntityAsync("USER", $"{sanitizedUsername}_{userRole.Role}");
                }
                _logger.LogInformation("Removed all roles for user {Username}", username);
                return true;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error removing all roles for user {Username}", username);
                return false;
            }
        }
    }

    public class UserRoleEntity : ITableEntity
    {
        public string PartitionKey { get; set; } = "USER";
        public string RowKey { get; set; } = string.Empty;
        public DateTimeOffset? Timestamp { get; set; }
        public ETag ETag { get; set; }

        public string Username { get; set; } = string.Empty;
        public string Role { get; set; } = string.Empty;
        public DateTime CreatedAt { get; set; }
        public DateTime? UpdatedAt { get; set; }
        public string CreatedBy { get; set; } = string.Empty;
        public string? UpdatedBy { get; set; }
    }
}
