# ECS-30: S3 Cache Concurrent Update Protection

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
   - Add monitoring for retry attempts

### Phase 3: Performance Optimization (Low Priority)
3. **Consider Distributed Lock for High Traffic**
   - Only if conditional operations cause performance issues
   - Monitor lock contention
   - Implement lock queuing if needed

## Monitoring and Alerting

### Metrics to Track
1. **Cache Update Conflicts**: Number of conditional check failures
2. **Retry Attempts**: How often cache builds are retried
3. **Concurrent Builds**: Multiple instances building simultaneously
4. **Cache Freshness**: Time between cache updates

### Alert Thresholds
- > 5 cache conflicts per minute
- > 3 retry attempts for single cache build
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
