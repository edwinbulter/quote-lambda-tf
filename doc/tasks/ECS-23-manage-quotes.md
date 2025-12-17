# ECS-23: Manage Quotes for Administrators

## Table of Contents
- [Overview](#overview)
- [User Stories](#user-stories)
- [Requirements](#requirements)
  - [Navigation Structure](#navigation-structure)
  - [Quote Management Screen](#quote-management-screen)
  - [Search Functionality](#search-functionality)
  - [Add Quotes Functionality](#add-quotes-functionality)
  - [Pagination](#pagination)
- [Implementation Steps](#implementation-steps)
  - [Phase 1: Backend Updates](#phase-1-backend-updates)
  - [Phase 2: Frontend - Quote Management Screen](#phase-2-frontend---quote-management-screen)
  - [Phase 3: Frontend - API Integration](#phase-3-frontend---api-integration)
  - [Phase 4: Frontend - App Integration](#phase-4-frontend---app-integration)
  - [Phase 5: Styling](#phase-5-styling)
  - [Phase 6: Testing](#phase-6-testing)
- [Technical Considerations](#technical-considerations)
- [API Endpoints Summary](#api-endpoints-summary)
- [Security Considerations](#security-considerations)
- [Acceptance Criteria](#acceptance-criteria)
- [Estimated Effort](#estimated-effort)
- [Dependencies](#dependencies)

## Overview
Implement a quote management interface accessible only to ADMIN users, allowing them to view all quotes in the system, search by quote text and author, add new quotes from ZEN API, and manage pagination.

## User Stories

### US-1: View All Quotes
**As an** administrator  
**I want to** see a paginated list of all quotes in the system  
**So that** I can manage and monitor the quote database

### US-2: Search Quotes
**As an** administrator  
**I want to** search quotes by text and author  
**So that** I can quickly find specific quotes

### US-3: Add New Quotes
**As an** administrator  
**I want to** fetch and add new quotes from ZEN API  
**So that** I can expand the quote database with unique quotes

### US-4: Monitor Quote Count
**As an** administrator  
**I want to** see the total number of quotes in the database  
**So that** I can track database growth

## Requirements

### Navigation Structure
```
Sidepanel
└── Manage Button (enabled only when signed in)
    └── Management Screen
        ├── Manage Favourites (enabled only for USER role)
        ├── Viewed Quotes (enabled only for USER role)
        ├── User Management (enabled only for ADMIN role)
        └── Manage Quotes (enabled only for ADMIN role)  // NEW
```

### Quote Management Screen

#### Features
1. **Quote Count Display**
   - Display total number of quotes in database at top of screen
   - Update count instantly when new quotes are added
   - Format: "Total Quotes: 1,234"

2. **Search Functionality**
   - Search by Quote Text (searches in `quoteText` field)
   - Search by Author (searches in `author` field)
   - Both fields are optional and can be used independently or together
   - Search is case-insensitive
   - Searches for partial matches (contains)
   - Results update in real-time as user types (with debouncing)
   - Clear button to reset search

3. **Add Quotes Button**
   - Large, prominent button to fetch new quotes from ZEN API
   - Shows loading state while fetching
   - Displays success/error message after operation
   - Updates quote count immediately after successful addition
   - Prevents duplicate quotes using same logic as `getQuote()` method:
     - Compare quotes by `quoteText` only
     - Remove any fetched quotes that already exist in database
     - Assign new sequential IDs to new quotes

4. **Quotes Table**
   - Columns:
     - ID (numeric, sortable)
     - Quote Text (text, truncated with ellipsis)
     - Author (text)
     - Like Count (numeric, shows how many users have liked this quote)
   - Sortable columns (click header to sort ascending/descending)
   - Hover effect on rows
   - Sticky header for scrolling

5. **Pagination**
   - Default: 50 quotes per page
   - Configurable page size: 10, 25, 50, 100, 250
   - Page navigation: Previous/Next buttons and page number display
   - Current page indicator
   - Total pages indicator
   - Jump to page input field

6. **Constraints**
   - Only visible to authenticated users
   - Only enabled for users with ADMIN role
   - Show appropriate message if user is not an ADMIN

#### Access Control
- Only visible to authenticated users
- Only enabled for users with ADMIN role
- Show appropriate message if user is not an ADMIN

### Search Functionality

#### Quote Text Search
- Searches in the `quoteText` field
- Case-insensitive matching
- Partial matches (contains)
- Example: searching "life" returns all quotes containing "life"

#### Author Search
- Searches in the `author` field
- Case-insensitive matching
- Partial matches (contains)
- Example: searching "Einstein" returns all quotes by Einstein

#### Combined Search
- When both fields are filled, results must match BOTH criteria (AND logic)
- Results are filtered on the backend for performance

#### Search Behavior
- Debounced input (500ms delay) to avoid excessive API calls
- Clear button to reset both search fields
- Search results respect pagination settings
- Total count updates to show matching quotes

### Add Quotes Functionality

#### Fetching Process
1. Click "Add Quotes" button
2. Backend fetches quotes from ZEN API (https://zenquotes.io/api/quotes)
3. Backend compares fetched quotes with existing database quotes
   - Comparison is done by `quoteText` only (same as `getQuote()` method)
   - Remove any fetched quotes that already exist
4. Assign new sequential IDs to new quotes
5. Save new quotes to database
6. Return count of quotes added

#### Duplicate Prevention
- Use same logic as `QuoteService.getQuote()` method:
  ```java
  // Fetch all current quotes from database
  List<Quote> currentDatabaseQuotes = quoteRepository.getAllQuotes();
  
  // Fetch new quotes from ZEN
  Set<Quote> fetchedQuotes = ZenClient.getSomeUniqueQuotes();
  
  // Remove duplicates by comparing quoteText
  fetchedQuotes.removeAll(new HashSet<>(currentDatabaseQuotes));
  
  // Assign new IDs
  AtomicInteger idGenerator = new AtomicInteger(currentDatabaseQuotes.size() + 1);
  fetchedQuotes.forEach(quote -> quote.setId(idGenerator.getAndIncrement()));
  
  // Save to database
  quoteRepository.saveAll(fetchedQuotes);
  ```

#### Error Handling
- If ZEN API fetch fails, show error message
- If database save fails, show error message
- Provide clear error messages to user

### Pagination

#### Page Size Options
- 10 quotes per page
- 25 quotes per page
- 50 quotes per page (default)
- 100 quotes per page
- 250 quotes per page

#### Navigation
- Previous/Next buttons
- Page number input field (jump to page)
- Current page display: "Page 3 of 25"
- Total quotes display updates based on search results

## Implementation Steps

### Phase 1: Backend Updates

#### 1.1 Create Quote Management Endpoints

**File:** `quote-lambda-tf-backend/src/main/java/ebulter/quote/lambda/QuoteHandler.java`

Add new endpoints:

**GET /admin/quotes** - List all quotes with pagination and search
- Requires ADMIN role
- Query parameters:
  - `page` (optional, default 1): page number
  - `pageSize` (optional, default 50): quotes per page
  - `quoteText` (optional): search in quote text (case-insensitive, partial match)
  - `author` (optional): search by author (case-insensitive, partial match)
- Response format:
  ```json
  {
    "quotes": [
      {
        "id": 1,
        "quoteText": "The only way to do great work is to love what you do.",
        "author": "Steve Jobs",
        "likeCount": 5
      }
    ],
    "totalCount": 1234,
    "page": 1,
    "pageSize": 50,
    "totalPages": 25
  }
  ```

**POST /admin/quotes/fetch** - Fetch and add new quotes from ZEN API
- Requires ADMIN role
- No request body
- Response format:
  ```json
  {
    "quotesAdded": 15,
    "totalQuotes": 1249,
    "message": "Successfully added 15 new quotes"
  }
  ```

#### 1.2 Create Quote Management Service

**File:** `quote-lambda-tf-backend/src/main/java/ebulter/quote/lambda/service/QuoteManagementService.java`

```java
public class QuoteManagementService {
    private final QuoteRepository quoteRepository;
    private final UserLikeRepository userLikeRepository;

    public QuoteManagementService(QuoteRepository quoteRepository, UserLikeRepository userLikeRepository) {
        this.quoteRepository = quoteRepository;
        this.userLikeRepository = userLikeRepository;
    }

    public QuotePageResponse getQuotesWithPagination(int page, int pageSize, String quoteText, String author) {
        // Get all quotes
        List<Quote> allQuotes = quoteRepository.getAllQuotes();
        
        // Apply filters
        List<Quote> filteredQuotes = allQuotes.stream()
            .filter(q -> quoteText == null || q.getQuoteText().toLowerCase().contains(quoteText.toLowerCase()))
            .filter(q -> author == null || q.getAuthor().toLowerCase().contains(author.toLowerCase()))
            .toList();
        
        // Calculate pagination
        int totalCount = filteredQuotes.size();
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalCount);
        
        // Get page of quotes
        List<Quote> pageQuotes = filteredQuotes.subList(startIndex, endIndex);
        
        // Add like counts
        List<QuoteWithLikeCount> quotesWithLikes = pageQuotes.stream()
            .map(q -> new QuoteWithLikeCount(
                q.getId(),
                q.getQuoteText(),
                q.getAuthor(),
                userLikeRepository.getLikeCountForQuote(q.getId())
            ))
            .toList();
        
        return new QuotePageResponse(quotesWithLikes, totalCount, page, pageSize, totalPages);
    }

    public QuoteAddResponse fetchAndAddNewQuotes() {
        try {
            List<Quote> currentDatabaseQuotes = quoteRepository.getAllQuotes();
            Set<Quote> fetchedQuotes = ZenClient.getSomeUniqueQuotes();
            
            // Remove duplicates by quoteText
            fetchedQuotes.removeAll(new HashSet<>(currentDatabaseQuotes));
            
            // Assign new IDs
            AtomicInteger idGenerator = new AtomicInteger(currentDatabaseQuotes.size() + 1);
            fetchedQuotes.forEach(quote -> quote.setId(idGenerator.getAndIncrement()));
            
            // Save to database
            quoteRepository.saveAll(fetchedQuotes);
            
            int quotesAdded = fetchedQuotes.size();
            int totalQuotes = currentDatabaseQuotes.size() + quotesAdded;
            
            return new QuoteAddResponse(quotesAdded, totalQuotes, "Successfully added " + quotesAdded + " new quotes");
        } catch (IOException e) {
            logger.error("Failed to fetch quotes from ZEN API", e);
            throw new RuntimeException("Failed to fetch quotes: " + e.getMessage());
        }
    }
}
```

#### 1.3 Update Authorization Logic

Add authorization check for admin endpoints (already exists in QuoteHandler).

### Phase 2: Frontend - Quote Management Screen

#### 2.1 Create Quote Management Component

**File:** `quote-lambda-tf-frontend/src/components/QuoteManagementScreen.tsx`

```typescript
interface QuoteWithLikeCount {
  id: number;
  quoteText: string;
  author: string;
  likeCount: number;
}

interface QuotePageResponse {
  quotes: QuoteWithLikeCount[];
  totalCount: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export function QuoteManagementScreen({ onBack }: { onBack: () => void }) {
  const [quotes, setQuotes] = useState<QuoteWithLikeCount[]>([]);
  const [totalCount, setTotalCount] = useState<number>(0);
  const [page, setPage] = useState<number>(1);
  const [pageSize, setPageSize] = useState<number>(50);
  const [totalPages, setTotalPages] = useState<number>(0);
  
  const [quoteTextSearch, setQuoteTextSearch] = useState<string>("");
  const [authorSearch, setAuthorSearch] = useState<string>("");
  const [loading, setLoading] = useState<boolean>(true);
  const [addingQuotes, setAddingQuotes] = useState<boolean>(false);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);
  const [sortBy, setSortBy] = useState<'id' | 'quoteText' | 'author'>('id');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc');

  useEffect(() => {
    loadQuotes();
  }, [page, pageSize, quoteTextSearch, authorSearch, sortBy, sortOrder]);

  const loadQuotes = async () => {
    try {
      setLoading(true);
      const response = await adminApi.getQuotes(page, pageSize, quoteTextSearch, authorSearch, sortBy, sortOrder);
      setQuotes(response.quotes);
      setTotalCount(response.totalCount);
      setTotalPages(response.totalPages);
    } catch (error) {
      console.error('Failed to load quotes:', error);
      showToast('Failed to load quotes', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleAddQuotes = async () => {
    try {
      setAddingQuotes(true);
      const response = await adminApi.fetchAndAddNewQuotes();
      setTotalCount(response.totalQuotes);
      setPage(1); // Reset to first page
      showToast(`Successfully added ${response.quotesAdded} new quotes`, 'success');
      await loadQuotes();
    } catch (error) {
      console.error('Failed to add quotes:', error);
      showToast('Failed to add quotes', 'error');
    } finally {
      setAddingQuotes(false);
    }
  };

  const handlePageSizeChange = (newPageSize: number) => {
    setPageSize(newPageSize);
    setPage(1);
  };

  const handleSort = (column: 'id' | 'quoteText' | 'author') => {
    if (sortBy === column) {
      setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
    } else {
      setSortBy(column);
      setSortOrder('asc');
    }
  };

  const showToast = (message: string, type: 'success' | 'error') => {
    setToast({ message, type });
  };

  return (
    <div className="quote-management-screen">
      <div className="quote-management-header">
        <button className="back-button" onClick={onBack}>
          ← Back
        </button>
        <h2>Manage Quotes</h2>
      </div>

      <div className="quote-count-section">
        <span className="quote-count">Total Quotes: {totalCount.toLocaleString()}</span>
        <button 
          className="add-quotes-button" 
          onClick={handleAddQuotes}
          disabled={addingQuotes}
        >
          {addingQuotes ? 'Adding Quotes...' : 'Add Quotes from ZEN'}
        </button>
      </div>

      <div className="search-section">
        <input
          type="text"
          placeholder="Search by quote text..."
          value={quoteTextSearch}
          onChange={(e) => {
            setQuoteTextSearch(e.target.value);
            setPage(1);
          }}
          className="search-input"
        />
        <input
          type="text"
          placeholder="Search by author..."
          value={authorSearch}
          onChange={(e) => {
            setAuthorSearch(e.target.value);
            setPage(1);
          }}
          className="search-input"
        />
        <button 
          className="clear-search-button"
          onClick={() => {
            setQuoteTextSearch("");
            setAuthorSearch("");
            setPage(1);
          }}
        >
          Clear
        </button>
      </div>

      {loading ? (
        <div className="loading">Loading quotes...</div>
      ) : quotes.length === 0 ? (
        <div className="empty-state">No quotes found.</div>
      ) : (
        <>
          <div className="quotes-table-container">
            <table className="quotes-table">
              <thead>
                <tr>
                  <th onClick={() => handleSort('id')} className="sortable">
                    ID {sortBy === 'id' && (sortOrder === 'asc' ? '↑' : '↓')}
                  </th>
                  <th onClick={() => handleSort('quoteText')} className="sortable">
                    Quote Text {sortBy === 'quoteText' && (sortOrder === 'asc' ? '↑' : '↓')}
                  </th>
                  <th onClick={() => handleSort('author')} className="sortable">
                    Author {sortBy === 'author' && (sortOrder === 'asc' ? '↑' : '↓')}
                  </th>
                  <th>Likes</th>
                </tr>
              </thead>
              <tbody>
                {quotes.map((quote) => (
                  <tr key={quote.id}>
                    <td className="id-cell">{quote.id}</td>
                    <td className="quote-text-cell">{quote.quoteText}</td>
                    <td className="author-cell">{quote.author}</td>
                    <td className="like-count-cell">{quote.likeCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="pagination-section">
            <div className="page-size-selector">
              <label>Quotes per page:</label>
              <select value={pageSize} onChange={(e) => handlePageSizeChange(Number(e.target.value))}>
                <option value={10}>10</option>
                <option value={25}>25</option>
                <option value={50}>50</option>
                <option value={100}>100</option>
                <option value={250}>250</option>
              </select>
            </div>

            <div className="pagination-controls">
              <button 
                onClick={() => setPage(Math.max(1, page - 1))}
                disabled={page === 1}
              >
                Previous
              </button>
              
              <span className="page-info">
                Page {page} of {totalPages}
              </span>
              
              <input
                type="number"
                min="1"
                max={totalPages}
                value={page}
                onChange={(e) => setPage(Math.max(1, Math.min(totalPages, Number(e.target.value))))}
                className="page-input"
              />
              
              <button 
                onClick={() => setPage(Math.min(totalPages, page + 1))}
                disabled={page === totalPages}
              >
                Next
              </button>
            </div>
          </div>
        </>
      )}

      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast(null)}
        />
      )}
    </div>
  );
}
```

### Phase 3: Frontend - API Integration

#### 3.1 Create Admin API Module Extensions

**File:** `quote-lambda-tf-frontend/src/api/adminApi.ts`

Add to existing adminApi:

```typescript
interface QuoteWithLikeCount {
    id: number;
    quoteText: string;
    author: string;
    likeCount: number;
}

interface QuotePageResponse {
    quotes: QuoteWithLikeCount[];
    totalCount: number;
    page: number;
    pageSize: number;
    totalPages: number;
}

interface QuoteAddResponse {
    quotesAdded: number;
    totalQuotes: number;
    message: string;
}

async function getQuotes(
    page: number = 1,
    pageSize: number = 50,
    quoteText?: string,
    author?: string,
    sortBy?: string,
    sortOrder?: string
): Promise<QuotePageResponse> {
    const authHeaders = await getAuthHeaders();
    const params = new URLSearchParams();
    params.append('page', page.toString());
    params.append('pageSize', pageSize.toString());
    if (quoteText) params.append('quoteText', quoteText);
    if (author) params.append('author', author);
    if (sortBy) params.append('sortBy', sortBy);
    if (sortOrder) params.append('sortOrder', sortOrder);

    const response = await fetch(`${BASE_URL}/admin/quotes?${params.toString()}`, {
        method: "GET",
        headers: {
            ...authHeaders,
        },
    });
    
    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Failed to fetch quotes: ${response.status} - ${errorText}`);
    }
    
    return await response.json();
}

async function fetchAndAddNewQuotes(): Promise<QuoteAddResponse> {
    const authHeaders = await getAuthHeaders();
    const response = await fetch(`${BASE_URL}/admin/quotes/fetch`, {
        method: "POST",
        headers: {
            ...authHeaders,
        },
    });
    
    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Failed to add quotes: ${response.status} - ${errorText}`);
    }
    
    return await response.json();
}

export default {
    listUsers,
    addUserToGroup,
    removeUserFromGroup,
    deleteUser,
    getQuotes,
    fetchAndAddNewQuotes,
};
```

### Phase 4: Frontend - App Integration

#### 4.1 Update ManagementScreen Component

Add new navigation option to `ManagementScreen.tsx`:

```typescript
<button
    className="management-menu-item"
    onClick={onNavigateToQuoteManagement}
    disabled={!hasAdminRole}
    title={!hasAdminRole ? 'ADMIN role required' : ''}
>
    Manage Quotes
</button>
```

#### 4.2 Update App.tsx

Update management view state:

```typescript
const [managementView, setManagementView] = useState<'main' | 'favourites' | 'viewed' | 'users' | 'quotes'>('main');
```

Add conditional rendering for quote management:

```typescript
{showManagement ? (
    managementView === 'main' ? (
        <ManagementScreen
            onBack={closeManagement}
            onNavigateToFavourites={() => setManagementView('favourites')}
            onNavigateToViewedQuotes={() => setManagementView('viewed')}
            onNavigateToUserManagement={() => setManagementView('users')}
            onNavigateToQuoteManagement={() => setManagementView('quotes')}
            hasUserRole={hasRole('USER')}
            hasAdminRole={hasRole('ADMIN')}
        />
    ) : managementView === 'favourites' ? (
        <ManageFavouritesScreen onBack={() => setManagementView('main')} />
    ) : managementView === 'viewed' ? (
        <ViewedQuotesScreen onBack={() => setManagementView('main')} />
    ) : managementView === 'users' ? (
        <UserManagementScreen onBack={() => setManagementView('main')} />
    ) : (
        <QuoteManagementScreen onBack={() => setManagementView('main')} />
    )
) : ...
```

### Phase 5: Styling

#### 5.1 Create CSS File

**File:** `quote-lambda-tf-frontend/src/components/QuoteManagementScreen.css`

```css
.quote-management-screen {
    position: absolute;
    top: 0;
    left: 141px;
    width: auto;
    max-width: 1400px;
    height: 100vh;
    background-color: white;
    display: flex;
    flex-direction: column;
    padding: 20px;
    box-sizing: border-box;
    overflow: hidden;
}

.quote-management-header {
    display: flex;
    align-items: center;
    gap: 20px;
    margin-bottom: 20px;
    flex-shrink: 0;
}

.quote-management-header h2 {
    margin: 0;
    color: green;
    font-size: 28px;
}

.quote-count-section {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 20px;
    padding: 15px;
    background-color: #f0f8f0;
    border-radius: 4px;
    flex-shrink: 0;
}

.quote-count {
    font-size: 18px;
    font-weight: 600;
    color: green;
}

.add-quotes-button {
    padding: 10px 20px;
    background-color: green;
    color: white;
    border: none;
    border-radius: 4px;
    cursor: pointer;
    font-size: 14px;
    font-weight: 500;
    transition: background-color 0.2s;
}

.add-quotes-button:hover:not(:disabled) {
    background-color: darkgreen;
}

.add-quotes-button:disabled {
    background-color: #ccc;
    cursor: not-allowed;
    opacity: 0.6;
}

.search-section {
    display: flex;
    gap: 10px;
    margin-bottom: 20px;
    flex-shrink: 0;
}

.search-input {
    flex: 1;
    padding: 10px;
    border: 1px solid #ddd;
    border-radius: 4px;
    font-size: 14px;
}

.search-input:focus {
    outline: none;
    border-color: green;
    box-shadow: 0 0 5px rgba(0, 128, 0, 0.3);
}

.clear-search-button {
    padding: 10px 15px;
    background-color: #f0f0f0;
    border: 1px solid #ddd;
    border-radius: 4px;
    cursor: pointer;
    font-size: 14px;
    transition: background-color 0.2s;
}

.clear-search-button:hover {
    background-color: #e0e0e0;
}

.quotes-table-container {
    flex: 1;
    overflow-y: auto;
    overflow-x: auto;
    border: 1px solid #ddd;
    border-radius: 4px;
    margin-bottom: 20px;
}

.quotes-table {
    width: auto;
    border-collapse: collapse;
    font-size: 14px;
}

.quotes-table th,
.quotes-table td {
    padding: 12px;
    text-align: left;
    border-bottom: 1px solid #ddd;
    white-space: nowrap;
}

.quotes-table th {
    background-color: #f8f9fa;
    font-weight: 600;
    color: green;
    position: sticky;
    top: 0;
}

.quotes-table th.sortable {
    cursor: pointer;
    user-select: none;
}

.quotes-table th.sortable:hover {
    background-color: #e8f5e9;
}

.quotes-table tbody tr:hover {
    background-color: #f5f5f5;
}

.id-cell {
    text-align: center;
    font-weight: 500;
}

.quote-text-cell {
    max-width: 400px;
    white-space: normal;
    word-wrap: break-word;
}

.author-cell {
    font-style: italic;
    color: #666;
}

.like-count-cell {
    text-align: center;
}

.pagination-section {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 15px;
    background-color: #f8f9fa;
    border-radius: 4px;
    flex-shrink: 0;
}

.page-size-selector {
    display: flex;
    align-items: center;
    gap: 10px;
}

.page-size-selector label {
    font-weight: 500;
}

.page-size-selector select {
    padding: 6px 10px;
    border: 1px solid #ddd;
    border-radius: 4px;
    font-size: 14px;
}

.pagination-controls {
    display: flex;
    align-items: center;
    gap: 10px;
}

.pagination-controls button {
    padding: 8px 12px;
    background-color: white;
    border: 1px solid #ddd;
    border-radius: 4px;
    cursor: pointer;
    font-size: 14px;
    transition: background-color 0.2s;
}

.pagination-controls button:hover:not(:disabled) {
    background-color: #e8f5e9;
}

.pagination-controls button:disabled {
    background-color: #f0f0f0;
    cursor: not-allowed;
    opacity: 0.5;
}

.page-info {
    font-weight: 500;
    min-width: 120px;
    text-align: center;
}

.page-input {
    width: 60px;
    padding: 6px;
    border: 1px solid #ddd;
    border-radius: 4px;
    font-size: 14px;
    text-align: center;
}

.page-input:focus {
    outline: none;
    border-color: green;
}

.loading {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 300px;
    font-size: 16px;
    color: #666;
}

.empty-state {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 300px;
    font-size: 16px;
    color: #999;
}

@media (max-width: 1024px) {
    .quote-management-screen {
        max-width: 100%;
        padding: 12px;
    }

    .quote-count-section {
        flex-direction: column;
        align-items: flex-start;
        gap: 10px;
    }

    .search-section {
        flex-direction: column;
    }

    .quotes-table {
        font-size: 12px;
    }

    .quotes-table th,
    .quotes-table td {
        padding: 8px;
    }

    .quote-text-cell {
        max-width: 250px;
    }
}

@media (max-width: 768px) {
    .quote-management-screen {
        left: 0;
        width: 100vw;
        padding: 10px;
    }

    .quote-management-header h2 {
        font-size: 20px;
    }

    .quote-count {
        font-size: 14px;
    }

    .quotes-table {
        font-size: 11px;
    }

    .quotes-table th,
    .quotes-table td {
        padding: 6px;
    }

    .quote-text-cell {
        max-width: 150px;
    }

    .pagination-section {
        flex-direction: column;
        gap: 10px;
    }

    .pagination-controls {
        width: 100%;
        justify-content: space-between;
    }
}
```

### Phase 6: Testing

#### 6.1 Backend Testing
- Test list quotes endpoint returns all quotes with correct pagination
- Test search by quote text (case-insensitive, partial match)
- Test search by author (case-insensitive, partial match)
- Test combined search (AND logic)
- Test add quotes endpoint fetches from ZEN and adds unique quotes
- Test duplicate prevention (same logic as getQuote method)
- Test authorization (only ADMINs can access)
- Test with non-existent search terms
- Test pagination with various page sizes
- Test sorting by different columns

#### 6.2 Frontend Testing
- Test quote list loads correctly
- Test search by quote text updates results
- Test search by author updates results
- Test combined search works correctly
- Test clear search button resets both fields
- Test add quotes button fetches and adds new quotes
- Test quote count updates after adding quotes
- Test pagination navigation (previous/next)
- Test page size selector updates table
- Test jump to page input
- Test sorting by clicking column headers
- Test with different user roles (ADMIN vs non-ADMIN)
- Test loading states
- Test error handling

#### 6.3 Integration Testing
- Test full flow: Search quotes → Add new quotes → Count updates
- Test full flow: Change page size → Navigate pages
- Test full flow: Sort by column → Search → Pagination
- Test edge case: No search results
- Test edge case: All quotes already in database (no new quotes added)
- Test edge case: ZEN API fails
- Test edge case: Database save fails

## Technical Considerations

### Backend
1. **Quote Comparison**: Use same logic as `QuoteService.getQuote()` method
   - Compare by `quoteText` only
   - Use HashSet equality for comparison
   - Remove duplicates before saving

2. **Pagination**: Implement server-side pagination
   - Calculate total pages based on total count and page size
   - Return only requested page of quotes
   - Include like count for each quote

3. **Search**: Implement case-insensitive partial matching
   - Use `.toLowerCase().contains()` for search
   - Apply filters on backend for performance
   - Support AND logic when both search fields are used

4. **Sorting**: Support sorting by ID, quote text, and author
   - Default sort by ID ascending
   - Allow toggle between ascending/descending

5. **Like Count**: Query like count for each quote
   - Use `UserLikeRepository.getLikeCountForQuote()`
   - Include in response

### Frontend
1. **State Management**: Manage pagination, search, and sort state
   - Page number
   - Page size
   - Search terms (quote text and author)
   - Sort column and order

2. **Search Debouncing**: Debounce search input (500ms)
   - Avoid excessive API calls while typing
   - Reset to page 1 when search changes

3. **Optimistic Updates**: Update quote count immediately
   - Show new count after successful add
   - Rollback on error

4. **Error Handling**: Clear error messages for common scenarios
   - Network errors
   - Authorization errors
   - ZEN API failures
   - Database errors

5. **Responsive Design**: Table should work on different screen sizes
   - Horizontal scrolling for table
   - Stack controls on mobile
   - Adjust font sizes for smaller screens

## API Endpoints Summary

### New Endpoints
- `GET /admin/quotes` - List all quotes with pagination and search (ADMIN only)
- `POST /admin/quotes/fetch` - Fetch and add new quotes from ZEN API (ADMIN only)

### Query Parameters for GET /admin/quotes
- `page` (optional, default 1): page number
- `pageSize` (optional, default 50): quotes per page
- `quoteText` (optional): search in quote text
- `author` (optional): search by author
- `sortBy` (optional, default 'id'): sort column
- `sortOrder` (optional, default 'asc'): sort order

## Security Considerations

### Access Control
- **ADMIN role required**: All admin endpoints require ADMIN group membership
- **JWT validation**: All requests validated via Cognito authorizer
- **Authorization check**: Verify ADMIN role on all endpoints

### Audit Trail
- **CloudWatch Logs**: Log all admin operations
- **Structured logging**: Use JSON format for all log entries
- **Log fields**: 
  - `timestamp` (ISO 8601)
  - `level` (INFO, WARN, ERROR)
  - `event` (quotes_list, quotes_fetch, etc.)
  - `requestingUser` (username of admin)
  - `action` (list, fetch, search)
  - `result` (success, failure)
  - `errorMessage` (if result is failure)

### Error Handling
- **Sensitive information**: Do not expose internal errors to frontend
- **Generic errors**: Return generic error messages for security
- **Detailed logging**: Log detailed errors server-side for debugging

## Acceptance Criteria

### Quote Management Screen
- [ ] ADMIN users can view list of all quotes
- [ ] Each quote shows ID, text, author, and like count
- [ ] Search by quote text works (case-insensitive, partial match)
- [ ] Search by author works (case-insensitive, partial match)
- [ ] Combined search works (AND logic)
- [ ] Clear search button resets both fields
- [ ] Total quote count displays at top
- [ ] Add Quotes button fetches from ZEN API
- [ ] New quotes are added to database
- [ ] Duplicate quotes are not added (same logic as getQuote)
- [ ] Quote count updates after adding quotes
- [ ] Table is pageable with default 50 quotes per page
- [ ] Page size can be changed (10, 25, 50, 100, 250)
- [ ] Pagination navigation works (previous/next)
- [ ] Jump to page input works
- [ ] Sorting by columns works (ID, text, author)
- [ ] Sorting toggle between ascending/descending works
- [ ] Back button returns to Management screen
- [ ] Loading states display correctly
- [ ] Error messages display correctly
- [ ] Toast notifications for success/error

### Navigation
- [ ] Manage Quotes option appears in Management screen
- [ ] Manage Quotes is only enabled for ADMIN role
- [ ] Non-ADMIN users see disabled button with tooltip
- [ ] Navigation flow works correctly

### Authorization
- [ ] Only ADMIN users can access admin endpoints
- [ ] Non-ADMIN users get 403 Forbidden

## Estimated Effort

- **Backend**: 3-4 days
  - Create QuoteManagementService: 1 day
  - Create API endpoints: 1 day
  - Testing: 1-2 days

- **Frontend**: 3-4 days
  - Create QuoteManagementScreen component: 1.5 days
  - Create API integration: 0.5 days
  - Styling and responsive design: 1 day
  - Testing: 1 day

- **Total**: 6-8 days

## Dependencies

- Backend: Existing QuoteRepository, QuoteService, ZenClient, UserLikeRepository
- Frontend: Existing ManagementScreen, Toast component, adminApi module
- External: ZEN Quotes API (https://zenquotes.io/api/quotes)
