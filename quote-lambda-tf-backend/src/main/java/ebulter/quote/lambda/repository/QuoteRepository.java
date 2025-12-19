package ebulter.quote.lambda.repository;

import ebulter.quote.lambda.model.Quote;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.util.*;
import java.util.stream.Collectors;

public class QuoteRepository {
    private static final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private static final String TABLE_NAME = System.getenv("DYNAMODB_TABLE");
    private static final String USER_LIKES_TABLE = "UserLikes"; // Update with your actual table name

    public QuoteRepository() {
    }

    public List<Quote> getAllQuotes() {
        // First, get all quotes
        ScanResponse quoteScan = dynamoDb.scan(ScanRequest.builder()
                .tableName(TABLE_NAME)
                .build());

        if (quoteScan.items().isEmpty()) {
            return Collections.emptyList();
        }

        // Extract quote IDs
        List<Integer> quoteIds = quoteScan.items().stream()
                .map(item -> Integer.parseInt(item.get("id").n()))
                .collect(Collectors.toList());

        // Get like counts for all quotes
        Map<Integer, Integer> likeCounts = getLikeCounts(quoteIds);

        // Map to Quote objects with like counts
        return quoteScan.items().stream()
                .map(item -> {
                    Quote quote = new Quote(
                            Integer.parseInt(item.get("id").n()),
                            item.get("quoteText").s(),
                            item.get("author").s(),
                            likeCounts.getOrDefault(Integer.parseInt(item.get("id").n()), 0)
                    );
                    return quote;
                })
                .collect(Collectors.toList());
    }

    private Map<Integer, Integer> getLikeCounts(List<Integer> quoteIds) {
        Map<Integer, Integer> likeCounts = new HashMap<>();

        // Process in batches to avoid query size limits
        int batchSize = 100;
        for (int i = 0; i < quoteIds.size(); i += batchSize) {
            List<Integer> batch = quoteIds.subList(i, Math.min(i + batchSize, quoteIds.size()));

            // Create a map of quote ID to like count for this batch
            Map<Integer, Integer> batchCounts = new HashMap<>();

            // Get all likes for this batch of quote IDs
            for (Integer quoteId : batch) {
                Map<String, AttributeValue> expressionValues = new HashMap<>();
                expressionValues.put(":quoteId", AttributeValue.builder().n(String.valueOf(quoteId)).build());

                QueryRequest queryRequest = QueryRequest.builder()
                        .tableName(USER_LIKES_TABLE)
                        .keyConditionExpression("quoteId = :quoteId")
                        .expressionAttributeValues(expressionValues)
                        .select("COUNT")
                        .build();

                int count = dynamoDb.query(queryRequest).count();
                batchCounts.put(quoteId, count);
            }

            likeCounts.putAll(batchCounts);
        }

        return likeCounts;
    }

    public void saveAll(Set<Quote> quotes) {
        quotes.forEach(this::saveQuote);
    }

    public void saveQuote(Quote quote) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().n(String.valueOf(quote.getId())).build());
        item.put("quoteText", AttributeValue.builder().s(quote.getQuoteText()).build());
        item.put("author", AttributeValue.builder().s(quote.getAuthor()).build());

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build();

        dynamoDb.putItem(putItemRequest);
    }

    public Quote findById(int id) {
        HashMap<String,AttributeValue> keyToGet = new HashMap<>();
        keyToGet.put("id", AttributeValue.builder().n(String.valueOf(id)).build());
        GetItemRequest request = GetItemRequest.builder().tableName(TABLE_NAME).key(keyToGet).build();
        GetItemResponse getItemResponse = dynamoDb.getItem(request);
        if (getItemResponse.hasItem()) {
            Map<String, AttributeValue> item = getItemResponse.item();
            // Get like count for this specific quote
            int likeCount = getLikeCounts(Collections.singletonList(id)).getOrDefault(id, 0);
            return new Quote(
                    Integer.parseInt(item.get("id").n()),
                    item.get("quoteText").s(),
                    item.get("author").s(),
                    likeCount
            );
        } else {
            return null;
        }
    }
}