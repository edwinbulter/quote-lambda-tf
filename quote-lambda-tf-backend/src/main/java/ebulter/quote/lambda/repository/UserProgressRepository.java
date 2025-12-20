package ebulter.quote.lambda.repository;

import ebulter.quote.lambda.model.UserProgress;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;

public class UserProgressRepository {
    private static final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private final String tableName;

    public UserProgressRepository() {
        this.tableName = System.getenv("DYNAMODB_USER_PROGRESS_TABLE");
        if (this.tableName == null) {
            throw new IllegalStateException("DYNAMODB_USER_PROGRESS_TABLE environment variable not set");
        }
    }

    /**
     * Save or update user progress
     */
    public void saveUserProgress(UserProgress userProgress) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("username", AttributeValue.builder().s(userProgress.getUsername()).build());
        item.put("lastQuoteId", AttributeValue.builder().n(String.valueOf(userProgress.getLastQuoteId())).build());
        item.put("updatedAt", AttributeValue.builder().n(String.valueOf(userProgress.getUpdatedAt())).build());

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();

        dynamoDb.putItem(putItemRequest);
    }

    /**
     * Get user progress by username
     */
    public UserProgress getUserProgress(String username) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("username", AttributeValue.builder().s(username).build());

        GetItemRequest getItemRequest = GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .build();

        GetItemResponse response = dynamoDb.getItem(getItemRequest);

        if (response.item() == null || response.item().isEmpty()) {
            return null;
        }

        Map<String, AttributeValue> item = response.item();
        return new UserProgress(
                item.get("username").s(),
                Integer.parseInt(item.get("lastQuoteId").n()),
                Long.parseLong(item.get("updatedAt").n())
        );
    }

    /**
     * Update user's last quote ID
     */
    public void updateLastQuoteId(String username, int lastQuoteId) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("username", AttributeValue.builder().s(username).build());

        Map<String, AttributeValueUpdate> updates = new HashMap<>();
        updates.put("lastQuoteId", AttributeValueUpdate.builder()
                .value(AttributeValue.builder().n(String.valueOf(lastQuoteId)).build())
                .action(AttributeAction.PUT)
                .build());
        updates.put("updatedAt", AttributeValueUpdate.builder()
                .value(AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build())
                .action(AttributeAction.PUT)
                .build());

        UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .attributeUpdates(updates)
                .build();

        dynamoDb.updateItem(updateItemRequest);
    }

    /**
     * Delete user progress
     */
    public void deleteUserProgress(String username) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("username", AttributeValue.builder().s(username).build());

        DeleteItemRequest deleteItemRequest = DeleteItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .build();

        dynamoDb.deleteItem(deleteItemRequest);
    }
}
