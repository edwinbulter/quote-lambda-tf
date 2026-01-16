using QuoteAzureBackend.Models.Admin;

namespace QuoteAzureBackend.Services
{
    public interface IAdminService
    {
        Task<List<AdminUserInfo>> ListAllUsersAsync();
        Task<QuotePageResponse> GetQuotesAsync(int page, int pageSize, string? quoteText, string? author, string? sortBy, string? sortOrder);
        Task<QuoteAddResponse> FetchAndAddNewQuotesAsync(string requestingUsername);
        Task<int> GetTotalLikesAsync();
    }
}
