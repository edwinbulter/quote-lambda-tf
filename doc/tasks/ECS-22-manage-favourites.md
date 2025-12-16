# ECS-22: Manage Favourites and Viewed Quotes

## Table of Contents
- [Overview](#overview)
- [User Stories](#user-stories)
  - [US-1: Manage Favourites](#us-1-manage-favourites)
  - [US-2: View and Like Viewed Quotes](#us-2-view-and-like-viewed-quotes)
- [Requirements](#requirements)
  - [Navigation Structure](#navigation-structure)
  - [Manage Favourites Screen](#manage-favourites-screen)
  - [Viewed Quotes Screen](#viewed-quotes-screen)
- [Implementation Steps](#implementation-steps)
  - [Phase 1: Backend Updates](#phase-1-backend-updates)
  - [Phase 2: Frontend - Management Screen Structure](#phase-2-frontend---management-screen-structure)
  - [Phase 3: Frontend - API Integration](#phase-3-frontend---api-integration)
  - [Phase 4: Frontend - App Integration](#phase-4-frontend---app-integration)
  - [Phase 5: Styling](#phase-5-styling)
  - [Phase 6: Testing](#phase-6-testing)
- [Technical Considerations](#technical-considerations)
- [API Endpoints Summary](#api-endpoints-summary)
- [Database Schema Changes](#database-schema-changes)
- [Acceptance Criteria](#acceptance-criteria)
- [Estimated Effort](#estimated-effort)
- [Dependencies](#dependencies)
- [Future Enhancements](#future-enhancements)

## Overview
Implement management screens for user favourites and viewed quotes, accessible through a new Management interface in the sidepanel.

## User Stories

### US-1: Manage Favourites
**As a** signed-in user with USER role  
**I want to** manage my favourite quotes  
**So that** I can delete unwanted favourites and reorder them according to my preference

### US-2: View and Like Viewed Quotes
**As a** signed-in user with USER role  
**I want to** see my quote viewing history  
**So that** I can like quotes I've previously viewed

## Requirements

### Navigation Structure
```
Sidepanel
└── Manage Button (enabled only when signed in)
    └── Management Screen
        ├── Manage Favourites (enabled only for USER role)
        └── Viewed Quotes (enabled only for USER role)
```

### Manage Favourites Screen

#### Features
1. **Display favourites table** with columns:
   - Quote text
   - Author
   - Order controls (up/down arrows or drag-and-drop)
   - Delete button

2. **Delete functionality**
   - Delete button for each favourite
   - Confirmation dialog before deletion
   - Update backend to remove like

3. **Reorder functionality**
   - Up/Down arrow buttons OR drag-and-drop interface
   - Persist order in backend (requires new `order` field in user-likes table)
   - Real-time visual feedback

4. **Navigation**
   - Back button to return to Management screen
   - Screen uses full space of quoteview and favourites component area

#### Access Control
- Only visible to authenticated users
- Only enabled for users with USER role
- Show appropriate message if user has no favourites

### Viewed Quotes Screen

#### Features
1. **Display viewed quotes table** with columns:
   - Quote text
   - Author
   - Viewed at (timestamp)
   - Like toggle button

2. **Like/Unlike functionality**
   - Toggle button for each quote (like/unlike)
   - Button states:
     - **Liked**: Filled/highlighted button with "Unlike" label or heart icon
     - **Not liked**: Empty/outline button with "Like" label or heart icon
   - Single click toggles between like and unlike
   - Updates backend immediately (optimistic update)
   - Real-time visual feedback (spinner during save, checkmark on success)
   - Error handling with retry option if save fails

3. **Sorting**
   - Default sort: viewedAt descending (most recent first)
   - Optional: Allow sorting by other columns

4. **Navigation**
   - Back button to return to Management screen
   - Screen uses full space of quoteview and favourites component area

#### Access Control
- Only visible to authenticated users
- Only enabled for users with USER role
- Show appropriate message if user has no viewed quotes

## Implementation Steps

### Phase 1: Backend Updates

#### 1.1 Add Order Field to User Likes Table
**File:** `quote-lambda-tf-backend/infrastructure/dynamodb.tf`

- Add optional `order` attribute to user_likes_table (Number type)
- No schema change needed (DynamoDB is schemaless for non-key attributes)

#### 1.2 Update UserLike Model
**File:** `quote-lambda-tf-backend/src/main/java/ebulter/quote/lambda/model/UserLike.java`

```java
private Integer order; // Add this field with getter/setter
```

#### 1.3 Update Like Endpoint to Set Initial Order
**File:** `quote-lambda-tf-backend/src/main/java/ebulter/quote/lambda/QuoteHandler.java`

When a user likes a quote, automatically assign an order value:
- Query user's existing likes to find the highest order value
- Set new like's order to `maxOrder + 1`
- If user has no likes yet, set order to `1`

```java
// When liking a quote
int maxOrder = userLikeRepository.getMaxOrderForUser(username);
userLike.setOrder(maxOrder + 1);
userLikeRepository.save(userLike);
```

This ensures new likes are automatically added to the end of the favourites list.

#### 1.4 Create Reorder Endpoint
**File:** `quote-lambda-tf-backend/src/main/java/ebulter/quote/lambda/QuoteHandler.java`

Add new endpoint: `PUT /quote/{id}/reorder`
- Accepts: `{ "order": number }`
- Updates the order field for the user's like
- Requires authentication and USER role

#### 1.5 Update Get Liked Quotes Endpoint
**File:** `quote-lambda-tf-backend/src/main/java/ebulter/quote/lambda/service/QuoteService.java`

Modify `getLikedQuotesByUser()` to:
- Return quotes sorted by `order` field (ascending)
- Handle null order values (put at end)

#### 1.6 Update Unlike Endpoint
**File:** `quote-lambda-tf-backend/src/main/java/ebulter/quote/lambda/QuoteHandler.java`

Verify `DELETE /quote/{id}/unlike` endpoint exists and works correctly

#### 1.7 Add Helper Method to UserLikeRepository
**File:** `quote-lambda-tf-backend/src/main/java/ebulter/quote/lambda/repository/UserLikeRepository.java`

Add method to find the maximum order for a user:
```java
public int getMaxOrderForUser(String username) {
    // Query all likes for user and find max order
    // Return 0 if no likes exist (so first like gets order 1)
}
```

### Phase 2: Frontend - Management Screen Structure

#### 2.1 Create Management Screen Component
**File:** `quote-lambda-tf-frontend/src/components/ManagementScreen.tsx`

```typescript
interface ManagementScreenProps {
  onBack: () => void;
  onNavigateToFavourites: () => void;
  onNavigateToViewedQuotes: () => void;
  hasUserRole: boolean;
}
```

Features:
- Display two navigation links
- Enable/disable based on USER role
- Handle navigation to sub-screens

#### 2.2 Create Manage Favourites Component
**File:** `quote-lambda-tf-frontend/src/components/ManageFavouritesScreen.tsx`

```typescript
interface ManageFavouritesScreenProps {
  onBack: () => void;
  username: string;
}
```

Features:
- Fetch liked quotes from API
- Display in table format
- Implement delete functionality
- Implement reorder functionality (up/down arrows or drag-and-drop)
- Back button to Management screen

#### 2.3 Create Viewed Quotes Component
**File:** `quote-lambda-tf-frontend/src/components/ViewedQuotesScreen.tsx`

```typescript
interface ViewedQuotesScreenProps {
  onBack: () => void;
  username: string;
}
```

Features:
- Fetch viewed quotes from API (already exists: `/quote/history`)
- Fetch liked quotes to determine toggle state
- Display in table format sorted by viewedAt (descending)
- Toggle button for each quote (like/unlike)
- Button visual states:
  - Filled/highlighted when liked
  - Empty/outline when not liked
- Real-time feedback during toggle (spinner, success checkmark)
- Back button to Management screen

### Phase 3: Frontend - API Integration

#### 3.1 Add Reorder API Function
**File:** `quote-lambda-tf-frontend/src/api/quoteApi.ts`

```typescript
async function reorderLikedQuote(quoteId: number, order: number): Promise<void>
```

#### 3.2 Add Toggle Like API Function
**File:** `quote-lambda-tf-frontend/src/api/quoteApi.ts`

```typescript
async function toggleLikeQuote(quoteId: number, isCurrentlyLiked: boolean): Promise<void>
```

This function will:
- Call `likeQuote()` if quote is not currently liked
- Call `unlikeQuote()` if quote is currently liked
- Return the new like state

#### 3.3 Update getLikedQuotes
Ensure it returns quotes in the correct order (backend should handle this)

### Phase 4: Frontend - App Integration

#### 4.1 Update App.tsx State
**File:** `quote-lambda-tf-frontend/src/App.tsx`

Add state for:
```typescript
const [showManagement, setShowManagement] = useState(false);
const [managementView, setManagementView] = useState<'main' | 'favourites' | 'viewed'>('main');
```

#### 4.2 Add Manage Button to Sidepanel
**File:** `quote-lambda-tf-frontend/src/App.tsx`

Add button in sidepanel:
- Label: "Manage"
- Enabled only when `isAuthenticated === true`
- onClick: `setShowManagement(true)` and `setManagementView('main')`

#### 4.3 Conditional Rendering
**File:** `quote-lambda-tf-frontend/src/App.tsx`

Replace quoteview and favourites component area with:
```typescript
{showManagement ? (
  managementView === 'main' ? (
    <ManagementScreen
      onBack={() => setShowManagement(false)}
      onNavigateToFavourites={() => setManagementView('favourites')}
      onNavigateToViewedQuotes={() => setManagementView('viewed')}
      hasUserRole={hasRole('USER')}
    />
  ) : managementView === 'favourites' ? (
    <ManageFavouritesScreen
      onBack={() => setManagementView('main')}
      username={username}
    />
  ) : (
    <ViewedQuotesScreen
      onBack={() => setManagementView('main')}
      username={username}
    />
  )
) : (
  // Existing quoteview and favourites components
)}
```

### Phase 5: Styling

#### 5.1 Create CSS Files
- `ManagementScreen.css`
- `ManageFavouritesScreen.css`
- `ViewedQuotesScreen.css`

#### 5.2 Table Styling
- Responsive table layout
- Hover effects on rows
- Button styling for delete/like/reorder
- Loading states
- Empty state messages

#### 5.3 Update App.css
- Ensure management screens use full available space
- Consistent styling with existing components

### Phase 6: Testing

#### 6.1 Backend Testing
- Test reorder endpoint with various order values
- Test unlike endpoint
- Test get liked quotes returns correct order
- Test authorization (USER role required)

#### 6.2 Frontend Testing
- Test navigation flow (Manage → Management → Favourites/Viewed → Back)
- Test delete favourite functionality
- Test reorder favourite functionality
- Test like from viewed quotes
- Test role-based access control
- Test empty states (no favourites, no viewed quotes)
- Test loading states

#### 6.3 Integration Testing
- Test full flow: view quote → like → manage favourites → reorder → delete
- Test full flow: view quote → manage viewed quotes → like from history
- Test with multiple users to ensure data isolation

## Technical Considerations

### Backend
1. **Order field**: Use integer values with simple direct update
   - **Rationale**: 
     - Favourites lists are typically small (< 100 items)
     - Each user only modifies their own data (partitioned by username in DynamoDB)
     - No cross-user conflicts possible
     - Same-user conflicts are rare (only when reordering from multiple devices simultaneously)
   - **Implementation**: When user moves an item, update only that item's order value
   - **Benefit**: Simple logic, no version tracking needed, last-write-wins is acceptable for this use case
   - **Future optimization**: If needed for very large lists (1000+), can refactor to batch reorder or lexicographic ordering
2. **Race conditions**: Not a concern for this implementation
   - Data is partitioned by username (hash key in user-likes table)
   - Each user only reorders their own favourites
   - Same-user concurrent reorders are rare; last-write-wins is acceptable
   - No need for optimistic locking or version tracking
3. **Validation**: Ensure order values are positive integers
4. **Performance**: Consider pagination for large lists

### Frontend
1. **State management**: Keep favourites and viewed quotes in sync
2. **Optimistic updates**: Update UI immediately, rollback on error
3. **Drag-and-drop**: Consider using a library like `react-beautiful-dnd` or `@dnd-kit/core`
4. **Accessibility**: Ensure keyboard navigation works for reordering
5. **Mobile**: Ensure touch-friendly controls for reordering

### UX Considerations
1. **Confirmation dialogs**: Ask before deleting favourites
2. **Success feedback**: Show toast/notification on successful actions
3. **Error handling**: Clear error messages for failed operations
4. **Loading states**: Show spinners during API calls
5. **Empty states**: Helpful messages when lists are empty

## API Endpoints Summary

### Existing Endpoints (to be used)
- `GET /quote/liked` - Get user's liked quotes (with order)
- `GET /quote/history` - Get user's viewed quotes
- `POST /quote/{id}/like` - Like a quote
- `DELETE /quote/{id}/unlike` - Unlike a quote

### New Endpoints (to be created)
- `PUT /quote/{id}/reorder` - Update order of liked quote
  - Body: `{ "order": number }`
  - Returns: 204 No Content

## Database Schema Changes

### user_likes_table
Add field (no schema change needed in DynamoDB):
- `order` (Number, optional) - Sort order for user's favourites

## Acceptance Criteria

### Manage Favourites
- [ ] User can view all their liked quotes in a table
- [ ] User can delete a favourite quote
- [ ] User can reorder favourites (up/down or drag-and-drop)
- [ ] Order persists across sessions
- [ ] Only accessible to authenticated users with USER role
- [ ] Back button returns to Management screen

### Viewed Quotes
- [ ] User can view all their viewed quotes in a table
- [ ] Quotes are sorted by viewedAt (most recent first)
- [ ] User can toggle like/unlike for each quote
- [ ] Toggle button shows visual state (filled when liked, empty when not liked)
- [ ] Single click toggles between like and unlike
- [ ] Real-time visual feedback during toggle (spinner, checkmark, error handling)
- [ ] Only accessible to authenticated users with USER role
- [ ] Back button returns to Management screen

### Navigation
- [ ] Manage button appears in sidepanel
- [ ] Manage button is only enabled when user is signed in
- [ ] Management screen shows two links: Manage Favourites and Viewed Quotes
- [ ] Links are only enabled for users with USER role
- [ ] Management screens use full space of quoteview and favourites area
- [ ] Navigation flow works correctly (Manage → Management → Sub-screens → Back)

### General
- [ ] All API calls include proper authentication
- [ ] Error handling for all failure scenarios
- [ ] Loading states during API calls
- [ ] Empty states when no data available
- [ ] Responsive design works on mobile and desktop
- [ ] Accessibility requirements met (keyboard navigation, screen readers)

## Estimated Effort
- Backend: 4-6 hours
- Frontend: 8-12 hours
- Testing: 4-6 hours
- **Total: 16-24 hours** (2-3 days)

## Dependencies
- Existing authentication system
- Existing role-based access control
- Existing liked quotes and viewed quotes functionality

## Future Enhancements
- Search/filter in favourites and viewed quotes
- Export favourites to file
- Share favourites with other users
- Bulk operations (delete multiple, reorder multiple)
- Pagination for large lists
- Sort options for viewed quotes (by author, by quote text, etc.)
