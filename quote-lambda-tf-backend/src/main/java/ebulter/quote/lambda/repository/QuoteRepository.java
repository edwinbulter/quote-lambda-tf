package ebulter.quote.lambda.repository;

import ebulter.quote.lambda.model.Quote;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class QuoteRepository {
    private static final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private static final String TABLE_NAME = System.getenv("DYNAMODB_TABLE");

    public QuoteRepository() {
    }

    public List<Quote> getAllQuotes() {
        ScanResponse scanResponse = dynamoDb.scan(ScanRequest.builder().tableName(TABLE_NAME).build());
        return scanResponse.items().stream().map(item ->
                new Quote(Integer.parseInt(item.get("id").n()), item.get("quoteText").s(), item.get("author").s())).toList();
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
            return new Quote(Integer.parseInt(item.get("id").n()), item.get("quoteText").s(), item.get("author").s());
        } else {
            return null;
        }
    }

}
