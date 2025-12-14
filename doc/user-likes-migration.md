# User Likes Migration Plan

## Overview

This document describes the changes needed to migrate from a simple like counter (stored on each Quote) to a user-specific likes system that tracks which users liked which quotes. This enables querying liked quotes per user.

## Current State

- Each `Quote` has a `likes` field (integer) that counts total likes
- No tracking of which users liked which quotes
- `getLikedQuotes()` returns all quotes with `likes > 0`

## Target State

- `Quote` model no longer stores likes count
- New `UserLike` model tracks user-quote relationships
- New DynamoDB table stores user likes
- Can query all quotes liked by a specific user
- Can query all users who liked a specific quote (via GSI)

---

## Backend Code Changes

### 1. Create New Model Classes

#### 1.1 Create `UserLike.java`

**Location:** `src/main/java/ebulter/quote/lambda/model/UserLike.java`

```java
package ebulter.quote.lambda.model;

public class UserLike {
    private String username;    // Cognito username
    private int quoteId;        // Reference to Quote.id
    private long likedAt;       // Unix timestamp (milliseconds)

    public UserLike() {
    }

    public UserLike(String username, int quoteId, long likedAt) {
        this.username = username;
        this.quoteId = quoteId;
        this.likedAt = likedAt;
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

    public long getLikedAt() {
        return likedAt;
    }

    public void setLikedAt(long likedAt) {
        this.likedAt = likedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserLike userLike = (UserLike) o;
        return quoteId == userLike.quoteId && username.equals(userLike.username);
    }

    @Override
    public int hashCode() {
        int result = username.hashCode();
        result = 31 * result + quoteId;
        return result;
    }
}
```

#### 1.2 Modify `Quote.java`

**Changes:**
- Remove `likes` field
- Remove `getLikes()` and `setLikes()` methods
- Update constructor to remove `likes` parameter

**Modified sections:**
```java
public class Quote {
    private int id;
    private String quoteText;
    private String author;
    // REMOVED: private int likes;

    public Quote() {
        // REMOVED: this.likes = 0;
    }

    public Quote(int id, String quoteText, String author) {
        this.id = id;
        this.quoteText = quoteText;
        this.author = author;
        // REMOVED: this.likes = likes;
    }

    // REMOVED: getLikes() and setLikes() methods
}
```

### 2. Create New Repository Class

#### 2.1 Create `UserLikeRepository.java`

**Location:** `src/main/java/ebulter/quote/lambda/repository/UserLikeRepository.java`

```java
package ebulter.quote.lambda.repository;

import ebulter.quote.lambda.model.UserLike;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserLikeRepository {
    private static final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private static final String TABLE_NAME = System.getenv("DYNAMODB_USER_LIKES_TABLE");

    public UserLikeRepository() {
    }

    /**
     * Save a user like (or update timestamp if already exists)
     */
    public void saveUserLike(UserLike userLike) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("username", AttributeValue.builder().s(userLike.getUsername()).build());
        item.put("quoteId", AttributeValue.builder().n(String.valueOf(userLike.getQuoteId())).build());
        item.put("likedAt", AttributeValue.builder().n(String.valueOf(userLike.getLikedAt())).build());

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build();

        dynamoDb.putItem(putItemRequest);
    }

    /**
     * Get all quotes liked by a specific user
     */
    public List<UserLike> getLikesByUser(String username) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":username", AttributeValue.builder().s(username).build());

        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("username = :username")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        QueryResponse queryResponse = dynamoDb.query(queryRequest);
        
        return queryResponse.items().stream()
                .map(item -> new UserLike(
                        item.get("username").s(),
                        Integer.parseInt(item.get("quoteId").n()),
                        Long.parseLong(item.get("likedAt").n())
                ))
                .toList();
    }

    /**
     * Check if a user has liked a specific quote
     */
    public boolean hasUserLikedQuote(String username, int quoteId) {
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

    /**
     * Remove a user like (unlike)
     */
    public void deleteUserLike(String username, int quoteId) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("username", AttributeValue.builder().s(username).build());
        key.put("quoteId", AttributeValue.builder().n(String.valueOf(quoteId)).build());

        DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
                .build();

        dynamoDb.deleteItem(deleteRequest);
    }

    /**
     * Get the count of likes for a specific quote (using GSI)
     */
    public int getLikeCountForQuote(int quoteId) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":quoteId", AttributeValue.builder().n(String.valueOf(quoteId)).build());

        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName("QuoteIdIndex")
                .keyConditionExpression("quoteId = :quoteId")
                .expressionAttributeValues(expressionAttributeValues)
                .select(Select.COUNT)
                .build();

        QueryResponse queryResponse = dynamoDb.query(queryRequest);
        return queryResponse.count();
    }
}
```

### 3. Update Existing Repository

#### 3.1 Modify `QuoteRepository.java`

**Changes:**
- Remove `likes` field from all DynamoDB operations
- Remove `updateLikes()` method
- Remove `getLikedQuotes()` method (moved to service layer)

**Key modifications:**

```java
// In getAllQuotes() - remove likes parameter
return scanResponse.items().stream().map(item ->
    new Quote(
        Integer.parseInt(item.get("id").n()), 
        item.get("quoteText").s(), 
        item.get("author").s()
        // REMOVED: Integer.parseInt(item.get("likes").n())
    )).toList();

// In saveQuote() - remove likes attribute
Map<String, AttributeValue> item = new HashMap<>();
item.put("id", AttributeValue.builder().n(String.valueOf(quote.getId())).build());
item.put("quoteText", AttributeValue.builder().s(quote.getQuoteText()).build());
item.put("author", AttributeValue.builder().s(quote.getAuthor()).build());
// REMOVED: item.put("likes", AttributeValue.builder().n(String.valueOf(quote.getLikes())).build());

// In findById() - remove likes parameter
return new Quote(
    Integer.parseInt(item.get("id").n()), 
    item.get("quoteText").s(), 
    item.get("author").s()
    // REMOVED: Integer.parseInt(item.get("likes").n())
);

// REMOVE ENTIRELY: updateLikes() method
// REMOVE ENTIRELY: getLikedQuotes() method
```

### 4. Update Service Layer

#### 4.1 Modify `QuoteService.java`

**Changes:**
- Add `UserLikeRepository` dependency
- Update `likeQuote()` to accept `username` and create `UserLike` record
- Update `getLikedQuotes()` to accept `username` and return user-specific liked quotes
- Add `unlikeQuote()` method
- Add `getLikeCount()` method for displaying like counts

**Key modifications:**

```java
public class QuoteService {
    private static final Logger logger = LoggerFactory.getLogger(QuoteService.class);
    private final QuoteRepository quoteRepository;
    private final UserLikeRepository userLikeRepository;  // NEW

    public QuoteService(QuoteRepository quoteRepository, UserLikeRepository userLikeRepository) {
        this.quoteRepository = quoteRepository;
        this.userLikeRepository = userLikeRepository;  // NEW
    }

    // MODIFIED: likeQuote now takes username
    public Quote likeQuote(String username, int quoteId) {
        Quote quote = quoteRepository.findById(quoteId);
        if (quote != null) {
            UserLike userLike = new UserLike(username, quoteId, System.currentTimeMillis());
            userLikeRepository.saveUserLike(userLike);
            return quote;
        } else {
            return QuoteUtil.getErrorQuote("Quote to like not found");
        }
    }

    // NEW: Unlike a quote
    public void unlikeQuote(String username, int quoteId) {
        userLikeRepository.deleteUserLike(username, quoteId);
    }

    // MODIFIED: getLikedQuotes now takes username and returns user-specific likes
    public List<Quote> getLikedQuotes(String username) {
        List<UserLike> userLikes = userLikeRepository.getLikesByUser(username);
        return userLikes.stream()
                .map(userLike -> quoteRepository.findById(userLike.getQuoteId()))
                .filter(quote -> quote != null)
                .sorted((q1, q2) -> q1.getId() - q2.getId())
                .toList();
    }

    // NEW: Get like count for a quote
    public int getLikeCount(int quoteId) {
        return userLikeRepository.getLikeCountForQuote(quoteId);
    }

    // NEW: Check if user has liked a quote
    public boolean hasUserLikedQuote(String username, int quoteId) {
        return userLikeRepository.hasUserLikedQuote(username, quoteId);
    }
}
```

### 5. Update Handler/Controller Layer ✅ COMPLETED

**File:** `src/main/java/ebulter/quote/lambda/QuoteHandler.java`

**Changes completed:**
- ✅ Added `UserLikeRepository` import
- ✅ Updated constructor to instantiate `QuoteService` with both repositories
- ✅ Added `extractUsername()` method to extract `cognito:username` from JWT token
- ✅ Updated `/like` endpoint to extract username and pass to `quoteService.likeQuote(username, quoteId)`
- ✅ Updated `/liked` endpoint to extract username and pass to `quoteService.getLikedQuotes(username)`
- ✅ Added new `/unlike` endpoint (DELETE method) with username extraction
- ✅ Updated CORS headers to include DELETE method
- ✅ Updated test file `QuoteHandlerTest.java` to match new signatures

---

## Terraform Infrastructure Changes

### 1. Create New DynamoDB Table

**File:** `infrastructure/dynamodb.tf`

**Add the following resource:**

```hcl
# DynamoDB table for storing user likes
resource "aws_dynamodb_table" "user_likes_table" {
  name           = local.environment == "prod" ? "user-likes" : "user-likes-${local.environment}"
  billing_mode   = "PROVISIONED"
  read_capacity  = var.dynamodb_read_capacity
  write_capacity = var.dynamodb_write_capacity
  hash_key       = "username"
  sort_key       = "quoteId"

  attribute {
    name = "username"
    type = "S"
  }

  attribute {
    name = "quoteId"
    type = "N"
  }

  attribute {
    name = "likedAt"
    type = "N"
  }

  # Global Secondary Index for querying by quote (to get all users who liked it)
  global_secondary_index {
    name               = "QuoteIdIndex"
    hash_key           = "quoteId"
    sort_key           = "likedAt"
    projection_type    = "ALL"
    read_capacity      = var.dynamodb_read_capacity
    write_capacity     = var.dynamodb_write_capacity
  }

  # Enable point-in-time recovery for data protection
  point_in_time_recovery {
    enabled = true
  }

  # Server-side encryption
  server_side_encryption {
    enabled = true
  }

  tags = {
    Name      = "${var.project_name}-user-likes"
    ManagedBy = "Terraform"
  }
}

# Autoscaling for read capacity - user_likes_table
resource "aws_appautoscaling_target" "user_likes_table_read_target" {
  max_capacity       = 100
  min_capacity       = var.dynamodb_read_capacity
  resource_id        = "table/${aws_dynamodb_table.user_likes_table.name}"
  scalable_dimension = "dynamodb:table:ReadCapacityUnits"
  service_namespace  = "dynamodb"
}

resource "aws_appautoscaling_policy" "user_likes_table_read_policy" {
  name               = "DynamoDBReadCapacityUtilization:${aws_appautoscaling_target.user_likes_table_read_target.resource_id}"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.user_likes_table_read_target.resource_id
  scalable_dimension = aws_appautoscaling_target.user_likes_table_read_target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.user_likes_table_read_target.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "DynamoDBReadCapacityUtilization"
    }
    target_value = 70.0
  }
}

# Autoscaling for write capacity - user_likes_table
resource "aws_appautoscaling_target" "user_likes_table_write_target" {
  max_capacity       = 100
  min_capacity       = var.dynamodb_write_capacity
  resource_id        = "table/${aws_dynamodb_table.user_likes_table.name}"
  scalable_dimension = "dynamodb:table:WriteCapacityUnits"
  service_namespace  = "dynamodb"
}

resource "aws_appautoscaling_policy" "user_likes_table_write_policy" {
  name               = "DynamoDBWriteCapacityUtilization:${aws_appautoscaling_target.user_likes_table_write_target.resource_id}"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.user_likes_table_write_target.resource_id
  scalable_dimension = aws_appautoscaling_target.user_likes_table_write_target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.user_likes_table_write_target.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "DynamoDBWriteCapacityUtilization"
    }
    target_value = 70.0
  }
}

# Autoscaling for GSI read capacity
resource "aws_appautoscaling_target" "user_likes_gsi_read_target" {
  max_capacity       = 100
  min_capacity       = var.dynamodb_read_capacity
  resource_id        = "table/${aws_dynamodb_table.user_likes_table.name}/index/QuoteIdIndex"
  scalable_dimension = "dynamodb:index:ReadCapacityUnits"
  service_namespace  = "dynamodb"
}

resource "aws_appautoscaling_policy" "user_likes_gsi_read_policy" {
  name               = "DynamoDBReadCapacityUtilization:${aws_appautoscaling_target.user_likes_gsi_read_target.resource_id}"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.user_likes_gsi_read_target.resource_id
  scalable_dimension = aws_appautoscaling_target.user_likes_gsi_read_target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.user_likes_gsi_read_target.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "DynamoDBReadCapacityUtilization"
    }
    target_value = 70.0
  }
}

# Autoscaling for GSI write capacity
resource "aws_appautoscaling_target" "user_likes_gsi_write_target" {
  max_capacity       = 100
  min_capacity       = var.dynamodb_write_capacity
  resource_id        = "table/${aws_dynamodb_table.user_likes_table.name}/index/QuoteIdIndex"
  scalable_dimension = "dynamodb:index:WriteCapacityUnits"
  service_namespace  = "dynamodb"
}

resource "aws_appautoscaling_policy" "user_likes_gsi_write_policy" {
  name               = "DynamoDBWriteCapacityUtilization:${aws_appautoscaling_target.user_likes_gsi_write_target.resource_id}"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.user_likes_gsi_write_target.resource_id
  scalable_dimension = aws_appautoscaling_target.user_likes_gsi_write_target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.user_likes_gsi_write_target.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "DynamoDBWriteCapacityUtilization"
    }
    target_value = 70.0
  }
}
```

### 2. Update Lambda IAM Policy

**File:** `infrastructure/lambda.tf`

**Modify the `aws_iam_policy.lambda_dynamodb_policy` resource:**

```hcl
resource "aws_iam_policy" "lambda_dynamodb_policy" {
  name        = local.environment == "prod" ? "${var.project_name}-dynamodb-policy" : "${var.project_name}-dynamodb-policy-${local.environment}"
  description = "IAM policy for Lambda to access DynamoDB"
  
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
          "dynamodb:Scan",
          "dynamodb:Query"
        ]
        Resource = [
          aws_dynamodb_table.quotes_table.arn,
          "${aws_dynamodb_table.quotes_table.arn}/index/*",
          aws_dynamodb_table.user_likes_table.arn,              # NEW
          "${aws_dynamodb_table.user_likes_table.arn}/index/*"  # NEW
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      }
    ]
  })
}
```

### 3. Update Lambda Environment Variables

**File:** `infrastructure/lambda.tf`

**Modify the `aws_lambda_function.quote_lambda` resource:**

```hcl
resource "aws_lambda_function" "quote_lambda" {
  # ... existing configuration ...
  
  environment {
    variables = {
      DYNAMODB_TABLE            = aws_dynamodb_table.quotes_table.name
      DYNAMODB_USER_LIKES_TABLE = aws_dynamodb_table.user_likes_table.name  # NEW
    }
  }
  
  # ... rest of configuration ...
}
```

### 4. Add Output for New Table

**File:** `infrastructure/outputs.tf`

**Add:**

```hcl
output "user_likes_table_name" {
  description = "Name of the DynamoDB user likes table"
  value       = aws_dynamodb_table.user_likes_table.name
}

output "user_likes_table_arn" {
  description = "ARN of the DynamoDB user likes table"
  value       = aws_dynamodb_table.user_likes_table.arn
}
```

---

## Migration Strategy

### Phase 1: Infrastructure Setup ✅ COMPLETED
1. ✅ Create the new `UserLike` model class
2. ✅ Create the `UserLikeRepository` class
3. ✅ Update Terraform configuration for new DynamoDB table
4. ✅ Deploy Terraform changes to create the new table

### Phase 2: Backend Code Migration ✅ COMPLETED
1. ✅ Remove `likes` field from Quote model
2. ✅ Update all repository methods to remove likes references
3. ✅ Update service layer to use UserLike system
4. ✅ Update QuoteHandler constructor to inject UserLikeRepository

### Phase 3: Handler/API Updates ✅ COMPLETED
1. ✅ Extract username from JWT token in handler
2. ✅ Update `/like` endpoint to use username parameter
3. ✅ Update `/liked` endpoint to use username parameter
4. ✅ Add `/unlike` endpoint
5. ✅ Update CORS headers to include DELETE method
6. ✅ Update test files to match new signatures
7. ⏳ **NEXT:** Build and deploy Lambda function

### Phase 4: Deployment & Testing ⏳ IN PROGRESS
1. ⏳ Build Lambda package: `mvn clean package`
2. ⏳ Deploy Lambda function with Terraform
3. ⏳ Test all endpoints with authenticated requests
4. ⏳ Verify DynamoDB tables are working correctly

### Phase 5: Frontend Updates 🔜 PENDING
1. Update frontend to use new API endpoints
2. Add unlike functionality to UI
3. Display like counts per quote
4. Test end-to-end functionality

### Phase 6: Cleanup (Optional)
1. Remove old `likes` attribute from existing DynamoDB records (won't affect functionality if left)

---

## API Changes Required

### Current Endpoints
- `POST /quotes/{id}/like` - Increments like counter
- `GET /quotes/liked` - Returns quotes with likes > 0

### New Endpoints
- `POST /quotes/{id}/like` - Creates UserLike record (requires authenticated user)
- `DELETE /quotes/{id}/like` - Removes UserLike record (unlike)
- `GET /quotes/liked` - Returns quotes liked by authenticated user
- `GET /quotes/{id}/likes/count` - Returns total like count for a quote
- `GET /quotes/{id}/likes/status` - Returns whether current user has liked the quote

---

## Testing Checklist

### Backend Tests
- [ ] Test UserLike model serialization/deserialization
- [ ] Test UserLikeRepository CRUD operations
- [ ] Test QuoteService.likeQuote() with username
- [ ] Test QuoteService.unlikeQuote()
- [ ] Test QuoteService.getLikedQuotes() returns user-specific likes
- [ ] Test QuoteService.getLikeCount() returns correct count
- [ ] Test Quote model without likes field

### Infrastructure Tests
- [ ] Verify user_likes_table is created successfully
- [ ] Verify GSI (QuoteIdIndex) is created
- [ ] Verify Lambda has permissions to access new table
- [ ] Verify environment variable is set correctly
- [ ] Test autoscaling configuration

### Integration Tests
- [ ] User can like a quote
- [ ] User can unlike a quote
- [ ] User can view their liked quotes
- [ ] Like count displays correctly
- [ ] Multiple users can like the same quote
- [ ] User cannot like the same quote twice (idempotent)

---

## Rollback Plan

If issues arise:
1. Revert backend code to previous version (restores likes field)
2. Keep new DynamoDB table (no harm in leaving it)
3. Investigate and fix issues
4. Redeploy when ready

The new table can remain in place without affecting the old system.

---

## Benefits of This Approach

1. **User-specific data**: Track individual user preferences
2. **Scalability**: No hot partition issues (likes distributed across users)
3. **Flexibility**: Easy to add features like "users who liked this also liked..."
4. **Analytics**: Better insights into user behavior
5. **Unlike capability**: Users can remove their likes
6. **Accurate counts**: Total likes = count of UserLike records per quote

---

## Next Steps (Immediate Actions Required)

### 1. Deploy Terraform Infrastructure
```bash
cd infrastructure
terraform plan -var-file=dev.tfvars  # Review changes
terraform apply -var-file=dev.tfvars  # Deploy new table
```

### 2. Update QuoteHandler to Extract Username

Add a helper method to extract username from JWT:

```java
private String extractUsername(APIGatewayProxyRequestEvent event) {
    try {
        Map<String, String> headers = event.getHeaders();
        if (headers == null) {
            return null;
        }
        
        String authHeader = headers.get("authorization");
        if (authHeader == null) {
            authHeader = headers.get("Authorization");
        }
        
        if (authHeader == null || authHeader.isEmpty()) {
            return null;
        }
        
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        DecodedJWT jwt = JWT.decode(token);
        
        // Extract username from Cognito token
        return jwt.getClaim("cognito:username").asString();
    } catch (Exception e) {
        logger.error("Error extracting username", e);
        return null;
    }
}
```

### 3. Update `/like` Endpoint (Line 61-74)

```java
} else if (path.endsWith("/like")) {
    // Check authorization for like endpoint
    if (!hasUserRole(event)) {
        return createForbiddenResponse("USER role required to like quotes");
    }
    
    // Extract username from token
    String username = extractUsername(event);
    if (username == null) {
        return createErrorResponse("Could not extract username from token");
    }
    
    // Log user information for auditing
    logUserInfo(event, "LIKE_QUOTE");
    
    // Extract ID from path like "/quote/75/like"
    String[] pathParts = path.split("/");
    int id = Integer.parseInt(pathParts[pathParts.length - 2]);
    Quote quote = quoteService.likeQuote(username, id);
    return createResponse(quote);
}
```

### 4. Update `/liked` Endpoint (Line 75-77)

```java
} else if (path.endsWith("/liked")) {
    // Extract username from token
    String username = extractUsername(event);
    if (username == null) {
        return createErrorResponse("Could not extract username from token");
    }
    
    List<Quote> likedQuotes = quoteService.getLikedQuotes(username);
    return createResponse(likedQuotes);
}
```

### 5. Add `/unlike` Endpoint

Add after the `/liked` endpoint:

```java
} else if (path.matches(".*/quote/\\d+/unlike")) {
    // Check authorization
    if (!hasUserRole(event)) {
        return createForbiddenResponse("USER role required to unlike quotes");
    }
    
    // Extract username from token
    String username = extractUsername(event);
    if (username == null) {
        return createErrorResponse("Could not extract username from token");
    }
    
    // Extract ID from path like "/quote/75/unlike"
    String[] pathParts = path.split("/");
    int id = Integer.parseInt(pathParts[pathParts.length - 2]);
    quoteService.unlikeQuote(username, id);
    
    // Return success response
    APIGatewayProxyResponseEvent response = createBaseResponse();
    response.setStatusCode(HttpStatus.SC_NO_CONTENT);
    return response;
}
```

### 6. Update CORS Headers

Update `createOptionsResponse()` to include DELETE method:

```java
headers.put("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE, OPTIONS");
```

### 7. Build and Deploy

```bash
# Build the project
mvn clean package

# Deploy with Terraform
cd infrastructure
terraform apply -var-file=dev.tfvars
```

### 8. Update API Gateway Routes (if needed)

Ensure the API Gateway has routes configured for:
- `POST /quote/{id}/like`
- `DELETE /quote/{id}/unlike`
- `GET /liked`

---

## Estimated Effort

- **Backend code changes**: ✅ COMPLETED (4-6 hours)
- **Terraform changes**: ✅ COMPLETED (1-2 hours)
- **Handler updates**: ✅ COMPLETED (1-2 hours)
- **Test updates**: ✅ COMPLETED (30 minutes)
- **Deployment**: ⏳ IN PROGRESS (1 hour)
- **Testing**: 🔜 PENDING (2-3 hours)
- **Frontend updates**: 🔜 PENDING (2-4 hours)
- **Total**: ~11-18 hours (75% complete)

---

## Implementation Decisions

1. **User identification**: Use Cognito `username` (not `sub` UUID) as the user identifier
2. **Migration of existing likes**: Not required - existing like counts will be discarded
3. **Rate limiting**: No limits on the number of quotes a user can like
4. **Soft delete**: Hard delete - unliking permanently removes the UserLike record
5. **Notifications**: No notification system for likes
