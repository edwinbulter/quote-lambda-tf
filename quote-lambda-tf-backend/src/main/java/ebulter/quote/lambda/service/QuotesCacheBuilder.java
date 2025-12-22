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
     * Build and upload cache to S3 with conditional operations and retry logic
     */
    public void buildAndUploadCache() {
        final int MAX_RETRIES = 3;
        int retryCount = 0;

        while (retryCount < MAX_RETRIES) {
            try {
                buildAndUploadCacheWithRetry();
                return; // Success, exit retry loop
            } catch (Exception e) {
                if (e instanceof S3Exception && ((S3Exception) e).statusCode() == 412) { // Precondition Failed
                    retryCount++;
                    if (retryCount >= MAX_RETRIES) {
                        logger.error("Failed to update cache after {} attempts", MAX_RETRIES, e);
                        throw new RuntimeException("Cache update failed due to concurrent modifications", e);
                    }

                    // Simple exponential backoff (Phase 1 - basic implementation)
                    try {
                        Thread.sleep((long) Math.pow(2, retryCount) * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Cache update interrupted", ie);
                    }
                } else if (e.getMessage() != null && e.getMessage().contains("Cache update conflict detected")) {
                    retryCount++;
                    if (retryCount >= MAX_RETRIES) {
                        logger.error("Failed to update cache after {} attempts", MAX_RETRIES, e);
                        throw new RuntimeException("Cache update failed due to concurrent modifications", e);
                    }

                    // Simple exponential backoff (Phase 1 - basic implementation)
                    try {
                        Thread.sleep((long) Math.pow(2, retryCount) * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Cache update interrupted", ie);
                    }
                } else {
                    throw new RuntimeException("Cache update failed", e);
                }
            }
        }
    }

    private void buildAndUploadCacheWithRetry() throws Exception {
        logger.info("Building quotes cache for S3 upload with conditional protection");

        // Step 1: Get current cache metadata
        HeadObjectResponse currentMetadata = getCurrentCacheMetadata();
        String currentVersion = currentMetadata != null ?
                currentMetadata.metadata().get("version") : "0";
        String currentETag = currentMetadata != null ?
                currentMetadata.eTag() : null;

        // Step 2: Build new cache data
        List<Quote> allQuotes = quoteRepository.getAllQuotes();
        logger.info("Loaded {} quotes from DynamoDB", allQuotes.size());

        Map<String, Map<String, List<Integer>>> sortedIndices = buildSortedIndices(allQuotes);
        int newVersion = Integer.parseInt(currentVersion) + 1;

        QuotesCacheData cacheData = new QuotesCacheData(
                newVersion,
                Instant.now(),
                allQuotes.size(),
                allQuotes,
                sortedIndices
        );

        // Step 3: Serialize to JSON
        String jsonCache = objectMapper.writeValueAsString(cacheData);

        // Step 4: Upload to S3
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(CACHE_KEY)
                .metadata(Map.of("version", String.valueOf(newVersion)))
                .build();

        try {
            s3Client.putObject(putRequest, RequestBody.fromString(jsonCache));
            logger.info("Successfully uploaded cache version {}", newVersion);
        } catch (S3Exception e) {
            if (e.statusCode() == 412 || (e.getMessage() != null && e.getMessage().contains("Cache update conflict detected"))) {
                logger.warn("Cache update conflict detected. Another instance updated the cache.");
                throw e; // Re-throw to trigger retry logic
            } else {
                throw e; // Re-throw other S3 exceptions
            }
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
