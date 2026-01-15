# Azure Backend Quote API Endpoints

This document describes all the API endpoints required by the frontend application based on the `quoteApi.ts` analysis.

## Base URL
```
https://quote-backend-function.azurewebsites.net/api
```

## Authentication
- All endpoints marked as **Authenticated** require an `Authorization` header with a JWT token
- Unauthenticated endpoints work without authentication but may have limited functionality

## Endpoints

### 1. Basic Quote Operations

#### GET /quote
- **Description**: Get a random quote (unauthenticated)
- **Authentication**: None
- **Response**: `Quote` object
- **Frontend Usage**: `getQuote()`
- **Current Status**: ✅ Implemented

#### POST /quote
- **Description**: Get a unique quote excluding specified IDs
- **Authentication**: None
- **Request Body**: `number[]` - Array of quote IDs to exclude
- **Response**: `Quote` object
- **Frontend Usage**: `getUniqueQuote(receivedQuotes)`
- **Current Status**: ✅ Implemented

#### GET /quote/{id}
- **Description**: Get a specific quote by ID
- **Authentication**: Optional (enhanced with auth if available)
- **Parameters**: `id` (number) - Quote ID
- **Response**: `Quote` object
- **Frontend Usage**: `getQuoteById(quoteId)`
- **Current Status**: ✅ Implemented

#### GET /quote/random
- **Description**: Get a random quote (dedicated endpoint)
- **Authentication**: None
- **Response**: `Quote` object
- **Current Status**: ✅ Implemented

### 2. Like/Unlike Operations

#### POST /quote/{id}/like
- **Description**: Like a specific quote
- **Authentication**: Required
- **Parameters**: `id` (number) - Quote ID
- **Response**: Updated `Quote` object
- **Frontend Usage**: `likeQuote(quote)`
- **Current Status**: ✅ Implemented

#### DELETE /quote/{id}/unlike
- **Description**: Unlike a specific quote
- **Authentication**: Required
- **Parameters**: `id` (number) - Quote ID
- **Response**: 204 No Content
- **Frontend Usage**: `unlikeQuote(quoteId)`
- **Current Status**: ✅ Implemented

#### GET /quote/liked
- **Description**: Get user's liked quotes
- **Authentication**: Required
- **Response**: `Quote[]` array
- **Frontend Usage**: `getLikedQuotes()`
- **Current Status**: ✅ Implemented

#### PUT /quote/{id}/reorder
- **Description**: Reorder a liked quote
- **Authentication**: Required
- **Parameters**: `id` (number) - Quote ID
- **Request Body**: `{ order: number }`
- **Response**: 204 No Content
- **Frontend Usage**: `reorderLikedQuote(quoteId, order)`
- **Current Status**: ❌ Not Implemented

### 3. Sequential Navigation

#### POST /quote/next
- **Description**: Get next quote (unauthenticated)
- **Authentication**: None
- **Request Body**: `{ currentQuoteId: number }`
- **Response**: `Quote` object
- **Frontend Usage**: `getNextQuote(currentQuoteId)`
- **Current Status**: ❌ Not Implemented

#### GET /quote/{id}/next
- **Description**: Get next quote in sequence (authenticated)
- **Authentication**: Required
- **Parameters**: `id` (number) - Current quote ID
- **Response**: `Quote` object
- **Frontend Usage**: `getNextAuthenticatedQuote(currentQuoteId)`
- **Current Status**: ❌ Not Implemented

#### GET /quote/{id}/previous
- **Description**: Get previous quote in sequence
- **Authentication**: Required
- **Parameters**: `id` (number) - Current quote ID
- **Response**: `Quote` object
- **Frontend Usage**: `getPreviousQuote(currentQuoteId)`
- **Current Status**: ❌ Not Implemented

### 4. User Progress & History

#### GET /quote/progress
- **Description**: Get user's reading progress
- **Authentication**: Required
- **Response**: `{ lastQuoteId: number; username: string; updatedAt: number }`
- **Frontend Usage**: `getUserProgress()`
- **Current Status**: ❌ Not Implemented

#### GET /quote/viewed
- **Description**: Get all viewed quotes (1 to lastQuoteId)
- **Authentication**: Required
- **Response**: `Quote[]` array
- **Frontend Usage**: `getViewedQuotes()`
- **Current Status**: ❌ Not Implemented

#### DELETE /quote/viewed
- **Description**: Delete all viewed quotes history
- **Authentication**: Required
- **Response**: 204 No Content
- **Frontend Usage**: `deleteAllViewedQuotes()`
- **Current Status**: ❌ Not Implemented

### 4. External API Integration

*(No external API endpoints are currently used by the frontend)*

## Data Models

### Quote
```typescript
interface Quote {
  id: number;
  quoteText: string;
  author: string;
  likeCount: number;
  createdAt: string;
  source: string;
}
```

### User Progress
```typescript
interface UserProgress {
  lastQuoteId: number;
  username: string;
  updatedAt: number;
}
```

## Implementation Status

### ✅ Implemented (7/16)
- GET /quotes
- GET /quote/random
- GET /quote/{id}
- GET /quote (unauthenticated random)
- POST /quote (unique quote)
- POST /quote/{id}/like
- DELETE /quote/{id}/unlike
- GET /quote/liked

### ❌ Not Implemented (9/16)
- PUT /quote/{id}/reorder
- POST /quote/next
- GET /quote/{id}/next
- GET /quote/{id}/previous
- GET /quote/progress
- GET /quote/viewed
- DELETE /quote/viewed
- GET /quote (authenticated with view tracking)

## Priority Implementation Order

### High Priority (Core Functionality)
1. POST /quote/{id}/like - Basic like functionality
2. GET /quote/liked - View liked quotes
3. DELETE /quote/{id}/unlike - Unlike functionality

### Medium Priority (Enhanced Features)
4. GET /quote/progress - User progress tracking
5. GET /quote/viewed - View history
6. POST /quote/next - Sequential navigation

### Low Priority (Advanced Features)
7. PUT /quote/{id}/reorder - Quote reordering
8. GET /quote/{id}/next - Authenticated sequential navigation
9. GET /quote/{id}/previous - Previous quote navigation
10. DELETE /quote/viewed - Clear history
11. GET /quote (authenticated) - View tracking
12. POST /quote (unique) - Smart quote selection

## Notes

- The frontend uses AWS Cognito for authentication via JWT tokens
- All endpoints support retry logic with exponential backoff
- The frontend shows backend restart notifications on failures
- Some endpoints have both authenticated and unauthenticated variants
- The BASE_URL is configured in the frontend constants file
