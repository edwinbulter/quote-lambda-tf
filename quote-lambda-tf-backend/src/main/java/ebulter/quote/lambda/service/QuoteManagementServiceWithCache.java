package ebulter.quote.lambda.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import ebulter.quote.lambda.model.Quote;
import ebulter.quote.lambda.model.QuoteAddResponse;
import ebulter.quote.lambda.model.QuotePageResponse;
import ebulter.quote.lambda.model.QuoteWithLikeCount;
import ebulter.quote.lambda.model.QuotesCache;
import ebulter.quote.lambda.model.QuotesCacheData;
import ebulter.quote.lambda.repository.QuoteRepository;
import ebulter.quote.lambda.repository.UserLikeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import java.util.UUID;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Quote management service with S3 caching support
 */
public class QuoteManagementServiceWithCache {
    private static final Logger logger = LoggerFactory.getLogger(QuoteManagementServiceWithCache.class);
    private static final String CACHE_KEY = "quotes-cache.json";
    
    // Static cache that persists across Lambda invocations
    private static volatile QuotesCache cache = null;
    private static volatile long lastCacheCheck = 0;
    private static final long CACHE_CHECK_INTERVAL_MS = 60000; // Check cache freshness every minute
    
    private final QuoteRepository quoteRepository;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;
    private final String bucketName;
    private final QuotesCacheBuilder cacheBuilder;
    
    public QuoteManagementServiceWithCache(QuoteRepository quoteRepository, 
                                         S3Client s3Client,
                                         ObjectMapper objectMapper,
                                         String bucketName) {
        this.quoteRepository = quoteRepository;
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
        this.bucketName = bucketName;
        this.cacheBuilder = new QuotesCacheBuilder(bucketName);
        this.cacheBuilder.setQuoteRepository(quoteRepository);
        this.cacheBuilder.setS3Client(s3Client);
        this.cacheBuilder.setObjectMapper(objectMapper);
    }
    
    /**
     * Get quotes with pagination using S3 cache
     */
    public QuotePageResponse getQuotesWithPagination(int page, int pageSize, 
            String quoteText, String author, String sortBy, String sortOrder) {
        
        logger.info("Getting quotes: page={}, pageSize={}, quoteText={}, author={}, sortBy={}, sortOrder={}", 
                page, pageSize, quoteText, author, sortBy, sortOrder);
        
        try {
            // Try cache first
            try {
                return getFromCache(page, pageSize, quoteText, author, sortBy, sortOrder);
            } catch (Exception e) {
                logger.warn("Cache miss or error, falling back to DynamoDB", e);
                return getFromDynamoDB(page, pageSize, quoteText, author, sortBy, sortOrder);
            }
        } catch (Exception e) {
            logger.error("Failed to get quotes with pagination", e);
            throw new RuntimeException("Failed to get quotes: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get quotes from S3 cache
     */
    private QuotePageResponse getFromCache(int page, int pageSize, 
            String quoteText, String author, String sortBy, String sortOrder) throws Exception {
        
        ensureCacheLoaded();
        checkCacheFreshness();
        
        if (cache == null) {
            throw new RuntimeException("Cache not available");
        }
        
        // Normalize sort key
        String normalizedSortBy = normalizeSortKey(sortBy);
        
        // Get sorted indices for the requested sort
        List<Integer> sortedIds = cache.sortedIndices
            .get(normalizedSortBy)
            .get(sortOrder.toLowerCase());
            
        if (sortedIds == null) {
            throw new IllegalArgumentException("Invalid sort order: " + sortOrder);
        }
        
        // Apply filters if needed
        List<Integer> filteredIds = sortedIds;
        if (quoteText != null || author != null) {
            filteredIds = sortedIds.stream()
                .filter(id -> {
                    Quote quote = cache.quoteMap.get(id);
                    boolean textMatch = quoteText == null || 
                        quote.getQuoteText().toLowerCase().contains(quoteText.toLowerCase());
                    boolean authorMatch = author == null || 
                        quote.getAuthor().toLowerCase().contains(author.toLowerCase());
                    return textMatch && authorMatch;
                })
                .collect(Collectors.toList());
        }
        
        // Apply pagination
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, filteredIds.size());
        
        List<QuoteWithLikeCount> pageQuotes = new ArrayList<>();
        for (int i = startIndex; i < endIndex; i++) {
            Quote quote = cache.quoteMap.get(filteredIds.get(i));
            pageQuotes.add(new QuoteWithLikeCount(
                quote.getId(),
                quote.getQuoteText(),
                quote.getAuthor(),
                quote.getLikeCount()
            ));
        }
        
        int totalCount = filteredIds.size();
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        
        logger.info("Returning page {} of {} with {} quotes from cache", 
            page, totalPages, pageQuotes.size());
        
        return new QuotePageResponse(pageQuotes, totalCount, page, pageSize, totalPages);
    }
    
    /**
     * Get quotes from DynamoDB (fallback)
     */
    private QuotePageResponse getFromDynamoDB(int page, int pageSize, 
            String quoteText, String author, String sortBy, String sortOrder) {
        
        logger.info("Falling back to DynamoDB for quotes");
        
        // Get all quotes
        List<Quote> allQuotes = quoteRepository.getAllQuotes();
        logger.info("Retrieved {} quotes from database", allQuotes.size());
        
        // Apply filters
        List<Quote> filteredQuotes = allQuotes.stream()
            .filter(q -> quoteText == null || quoteText.isEmpty() || 
                    q.getQuoteText().toLowerCase().contains(quoteText.toLowerCase()))
            .filter(q -> author == null || author.isEmpty() || 
                    q.getAuthor().toLowerCase().contains(author.toLowerCase()))
            .collect(Collectors.toList());
        
        // Apply sorting
        Comparator<Quote> comparator = getComparator(sortBy, sortOrder);
        filteredQuotes.sort(comparator);
        
        // Calculate pagination
        int totalCount = filteredQuotes.size();
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalCount);
        
        // Validate page bounds
        if (startIndex >= totalCount && totalCount > 0) {
            startIndex = 0;
            endIndex = Math.min(pageSize, totalCount);
            page = 1;
        }
        
        // Get page of quotes
        List<Quote> pageQuotes = startIndex < totalCount ? 
                filteredQuotes.subList(startIndex, endIndex) : 
                Collections.emptyList();
        
        // Convert to QuoteWithLikeCount
        List<QuoteWithLikeCount> quotesWithLikes = pageQuotes.stream()
            .map(q -> new QuoteWithLikeCount(
                q.getId(),
                q.getQuoteText(),
                q.getAuthor(),
                q.getLikeCount()
            ))
            .collect(Collectors.toList());
        
        return new QuotePageResponse(quotesWithLikes, totalCount, page, pageSize, totalPages);
    }
    
    /**
     * Fetch and add new quotes, then rebuild cache
     */
    public QuoteAddResponse fetchAndAddNewQuotes(String requestingUsername) {
        logger.info("Fetching new quotes from ZEN API (requested by {})", requestingUsername);
        
        try {
            // Get current quotes
            List<Quote> currentDatabaseQuotes = quoteRepository.getAllQuotes();
            int initialCount = currentDatabaseQuotes.size();
            
            // Fetch from ZEN API
            Set<Quote> fetchedQuotes = ebulter.quote.lambda.client.ZenClient.getSomeUniqueQuotes();
            
            // Remove duplicates
            fetchedQuotes.removeAll(new HashSet<>(currentDatabaseQuotes));
            
            if (fetchedQuotes.isEmpty()) {
                return new QuoteAddResponse(0, initialCount, "No new quotes to add");
            }
            
            // Assign new IDs
            AtomicInteger idGenerator = new AtomicInteger(initialCount + 1);
            fetchedQuotes.forEach(quote -> quote.setId(idGenerator.getAndIncrement()));
            
            // Save to database
            quoteRepository.saveAll(fetchedQuotes);
            
            // Rebuild and upload cache
            logger.info("Rebuilding cache after adding {} new quotes", fetchedQuotes.size());
            cacheBuilder.buildAndUploadCache();
            
            // Clear in-memory cache to force reload
            clearCache();
            
            int quotesAdded = fetchedQuotes.size();
            int totalQuotes = initialCount + quotesAdded;
            
            return new QuoteAddResponse(quotesAdded, totalQuotes, 
                "Successfully added " + quotesAdded + " new quotes");
                
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch quotes from ZEN API: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to add new quotes: " + e.getMessage(), e);
        }
    }
    
    /**
     * Ensure cache is loaded
     */
    private void ensureCacheLoaded() throws Exception {
        if (cache == null) {
            synchronized (QuoteManagementServiceWithCache.class) {
                if (cache == null) {
                    loadCacheFromS3();
                }
            }
        }
    }
    
    /**
     * Check cache freshness
     */
    private void checkCacheFreshness() throws Exception {
        long now = System.currentTimeMillis();
        if (now - lastCacheCheck > CACHE_CHECK_INTERVAL_MS) {
            synchronized (QuoteManagementServiceWithCache.class) {
                if (now - lastCacheCheck > CACHE_CHECK_INTERVAL_MS) {
                    try {
                        HeadObjectRequest request = HeadObjectRequest.builder()
                            .bucket(bucketName)
                            .key(CACHE_KEY)
                            .build();
                            
                        var metadata = s3Client.headObject(request);
                        int s3Version = Integer.parseInt(metadata.metadata().get("version"));
                        
                        if (cache == null || cache.version != s3Version) {
                            loadCacheFromS3();
                        }
                        lastCacheCheck = now;
                    } catch (S3Exception e) {
                        if (e.statusCode() == 404) {
                            // Cache doesn't exist, build it
                            cacheBuilder.buildAndUploadCache();
                            loadCacheFromS3();
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Load cache from S3
     */
    private void loadCacheFromS3() throws Exception {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(CACHE_KEY)
                .build();
                
            try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request)) {
                String jsonContent = new String(response.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                
                QuotesCacheData data = objectMapper.readValue(jsonContent, QuotesCacheData.class);
                
                cache = new QuotesCache(
                    data.getVersion(),
                    data.getQuotes(),
                    data.getSortedIndices()
                );
                
                logger.info("Loaded cache version {} with {} quotes", 
                    cache.version, cache.quotes.size());
            }
                
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                // Cache doesn't exist, build it
                logger.info("Cache not found in S3, building initial cache");
                cacheBuilder.buildAndUploadCache();
                loadCacheFromS3();
            } else {
                throw e;
            }
        }
    }
    
    /**
     * Clear in-memory cache
     */
    public static void clearCache() {
        cache = null;
        lastCacheCheck = 0;
        logger.info("In-memory cache cleared");
    }
    
    /**
     * Refresh cache manually with debug logging
     */
    public void refreshCache() {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        logger.info("[{}] Manual cache refresh requested", requestId);
        
        try {
            long refreshStart = System.currentTimeMillis();
            cacheBuilder.buildAndUploadCache();
            clearCache();
            long refreshDuration = System.currentTimeMillis() - refreshStart;
            logger.info("[{}] Cache refresh completed successfully in {}ms", 
                requestId, refreshDuration);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("concurrent modifications")) {
                logger.warn("[{}] Cache refresh failed due to concurrent updates. " +
                    "Another instance may have updated the cache.", requestId);
            } else {
                logger.error("[{}] Cache refresh failed", requestId, e);
                throw e;
            }
        }
    }
    
    /**
     * Normalize sort key to match cache indices
     */
    private String normalizeSortKey(String sortBy) {
        if (sortBy == null) return "id";
        return switch (sortBy.toLowerCase()) {
            case "quotetext" -> "quoteText";
            case "author" -> "author";
            case "likecount" -> "likeCount";
            default -> "id";
        };
    }
    
    /**
     * Get comparator for DynamoDB fallback
     */
    private Comparator<Quote> getComparator(String sortBy, String sortOrder) {
        Comparator<Quote> comparator = switch (sortBy != null ? sortBy.toLowerCase() : "id") {
            case "quotetext" -> Comparator.comparing(q -> q.getQuoteText().toLowerCase());
            case "author" -> Comparator.comparing(q -> q.getAuthor().toLowerCase());
            case "likecount" -> {
                if ("desc".equalsIgnoreCase(sortOrder)) {
                    Comparator<Quote> likeCountComparator = Comparator.comparingInt(Quote::getLikeCount).reversed();
                    comparator = likeCountComparator.thenComparingInt(Quote::getId);
                } else {
                    Comparator<Quote> likeCountComparator = Comparator.comparingInt(Quote::getLikeCount);
                    comparator = likeCountComparator.thenComparingInt(Quote::getId);
                }
                yield comparator;
            }
            default -> Comparator.comparingInt(Quote::getId);
        };

        if ("desc".equalsIgnoreCase(sortOrder) && !"likecount".equalsIgnoreCase(sortBy)) {
            comparator = comparator.reversed();
        }
        
        return comparator;
    }
}
