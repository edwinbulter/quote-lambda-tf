# ECS-30: S3 Cache Concurrent Update Protection

## Table of Contents

1. [Problem Description](#problem-description)
2. [Current Vulnerabilities](#current-vulnerabilities)
   - 2.1. Race Condition in Version Generation
   - 2.2. Non-Atomic Cache Upload
   - 2.3. Manual Refresh Race Condition
   - 2.4. Cache Freshness Check Race
3. [Impact Analysis](#impact-analysis)
   - 3.1. High Traffic Scenarios
   - 3.2. Potential Consequences
4. [Recommended Solutions](#recommended-solutions)
   - 4.1. Solution 1: S3 Conditional Operations (Recommended)
   - 4.2. Solution 2: Distributed Lock with DynamoDB
   - 4.3. Solution 3: UUID-Based Versioning
   - 4.4. Solution 4: Atomic Two-Phase Upload
5. [Implementation Priority](#implementation-priority)
   - 5.1. Phase 1: Immediate Protection (High Priority)
   - 5.2. Phase 2: Enhanced Reliability (Medium Priority)
   - 5.3. Phase 3: Performance Optimization (Low Priority)
6. [Monitoring and Alerting](#monitoring-and-alerting)
   - 6.1. Metrics to Track
   - 6.2. Alert Thresholds
7. [Testing Strategy](#testing-strategy)
   - 7.1. Unit Tests
   - 7.2. Integration Tests
   - 7.3. Load Testing
8. [Rollback Plan](#rollback-plan)
9. [Conclusion](#conclusion)
10. [Implementation](#implementation)
    - 10.1. Chosen Implementation
    - 10.2. Phase 1: S3 Conditional Operations Implementation
    - 10.3. Phase 2: Enhanced Reliability Implementation
    - 10.4. Verification Steps
    - 10.5. Success Criteria
    - 10.6. Rollback Procedure

## Problem Description

The current S3 cache implementation in the Quote Lambda backend is **NOT protected against concurrent updates**. This creates several critical race conditions when multiple Lambda instances run simultaneously.

## Current Vulnerabilities

### 1. Race Condition in Version Generation
```java
// In QuotesCacheBuilder.getNextVersion()
HeadObjectRequest request = HeadObjectRequest.builder()
    .bucket(bucketName)
    .key(CACHE_KEY)
    .build();
    
var metadata = s3Client.headObject(request);
String versionStr = metadata.metadata().get("version");
return versionStr != null ? Integer.parseInt(versionStr) + 1 : 1;
```

**Problem**: Multiple Lambda instances can:
- Read the same version number simultaneously
- Both increment to the same new version
- One overwrites the other's cache upload
- Result: Lost cache updates and inconsistent state

### 2. Non-Atomic Cache Upload
```java
// In QuotesCacheBuilder.buildAndUploadCache()
int version = getNextVersion();           // Step 1: Read version
// ... build cache data (takes time) ...
uploadToS3(cacheData);                    // Step 2: Upload with potentially stale version
```

**Problem**: Another Lambda could upload a newer version between Step 1 and Step 2, causing:
- Version collision
- Cache overwrite
- Wasted DynamoDB reads

### 3. Manual Refresh Race Condition
```java
// In QuoteManagementServiceWithCache.refreshCache()
public void refreshCache() {
    logger.info("Manual cache refresh requested");
    cacheBuilder.buildAndUploadCache();  // Build cache
    clearCache();                       // Clear local cache
}
```

**Problem**: Multiple concurrent refreshes can:
- Build cache simultaneously (wasting DynamoDB capacity)
- Clear each other's in-memory cache
- Cause unnecessary S3 uploads

### 4. Cache Freshness Check Race
```java
// In checkCacheFreshness()
if (cache == null || cache.version != s3Version) {
    loadCacheFromS3();  // Multiple Lambdas could trigger this simultaneously
}
```

**Problem**: Multiple Lambda instances can:
- Detect stale cache simultaneously
- All trigger cache reload
- Create thundering herd effect

## Impact Analysis

### High Traffic Scenarios
- **Lambda Cold Starts**: Multiple instances starting simultaneously
- **Manual Cache Refresh**: Admin triggers refresh while automatic refresh occurs
- **Cache Expiration**: Multiple instances detect expired cache

### Potential Consequences
1. **Cache Corruption**: One Lambda overwrites another's cache
2. **Performance Degradation**: Multiple unnecessary DynamoDB scans
3. **Inconsistent Data**: Different Lambda instances serving different cache versions
4. **Increased Costs**: Wasted DynamoDB read capacity and S3 operations
5. **User Experience**: Slower responses during cache rebuilds

## Recommended Solutions

### Solution 1: S3 Conditional Operations (Recommended)

**Approach**: Use S3's built-in conditional operations for atomic updates

```java
// Implementation sketch
public void buildAndUploadCache() {
    // Get current version and ETag
    HeadObjectResponse current = s3Client.headObject(request);
    String currentVersion = current.metadata().get("version");
    String currentETag = current.eTag();
    
    // Build new cache
    int newVersion = Integer.parseInt(currentVersion) + 1;
    QuotesCacheData cacheData = buildCacheData(newVersion);
    
    // Upload with conditional write
    PutObjectRequest putRequest = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(CACHE_KEY)
        .metadata(Map.of("version", String.valueOf(newVersion)))
        .ifMatch(currentETag)  // Only upload if object hasn't changed
        .build();
    
    try {
        s3Client.putObject(putRequest, RequestBody.fromString(json));
    } catch (ConditionalCheckFailedException e) {
        // Another instance updated the cache, retry or abort
        logger.warn("Cache update conflict, retrying...");
        buildAndUploadCache(); // Retry with new version
    }
}
```

**Pros**:
- Built-in to S3
- No additional infrastructure needed
- Atomic operations
- Handles race conditions automatically

**Cons**:
- Requires retry logic
- Slightly more complex error handling

### Solution 2: Distributed Lock with DynamoDB

**Approach**: Use DynamoDB conditional writes as a distributed lock

```java
// Lock table structure
// PK: "CACHE_LOCK"
// SK: "quotes-cache"
// Attributes: lockId, expiryTime

public void buildAndUploadCache() {
    String lockId = UUID.randomUUID().toString();
    long expiryTime = System.currentTimeMillis() + 30000; // 30 seconds
    
    // Try to acquire lock
    try {
        dynamoDbClient.putItem(PutItemRequest.builder()
            .tableName("cache-locks")
            .item(Map.of(
                "PK", AttributeValue.fromS("CACHE_LOCK"),
                "SK", AttributeValue.fromS("quotes-cache"),
                "lockId", AttributeValue.fromS(lockId),
                "expiryTime", AttributeValue.fromN(String.valueOf(expiryTime))
            ))
            .conditionExpression("attribute_not_exists(PK)")
            .build());
        
        // Got the lock, build cache
        buildCacheInternal();
        
    } catch (ConditionalCheckFailedException e) {
        // Another instance has the lock
        logger.info("Cache update in progress, waiting...");
        waitForLockRelease();
        // Optionally: load latest cache instead of rebuilding
    } finally {
        // Release lock
        releaseLock(lockId);
    }
}
```

**Pros**:
- Explicit lock management
- Can implement lock queuing
- More control over concurrency

**Cons**:
- Additional DynamoDB table
- Lock cleanup complexity
- Potential for deadlocks

### Solution 3: UUID-Based Versioning

**Approach**: Replace sequential versioning with UUIDs to eliminate collisions

```java
public void buildAndUploadCache() {
    String version = UUID.randomUUID().toString();
    
    // Load all quotes and build cache data
    List<Quote> allQuotes = quoteRepository.getAllQuotes();
    Map<String, Map<String, List<Integer>>> sortedIndices = buildSortedIndices(allQuotes);
    
    QuotesCacheData cacheData = new QuotesCacheData(
        version,                    // UUID instead of integer
        Instant.now(),
        allQuotes.size(),
        allQuotes,
        sortedIndices
    );
    
    // Serialize cache data to JSON
    String jsonCache = objectMapper.writeValueAsString(cacheData);
    
    // Upload cache with UUID version as part of object key
    String cacheKey = "quotes-cache-" + version + ".json";
    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(bucketName)
            .key(cacheKey)
            .metadata(Map.of("uuid", version, "timestamp", Instant.now().toString()))
            .build(),
        RequestBody.fromString(jsonCache)
    );
    
    // Update pointer atomically to latest version
    String pointerData = objectMapper.writeValueAsString(Map.of(
        "latestVersion", version,
        "timestamp", Instant.now().toString(),
        "cacheKey", cacheKey
    ));
    
    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(bucketName)
            .key("quotes-cache-latest.json")  // Pointer to latest version
            .build(),
        RequestBody.fromString(pointerData)
    );
}
```

**Pros**:
- No version collisions
- Simple implementation
- Can keep multiple versions

**Cons**:
- More S3 objects
- Two-step update process
- Pointer update still needs protection

### Solution 4: Atomic Two-Phase Upload

**Approach**: Upload to temporary location first, then atomically rename

```java
public void buildAndUploadCache() {
    String tempKey = "quotes-cache-temp-" + UUID.randomUUID() + ".json";
    String finalKey = "quotes-cache.json";
    
    // Build cache data
    List<Quote> allQuotes = quoteRepository.getAllQuotes();
    Map<String, Map<String, List<Integer>>> sortedIndices = buildSortedIndices(allQuotes);
    int newVersion = getNextVersion();
    
    QuotesCacheData cacheData = new QuotesCacheData(
        newVersion,
        Instant.now(),
        allQuotes.size(),
        allQuotes,
        sortedIndices
    );
    
    // Serialize to JSON
    String json = objectMapper.writeValueAsString(cacheData);
    
    // Step 1: Upload to temporary location (not visible to readers)
    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(bucketName)
            .key(tempKey)
            .metadata(Map.of("version", String.valueOf(newVersion)))
            .build(),
        RequestBody.fromString(json)
    );
    
    // Step 2: Atomically move to final location using S3 CopyObject
    // This is "atomic" because S3 guarantees the copy operation is all-or-nothing
    // - Either the entire object is copied successfully
    // - Or no change is made if the operation fails
    // - Readers never see a partially written cache file
    CopyObjectRequest copyRequest = CopyObjectRequest.builder()
        .bucket(bucketName)
        .sourceKey(tempKey)
        .destinationKey(finalKey)
        .metadataDirective(MetadataDirective.REPLACE)
        .metadata(Map.of("version", String.valueOf(newVersion)))
        .build();
    
    s3Client.copyObject(copyRequest);
    
    // Step 3: Clean up temporary file
    s3Client.deleteObject(DeleteObjectRequest.builder()
        .bucket(bucketName)
        .key(tempKey)
        .build());
}
```

**Pros**:
- Atomic final step
- No partial uploads visible
- Simple to understand

**Cons**:
- Two S3 operations
- Temporary objects need cleanup
- Still needs version protection

## Implementation Priority

### Phase 1: Immediate Protection (High Priority)
1. **Implement S3 Conditional Operations** (Solution 1)
   - Minimal code changes
   - Uses existing S3 features
   - Provides immediate protection

### Phase 2: Enhanced Reliability (Medium Priority)
2. **Add Retry Logic with Exponential Backoff**
   - Handle conditional check failures gracefully
   - Prevent infinite retry loops
   - Add jitter to prevent thundering herd effects

### Phase 3: Performance Optimization (Low Priority)
3. **Consider Distributed Lock for High Traffic**
   - Only if conditional operations cause performance issues
   - Monitor lock contention
   - Implement lock queuing if needed

## Monitoring and Alerting

### Metrics to Track
1. **Cache Update Conflicts**: Number of conditional check failures
2. **Concurrent Builds**: Multiple instances building simultaneously
3. **Cache Freshness**: Time between cache updates

### Alert Thresholds
- > 5 cache conflicts per minute
- Cache age > 10 minutes

## Testing Strategy

### Unit Tests
- Mock S3 conditional operations
- Test conflict scenarios
- Verify retry logic

### Integration Tests
- Multiple Lambda instances updating simultaneously
- Network failure scenarios
- Cache corruption recovery

### Load Testing
- 100+ concurrent Lambda instances
- High-frequency cache refreshes
- Mixed read/write operations

## Rollback Plan

If issues arise with concurrent update protection:
1. Disable conditional operations temporarily
2. Fall back to sequential versioning with logging
3. Monitor for cache corruption
4. Implement emergency cache rebuild procedure

## Conclusion

The current S3 cache implementation has critical concurrency vulnerabilities that can lead to cache corruption, performance issues, and increased costs. Implementing S3 conditional operations (Solution 1) provides immediate protection with minimal code changes and should be prioritized for Phase 1 implementation.

Additional solutions can be evaluated based on real-world performance metrics and traffic patterns after the initial protection is in place.

## Implementation

### Chosen Implementation

**Solution 1: S3 Conditional Operations** has been selected for Phase 1 implementation due to:
- Minimal code changes required
- No additional infrastructure needed
- Immediate protection against race conditions
- Built-in atomic operations provided by S3

**Phase 2: Enhanced Reliability** will build upon Phase 1 by adding:
- Exponential backoff with jitter for retry logic
- Comprehensive metrics collection and monitoring
- CloudWatch alarms for proactive alerting
- Advanced retry patterns to prevent thundering herd

This two-phase approach provides immediate protection while ensuring long-term reliability and observability.

### Phase 1: S3 Conditional Operations Implementation

#### Step 1: Update QuotesCacheBuilder.java

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
// Add monitoring for cache conflicts
private static final AtomicLong cacheConflictCount = new AtomicLong(0);
private static final AtomicLong cacheUpdateAttempts = new AtomicLong(0);

// Modify refreshCache method to include monitoring
public void refreshCache() {
    logger.info("Manual cache refresh requested");
    cacheUpdateAttempts.incrementAndGet();
    
    try {
        cacheBuilder.buildAndUploadCache();
        clearCache();
        logger.info("Cache refresh completed successfully");
    } catch (RuntimeException e) {
        if (e.getMessage().contains("concurrent modifications")) {
            cacheConflictCount.incrementAndGet();
            logger.warn("Cache refresh failed due to concurrent updates. Another instance may have updated the cache.");
        } else {
            logger.error("Cache refresh failed", e);
            throw e;
        }
    }
}

// Add metrics endpoint for monitoring
public Map<String, Long> getCacheMetrics() {
    return Map.of(
        "conflictCount", cacheConflictCount.get(),
        "updateAttempts", cacheUpdateAttempts.get()
    );
}
```

#### Step 3: Add Unit Tests

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

### Phase 2: Enhanced Reliability Implementation

#### Step 1: Enhance Retry Logic with Jitter

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

#### Step 2: Add Debug Logging for Verification

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
    cacheUpdateAttempts.incrementAndGet();
    
    try {
        long refreshStart = System.currentTimeMillis();
        cacheBuilder.buildAndUploadCache();
        clearCache();
        long refreshDuration = System.currentTimeMillis() - refreshStart;
        logger.info("[{}] Cache refresh completed successfully in {}ms", 
            requestId, refreshDuration);
    } catch (RuntimeException e) {
        if (e.getMessage().contains("concurrent modifications")) {
            cacheConflictCount.incrementAndGet();
            logger.warn("[{}] Cache refresh failed due to concurrent updates. " +
                "Another instance may have updated the cache.", requestId);
        } else {
            logger.error("[{}] Cache refresh failed", requestId, e);
            throw e;
        }
    }
}
```

### Verification Steps

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
4. **Verify cache integrity** by checking version numbers
5. **Monitor error rates** to ensure no regressions

### Success Criteria

- ✅ No cache corruption under concurrent load
- ✅ Automatic retry on conflicts (max 3 attempts)
- ✅ < 1% conflict rate under normal load
- ✅ < 30 second cache update time

### Rollback Procedure

If issues are detected:
1. **Disable conditional operations** by setting feature flag
2. **Revert to original implementation** in emergency deployment
3. **Monitor for cache corruption**
4. **Investigate root cause** before re-enabling
