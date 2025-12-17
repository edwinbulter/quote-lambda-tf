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

    /**
     * Delete all views for a specific user
     */
    public void deleteAllViewsForUser(String username) {
        List<UserView> views = getViewsByUser(username);
        for (UserView view : views) {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("username", AttributeValue.builder().s(username).build());
            key.put("quoteId", AttributeValue.builder().n(String.valueOf(view.getQuoteId())).build());

            DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(key)
                    .build();

            dynamoDb.deleteItem(deleteRequest);
        }
    }
}
