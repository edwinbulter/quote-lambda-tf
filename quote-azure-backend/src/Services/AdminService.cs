using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models.Admin;
using QuoteAzureBackend.Data;
using QuoteAzureBackend.Models;
using QuotePageResponse = QuoteAzureBackend.Models.QuotePageResponse;
using QuoteAddResponse = QuoteAzureBackend.Models.QuoteAddResponse;

namespace QuoteAzureBackend.Services
{
    public class AdminService : IAdminService
    {
        private readonly IUserRoleRepository _userRoleRepository;
        private readonly IQuoteManagementService _quoteManagementService;
        private readonly ILogger<AdminService> _logger;

        public AdminService(
            IUserRoleRepository userRoleRepository,
            IQuoteManagementService quoteManagementService,
            ILogger<AdminService> logger)
        {
            _userRoleRepository = userRoleRepository;
            _quoteManagementService = quoteManagementService;
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
                        Username = userRole.Username,
                        Email = userRole.Username + "@example.com", // Placeholder - could be enhanced with Azure AD lookup
                        Roles = new[] { string.IsNullOrEmpty(userRole.Role) ? string.Empty : userRole.Role.ToUpper() },
                        Enabled = true, // Azure AD users are enabled by default
                        UserStatus = "ACTIVE"
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

        public async Task<QuotePageResponse> GetQuotesAsync(int page, int pageSize, string? quoteText, string? author, string? sortBy, string? sortOrder)
        {
            _logger.LogInformation("Getting quotes with filters - Page: {Page}, Size: {PageSize}", page, pageSize);
            
            try
            {
                var quotes = await _quoteManagementService.GetQuotesAsync(page, pageSize, quoteText, author, sortBy, sortOrder);
                
                // Convert to admin response format
                var adminResponse = new QuoteAzureBackend.Models.Admin.QuotePageResponse
                {
                    Quotes = quotes.Quotes.Select(q => new QuoteWithLikeCount
                    {
                        Id = q.Id,
                        QuoteText = q.QuoteText,
                        Author = q.Author,
                        LikeCount = q.LikeCount,
                        CreatedAt = q.CreatedAt
                    }).ToList(),
                    TotalCount = quotes.TotalCount,
                    Page = quotes.Page,
                    PageSize = quotes.PageSize,
                    TotalPages = quotes.TotalPages
                };
                
                // Convert back to the expected return type
                return new QuotePageResponse
                {
                    Quotes = quotes.Quotes,
                    TotalCount = quotes.TotalCount,
                    Page = quotes.Page,
                    PageSize = quotes.PageSize,
                    TotalPages = quotes.TotalPages
                };
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to get quotes");
                throw new InvalidOperationException("Failed to get quotes: " + ex.Message, ex);
            }
        }

        public async Task<QuoteAddResponse> FetchAndAddNewQuotesAsync(string requestingUsername)
        {
            _logger.LogInformation("Fetching and adding new quotes (requested by {RequestingUsername})", requestingUsername);
            
            try
            {
                var result = await _quoteManagementService.FetchAndAddNewQuotesAsync(requestingUsername);
                
                return new QuoteAddResponse
                {
                    QuotesAdded = result.QuotesAdded,
                    TotalQuotes = result.TotalQuotes,
                    Message = result.Message
                };
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to fetch and add new quotes");
                throw new InvalidOperationException("Failed to fetch and add new quotes: " + ex.Message, ex);
            }
        }

        public async Task<int> GetTotalLikesAsync()
        {
            _logger.LogInformation("Getting total likes count");
            
            try
            {
                return await _quoteManagementService.GetTotalLikesAsync();
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to get total likes");
                throw new InvalidOperationException("Failed to get total likes: " + ex.Message, ex);
            }
        }

        public async Task<bool> DeleteQuoteAsync(int id, string requestingUsername)
        {
            _logger.LogInformation("Deleting quote {Id} (requested by {RequestingUsername})", id, requestingUsername);
            
            try
            {
                return await _quoteManagementService.DeleteQuoteAsync(id, requestingUsername);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to delete quote");
                throw new InvalidOperationException("Failed to delete quote: " + ex.Message, ex);
            }
        }

        public async Task<Quote?> UpdateQuoteAsync(int id, Quote quote, string requestingUsername)
        {
            _logger.LogInformation("Updating quote {Id} (requested by {RequestingUsername})", id, requestingUsername);
            
            try
            {
                return await _quoteManagementService.UpdateQuoteAsync(id, quote, requestingUsername);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to update quote");
                throw new InvalidOperationException("Failed to update quote: " + ex.Message, ex);
            }
        }
    }
}
