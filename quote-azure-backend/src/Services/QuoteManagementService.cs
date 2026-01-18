using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Data;
using System.Text.Json;

namespace QuoteAzureBackend.Services
{
    public class QuoteManagementService : IQuoteManagementService
    {
        private readonly IQuoteRepository _quoteRepository;
        private readonly IUserActivityRepository _userActivityRepository;
        private readonly IZenQuotesService _zenQuotesService;
        private readonly ILogger<QuoteManagementService> _logger;
        private readonly HttpClient _httpClient;

        public QuoteManagementService(
            IQuoteRepository quoteRepository,
            IUserActivityRepository userActivityRepository,
            IZenQuotesService zenQuotesService,
            ILogger<QuoteManagementService> logger,
            HttpClient httpClient)
        {
            _quoteRepository = quoteRepository;
            _userActivityRepository = userActivityRepository;
            _zenQuotesService = zenQuotesService;
            _logger = logger;
            _httpClient = httpClient;
        }

        public async Task<QuotePageResponse> GetQuotesAsync(int page, int pageSize, string? quoteText, string? author, string? sortBy, string? sortOrder)
        {
            _logger.LogInformation("Getting quotes - Page: {Page}, Size: {PageSize}, Filter: {QuoteText}, Author: {Author}", 
                page, pageSize, quoteText, author);

            try
            {
                // Get all quotes (repository doesn't support pagination yet)
                var allQuotes = await _quoteRepository.GetAllQuotesAsync();
                
                // Apply filters
                var filteredQuotes = allQuotes.AsEnumerable();
                
                if (!string.IsNullOrWhiteSpace(quoteText))
                {
                    filteredQuotes = filteredQuotes.Where(q => 
                        q.QuoteText.Contains(quoteText, StringComparison.OrdinalIgnoreCase));
                }
                
                if (!string.IsNullOrWhiteSpace(author))
                {
                    filteredQuotes = filteredQuotes.Where(q => 
                        q.Author.Contains(author, StringComparison.OrdinalIgnoreCase));
                }
                
                // Apply sorting
                filteredQuotes = ApplySorting(filteredQuotes, sortBy, sortOrder);
                
                // Get total count
                var totalCount = filteredQuotes.Count();
                
                // Apply pagination
                var quotes = filteredQuotes
                    .Skip((page - 1) * pageSize)
                    .Take(pageSize)
                    .ToList();
                
                // Add like counts to each quote
                foreach (var quote in quotes)
                {
                    quote.LikeCount = await GetLikeCountAsync(quote.Id);
                }
                
                return new QuotePageResponse
                {
                    Quotes = quotes,
                    TotalCount = totalCount,
                    Page = page,
                    PageSize = pageSize,
                    TotalPages = (int)Math.Ceiling((double)totalCount / pageSize)
                };
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting quotes");
                throw new InvalidOperationException("Failed to get quotes: " + ex.Message, ex);
            }
        }

        public async Task<QuoteAddResponse> FetchAndAddNewQuotesAsync(string requestingUsername)
        {
            _logger.LogInformation("Fetching and adding new quotes (requested by {RequestingUsername})", requestingUsername);
            
            try
            {
                // Fetch quotes from ZenQuotes API
                var newQuotes = await _zenQuotesService.GetMultipleQuotesAsync(); // Get 10 random quotes
                
                var quotesAdded = 0;
                var totalQuotes = 0;
                
                foreach (var quote in newQuotes)
                {
                    // Check if quote already exists
                    var existingQuotes = await _quoteRepository.GetAllQuotesAsync();
                    if (!existingQuotes.Any(q => q.QuoteText == quote.QuoteText && q.Author == quote.Author))
                    {
                        var addedQuote = await _quoteRepository.AddQuoteAsync(quote);
                        if (addedQuote != null)
                        {
                            quotesAdded++;
                        }
                    }
                }
                
                totalQuotes = (await _quoteRepository.GetAllQuotesAsync()).Count;
                
                _logger.LogInformation("Successfully added {Count} new quotes. Total quotes: {Total}", quotesAdded, totalQuotes);
                
                return new QuoteAddResponse
                {
                    QuotesAdded = quotesAdded,
                    TotalQuotes = totalQuotes,
                    Message = $"Successfully added {quotesAdded} new quotes"
                };
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to fetch and add new quotes");
                throw new InvalidOperationException("Failed to fetch and add new quotes: " + ex.Message, ex);
            }
        }

        public async Task<int> GetTotalQuotesCountAsync(string? quoteText = null, string? author = null)
        {
            try
            {
                var allQuotes = await _quoteRepository.GetAllQuotesAsync();
                
                if (!string.IsNullOrWhiteSpace(quoteText))
                {
                    allQuotes = allQuotes.Where(q => 
                        q.QuoteText.Contains(quoteText, StringComparison.OrdinalIgnoreCase)).ToList();
                }
                
                if (!string.IsNullOrWhiteSpace(author))
                {
                    allQuotes = allQuotes.Where(q => 
                        q.Author.Contains(author, StringComparison.OrdinalIgnoreCase)).ToList();
                }
                
                return allQuotes.Count;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting total quotes count");
                throw new InvalidOperationException("Failed to get total quotes count: " + ex.Message, ex);
            }
        }

        public async Task<int> GetTotalLikesAsync()
        {
            try
            {
                // Get total likes by counting all records in userlikes table
                return await _userActivityRepository.GetTotalLikesCountAsync();
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting total likes");
                throw new InvalidOperationException("Failed to get total likes: " + ex.Message, ex);
            }
        }

        public async Task<Quote?> GetQuoteByIdAsync(int id)
        {
            try
            {
                var quote = await _quoteRepository.GetQuoteByIdAsync(id);
                if (quote != null)
                {
                    quote.LikeCount = await GetLikeCountAsync(id);
                }
                return quote;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting quote by ID: {Id}", id);
                throw new InvalidOperationException("Failed to get quote: " + ex.Message, ex);
            }
        }

        public async Task<bool> DeleteQuoteAsync(int id, string requestingUsername)
        {
            _logger.LogInformation("Deleting quote {Id} (requested by {RequestingUsername})", id, requestingUsername);
            
            try
            {
                // Check if user is admin (this should be handled at the handler level)
                // For now, proceed with deletion
                
                var success = await _quoteRepository.DeleteQuoteAsync(id);
                
                if (success)
                {
                    // Clean up user activities related to this quote
                    CleanupQuoteActivitiesAsync(id);
                    _logger.LogInformation("Successfully deleted quote {Id}", id);
                }
                
                return success;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error deleting quote {Id}", id);
                throw new InvalidOperationException("Failed to delete quote: " + ex.Message, ex);
            }
        }

        public async Task<Quote?> UpdateQuoteAsync(int id, Quote quote, string requestingUsername)
        {
            _logger.LogInformation("Updating quote {Id} (requested by {RequestingUsername})", id, requestingUsername);
            
            try
            {
                // Ensure the quote ID matches
                quote.Id = id;
                
                var success = await _quoteRepository.UpdateQuoteAsync(quote);
                
                if (success)
                {
                    var updatedQuote = await _quoteRepository.GetQuoteByIdAsync(id);
                    if (updatedQuote != null)
                    {
                        updatedQuote.LikeCount = await GetLikeCountAsync(id);
                        _logger.LogInformation("Successfully updated quote {Id}", id);
                        return updatedQuote;
                    }
                }
                
                return null;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error updating quote {Id}", id);
                throw new InvalidOperationException("Failed to update quote: " + ex.Message, ex);
            }
        }

        private IEnumerable<Quote> ApplySorting(IEnumerable<Quote> quotes, string? sortBy, string? sortOrder)
        {
            var sortDescending = "desc".Equals(sortOrder, StringComparison.OrdinalIgnoreCase);
            
            return sortBy?.ToLowerInvariant() switch
            {
                "author" => sortDescending 
                    ? quotes.OrderByDescending(q => q.Author)
                    : quotes.OrderBy(q => q.Author),
                
                "likes" => sortDescending 
                    ? quotes.OrderByDescending(q => q.LikeCount)
                    : quotes.OrderBy(q => q.LikeCount),
                
                "createdat" or "date" => sortDescending 
                    ? quotes.OrderByDescending(q => q.CreatedAt)
                    : quotes.OrderBy(q => q.CreatedAt),
                
                _ => sortDescending 
                    ? quotes.OrderByDescending(q => q.Id)
                    : quotes.OrderBy(q => q.Id)
            };
        }

        private Task<int> GetLikeCountAsync(int quoteId)
        {
            try
            {
                // This would typically come from a dedicated likes repository
                // For now, we'll use the existing quote service method
                // Note: This is a simplified implementation
                return Task.FromResult(0); // TODO: Implement proper like counting
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Error getting like count for quote {Id}", quoteId);
                return Task.FromResult(0);
            }
        }

        private void CleanupQuoteActivitiesAsync(int quoteId)
        {
            try
            {
                // Clean up all user activities related to this quote
                // This would typically be done in the user activity repository
                // TODO: Implement activity cleanup
                _logger.LogInformation("Cleaning up activities for deleted quote {Id}", quoteId);
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Error cleaning up activities for quote {Id}", quoteId);
            }
        }
    }
}
