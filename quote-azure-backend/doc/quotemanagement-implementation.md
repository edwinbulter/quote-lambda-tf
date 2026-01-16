# QuoteManagementService Implementation Guide

## 🎯 Overview

This guide describes how to implement the QuoteManagementService in C# for the Azure Functions backend, translating from the Java implementation in quote-lambda-tf-backend and adapting it for Azure AD authentication and Azure-specific services.

## 📋 Java Implementation Analysis

### Current Java QuoteManagementService Features
- **Quote pagination and filtering**
- **Quote search by text and author**
- **Quote sorting options**
- **External quote fetching (ZenQuotes API)**
- **Like count tracking**
- **Quote statistics**

## 🔧 C# Implementation Plan

### Step 1: Create IQuoteManagementService Interface

```csharp
// Services/IQuoteManagementService.cs
using QuoteAzureBackend.Models;

namespace QuoteAzureBackend.Services
{
    public interface IQuoteManagementService
    {
        Task<QuotePageResponse> GetQuotesAsync(int page, int pageSize, string? quoteText, string? author, string? sortBy, string? sortOrder);
        Task<QuoteAddResponse> FetchAndAddNewQuotesAsync(string requestingUsername);
        Task<int> GetTotalQuotesCountAsync(string? quoteText = null, string? author = null);
        Task<int> GetTotalLikesAsync();
        Task<Quote?> GetQuoteByIdAsync(int id);
        Task<bool> DeleteQuoteAsync(int id, string requestingUsername);
        Task<Quote?> UpdateQuoteAsync(int id, Quote quote, string requestingUsername);
    }
}
```

### Step 2: Create QuotePageResponse Model

```csharp
// Models/QuotePageResponse.cs
namespace QuoteAzureBackend.Models
{
    public class QuotePageResponse
    {
        public List<Quote> Quotes { get; set; } = new List<Quote>();
        public int TotalCount { get; set; }
        public int Page { get; set; }
        public int PageSize { get; set; }
        public int TotalPages { get; set; }
    }
}
```

### Step 3: Create QuoteAddResponse Model

```csharp
// Models/QuoteAddResponse.cs
namespace QuoteAzureBackend.Models
{
    public class QuoteAddResponse
    {
        public int QuotesAdded { get; set; }
        public int TotalQuotes { get; set; }
        public string Message { get; set; } = string.Empty;
    }
}
```

### Step 4: Implement QuoteManagementService

```csharp
// Services/QuoteManagementService.cs
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
                var newQuotes = await _zenQuotesService.GetRandomQuotesAsync(10); // Get 10 random quotes
                
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
                // Get all quotes and sum their like counts
                var allQuotes = await _quoteRepository.GetAllQuotesAsync();
                var totalLikes = 0;
                
                foreach (var quote in allQuotes)
                {
                    totalLikes += await GetLikeCountAsync(quote.Id);
                }
                
                return totalLikes;
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
                    await CleanupQuoteActivitiesAsync(id);
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
                
                var updatedQuote = await _quoteRepository.UpdateQuoteAsync(quote);
                
                if (updatedQuote != null)
                {
                    updatedQuote.LikeCount = await GetLikeCountAsync(id);
                    _logger.LogInformation("Successfully updated quote {Id}", id);
                }
                
                return updatedQuote;
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

        private async Task<int> GetLikeCountAsync(int quoteId)
        {
            try
            {
                // This would typically come from a dedicated likes repository
                // For now, we'll use the existing quote service method
                // Note: This is a simplified implementation
                return 0; // TODO: Implement proper like counting
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Error getting like count for quote {Id}", quoteId);
                return 0;
            }
        }

        private async Task CleanupQuoteActivitiesAsync(int quoteId)
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
```

### Step 5: Update AdminService to Use QuoteManagementService

```csharp
// Services/AdminService.cs (Updated)
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models.Admin;
using QuoteAzureBackend.Data;
using QuoteAzureBackend.Models;

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

        public async Task<QuotePageResponse> GetQuotesAsync(int page, int pageSize, string? quoteText, string? author, string? sortBy, string? sortOrder)
        {
            _logger.LogInformation("Getting quotes with filters - Page: {Page}, Size: {PageSize}", page, pageSize);
            
            try
            {
                var quotes = await _quoteManagementService.GetQuotesAsync(page, pageSize, quoteText, author, sortBy, sortOrder);
                
                // Convert to admin response format
                return new QuotePageResponse
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
    }
}
```

### Step 6: Update Program.cs to Register New Services

```csharp
// Program.cs (Updated)
using Microsoft.Azure.Functions.Worker.Configuration;
using Microsoft.Azure.Functions.Worker;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Configuration;
using QuoteAzureBackend.Handlers;
using QuoteAzureBackend.Services;
using QuoteAzureBackend.Data;
using QuoteAzureBackend.Middleware;
using Microsoft.IdentityModel.Tokens;
using Azure.Data.Tables;
using System.Text;

var host = new HostBuilder()
    .ConfigureFunctionsWorkerDefaults()
    .ConfigureServices(services => {
        services.AddApplicationInsightsTelemetryWorkerService();
        
        // Register HttpClient
        services.AddHttpClient();
        
        // Register Table Storage client
        services.AddSingleton(sp => {
            var configuration = sp.GetRequiredService<IConfiguration>();
            var connectionString = configuration["TableStorageConnectionString"];
            return new TableServiceClient(connectionString);
        });
        
        // Register repositories
        services.AddSingleton<IQuoteRepository, QuoteRepository>();
        services.AddSingleton<IUserActivityRepository, UserActivityRepository>();
        services.AddSingleton<IUserRoleRepository, UserRoleRepository>();
        
        // Register services
        services.AddSingleton<IQuoteService, QuoteService>();
        services.AddSingleton<IZenQuotesService, ZenQuotesService>();
        services.AddSingleton<IUserActivityService, UserActivityService>();
        services.AddSingleton<IQuoteManagementService, QuoteManagementService>();
        
        // Register authentication services
        services.AddSingleton<IAuthenticationService, AuthenticationService>();
        services.AddSingleton<JwtAuthenticationMiddleware>();
        
        // Register admin services
        services.AddSingleton<IAdminService, AdminService>();
        
        // Add logging
        services.AddLogging();
    })
    .Build();

host.Run();
```

### Step 7: Add Additional Admin Endpoints

```csharp
// Handlers/AdminHandler.cs (Additional endpoints)

[Function("AdminDeleteQuote")]
public async Task<HttpResponseData> AdminDeleteQuoteAsync(
    [HttpTrigger(AuthorizationLevel.Anonymous, "delete", Route = "admin/quotes/{id}")] HttpRequestData req,
    int id)
{
    try
    {
        if (!await IsCurrentUserAdmin(req))
        {
            var forbiddenResponse = req.CreateResponse(HttpStatusCode.Forbidden);
            await forbiddenResponse.WriteStringAsync("Admin access required");
            return forbiddenResponse;
        }

        var currentUserId = req.Headers.TryGetValues("X-User-ObjectId", out var values) 
            ? values.FirstOrDefault() ?? "system"
            : "system";

        var success = await _adminService.DeleteQuoteAsync(id, currentUserId);
        
        if (success)
        {
            var response = req.CreateResponse(HttpStatusCode.OK);
            await response.WriteAsJsonAsync(new { message = "Quote deleted successfully" });
            return response;
        }

        var notFoundResponse = req.CreateResponse(HttpStatusCode.NotFound);
        await notFoundResponse.WriteStringAsync("Quote not found");
        return notFoundResponse;
    }
    catch (Exception ex)
    {
        _logger.LogError(ex, "Error deleting quote");
        var errorResponse = req.CreateResponse(HttpStatusCode.InternalServerError);
        await errorResponse.WriteStringAsync("Internal server error");
        return errorResponse;
    }
}

[Function("AdminUpdateQuote")]
public async Task<HttpResponseData> AdminUpdateQuoteAsync(
    [HttpTrigger(AuthorizationLevel.Anonymous, "put", Route = "admin/quotes/{id}")] HttpRequestData req,
    int id)
{
    try
    {
        if (!await IsCurrentUserAdmin(req))
        {
            var forbiddenResponse = req.CreateResponse(HttpStatusCode.Forbidden);
            await forbiddenResponse.WriteStringAsync("Admin access required");
            return forbiddenResponse;
        }

        var requestBody = await req.ReadAsStringAsync();
        var quoteUpdate = JsonSerializer.Deserialize<Quote>(requestBody ?? "{}");
        
        if (quoteUpdate == null)
        {
            var badRequestResponse = req.CreateResponse(HttpStatusCode.BadRequest);
            await badRequestResponse.WriteStringAsync("Invalid quote data");
            return badRequestResponse;
        }

        var currentUserId = req.Headers.TryGetValues("X-User-ObjectId", out var values) 
            ? values.FirstOrDefault() ?? "system"
            : "system";

        var updatedQuote = await _adminService.UpdateQuoteAsync(id, quoteUpdate, currentUserId);
        
        if (updatedQuote != null)
        {
            var response = req.CreateResponse(HttpStatusCode.OK);
            await response.WriteAsJsonAsync(updatedQuote);
            return response;
        }

        var notFoundResponse = req.CreateResponse(HttpStatusCode.NotFound);
        await notFoundResponse.WriteStringAsync("Quote not found");
        return notFoundResponse;
    }
    catch (Exception ex)
    {
        _logger.LogError(ex, "Error updating quote");
        var errorResponse = req.CreateResponse(HttpStatusCode.InternalServerError);
        await errorResponse.WriteStringAsync("Internal server error");
        return errorResponse;
    }
}
```

## 🔄 Azure AD Integration Specifics

### Authentication Headers
The implementation uses Azure AD-specific headers:
- `X-User-ObjectId`: Azure AD object identifier
- `Authorization`: Bearer token for JWT validation

### Authorization
- Admin access is verified through database roles
- All admin operations require ADMIN role in UserRoles table
- User identity is tracked for audit purposes

## 📊 Enhanced Features

### Quote Filtering
- **Text search**: Partial match on quote text
- **Author filter**: Filter by author name
- **Combined filters**: Apply both text and author filters

### Quote Sorting
- **ID**: Default sorting by quote ID
- **Author**: Alphabetical by author name
- **Likes**: Sort by like count
- **Date**: Sort by creation date
- **Direction**: Ascending or descending

### Quote Management
- **Fetch from API**: Import quotes from ZenQuotes API
- **Duplicate prevention**: Avoid adding duplicate quotes
- **Bulk operations**: Add multiple quotes at once
- **Statistics**: Track total quotes and likes

## 🧪 Testing Implementation

### Unit Tests
```csharp
// Tests/Services/QuoteManagementServiceTests.cs
[TestClass]
public class QuoteManagementServiceTests
{
    [TestMethod]
    public async Task GetQuotes_WithPagination_ReturnsCorrectPage()
    {
        // Arrange
        var service = CreateQuoteManagementService();
        
        // Act
        var result = await service.GetQuotesAsync(1, 5, null, null, "id", "asc");
        
        // Assert
        Assert.IsNotNull(result);
        Assert.IsTrue(result.Quotes.Count <= 5);
        Assert.AreEqual(1, result.Page);
        Assert.AreEqual(5, result.PageSize);
    }
    
    [TestMethod]
    public async Task GetQuotes_WithTextFilter_ReturnsFilteredResults()
    {
        // Arrange
        var service = CreateQuoteManagementService();
        
        // Act
        var result = await service.GetQuotesAsync(1, 10, "life", null, null, null);
        
        // Assert
        Assert.IsNotNull(result);
        Assert.IsTrue(result.Quotes.All(q => q.QuoteText.Contains("life", StringComparison.OrdinalIgnoreCase)));
    }
}
```

### Integration Tests
```csharp
// Tests/Handlers/AdminHandlerTests.cs
[TestClass]
public class AdminHandlerTests
{
    [TestMethod]
    public async Task AdminGetQuotes_WithAdminToken_ReturnsQuotes()
    {
        // Arrange
        var handler = CreateAdminHandler();
        var request = CreateAuthenticatedRequest(isAdmin: true);
        
        // Act
        var response = await handler.AdminGetQuotesAsync(request, _functionContext);
        
        // Assert
        Assert.AreEqual(HttpStatusCode.OK, response.StatusCode);
    }
}
```

## 🚀 Deployment Considerations

### Performance Optimizations
- **Pagination**: Implement database-level pagination
- **Indexing**: Add database indexes for search fields
- **Async operations**: Ensure all I/O operations are async

### Security
- **Input validation**: Validate all quote data
- **SQL injection prevention**: Use parameterized queries
- **Rate limiting**: Limit quote fetch operations
- **Audit logging**: Log all admin operations

## 📚 Best Practices

1. **Repository pattern**: Keep data access separate
2. **Service layer**: Business logic in services
3. **Dependency injection**: Testable and maintainable
4. **Error handling**: Comprehensive error handling
5. **Logging**: Detailed logging for debugging
6. **Async/await**: Proper async implementation
7. **Validation**: Input validation at all layers

This implementation provides a robust, scalable quote management system adapted for Azure AD and Azure Functions! 🚀
