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
    private static final String TABLE_NAME = "quotes-lambda-tf-quotes";

    public QuoteRepository() {
    }

    public List<Quote> getAllQuotes() {
        ScanResponse scanResponse = dynamoDb.scan(ScanRequest.builder().tableName(TABLE_NAME).build());
        return scanResponse.items().stream().map(item ->
                new Quote(Integer.parseInt(item.get("id").n()), item.get("quoteText").s(), item.get("author").s(), Integer.parseInt(item.get("likes").n()))).toList();
    }

    public void saveAll(Set<Quote> quotes) {
        quotes.forEach(this::saveQuote);
    }

    public void saveQuote(Quote quote) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().n(String.valueOf(quote.getId())).build());
        item.put("quoteText", AttributeValue.builder().s(quote.getQuoteText()).build());
        item.put("author", AttributeValue.builder().s(quote.getAuthor()).build());
        item.put("likes", AttributeValue.builder().n(String.valueOf(quote.getLikes())).build());

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
            return new Quote(Integer.parseInt(item.get("id").n()), item.get("quoteText").s(), item.get("author").s(), Integer.parseInt(item.get("likes").n()));
        } else {
            return null;
        }
    }

    public void updateLikes(Quote quote) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("id", AttributeValue.builder().n(String.valueOf(quote.getId())).build());

        Map<String, AttributeValueUpdate> attributeUpdates = new HashMap<>();
        AttributeValueUpdate attributeValueUpdate = AttributeValueUpdate.builder()
                .value(AttributeValue.builder().n(String.valueOf(quote.getLikes())).build())
                .action(AttributeAction.PUT)
                .build();
        attributeUpdates.put("likes", attributeValueUpdate);

        UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
                .attributeUpdates(attributeUpdates)
                .build();

        dynamoDb.updateItem(updateItemRequest);
    }

    public List<Quote> getLikedQuotes() {
        return getAllQuotes().stream().filter(quote -> quote.getLikes() > 0).sorted((item1, item2) -> {
            int compareLikes = item2.getLikes() - item1.getLikes();
            if (compareLikes != 0) {
                return compareLikes;
            } else {
                return item1.getId() - item2.getId();
            }
        }).toList();
    }
}
