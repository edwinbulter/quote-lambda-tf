# ECS-30-5: Show Restarting Backend in Frontend

## Problem Description

Currently, when the backend is restarting or temporarily unavailable, management screens show generic "Failed to fetch" error popups instead of the user-friendly "Backend is restarting" notification that appears for the "New Quote" functionality. This creates inconsistent user experience across different parts of the application.

### Current Behavior

**Working (New Quote):**
- Shows "Backend is restarting (attempt X)..." notification
- Displays loading spinner with helpful messaging
- Automatically retries with exponential backoff
- Provides clear indication that the system is temporarily unavailable

**Broken (Management Screens):**
- Shows generic "Failed to fetch" error popup
- No indication that backend is restarting
- Users may think the system is permanently broken
- Inconsistent error handling across the application

### Error Analysis

The console logs show the following errors during backend restarts:
```
GET https://sy5vvqbh93.execute-api.eu-central-1.amazonaws.com//api/v1/quote net::ERR_FAILED

quoteApi.ts:119 Retrying getAuthenticatedQuote (attempt 4)... TypeError: Failed to fetch
    at quoteApi.ts:104:36
    at withRetry (apiRetry.ts:35:20)
    at async fetchNextQuote (App.tsx:160:35)
    at async newQuote (App.tsx:178:9)

Access to fetch at 'https://sy5vvqbh93.execute-api.eu-central-1.amazonaws.com//api/v1/quote' from origin 'http://localhost:5173' has been blocked by CORS policy: Response to preflight request doesn't pass access control check: It does not have HTTP ok status.
```

## Root Cause Analysis

### Inconsistent Error Handling Implementation

**Functions WITH Proper Backend Restart Handling:**
- `getAuthenticatedQuote()` - Uses `withRetry()` with `notifyBackendRestart()`
- `getUniqueQuote()` - Uses `withRetry()` with `notifyBackendRestart()`

**Functions WITHOUT Backend Restart Handling:**
- `likeQuote()` - Basic error throwing, no retry logic
- `unlikeQuote()` - Basic error throwing, no retry logic
- `getLikedQuotes()` - Basic error throwing, no retry logic
- `reorderLikedQuote()` - Basic error throwing, no retry logic
- `getQuoteById()` - Basic error throwing, no retry logic
- `getPreviousQuote()` - Basic error throwing, no retry logic
- `getNextQuote()` - Basic error throwing, no retry logic
- `getUserProgress()` - Basic error throwing, no retry logic
- `getViewedQuotes()` - Basic error throwing, no retry logic

**All Admin API Functions:**
- `listUsers()` - Basic error throwing, no retry logic
- `addUserToGroup()` - Basic error throwing, no retry logic
- `removeUserFromGroup()` - Basic error throwing, no retry logic
- `deleteUser()` - Basic error throwing, no retry logic
- `getQuotes()` - Basic error throwing, no retry logic
- `fetchAndAddNewQuotes()` - Basic error throwing, no retry logic
- `getTotalLikes()` - Basic error throwing, no retry logic

### Technical Issues

1. **Network Error Detection**: The `withRetry()` utility properly detects network errors and 500 status codes, but most API functions don't use it
2. **CORS Issues**: During backend restarts, CORS preflight requests fail, which should be treated as temporary unavailability
3. **User Experience**: Generic error messages don't communicate the temporary nature of the issue

## Solution

### Phase 1: Apply Retry Logic to All Quote API Functions

**Target Functions in `quoteApi.ts`:**
```typescript
// Functions that need retry logic added:
async function likeQuote(quote: Quote): Promise<Quote> {
    const authHeaders = await getAuthHeaders();
    
    return withRetry(
        async () => {
            const response = await fetch(`${BASE_URL}/quote/${quote.id}/like`, {
                method: "POST",
                headers: {
                    'Content-Type': 'application/json',
                    ...authHeaders,
                },
            });
            
            if (!response.ok) {
                throw new Error(`Failed to like quote: ${response.status} ${response.statusText}`);
            }
            
            return await response.json();
        },
        {
            onRetry: (attempt, error) => {
                console.log(`Retrying likeQuote (attempt ${attempt})...`, error);
                notifyBackendRestart(true, attempt);
            }
        }
    ).finally(() => {
        notifyBackendRestart(false);
    });
}

// Apply similar pattern to:
// - unlikeQuote()
// - getLikedQuotes() 
// - reorderLikedQuote()
// - getQuoteById()
// - getPreviousQuote()
// - getNextQuote()
// - getUserProgress()
// - getViewedQuotes()
```

### Phase 2: Apply Retry Logic to Admin API Functions

**Target Functions in `adminApi.ts`:**
```typescript
// Import retry utilities
import { withRetry } from '../utils/apiRetry';
import { notifyBackendRestart } from '../components/BackendRestartNotification';

// Apply retry pattern to all admin functions:
async function listUsers(): Promise<UserInfo[]> {
    const authHeaders = await getAuthHeaders();
    
    return withRetry(
        async () => {
            const response = await fetch(`${BASE_URL}/admin/users`, {
                method: "GET",
                headers: {
                    ...authHeaders,
                },
            });
            
            if (!response.ok) {
                throw new Error(`Failed to fetch users: ${response.status} ${response.statusText}`);
            }
            
            return await response.json();
        },
        {
            onRetry: (attempt, error) => {
                console.log(`Retrying listUsers (attempt ${attempt})...`, error);
                notifyBackendRestart(true, attempt);
            }
        }
    ).finally(() => {
        notifyBackendRestart(false);
    });
}

// Apply similar pattern to all other admin functions
```

### Phase 3: Enhanced Error Detection

**Update `apiRetry.ts` to better handle CORS and network errors:**
```typescript
// Enhanced error detection logic
const isServerError = error.message.includes('500') || 
                    error.message.includes('Internal Server Error') ||
                    error.message.includes('Failed to fetch') ||
                    error.message.includes('Network request failed') ||
                    error.message.includes('CORS') ||
                    error.message.includes('blocked by CORS policy') ||
                    error.message.includes('ERR_FAILED') ||
                    error.message.includes('ERR_CONNECTION_REFUSED');
```

### Phase 4: Frontend Integration

**Ensure BackendRestartNotification component is used in management screens:**
```typescript
// In management screen components:
import { useBackendRestartNotification } from '../components/BackendRestartNotification';
import { BackendRestartNotification } from '../components/BackendRestartNotification';

const ManagementScreen = () => {
    const { isOpen, retryCount } = useBackendRestartNotification();
    
    return (
        <div>
            <BackendRestartNotification isOpen={isOpen} retryCount={retryCount} />
            {/* Rest of component */}
        </div>
    );
};
```

## Implementation Steps

### Step 1: Update Quote API Functions
1. Modify all functions in `quoteApi.ts` (except `getQuote()`) to use `withRetry()`
2. Add `notifyBackendRestart()` calls with proper error handling
3. Ensure consistent error message formatting

### Step 2: Update Admin API Functions  
1. Import retry utilities into `adminApi.ts`
2. Apply retry pattern to all admin functions
3. Add proper error handling and user feedback

### Step 3: Enhance Error Detection
1. Update `apiRetry.ts` to handle CORS errors
2. Improve network error detection
3. Add better logging for debugging

### Step 4: Frontend Component Updates
1. Ensure all management screens use `BackendRestartNotification`
2. Add notification components where missing
3. Test error scenarios

### Step 5: Testing and Validation
1. Test backend restart scenarios
2. Verify consistent user experience
3. Test network connectivity issues
4. Validate error handling in all screens

## Expected Benefits

### User Experience Improvements
- **Consistent Messaging**: All screens show "Backend is restarting" instead of generic errors
- **Reduced Confusion**: Users understand the issue is temporary
- **Better Reliability**: Automatic retry reduces manual refresh needs
- **Professional Appearance**: Consistent error handling across application

### Technical Benefits
- **Unified Error Handling**: Single source of truth for retry logic
- **Better Resilience**: Automatic recovery from temporary issues
- **Improved Debugging**: Better logging and error tracking
- **Maintainability**: Centralized retry logic

### Cost and Performance Benefits
- **Reduced Support**: Fewer user reports of "broken" functionality
- **Better Resource Usage**: Intelligent retry with exponential backoff
- **Improved Monitoring**: Better visibility into backend issues

## Testing Strategy

### Unit Tests
- Test retry logic with mock failures
- Verify notification component behavior
- Test error detection logic

### Integration Tests  
- Test backend restart scenarios
- Verify CORS error handling
- Test network connectivity issues

### User Acceptance Tests
- Test user experience during backend restarts
- Verify consistent messaging across screens
- Test automatic recovery scenarios

## Implementation Timeline

**Phase 1**: Quote API Functions - 2-3 hours
**Phase 2**: Admin API Functions - 2-3 hours  
**Phase 3**: Enhanced Error Detection - 1 hour
**Phase 4**: Frontend Integration - 1-2 hours
**Phase 5**: Testing and Validation - 2-3 hours

**Total Estimated Time**: 8-12 hours

## Success Criteria

1. ✅ All API functions use consistent retry logic
2. ✅ Backend restarts show user-friendly notifications across all screens
3. ✅ No more "Failed to fetch" popups during temporary unavailability
4. ✅ Automatic recovery works for all management functions
5. ✅ Consistent user experience across the entire application
6. ✅ All tests pass including error scenarios

## Risk Mitigation

### Potential Issues
- **Breaking Changes**: Ensure existing error handling still works
- **Performance Impact**: Monitor retry behavior doesn't cause excessive requests
- **UI Conflicts**: Ensure notifications don't interfere with other UI elements

### Mitigation Strategies
- **Gradual Rollout**: Implement in phases starting with quote functions
- **Comprehensive Testing**: Thorough testing of error scenarios
- **Fallback Options**: Maintain basic error handling as fallback
- **Monitoring**: Add logging to track retry behavior and success rates
