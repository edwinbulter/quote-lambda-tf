package ebulter.quote.lambda.model;

import java.util.List;
import java.util.Map;

/**
 * Cache holder for quotes data loaded from S3
 */
public class QuotesCache {
    public int version;
    public List<Quote> quotes;
    public Map<String, Map<String, List<Integer>>> sortedIndices;
    public Map<Integer, Quote> quoteMap; // Fast ID lookup
    
    public QuotesCache() {}
    
    public QuotesCache(int version, List<Quote> quotes, 
                      Map<String, Map<String, List<Integer>>> sortedIndices) {
        this.version = version;
        this.quotes = quotes;
        this.sortedIndices = sortedIndices;
        // Build ID lookup map
        this.quoteMap = quotes.stream()
            .collect(java.util.stream.Collectors.toMap(Quote::getId, q -> q));
    }
}
