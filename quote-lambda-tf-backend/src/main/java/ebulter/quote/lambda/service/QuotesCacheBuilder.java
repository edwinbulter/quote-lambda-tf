package ebulter.quote.lambda.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import ebulter.quote.lambda.model.Quote;
import ebulter.quote.lambda.model.QuotesCacheData;
import ebulter.quote.lambda.repository.QuoteRepository;
import ebulter.quote.lambda.util.TimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
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
    private TimeProvider timeProvider;

    private final String bucketName;

    public QuotesCacheBuilder(String bucketName) {
        this(bucketName, new ebulter.quote.lambda.util.SystemTimeProvider());
    }

    public QuotesCacheBuilder(String bucketName, TimeProvider timeProvider) {
        this.bucketName = bucketName;
        this.timeProvider = timeProvider;
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

    public void setTimeProvider(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    /**
     * Build and upload cache to S3 with conditional operations and enhanced retry logic with jitter
     */
    public void buildAndUploadCache() {
        final int MAX_RETRIES = 5; // Increased for Phase 2
        final long BASE_DELAY_MS = 1000; // 1 second base delay
        final long MAX_DELAY_MS = 30000; // 30 seconds max delay
        int retryCount = 0;
        String requestId = UUID.randomUUID().toString();
        long operationStartTime = timeProvider.currentTimeMillis();

        logger.info("[{}] Starting cache update operation with enhanced retry logic", requestId);

        while (retryCount < MAX_RETRIES) {
            try {
                buildAndUploadCacheWithRetry();
                long operationTime = timeProvider.currentTimeMillis() - operationStartTime;
                logger.info("[{}] Cache update operation completed successfully in {}ms with {} retries", 
                    requestId, operationTime, retryCount);
                return; // Success, exit retry loop
            } catch (Exception e) {
                if (e instanceof S3Exception && ((S3Exception) e).statusCode() == 412) { // Precondition Failed
                    retryCount++;
                    if (retryCount >= MAX_RETRIES) {
                        long operationTime = timeProvider.currentTimeMillis() - operationStartTime;
                        logger.error("[{}] Failed to update cache after {} attempts in {}ms", 
                            requestId, MAX_RETRIES, operationTime, e);
                        throw new RuntimeException("Cache update failed due to concurrent modifications", e);
                    }

                    // Enhanced exponential backoff with jitter (Phase 2)
                    long delay = calculateBackoffWithJitter(retryCount, BASE_DELAY_MS, MAX_DELAY_MS);
                    logger.warn("[{}] Cache conflict detected, retry attempt {} in {}ms", requestId, retryCount, delay);
                    
                    try {
                        timeProvider.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("[{}] Cache update interrupted during retry", requestId, ie);
                        throw new RuntimeException("Cache update interrupted", ie);
                    }
                } else if (e.getMessage() != null && e.getMessage().contains("Cache update conflict detected")) {
                    retryCount++;
                    if (retryCount >= MAX_RETRIES) {
                        long operationTime = timeProvider.currentTimeMillis() - operationStartTime;
                        logger.error("[{}] Failed to update cache after {} attempts in {}ms", 
                            requestId, MAX_RETRIES, operationTime, e);
                        throw new RuntimeException("Cache update failed due to concurrent modifications", e);
                    }

                    // Enhanced exponential backoff with jitter (Phase 2)
                    long delay = calculateBackoffWithJitter(retryCount, BASE_DELAY_MS, MAX_DELAY_MS);
                    logger.warn("[{}] Cache conflict detected, retry attempt {} in {}ms", requestId, retryCount, delay);
                    
                    try {
                        timeProvider.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("[{}] Cache update interrupted during retry", requestId, ie);
                        throw new RuntimeException("Cache update interrupted", ie);
                    }
                } else {
                    long operationTime = timeProvider.currentTimeMillis() - operationStartTime;
                    logger.error("[{}] Unexpected error during cache update after {}ms", requestId, operationTime, e);
                    throw new RuntimeException("Cache update failed", e);
                }
            }
        }
    }

    /**
     * Calculate exponential backoff with jitter to prevent thundering herd
     */
    private long calculateBackoffWithJitter(int retryCount, long baseDelayMs, long maxDelayMs) {
        // Exponential backoff: baseDelay * 2^retryCount
        long exponentialDelay = baseDelayMs * (1L << Math.min(retryCount, 10));
        
        // Cap at max delay
        exponentialDelay = Math.min(exponentialDelay, maxDelayMs);
        
        // Add jitter: ±25% of the delay
        double jitterFactor = 0.75 + (Math.random() * 0.5); // Random between 0.75 and 1.25
        long jitteredDelay = (long) (exponentialDelay * jitterFactor);
        
        return jitteredDelay;
    }

    private void buildAndUploadCacheWithRetry() throws Exception {
        String requestId = UUID.randomUUID().toString();
        logger.info("[{}] Building quotes cache for S3 upload with conditional protection", requestId);

        // Step 1: Get current cache metadata
        HeadObjectResponse currentMetadata = getCurrentCacheMetadata();
        String currentVersion = currentMetadata != null ?
                currentMetadata.metadata().get("version") : "0";
        String currentETag = currentMetadata != null ?
                currentMetadata.eTag() : null;
        
        logger.debug("[{}] Current cache state - version: {}, etag: {}, exists: {}", 
            requestId, currentVersion, currentETag, currentMetadata != null);

        // Step 2: Build new cache data
        long startTime = timeProvider.currentTimeMillis();
        List<Quote> allQuotes = quoteRepository.getAllQuotes();
        long loadTime = timeProvider.currentTimeMillis() - startTime;
        
        logger.info("[{}] Loaded {} quotes from DynamoDB in {}ms", requestId, allQuotes.size(), loadTime);
        logger.debug("[{}] Quote sample: {}", requestId, 
            allQuotes.stream().limit(3).map(q -> q.getId() + ":" + q.getQuoteText().substring(0, Math.min(30, q.getQuoteText().length()))).toList());

        Map<String, Map<String, List<Integer>>> sortedIndices = buildSortedIndices(allQuotes);
        int newVersion = Integer.parseInt(currentVersion) + 1;

        QuotesCacheData cacheData = new QuotesCacheData(
                newVersion,
                Instant.now(),
                allQuotes.size(),
                allQuotes,
                sortedIndices
        );

        logger.debug("[{}] New cache data - version: {}, quotes: {}, indices: {}", 
            requestId, newVersion, allQuotes.size(), sortedIndices.keySet());

        // Step 3: Serialize to JSON
        startTime = timeProvider.currentTimeMillis();
        String jsonCache = objectMapper.writeValueAsString(cacheData);
        long serializationTime = timeProvider.currentTimeMillis() - startTime;
        long jsonSize = jsonCache.getBytes().length;
        
        logger.info("[{}] Serialized cache to JSON in {}ms, size: {} bytes", requestId, serializationTime, jsonSize);
        logger.debug("[{}] JSON preview: {}", requestId, jsonCache.substring(0, Math.min(200, jsonCache.length())));

        // Step 4: Upload to S3
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(CACHE_KEY)
                .metadata(Map.of("version", String.valueOf(newVersion)))
                .build();

        logger.info("[{}] Attempting to upload cache version {} to S3 bucket {}", 
            requestId, newVersion, bucketName);

        try {
            startTime = timeProvider.currentTimeMillis();
            s3Client.putObject(putRequest, RequestBody.fromString(jsonCache));
            long uploadTime = timeProvider.currentTimeMillis() - startTime;
            
            logger.info("[{}] Successfully uploaded cache version {} to S3 in {}ms", 
                requestId, newVersion, uploadTime);
            logger.info("[{}] Cache update completed - version {} (previous: {}), size: {} bytes", 
                requestId, newVersion, currentVersion, jsonSize);
                
        } catch (S3Exception e) {
            logger.error("[{}] S3 upload failed - status: {}, error: {}, request: {}", 
                requestId, e.statusCode(), e.getMessage(), putRequest);
            
            if (e.statusCode() == 412 || (e.getMessage() != null && e.getMessage().contains("Cache update conflict detected"))) {
                logger.warn("[{}] Cache update conflict detected. Another instance updated the cache.", requestId);
                logger.debug("[{}] Conflict details - expected version: {}, current etag: {}", 
                    requestId, newVersion, currentETag);
                throw e; // Re-throw to trigger retry logic
            } else {
                logger.error("[{}] Unexpected S3 error during cache upload", requestId, e);
                throw e; // Re-throw other S3 exceptions
            }
        } catch (Exception e) {
            logger.error("[{}] Unexpected error during cache upload", requestId, e);
            throw e;
        }
    }

    private HeadObjectResponse getCurrentCacheMetadata() {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(CACHE_KEY)
                    .build();
            return s3Client.headObject(request);
        } catch (NoSuchKeyException e) {
            logger.info("No existing cache found, creating new cache");
            return null;
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
}
