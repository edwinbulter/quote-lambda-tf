# ECS-30: S3 Cache Concurrent Update Protection - Implementation Steps

## Table of Contents

1. [Chosen Implementation](#chosen-implementation)
2. [Phase 1: S3 Conditional Operations Implementation](#phase-1-s3-conditional-operations-implementation)
   - 2.1. Step 1: Update QuotesCacheBuilder.java
   - 2.2. Step 2: Update QuoteManagementServiceWithCache.java
   - 2.3. Step 3: Add Unit Tests
3. [Phase 2: Enhanced Reliability Implementation](#phase-2-enhanced-reliability-implementation)
   - 3.1. Step 1: Enhance Retry Logic with Jitter
   - 3.2. Step 2: Add Debug Logging for Verification
4. [Verification Steps](#verification-steps)
5. [Success Criteria](#success-criteria)

## Chosen Implementation

**Solution 1: S3 Conditional Operations** has been selected for Phase 1 implementation due to:
- Minimal code changes required
- No additional infrastructure needed
- Immediate protection against race conditions
- Built-in atomic operations provided by S3

**Phase 2: Enhanced Reliability** will build upon Phase 1 by adding:
- Exponential backoff with jitter for retry logic
- Comprehensive debug logging for verification
- Advanced retry patterns to prevent thundering herd

## Phase 1: S3 Conditional Operations Implementation

### Step 1: Update QuotesCacheBuilder.java

**File**: `/quote-lambda-tf-backend/src/main/java/ebulter/quote/lambda/service/QuotesCacheBuilder.java`

```java
// Add these imports
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ConditionalCheckFailedException;
import software.amazon.awssdk.core.sync.RequestBody;

// Modify buildAndUploadCache method with basic retry logic
public void buildAndUploadCache() {
    final int MAX_RETRIES = 3;
    int retryCount = 0;
    
    while (retryCount < MAX_RETRIES) {
        try {
            buildAndUploadCacheWithRetry();
            return; // Success, exit retry loop
        } catch (ConditionalCheckFailedException e) {
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
    
    // Step 4: Upload with conditional write
    PutObjectRequest.Builder putRequestBuilder = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(CACHE_KEY)
        .metadata(Map.of("version", String.valueOf(newVersion)));
    
    // Add conditional check if we have an existing object
    if (currentETag != null) {
        putRequestBuilder.ifMatch(currentETag);
    }
    
    PutObjectRequest putRequest = putRequestBuilder.build();
    
    try {
        s3Client.putObject(putRequest, RequestBody.fromString(jsonCache));
        logger.info("Successfully uploaded cache version {}", newVersion);
    } catch (ConditionalCheckFailedException e) {
        logger.warn("Cache update conflict detected. Another instance updated the cache.");
        throw e; // Re-throw to trigger retry logic
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
```

#### Step 2: Update QuoteManagementServiceWithCache.java

**File**: `/quote-lambda-tf-backend/src/main/java/ebulter/quote/lambda/service/QuoteManagementServiceWithCache.java`

```java
// Modify refreshCache method with debug logging
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
```

### Step 3: Add Unit Tests

**File**: `/quote-lambda-tf-backend/src/test/java/ebulter/quote/lambda/service/QuotesCacheBuilderTest.java`

```java
@Test
public void testConcurrentCacheUpdate() throws Exception {
    // Mock S3 to simulate concurrent update
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenReturn(HeadObjectResponse.builder()
            .eTag("old-etag")
            .metadata(Map.of("version", "1"))
            .build());
    
    // First call should succeed
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder()
            .eTag("new-etag")
            .build());
    
    cacheBuilder.buildAndUploadCache();
    
    // Verify upload was called
    verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
}

@Test
public void testConditionalCheckFailure() throws Exception {
    // Simulate concurrent update scenario
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenReturn(HeadObjectResponse.builder()
            .eTag("old-etag")
            .metadata(Map.of("version", "1"))
            .build());
    
    // First call fails with conditional check
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenThrow(ConditionalCheckFailedException.builder()
            .message("At least one of the pre-conditions you specified did not hold")
            .build())
        .thenReturn(PutObjectResponse.builder()
            .eTag("new-etag")
            .build());
    
    // Should retry and succeed
    assertDoesNotThrow(() -> cacheBuilder.buildAndUploadCache());
    
    // Verify upload was called twice (initial + retry)
    verify(s3Client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
}
```

## Phase 2: Enhanced Reliability Implementation

### Step 1: Enhance Retry Logic with Jitter

**Enhance the existing retry logic** in `QuotesCacheBuilder.java` from Phase 1:

```java
// Replace the simple retry logic from Phase 1 with enhanced version
public void buildAndUploadCache() {
    final int MAX_RETRIES = 5;  // Increased from 3
    int retryCount = 0;
    
    while (retryCount < MAX_RETRIES) {
        try {
            buildAndUploadCacheWithRetry();
            return; // Success, exit retry loop
        } catch (ConditionalCheckFailedException e) {
            retryCount++;
            if (retryCount >= MAX_RETRIES) {
                logger.error("Failed to update cache after {} attempts", MAX_RETRIES, e);
                throw new RuntimeException("Cache update failed due to concurrent modifications", e);
            }
            
            // Enhanced exponential backoff with jitter (Phase 2 improvement)
            long baseDelay = Math.min(1000L * (1L << retryCount), 10000L);  // Cap at 10s
            long jitter = (long) (baseDelay * 0.1 * Math.random());  // 10% jitter
            long totalDelay = baseDelay + jitter;
            
            logger.warn("Cache update conflict on attempt {}, retrying after {}ms", 
                retryCount + 1, totalDelay);
            
            try {
                Thread.sleep(totalDelay);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Cache update interrupted", ie);
            }
        }
    }
}
```

### Step 2: Add Debug Logging for Verification

**Add comprehensive debug logging** to `QuotesCacheBuilder.java` to track cache behavior:

```java
// Add to buildAndUploadCache method
private void buildAndUploadCacheWithRetry() throws Exception {
    String requestId = UUID.randomUUID().toString().substring(0, 8);
    logger.info("[{}] Building quotes cache for S3 upload with conditional protection", requestId);
    
    // Step 1: Get current cache metadata
    HeadObjectResponse currentMetadata = getCurrentCacheMetadata();
    String currentVersion = currentMetadata != null ? 
        currentMetadata.metadata().get("version") : "0";
    String currentETag = currentMetadata != null ? 
        currentMetadata.eTag() : null;
    
    logger.debug("[{}] Current cache version: {}, ETag: {}", requestId, currentVersion, currentETag);
    
    // Step 2: Build new cache data
    long startTime = System.currentTimeMillis();
    List<Quote> allQuotes = quoteRepository.getAllQuotes();
    logger.info("[{}] Loaded {} quotes from DynamoDB in {}ms", 
        requestId, allQuotes.size(), System.currentTimeMillis() - startTime);
    
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
    logger.debug("[{}] Serialized cache data: {} bytes", requestId, jsonCache.length());
    
    // Step 4: Upload with conditional write
    PutObjectRequest.Builder putRequestBuilder = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(CACHE_KEY)
        .metadata(Map.of("version", String.valueOf(newVersion)));
    
    // Add conditional check if we have an existing object
    if (currentETag != null) {
        putRequestBuilder.ifMatch(currentETag);
        logger.debug("[{}] Conditional write enabled with ETag: {}", requestId, currentETag);
    } else {
        logger.info("[{}] No existing cache, performing initial upload", requestId);
    }
    
    PutObjectRequest putRequest = putRequestBuilder.build();
    
    try {
        long uploadStart = System.currentTimeMillis();
        s3Client.putObject(putRequest, RequestBody.fromString(jsonCache));
        long uploadDuration = System.currentTimeMillis() - uploadStart;
        logger.info("[{}] Successfully uploaded cache version {} in {}ms", 
            requestId, newVersion, uploadDuration);
    } catch (ConditionalCheckFailedException e) {
        logger.warn("[{}] Cache update conflict detected. Another instance updated the cache.", requestId);
        throw e; // Re-throw to trigger retry logic
    }
}

// Also update the retry method to include debug logging
public void buildAndUploadCache() {
    final int MAX_RETRIES = 5;  // Increased from 3
    int retryCount = 0;
    String requestId = UUID.randomUUID().toString().substring(0, 8);
    
    logger.info("[{}] Starting cache update process", requestId);
    
    while (retryCount < MAX_RETRIES) {
        try {
            buildAndUploadCacheWithRetry();
            logger.info("[{}] Cache update completed successfully after {} attempts", 
                requestId, retryCount + 1);
            return; // Success, exit retry loop
        } catch (ConditionalCheckFailedException e) {
            retryCount++;
            if (retryCount >= MAX_RETRIES) {
                logger.error("[{}] Failed to update cache after {} attempts", 
                    requestId, MAX_RETRIES, e);
                throw new RuntimeException("Cache update failed due to concurrent modifications", e);
            }
            
            // Enhanced exponential backoff with jitter (Phase 2 improvement)
            long baseDelay = Math.min(1000L * (1L << retryCount), 10000L);  // Cap at 10s
            long jitter = (long) (baseDelay * 0.1 * Math.random());  // 10% jitter
            long totalDelay = baseDelay + jitter;
            
            logger.warn("[{}] Cache update conflict on attempt {}, retrying after {}ms", 
                requestId, retryCount + 1, totalDelay);
            
            try {
                Thread.sleep(totalDelay);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.error("[{}] Cache update interrupted", requestId);
                throw new RuntimeException("Cache update interrupted", ie);
            }
        }
    }
}
```

**Also update QuoteManagementServiceWithCache.java** to add debug logging:

```java
// Modify refreshCache method with debug logging
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
```

## Verification Steps

1. **Deploy changes** to staging environment
2. **Run concurrent cache update test**:
   ```bash
   # Simulate multiple Lambda instances
   for i in {1..10}; do
     curl -X POST "https://staging-api/quotes/cache/refresh" &
   done
   wait
   ```
3. **Check CloudWatch logs** for debug information:
   - Verify each request has a unique requestId
   - Confirm conditional writes are being used (look for "Conditional write enabled")
   - Check for retry attempts with exponential backoff
   - Verify successful uploads show version numbers and timing
4. **Monitor error rates through CloudWatch Logs Insights**:
   - Query logs to calculate success/failure rates
   - Look for patterns in concurrent update conflicts
   - Sample query: `filter @message like "Cache refresh completed successfully" | stats count() as successes, count() as failures by bin(5m)`
5. **Verify cache integrity** by checking version numbers

## Success Criteria

- ✅ No cache corruption under concurrent load
- ✅ Automatic retry on conflicts (max 3 attempts in Phase 1, max 5 in Phase 2)
- ✅ < 1% conflict rate under normal load
- ✅ < 30 second cache update time
- ✅ Debug logging provides full visibility into cache operations

