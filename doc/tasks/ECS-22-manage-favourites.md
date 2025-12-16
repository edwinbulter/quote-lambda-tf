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
   - **No confirmation dialog** - Delete immediately on click
   - Update backend to remove like
   - Show success/error feedback

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
- Maintains sequential ordering by:
  - If moving to a higher order: decrement all likes between old and new order by 1
  - If moving to a lower order: increment all likes between new and old order by 1
  - Set the moved like to the new order value
- Updates all affected likes in a single operation
- Ensures no duplicate order values and no gaps in ordering
- Requires authentication and USER role

Examples:

**Moving to a lower order (moving up in the list):**
```
Before: [A(1), B(2), C(3), D(4)]
Move D to position 2 (order: 2):
- Increment all likes with order >= 2 and < 4: B(2→3), C(3→4)
- Set D to order 2
- Result: [A(1), D(2), B(3), C(4)] ✓
```

**Moving to a higher order (moving down in the list):**
```
Before: [A(1), B(2), C(3), D(4)]
Move A to position 3 (order: 3):
- Decrement all likes with order > 1 and <= 3: B(2→1), C(3→2)
- Set A to order 3
- Result: [B(1), C(2), A(3), D(4)] ✓
```

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
3. **Performance**: Consider pagination for large lists

### Frontend
1. **State management**: Management screens and main view are mutually exclusive
   - When user opens Management screen, main view (quoteview + favourites) is hidden
   - When user returns to main view, favourites are fetched fresh from backend
   - No need to keep both in sync simultaneously
   - If user wants to see updated data after management changes, they can refresh the page

2. **Reordering calculation**: Simplified frontend reordering
   - When user moves a favourite to a new position in the table:
     - The order value is simply the position in the table (1, 2, 3, ...)
     - No calculation needed: just assign order = position for all items
   - Update local state optimistically with new order values
   - Send only the moved item's new order to backend
   - Backend applies the same logic to maintain consistency
   - **Benefit**: Extremely simple logic, order values always stay positive and sequential, no complex calculations needed

3. **Optimistic updates**: Update UI immediately, rollback on error
   - Store current state before making API call
   - Update UI immediately (no waiting for backend)
   - Send API request asynchronously
   - On success: Show confirmation message
   - On error: Rollback UI to previous state and show error message
   
   **Example - Delete favourite:**
   ```typescript
   async function handleDeleteFavourite(quoteId: number) {
     const previousFavourites = [...favourites];
     
     // Optimistic update
     setFavourites(favourites.filter(f => f.id !== quoteId));
     
     try {
       await api.unlikeQuote(quoteId);
       showMessage("Favourite deleted");
     } catch (error) {
       // Rollback on error
       setFavourites(previousFavourites);
       showError("Failed to delete favourite. Please try again.");
     }
   }
   ```
   
   **Example - Reorder favourite:**
   ```typescript
   async function handleReorderFavourite(quoteId: number, newPosition: number) {
     const previousFavourites = [...favourites];
     
     // Optimistic update: Reorder in UI
     const updatedFavourites = [...favourites];
     const itemIndex = updatedFavourites.findIndex(f => f.id === quoteId);
     const [item] = updatedFavourites.splice(itemIndex, 1);
     updatedFavourites.splice(newPosition, 0, item);
     
     // Reassign order values based on new positions
     updatedFavourites.forEach((fav, index) => {
       fav.order = index + 1;
     });
     
     setFavourites(updatedFavourites);
     
     try {
       const movedItem = updatedFavourites[newPosition];
       await api.reorderLikedQuote(quoteId, movedItem.order);
       showMessage("Favourite reordered");
     } catch (error) {
       setFavourites(previousFavourites);
       showError("Failed to reorder favourite. Please try again.");
     }
   }
   ```
   
   **Example - Toggle like:**
   ```typescript
   async function handleToggleLike(quoteId: number, isCurrentlyLiked: boolean) {
     const previousViewedQuotes = [...viewedQuotes];
     
     // Optimistic update: Toggle like state
     const updatedViewedQuotes = viewedQuotes.map(q =>
       q.id === quoteId ? { ...q, isLiked: !isCurrentlyLiked } : q
     );
     setViewedQuotes(updatedViewedQuotes);
     
     try {
       if (isCurrentlyLiked) {
         await api.unlikeQuote(quoteId);
       } else {
         await api.likeQuote(quoteId);
       }
       showMessage(isCurrentlyLiked ? "Removed from favourites" : "Added to favourites");
     } catch (error) {
       setViewedQuotes(previousViewedQuotes);
       showError("Failed to update like. Please try again.");
     }
   }
   ```
   
   **Benefits:**
   - UI feels instant and responsive
   - Better user experience
   - Most operations succeed (errors are rare)
   - Easy to rollback if something fails

4. **Reordering UI**: Use Up/Down buttons instead of drag-and-drop
   - **Recommendation**: Start with simple Up/Down buttons (no library needed)
   - **Why**: 
     - Simplest to implement and maintain
     - No external dependencies
     - Works perfectly on mobile (touch-friendly)
     - Accessible (keyboard navigation works naturally)
     - Clear UX (users understand exactly what will happen)
     - Sufficient for small lists (< 100 items)
   
   **Implementation:**
   ```typescript
   function ManageFavouritesScreen() {
     const [favourites, setFavourites] = useState([...]);

     const handleMoveUp = (index: number) => {
       if (index === 0) return;
       
       const newFavourites = [...favourites];
       [newFavourites[index - 1], newFavourites[index]] = 
       [newFavourites[index], newFavourites[index - 1]];
       
       newFavourites.forEach((fav, i) => {
         fav.order = i + 1;
       });
       
       setFavourites(newFavourites);
       api.reorderLikedQuote(newFavourites[index - 1].id, newFavourites[index - 1].order);
     };

     const handleMoveDown = (index: number) => {
       if (index === favourites.length - 1) return;
       
       const newFavourites = [...favourites];
       [newFavourites[index], newFavourites[index + 1]] = 
       [newFavourites[index + 1], newFavourites[index]];
       
       newFavourites.forEach((fav, i) => {
         fav.order = i + 1;
       });
       
       setFavourites(newFavourites);
       api.reorderLikedQuote(newFavourites[index + 1].id, newFavourites[index + 1].order);
     };

     return (
       <table>
         <tbody>
           {favourites.map((fav, index) => (
             <tr key={fav.id}>
               <td>{fav.quoteText}</td>
               <td>{fav.author}</td>
               <td>
                 <button 
                   onClick={() => handleMoveUp(index)} 
                   disabled={index === 0}
                   title="Move up"
                 >
                   ↑
                 </button>
                 <button 
                   onClick={() => handleMoveDown(index)} 
                   disabled={index === favourites.length - 1}
                   title="Move down"
                 >
                   ↓
                 </button>
                 <button onClick={() => handleDelete(fav.id)}>Delete</button>
               </td>
             </tr>
           ))}
         </tbody>
       </table>
     );
   }
   ```
   
   **Future enhancement**: If users request drag-and-drop or list becomes very large (100+ items), upgrade to `@dnd-kit/core` (modern, actively maintained, better performance than `react-beautiful-dnd`)

5. **Accessibility**: Basic keyboard support
   - Buttons are focusable with Tab key
   - Buttons can be activated with Enter/Space
   - Add `aria-label` to buttons for screen readers
   - Add visible focus indicators in CSS

6. **Mobile**: Touch-friendly button design
   - Minimum button size: 44x44px (Apple's recommended standard)
   - Better labels: Use emoji + text (⬆️ Up, ⬇️ Down, 🗑️ Delete)
   - Spacing: 4px margin between buttons for easier tapping
   - Font size: 16px (prevents iOS auto-zoom)
   - Touch feedback: Visual feedback on tap (active state)
   
   **CSS:**
   ```css
   button {
     min-width: 44px;
     min-height: 44px;
     padding: 8px 12px;
     margin: 4px;
     font-size: 16px;
     border: 1px solid #ccc;
     border-radius: 4px;
     background-color: #f9f9f9;
     cursor: pointer;
     transition: background-color 0.2s;
   }

   /* Touch feedback */
   button:active {
     background-color: #e0e0e0;
     transform: scale(0.98);
   }

   /* Hover state */
   button:hover:not(:disabled) {
     background-color: #f0f0f0;
   }

   /* Disabled state */
   button:disabled {
     opacity: 0.5;
     cursor: not-allowed;
   }

   /* Actions column spacing */
   .actions {
     display: flex;
     gap: 4px;
     flex-wrap: wrap;
   }
   ```
   
   **Updated button labels:**
   ```typescript
   <button onClick={() => handleMoveUp(index)} disabled={index === 0}>
     ⬆️ Up
   </button>
   <button onClick={() => handleMoveDown(index)} disabled={index === favourites.length - 1}>
     ⬇️ Down
   </button>
   <button onClick={() => handleDelete(fav.id)}>
     🗑️ Delete
   </button>
   ```

### UX Considerations

#### 1. Success Feedback: Toast Notifications

Show temporary toast notifications for successful actions (delete, reorder, like).

**Toast Component:**
```typescript
// Toast.tsx
interface ToastProps {
  message: string;
  type: 'success' | 'error';
  duration?: number;
}

function Toast({ message, type, duration = 3000 }: ToastProps) {
  const [isVisible, setIsVisible] = useState(true);

  useEffect(() => {
    const timer = setTimeout(() => setIsVisible(false), duration);
    return () => clearTimeout(timer);
  }, [duration]);

  if (!isVisible) return null;

  return (
    <div className={`toast toast-${type}`}>
      {type === 'success' && '✓ '}
      {type === 'error' && '✗ '}
      {message}
    </div>
  );
}
```

**CSS:**
```css
.toast {
  position: fixed;
  bottom: 20px;
  right: 20px;
  padding: 12px 16px;
  border-radius: 4px;
  font-size: 14px;
  z-index: 2000;
  animation: slideIn 0.3s ease-in-out;
}

@keyframes slideIn {
  from {
    transform: translateX(400px);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

.toast-success {
  background-color: #4caf50;
  color: white;
}

.toast-error {
  background-color: #f44336;
  color: white;
}
```

**Usage in component:**
```typescript
function ManageFavouritesScreen() {
  const [toast, setToast] = useState<{
    message: string;
    type: 'success' | 'error';
  } | null>(null);

  const showToast = (message: string, type: 'success' | 'error') => {
    setToast({ message, type });
  };

  const handleDeleteFavourite = async (quoteId: number) => {
    const previousFavourites = [...favourites];
    setFavourites(favourites.filter(f => f.id !== quoteId));

    try {
      await api.unlikeQuote(quoteId);
      showToast('Favourite deleted', 'success');
    } catch (error) {
      setFavourites(previousFavourites);
      showToast('Failed to delete favourite', 'error');
    }
  };

  return (
    <>
      {/* Main content */}
      {toast && <Toast message={toast.message} type={toast.type} />}
    </>
  );
}
```

**Toast messages:**
- Success: "✓ Favourite deleted"
- Success: "✓ Favourite reordered"
- Success: "✓ Added to favourites"
- Success: "✓ Removed from favourites"
- Error: "✗ Failed to delete favourite"
- Error: "✗ Failed to reorder favourite"
- Error: "✗ Failed to update like"

#### 2. Error Handling

Clear error messages for failed operations (shown in toast notifications).

#### 3. Loading States

Show spinners during API calls (optional, for slower networks).

#### 4. Empty States

Helpful messages when lists are empty:
- "No favourites yet. Like quotes to add them here."
- "No viewed quotes yet."

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
