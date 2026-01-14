using QuoteAzureBackend.Models;

namespace QuoteAzureBackend.Data
{
    public interface IQuoteRepository
    {
        Task<Quote?> GetQuoteByIdAsync(int id);
        Task<List<Quote>> GetAllQuotesAsync();
        Task<Quote> AddQuoteAsync(Quote quote);
        Task<bool> DeleteQuoteAsync(int id);
    }
}
