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
        Task<UserRole?> GetUserRoleAsync(string objectId);
        Task<bool> AssignRoleAsync(string objectId, string email, string role, string assignedBy);
        Task<bool> RemoveRoleAsync(string objectId);
        Task<IEnumerable<UserRole>> GetAllUsersAsync();
        Task<bool> IsUserInRoleAsync(string objectId, string role);
    }

    public class UserRoleRepository : IUserRoleRepository
    {
        private readonly TableClient _tableClient;
        private readonly ILogger<UserRoleRepository> _logger;
        private const string TableName = "UserRoles";

        public UserRoleRepository(TableServiceClient tableServiceClient, ILogger<UserRoleRepository> logger)
        {
            _tableClient = tableServiceClient.GetTableClient(TableName);
            _logger = logger;
        }

        public async Task<UserRole?> GetUserRoleAsync(string objectId)
        {
            try
            {
                var response = await _tableClient.GetEntityAsync<UserRoleEntity>("USER", objectId);
                var entity = response.Value;
                
                return new UserRole
                {
                    ObjectId = entity.ObjectId,
                    Email = entity.Email,
                    Role = entity.Role,
                    CreatedAt = entity.CreatedAt,
                    UpdatedAt = entity.UpdatedAt,
                    CreatedBy = entity.CreatedBy,
                    UpdatedBy = entity.UpdatedBy
                };
            }
            catch (RequestFailedException ex) when (ex.Status == 404)
            {
                _logger.LogInformation("User role not found for ObjectId: {ObjectId}", objectId);
                return null;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting user role for ObjectId: {ObjectId}", objectId);
                return null;
            }
        }

        public async Task<bool> AssignRoleAsync(string objectId, string email, string role, string assignedBy)
        {
            try
            {
                var entity = new UserRoleEntity
                {
                    PartitionKey = "USER",
                    RowKey = objectId,
                    ObjectId = objectId,
                    Email = email,
                    Role = role.ToUpper(),
                    CreatedAt = DateTime.UtcNow,
                    UpdatedAt = DateTime.UtcNow,
                    CreatedBy = assignedBy,
                    UpdatedBy = assignedBy
                };

                await _tableClient.UpsertEntityAsync(entity);
                _logger.LogInformation("Assigned role {Role} to user {Email} by {AssignedBy}", role, email, assignedBy);
                return true;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error assigning role {Role} to user {ObjectId}", role, objectId);
                return false;
            }
        }

        public async Task<bool> RemoveRoleAsync(string objectId)
        {
            try
            {
                await _tableClient.DeleteEntityAsync("USER", objectId);
                _logger.LogInformation("Removed role for user with ObjectId: {ObjectId}", objectId);
                return true;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error removing role for user with ObjectId: {ObjectId}", objectId);
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
                        ObjectId = entity.ObjectId,
                        Email = entity.Email,
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

        public async Task<bool> IsUserInRoleAsync(string objectId, string role)
        {
            var userRole = await GetUserRoleAsync(objectId);
            return userRole?.Role.Equals(role.ToUpper(), StringComparison.OrdinalIgnoreCase) ?? false;
        }
    }

    public class UserRoleEntity : ITableEntity
    {
        public string PartitionKey { get; set; } = "USER";
        public string RowKey { get; set; } = string.Empty;
        public DateTimeOffset? Timestamp { get; set; }
        public ETag ETag { get; set; }

        public string ObjectId { get; set; } = string.Empty;
        public string Email { get; set; } = string.Empty;
        public string Role { get; set; } = string.Empty;
        public DateTime CreatedAt { get; set; }
        public DateTime? UpdatedAt { get; set; }
        public string CreatedBy { get; set; } = string.Empty;
        public string? UpdatedBy { get; set; }
    }
}
