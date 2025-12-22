package ebulter.quote.lambda.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * JSON structure for quotes cache stored in S3
 */
public class QuotesCacheData {
    @JsonProperty("version")
    private int version;
    
    @JsonProperty("lastUpdated")
    private Instant lastUpdated;
    
    @JsonProperty("totalCount")
    private int totalCount;
    
    @JsonProperty("quotes")
    private List<Quote> quotes;
    
    @JsonProperty("sortedIndices")
    private Map<String, Map<String, List<Integer>>> sortedIndices;
    
    public QuotesCacheData() {}
    
    public QuotesCacheData(int version, Instant lastUpdated, int totalCount,
                          List<Quote> quotes, Map<String, Map<String, List<Integer>>> sortedIndices) {
        this.version = version;
        this.lastUpdated = lastUpdated;
        this.totalCount = totalCount;
        this.quotes = quotes;
        this.sortedIndices = sortedIndices;
    }
    
    // Getters and setters
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    
    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
    
    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    
    public List<Quote> getQuotes() { return quotes; }
    public void setQuotes(List<Quote> quotes) { this.quotes = quotes; }
    
    public Map<String, Map<String, List<Integer>>> getSortedIndices() { return sortedIndices; }
    public void setSortedIndices(Map<String, Map<String, List<Integer>>> sortedIndices) { 
        this.sortedIndices = sortedIndices; 
    }
}
