# User Views Implementation Plan

## Overview

This document describes the changes needed to track which quotes each authenticated user has viewed. This enables:
- Avoiding showing the same quote twice to a user
- Navigating through a user's view history (first, previous, next, last)
- Maintaining backward compatibility with unauthenticated users (local storage)

## Current State

- Unauthenticated users: viewed quotes stored in browser local storage
- Navigation (first, previous, next, last) uses local `receivedQuotes` array
- No server-side tracking of viewed quotes per user

## Target State

- **Authenticated users**: viewed quotes stored in DynamoDB `user-views` table
- **Unauthenticated users**: continue using local storage (no changes)
- **Navigation for authenticated users**: 
  - First = first quote in user-views table
  - Previous/Next = navigate through user-views table chronologically
  - Last = last quote in user-views table
- **New quote API**: automatically records view when user is authenticated

---

## Backend Changes

### 1. Create UserView Model

#### 1.1 Create `UserView.java`

**Location:** `src/main/java/ebulter/quote/lambda/model/UserView.java`

```java
package ebulter.quote.lambda.model;

public class UserView {
    private String username;    // Cognito username
    private int quoteId;        // Reference to Quote.id
    private long viewedAt;      // Unix timestamp (milliseconds)

    public UserView() {
    }

    public UserView(String username, int quoteId, long viewedAt) {
        this.username = username;
        this.quoteId = quoteId;
        this.viewedAt = viewedAt;
    }

    // Getters and setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getQuoteId() {
        return quoteId;
    }

    public void setQuoteId(int quoteId) {
        this.quoteId = quoteId;
    }

    public long getViewedAt() {
        return viewedAt;
    }

    public void setViewedAt(long viewedAt) {
        this.viewedAt = viewedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserView userView = (UserView) o;
        return quoteId == userView.quoteId && username.equals(userView.username);
    }

    @Override
    public int hashCode() {
        int result = username.hashCode();
        result = 31 * result + quoteId;
        return result;
    }
}
```

### 2. Create UserViewRepository

#### 2.1 Create `UserViewRepository.java`

**Location:** `src/main/java/ebulter/quote/lambda/repository/UserViewRepository.java`

```java
package ebulter.quote.lambda.repository;

import ebulter.quote.lambda.model.UserView;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserViewRepository {
    private static final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private static final String TABLE_NAME = System.getenv("DYNAMODB_USER_VIEWS_TABLE");

    public UserViewRepository() {
    }

    /**
     * Save a user view (or update timestamp if already exists)
     */
    public void saveUserView(UserView userView) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("username", AttributeValue.builder().s(userView.getUsername()).build());
        item.put("quoteId", AttributeValue.builder().n(String.valueOf(userView.getQuoteId())).build());
        item.put("viewedAt", AttributeValue.builder().n(String.valueOf(userView.getViewedAt())).build());

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build();

        dynamoDb.putItem(putItemRequest);
    }

    /**
     * Get all quotes viewed by a specific user, ordered by viewedAt (chronological)
     */
    public List<UserView> getViewsByUser(String username) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":username", AttributeValue.builder().s(username).build());

        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("username = :username")
                .expressionAttributeValues(expressionAttributeValues)
                .scanIndexForward(true)  // Sort by viewedAt ascending (oldest first)
                .build();

        QueryResponse queryResponse = dynamoDb.query(queryRequest);
        
        return queryResponse.items().stream()
                .map(item -> new UserView(
                        item.get("username").s(),
                        Integer.parseInt(item.get("quoteId").n()),
                        Long.parseLong(item.get("viewedAt").n())
                ))
                .toList();
    }

    /**
     * Get viewed quote IDs for a user (for exclusion when fetching new quotes)
     */
    public List<Integer> getViewedQuoteIds(String username) {
        return getViewsByUser(username).stream()
                .map(UserView::getQuoteId)
                .toList();
    }

    /**
     * Check if a user has viewed a specific quote
     */
    public boolean hasUserViewedQuote(String username, int quoteId) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("username", AttributeValue.builder().s(username).build());
        key.put("quoteId", AttributeValue.builder().n(String.valueOf(quoteId)).build());

        GetItemRequest request = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
                .build();

        GetItemResponse response = dynamoDb.getItem(request);
        return response.hasItem();
    }
}
```

### 3. Update QuoteService

#### 3.1 Modify `QuoteService.java`

**Changes:**
1. Add `UserViewRepository` dependency
2. Update `getQuote()` to accept optional username and exclude viewed quotes
3. Add method to record view after returning quote
4. Add method to get user's view history

**Modified/New methods:**

```java
public class QuoteService {
    private static final Logger logger = LoggerFactory.getLogger(QuoteService.class);
    private final QuoteRepository quoteRepository;
    private final UserLikeRepository userLikeRepository;
    private final UserViewRepository userViewRepository;  // NEW

    public QuoteService(QuoteRepository quoteRepository, UserLikeRepository userLikeRepository, UserViewRepository userViewRepository) {
        this.quoteRepository = quoteRepository;
        this.userLikeRepository = userLikeRepository;
        this.userViewRepository = userViewRepository;  // NEW
    }

    /**
     * Get a random quote, optionally excluding quotes already viewed by the user
     */
    public Quote getQuote(String username, Set<Integer> idsToExclude) {
        // If username provided, add their viewed quotes to exclusion list
        if (username != null && !username.isEmpty()) {
            List<Integer> viewedIds = userViewRepository.getViewedQuoteIds(username);
            idsToExclude.addAll(viewedIds);
            logger.info("User {} has viewed {} quotes, excluding them", username, viewedIds.size());
        }
        
        // Existing logic to fetch quote...
        logger.info("start reading all quotes from DB, idsToExclude.size() = {}", idsToExclude.size());
        List<Quote> currentDatabaseQuotes = quoteRepository.getAllQuotes();
        // ... rest of existing logic
    }

    /**
     * Record that a user viewed a quote
     */
    public void recordView(String username, int quoteId) {
        if (username != null && !username.isEmpty()) {
            UserView userView = new UserView(username, quoteId, System.currentTimeMillis());
            userViewRepository.saveUserView(userView);
            logger.info("Recorded view for user {} on quote {}", username, quoteId);
        }
    }

    /**
     * Get all quotes viewed by a user, in chronological order
     */
    public List<Quote> getViewedQuotesByUser(String username) {
        List<UserView> userViews = userViewRepository.getViewsByUser(username);
        return userViews.stream()
                .map(userView -> quoteRepository.findById(userView.getQuoteId()))
                .filter(quote -> quote != null)
                .toList();
    }
}
```

### 4. Update QuoteHandler

#### 4.1 Modify `QuoteHandler.java`

**Changes:**
1. Initialize `UserViewRepository`
2. Update `/quote` endpoint to accept username and record views
3. Add new `/quote/history` endpoint to get user's view history

**Modified sections:**

```java
public class QuoteHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final QuoteService quoteService;
    
    public QuoteHandler() {
        QuoteRepository quoteRepository = new QuoteRepository();
        UserLikeRepository userLikeRepository = new UserLikeRepository();
        UserViewRepository userViewRepository = new UserViewRepository();  // NEW
        this.quoteService = new QuoteService(quoteRepository, userLikeRepository, userViewRepository);  // UPDATED
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        String path = input.getPath();
        String httpMethod = input.getHttpMethod();
        
        // Extract username from Cognito authorizer
        String username = extractUsername(input);

        try {
            if ("/quote".equals(path) && "GET".equals(httpMethod)) {
                return handleGetQuote(input, username);  // UPDATED
            } else if ("/quote/history".equals(path) && "GET".equals(httpMethod)) {
                return handleGetViewHistory(username);  // NEW
            }
            // ... existing routes
        } catch (Exception e) {
            logger.error("Error handling request", e);
            return createErrorResponse(500, "Internal server error");
        }
    }

    private APIGatewayProxyResponseEvent handleGetQuote(APIGatewayProxyRequestEvent input, String username) {
        // Parse excluded IDs from query parameters
        Set<Integer> idsToExclude = parseExcludedIds(input);
        
        // Get quote (will exclude viewed quotes if username provided)
        Quote quote = quoteService.getQuote(username, idsToExclude);
        
        // Record view if user is authenticated
        if (username != null && !username.isEmpty()) {
            quoteService.recordView(username, quote.getId());
        }
        
        return createSuccessResponse(quote);
    }

    private APIGatewayProxyResponseEvent handleGetViewHistory(String username) {
        if (username == null || username.isEmpty()) {
            return createErrorResponse(401, "Authentication required");
        }
        
        List<Quote> viewedQuotes = quoteService.getViewedQuotesByUser(username);
        return createSuccessResponse(viewedQuotes);
    }

    private String extractUsername(APIGatewayProxyRequestEvent input) {
        Map<String, Object> authorizer = input.getRequestContext().getAuthorizer();
        if (authorizer != null && authorizer.containsKey("claims")) {
            Map<String, String> claims = (Map<String, String>) authorizer.get("claims");
            return claims.get("cognito:username");
        }
        return null;
    }
}
```

---

## Infrastructure Changes

### 5. Create DynamoDB Table

#### 5.1 Create `dynamodb_user_views.tf`

**Location:** `quote-lambda-tf-backend/infrastructure/dynamodb_user_views.tf`

```hcl
# DynamoDB table for user views
resource "aws_dynamodb_table" "user_views" {
  name         = "${var.project_name}-user-views-${terraform.workspace}"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "username"
  range_key    = "viewedAt"

  attribute {
    name = "username"
    type = "S"
  }

  attribute {
    name = "viewedAt"
    type = "N"
  }

  attribute {
    name = "quoteId"
    type = "N"
  }

  # GSI to query by quoteId (optional, for analytics)
  global_secondary_index {
    name            = "QuoteIdIndex"
    hash_key        = "quoteId"
    range_key       = "viewedAt"
    projection_type = "ALL"
  }

  tags = {
    Name        = "${var.project_name}-user-views-${terraform.workspace}"
    Environment = terraform.workspace
    Project     = var.project_name
  }
}

# Output the table name
output "dynamodb_user_views_table_name" {
  value       = aws_dynamodb_table.user_views.name
  description = "Name of the DynamoDB user views table"
}
```

#### 5.2 Update `lambda.tf`

Add environment variable and IAM permissions:

```hcl
resource "aws_lambda_function" "quote_lambda" {
  # ... existing configuration

  environment {
    variables = {
      DYNAMODB_TABLE_NAME        = aws_dynamodb_table.quotes.name
      DYNAMODB_USER_LIKES_TABLE  = aws_dynamodb_table.user_likes.name
      DYNAMODB_USER_VIEWS_TABLE  = aws_dynamodb_table.user_views.name  # NEW
    }
  }
}

# Update IAM policy to include user-views table
resource "aws_iam_role_policy" "lambda_dynamodb_policy" {
  name = "${var.project_name}-lambda-dynamodb-policy-${terraform.workspace}"
  role = aws_iam_role.lambda_exec.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:UpdateItem",
          "dynamodb:DeleteItem",
          "dynamodb:Query",
          "dynamodb:Scan"
        ]
        Resource = [
          aws_dynamodb_table.quotes.arn,
          "${aws_dynamodb_table.quotes.arn}/index/*",
          aws_dynamodb_table.user_likes.arn,
          "${aws_dynamodb_table.user_likes.arn}/index/*",
          aws_dynamodb_table.user_views.arn,           # NEW
          "${aws_dynamodb_table.user_views.arn}/index/*"  # NEW
        ]
      }
    ]
  })
}
```

---

## Frontend Changes

### 6. Update API Client

#### 6.1 Modify `quoteApi.ts`

Add new API methods:

```typescript
const quoteApi = {
  // Existing methods...

  /**
   * Get user's view history (chronological order)
   */
  async getViewHistory(): Promise<Quote[]> {
    try {
      const response = await axios.get(`${API_BASE_URL}/quote/history`, {
        headers: await getAuthHeaders(),
      });
      return response.data;
    } catch (error) {
      console.error('Failed to fetch view history:', error);
      throw error;
    }
  },

  /**
   * Get a quote (automatically records view if authenticated)
   * For authenticated users, backend excludes already viewed quotes
   */
  async getQuote(): Promise<Quote> {
    try {
      const response = await axios.get(`${API_BASE_URL}/quote`, {
        headers: await getAuthHeaders(),
      });
      return response.data;
    } catch (error) {
      console.error('Failed to fetch quote:', error);
      throw error;
    }
  },

  /**
   * Get unique quote (for unauthenticated users using local exclusion)
   */
  async getUniqueQuote(receivedQuotes: Quote[]): Promise<Quote> {
    const idsToExclude = receivedQuotes.map(q => q.id).join(',');
    try {
      const response = await axios.get(`${API_BASE_URL}/quote`, {
        params: { exclude: idsToExclude },
        headers: await getAuthHeaders(),
      });
      return response.data;
    } catch (error) {
      console.error('Failed to fetch unique quote:', error);
      throw error;
    }
  },
};
```

### 7. Update App Component

#### 7.1 Modify `App.tsx`

**Changes:**
1. Load view history when user authenticates
2. Use server-side history for navigation when authenticated
3. Continue using local storage for unauthenticated users

**Key modifications:**

```typescript
const App: React.FC = () => {
  const { isAuthenticated, user } = useAuth();
  const [quote, setQuote] = useState<Quote | null>(null);
  const [receivedQuotes, setReceivedQuotes] = useState<Quote[]>([]);
  const [serverViewHistory, setServerViewHistory] = useState<Quote[]>([]);  // NEW
  const [loading, setLoading] = useState<boolean>(true);
  const indexRef = useRef<number>(0);

  // Load view history when user authenticates
  useEffect(() => {
    const loadViewHistory = async () => {
      if (isAuthenticated && user) {
        try {
          const history = await quoteApi.getViewHistory();
          setServerViewHistory(history);
          if (history.length > 0) {
            // Set current quote to last viewed quote
            setQuote(history[history.length - 1]);
            indexRef.current = history.length - 1;
          }
        } catch (error) {
          console.error('Failed to load view history:', error);
        }
      } else {
        // Clear server history when user signs out
        setServerViewHistory([]);
      }
    };
    loadViewHistory();
  }, [isAuthenticated, user]);

  const newQuote = async (): Promise<void> => {
    try {
      setLoading(true);
      if (isAuthenticated) {
        // For authenticated users, backend handles exclusion and recording
        const uniqueQuote = await quoteApi.getQuote();
        setQuote(uniqueQuote);
        // Add to server history
        setServerViewHistory(prev => [...prev, uniqueQuote]);
        indexRef.current = serverViewHistory.length;
      } else {
        // For unauthenticated users, use local exclusion
        const uniqueQuote = await quoteApi.getUniqueQuote(receivedQuotes);
        setQuote(uniqueQuote);
        indexRef.current = receivedQuotes.length;
        setReceivedQuotes(prev => [...prev, uniqueQuote]);
      }
    } catch (error) {
      console.error('Failed to fetch new quote:', error);
    } finally {
      setLoading(false);
    }
  };

  const previous = (): void => {
    const history = isAuthenticated ? serverViewHistory : receivedQuotes;
    if (indexRef.current > 0) {
      indexRef.current = indexRef.current - 1;
      setQuote(history[indexRef.current]);
    }
  };

  const next = (): void => {
    const history = isAuthenticated ? serverViewHistory : receivedQuotes;
    if (indexRef.current < history.length - 1) {
      indexRef.current = indexRef.current + 1;
      setQuote(history[indexRef.current]);
    }
  };

  const jumpToFirst = (): void => {
    const history = isAuthenticated ? serverViewHistory : receivedQuotes;
    indexRef.current = 0;
    setQuote(history[indexRef.current]);
  };

  const jumpToLast = (): void => {
    const history = isAuthenticated ? serverViewHistory : receivedQuotes;
    indexRef.current = history.length - 1;
    setQuote(history[indexRef.current]);
  };

  // Update button disabled logic
  const history = isAuthenticated ? serverViewHistory : receivedQuotes;
  const isPreviousDisabled = indexRef.current === 0;
  const isNextDisabled = indexRef.current >= history.length - 1;

  return (
    <div className="app">
      {/* ... existing JSX */}
      <button 
        className="previousButton" 
        disabled={isPreviousDisabled || signingIn || showProfile} 
        onClick={previous}
      >
        Previous
      </button>
      <button
        className="nextButton"
        disabled={isNextDisabled || signingIn || showProfile}
        onClick={next}
      >
        Next
      </button>
      {/* ... rest of JSX */}
    </div>
  );
};
```

---

## Testing Plan

### Backend Tests

1. **UserViewRepository Tests**
   - Test saving views
   - Test retrieving views by user
   - Test getting viewed quote IDs
   - Test checking if user viewed specific quote

2. **QuoteService Tests**
   - Test `getQuote()` excludes viewed quotes for authenticated users
   - Test `recordView()` saves to repository
   - Test `getViewedQuotesByUser()` returns quotes in chronological order

3. **QuoteHandler Tests**
   - Test `/quote` endpoint records views for authenticated users
   - Test `/quote` endpoint doesn't record views for unauthenticated users
   - Test `/quote/history` endpoint returns user's view history
   - Test `/quote/history` endpoint requires authentication

### Frontend Tests

1. **Unauthenticated User Flow**
   - Verify quotes stored in local `receivedQuotes` array
   - Verify navigation uses local array
   - Verify no API calls to `/quote/history`

2. **Authenticated User Flow**
   - Verify view history loaded on authentication
   - Verify new quotes added to server history
   - Verify navigation uses server history
   - Verify first/last buttons work with server history

3. **Authentication Transition**
   - Verify signing in loads server history
   - Verify signing out clears server history and uses local storage

---

## Deployment Steps

1. **Deploy Backend Infrastructure**
   ```bash
   cd quote-lambda-tf-backend/infrastructure
   terraform workspace select dev
   terraform apply -var-file="dev.tfvars"
   ```

2. **Build and Deploy Backend Code**
   ```bash
   cd quote-lambda-tf-backend
   mvn clean package
   # Upload to Lambda via GitHub Actions or manual deployment
   ```

3. **Deploy Frontend**
   ```bash
   cd quote-lambda-tf-frontend
   npm run build
   # Deploy to S3/CloudFront via GitHub Actions
   ```

4. **Verify Deployment**
   - Test as unauthenticated user (local storage navigation)
   - Test as authenticated user (server-side navigation)
   - Verify views are recorded in DynamoDB
   - Verify navigation works correctly

---

## Rollback Plan

If issues occur:

1. **Backend rollback**: Revert Lambda to previous version
2. **Frontend rollback**: Revert to previous S3/CloudFront deployment
3. **Infrastructure rollback**: 
   ```bash
   terraform workspace select dev
   terraform destroy -target=aws_dynamodb_table.user_views
   ```

---

## Future Enhancements

1. **View expiration**: Add TTL to auto-delete old views after 30/60/90 days
2. **View analytics**: Track most viewed quotes using QuoteIdIndex GSI
3. **Reset history**: Add button to clear user's view history
4. **Pagination**: For users with large view histories, implement pagination
5. **Sync local to server**: When user signs in, upload local storage views to server
