package ebulter.quote.lambda.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import ebulter.quote.lambda.model.Quote;
import ebulter.quote.lambda.model.QuotesCacheData;
import ebulter.quote.lambda.repository.QuoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for building and uploading quotes cache to S3
 */
public class QuotesCacheBuilder {
    private static final Logger logger = LoggerFactory.getLogger(QuotesCacheBuilder.class);
    private static final String CACHE_KEY = "quotes-cache.json";
    
    private QuoteRepository quoteRepository;
    private S3Client s3Client;
    private ObjectMapper objectMapper;
    
    private final String bucketName;
    
    public QuotesCacheBuilder(String bucketName) {
        this.bucketName = bucketName;
    }
    
    // Setters for dependency injection
    public void setQuoteRepository(QuoteRepository quoteRepository) {
        this.quoteRepository = quoteRepository;
    }
    
    public void setS3Client(S3Client s3Client) {
        this.s3Client = s3Client;
    }
    
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Build and upload cache to S3
     */
    public void buildAndUploadCache() {
        try {
            logger.info("Building quotes cache for S3 upload");
            
            // 1. Load all quotes from DynamoDB
            List<Quote> allQuotes = quoteRepository.getAllQuotes();
            logger.info("Loaded {} quotes from DynamoDB", allQuotes.size());
            
            // 2. Build sorted indices
            Map<String, Map<String, List<Integer>>> sortedIndices = buildSortedIndices(allQuotes);
            
            // 3. Get next version
            int version = getNextVersion();
            
            // 4. Create cache data
            QuotesCacheData cacheData = new QuotesCacheData(
                version,
                Instant.now(),
                allQuotes.size(),
                allQuotes,
                sortedIndices
            );
            
            // 5. Upload to S3
            uploadToS3(cacheData);
            
            logger.info("Successfully built and uploaded cache version {} with {} quotes", 
                version, allQuotes.size());
                
        } catch (Exception e) {
            logger.error("Failed to build and upload cache", e);
            throw new RuntimeException("Failed to build cache: " + e.getMessage(), e);
        }
    }
    
    /**
     * Build sorted indices for all sort options
     */
    private Map<String, Map<String, List<Integer>>> buildSortedIndices(List<Quote> allQuotes) {
        Map<String, Map<String, List<Integer>>> indices = new HashMap<>();
        
        // Sort by ID
        indices.put("id", Map.of(
            "asc", allQuotes.stream()
                .sorted(Comparator.comparingInt(Quote::getId))
                .map(Quote::getId)
                .collect(Collectors.toList()),
            "desc", allQuotes.stream()
                .sorted(Comparator.comparingInt(Quote::getId).reversed())
                .map(Quote::getId)
                .collect(Collectors.toList())
        ));
        
        // Sort by quoteText
        indices.put("quoteText", Map.of(
            "asc", allQuotes.stream()
                .sorted(Comparator.comparing((Quote q) -> q.getQuoteText().toLowerCase()))
                .map(Quote::getId)
                .collect(Collectors.toList()),
            "desc", allQuotes.stream()
                .sorted(Comparator.comparing((Quote q) -> q.getQuoteText().toLowerCase()).reversed())
                .map(Quote::getId)
                .collect(Collectors.toList())
        ));
        
        // Sort by author
        indices.put("author", Map.of(
            "asc", allQuotes.stream()
                .sorted(Comparator.comparing((Quote q) -> q.getAuthor().toLowerCase()))
                .map(Quote::getId)
                .collect(Collectors.toList()),
            "desc", allQuotes.stream()
                .sorted(Comparator.comparing((Quote q) -> q.getAuthor().toLowerCase()).reversed())
                .map(Quote::getId)
                .collect(Collectors.toList())
        ));
        
        // Sort by likeCount (with ID tiebreaker)
        indices.put("likeCount", Map.of(
            "asc", allQuotes.stream()
                .sorted(Comparator.comparingInt(Quote::getLikeCount)
                    .thenComparingInt(Quote::getId))
                .map(Quote::getId)
                .collect(Collectors.toList()),
            "desc", allQuotes.stream()
                .sorted(Comparator.comparingInt(Quote::getLikeCount).reversed()
                    .thenComparingInt(Quote::getId))
                .map(Quote::getId)
                .collect(Collectors.toList())
        ));
        
        return indices;
    }
    
    /**
     * Get next version number
     */
    private int getNextVersion() {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(CACHE_KEY)
                .build();
                
            var metadata = s3Client.headObject(request);
            String versionStr = metadata.metadata().get("version");
            return versionStr != null ? Integer.parseInt(versionStr) + 1 : 1;
            
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return 1; // First version
            }
            throw e;
        }
    }
    
    /**
     * Upload cache to S3
     */
    private void uploadToS3(QuotesCacheData cacheData) throws Exception {
        String json = objectMapper.writeValueAsString(cacheData);
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", String.valueOf(cacheData.getVersion()));
        
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(CACHE_KEY)
            .metadata(metadata)
            .build();
            
        s3Client.putObject(request, RequestBody.fromString(json));
        
        logger.debug("Uploaded cache to S3: bucket={}, key={}, version={}", 
            bucketName, CACHE_KEY, cacheData.getVersion());
    }
}
