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
        
        if (userLike.getOrder() != null) {
            item.put("order", AttributeValue.builder().n(String.valueOf(userLike.getOrder())).build());
        }

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build();

        dynamoDb.putItem(putItemRequest);
    }

    /**
     * Get all quotes liked by a specific user, sorted by order
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
                .map(item -> {
                    Integer order = null;
                    if (item.containsKey("order")) {
                        order = Integer.parseInt(item.get("order").n());
                    }
                    return new UserLike(
                            item.get("username").s(),
                            Integer.parseInt(item.get("quoteId").n()),
                            Long.parseLong(item.get("likedAt").n()),
                            order
                    );
                })
                .sorted((a, b) -> {
                    // Sort by order (ascending), null values go to end
                    if (a.getOrder() == null && b.getOrder() == null) return 0;
                    if (a.getOrder() == null) return 1;
                    if (b.getOrder() == null) return -1;
                    return Integer.compare(a.getOrder(), b.getOrder());
                })
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

    /**
     * Get the maximum order value for a user's likes
     * Returns 0 if user has no likes
     */
    public int getMaxOrderForUser(String username) {
        List<UserLike> likes = getLikesByUser(username);
        return likes.stream()
                .filter(like -> like.getOrder() != null)
                .mapToInt(UserLike::getOrder)
                .max()
                .orElse(0);
    }
}
