using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models.Admin;
using QuoteAzureBackend.Data;
using QuoteAzureBackend.Models;

namespace QuoteAzureBackend.Services
{
    public class AdminService : IAdminService
    {
        private readonly IUserRoleRepository _userRoleRepository;
        private readonly IQuoteService _quoteService;
        private readonly ILogger<AdminService> _logger;

        public AdminService(
            IUserRoleRepository userRoleRepository,
            IQuoteService quoteService,
            ILogger<AdminService> logger)
        {
            _userRoleRepository = userRoleRepository;
            _quoteService = quoteService;
            _logger = logger;
        }

        public async Task<List<AdminUserInfo>> ListAllUsersAsync()
        {
            _logger.LogInformation("Listing all users from database roles");
            
            try
            {
                var userRoles = await _userRoleRepository.GetAllUsersAsync();
                var adminUsers = new List<AdminUserInfo>();
                
                foreach (var userRole in userRoles)
                {
                    var adminUser = new AdminUserInfo
                    {
                        ObjectId = userRole.ObjectId,
                        Email = userRole.Email,
                        DisplayName = userRole.Email, // Could be enhanced with Azure AD lookup
                        Role = userRole.Role,
                        CreatedAt = userRole.CreatedAt,
                        UpdatedAt = userRole.UpdatedAt,
                        CreatedBy = userRole.CreatedBy,
                        UpdatedBy = userRole.UpdatedBy,
                        Enabled = true // Azure AD users are enabled by default
                    };
                    
                    adminUsers.Add(adminUser);
                }
                
                _logger.LogInformation("Successfully listed {Count} users", adminUsers.Count);
                return adminUsers;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to list users");
                throw new InvalidOperationException("Failed to list users: " + ex.Message, ex);
            }
        }

        public Task<QuotePageResponse> GetQuotesAsync(int page, int pageSize, string? quoteText, string? author, string? sortBy, string? sortOrder)
        {
            _logger.LogInformation("Getting quotes with filters - Page: {Page}, Size: {PageSize}", page, pageSize);
            
            try
            {
                // For now, return empty quotes list as these methods don't exist yet
                // TODO: Implement admin quote management with proper filtering and pagination
                return Task.FromResult(new QuotePageResponse
                {
                    Quotes = new List<QuoteWithLikeCount>(),
                    TotalCount = 0,
                    Page = page,
                    PageSize = pageSize,
                    TotalPages = 0
                });
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to get quotes");
                throw new InvalidOperationException("Failed to get quotes: " + ex.Message, ex);
            }
        }

        public Task<QuoteAddResponse> FetchAndAddNewQuotesAsync(string requestingUsername)
        {
            _logger.LogInformation("Fetching and adding new quotes (requested by {RequestingUsername})", requestingUsername);
            
            try
            {
                // TODO: Implement quote fetching from external source
                return Task.FromResult(new QuoteAddResponse
                {
                    QuotesAdded = 0,
                    TotalQuotes = 0,
                    Message = "Quote fetching not implemented yet"
                });
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to fetch and add new quotes");
                throw new InvalidOperationException("Failed to fetch and add new quotes: " + ex.Message, ex);
            }
        }

        public Task<int> GetTotalLikesAsync()
        {
            _logger.LogInformation("Getting total likes count");
            
            try
            {
                // TODO: Implement total likes calculation
                return Task.FromResult(0);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to get total likes");
                throw new InvalidOperationException("Failed to get total likes: " + ex.Message, ex);
            }
        }
    }
}
