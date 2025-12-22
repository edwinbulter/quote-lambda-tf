# ECS-30: Minimize Cache Reloads

## Problem Description

The current cache implementation performs unnecessary reload checks every minute, creating performance overhead and increased costs. The system checks S3 for cache freshness on **every API call** after a 60-second interval, regardless of whether the cache has actually changed.

## Current Implementation Analysis

### The Problem Code
```java
private static final long CACHE_CHECK_INTERVAL_MS = 60000; // Check cache freshness every minute

private void checkCacheFreshness() throws Exception {
    long now = System.currentTimeMillis();
    if (now - lastCacheCheck > CACHE_CHECK_INTERVAL_MS) {  // Every 60 seconds
        synchronized (QuoteManagementServiceWithCache.class) {
            if (now - lastCacheCheck > CACHE_CHECK_INTERVAL_MS) {
                // S3 HEAD request on every API call after 60s
                HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(CACHE_KEY)
                    .build();
                    
                var metadata = s3Client.headObject(request);
                int s3Version = Integer.parseInt(metadata.metadata().get("version"));
                
                if (cache == null || cache.version != s3Version) {
                    loadCacheFromS3();  // Expensive reload
                }
            }
        }
    }
}
```

### Why This Exists
1. **Multi-Instance Synchronization**: Multiple Lambda instances need to detect cache updates from other instances
2. **Manual Refresh Detection**: Admin can manually trigger cache refresh via API endpoint
3. **Stale Cache Prevention**: Prevent in-memory cache from diverging from S3 version

## Impact Analysis

### Performance Impact
- **S3 API Calls**: 1 HEAD request per minute per Lambda instance
- **Cold Start Penalty**: New instances immediately check S3 on first request
- **Response Latency**: +50-100ms added to API response during cache checks
- **Thundering Herd**: All instances check S3 simultaneously

### Cost Impact
- **S3 Requests**: ~43,200 HEAD requests per day (1 instance * 1440 checks/day)
- **Data Transfer**: Small but cumulative metadata transfer
- **Lambda Duration**: Increased execution time due to S3 network calls

### Scalability Issues
- **High Traffic**: More instances = more S3 requests
- **Burst Traffic**: Sudden traffic spikes cause S3 request bursts
- **Regional Deployment**: Multiple regions multiply the problem

## Current Cache Update Triggers

### 1. Automatic Updates (Rare)
- New quotes added to DynamoDB
- Quote metadata changes (like count updates)
- Scheduled cache refresh (if implemented)

### 2. Manual Updates (Infrequent)
- Admin triggers manual refresh via management interface
- Cache corruption detection and rebuild

### 3. The Reality
- **Cache changes are infrequent** (maybe a few times per day)
- **Cache checks are frequent** (every minute)
- **Wasted ratio**: 99.9% of checks find no changes

## Recommended Solutions

### Solution 1: Event-Driven Cache Updates (Recommended)

**Approach**: Use S3 event notifications to trigger cache invalidation

```java
// S3 Event Handler for cache updates
@EventHandler
public void onS3CacheUpdate(S3Event event) {
    for (S3EventRecord record : event.getRecords()) {
        if (record.getS3().getObject().getKey().equals(CACHE_KEY)) {
            // Invalidate local cache across all instances
            invalidateCacheAcrossInstances();
        }
    }
}

// Use DynamoDB for cross-instance cache invalidation
private void invalidateCacheAcrossInstances() {
    dynamoDbClient.putItem(PutItemRequest.builder()
        .tableName("cache-invalidation")
        .item(Map.of(
            "cacheKey", AttributeValue.fromS("quotes-cache"),
            "timestamp", AttributeValue.fromN(String.valueOf(System.currentTimeMillis())),
            "ttl", AttributeValue.fromN(String.valueOf(Instant.now().plusSeconds(300).getEpochSecond()))
        ))
        .build());
}

// Check for invalidation instead of S3 HEAD
private void checkCacheInvalidation() {
    try {
        GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
            .tableName("cache-invalidation")
            .key(Map.of("cacheKey", AttributeValue.fromS("quotes-cache")))
            .build());
            
        if (response.item() != null) {
            long invalidationTime = Long.parseLong(response.item().get("timestamp").n());
            if (invalidationTime > cacheLoadTime) {
                loadCacheFromS3();
                // Clean up invalidation record
                deleteInvalidationRecord();
            }
        }
    } catch (ResourceNotFoundException e) {
        // No invalidation needed
    }
}
```

**Pros**:
- No periodic S3 checks
- Immediate cache updates
- Efficient DynamoDB reads
- Event-driven architecture

**Cons**:
- Requires S3 event notification setup
- Additional DynamoDB table
- More complex infrastructure

### Solution 2: Longer Check Intervals + ETags

**Approach**: Reduce check frequency and use conditional requests

```java
private static final long CACHE_CHECK_INTERVAL_MS = 300000; // 5 minutes
private static String lastETag = null;

private void checkCacheFreshness() throws Exception {
    long now = System.currentTimeMillis();
    if (now - lastCacheCheck > CACHE_CHECK_INTERVAL_MS) {
        synchronized (QuoteManagementServiceWithCache.class) {
            if (now - lastCacheCheck > CACHE_CHECK_INTERVAL_MS) {
                try {
                    HeadObjectRequest request = HeadObjectRequest.builder()
                        .bucket(bucketName)
                        .key(CACHE_KEY)
                        .ifMatch(lastETag)  // Conditional request
                        .build();
                        
                    var metadata = s3Client.headObject(request);
                    String currentETag = metadata.eTag();
                    
                    if (!currentETag.equals(lastETag)) {
                        loadCacheFromS3();
                        lastETag = currentETag;
                    }
                    
                } catch (PreconditionFailedException e) {
                    // ETag mismatch, cache changed
                    loadCacheFromS3();
                    lastETag = metadata.eTag();
                }
            }
        }
    }
}
```

**Pros**:
- 5x fewer S3 requests
- Conditional requests reduce bandwidth
- Simple implementation
- Backward compatible

**Cons**:
- Still makes periodic requests
- Delayed cache updates (up to 5 minutes)
- Not event-driven

### Solution 3: Hybrid Approach - Smart Cache Checking

**Approach**: Combine multiple strategies for optimal performance

```java
private static final long CACHE_CHECK_INTERVAL_MS = 300000; // 5 minutes
private static final long HIGH_TRAFFIC_INTERVAL_MS = 60000;  // 1 minute for high traffic
private static final long LOW_TRAFFIC_INTERVAL_MS = 900000;  // 15 minutes for low traffic

private void checkCacheFreshness() throws Exception {
    long now = System.currentTimeMillis();
    long interval = getDynamicInterval();
    
    if (now - lastCacheCheck > interval) {
        synchronized (QuoteManagementServiceWithCache.class) {
            if (now - lastCacheCheck > interval) {
                // First check DynamoDB invalidation table (fast)
                if (checkInvalidationTable()) {
                    loadCacheFromS3();
                    return;
                }
                
                // Then check S3 ETag (slower)
                if (checkS3ETag()) {
                    loadCacheFromS3();
                }
            }
        }
    }
}

private long getDynamicInterval() {
    // Adjust interval based on traffic patterns
    long requestsPerMinute = getRequestRate();
    if (requestsPerMinute > 100) {
        return HIGH_TRAFFIC_INTERVAL_MS;    // Check more often during high traffic
    } else if (requestsPerMinute < 10) {
        return LOW_TRAFFIC_INTERVAL_MS;     // Check less often during low traffic
    } else {
        return CACHE_CHECK_INTERVAL_MS;     // Normal interval
    }
}
```

**Pros**:
- Adaptive to traffic patterns
- Multiple invalidation sources
- Balances performance and freshness
- Graceful degradation

**Cons**:
- More complex logic
- Requires traffic monitoring
- Multiple failure points

### Solution 4: Cache Versioning with TTL

**Approach**: Use TTL-based cache with version validation

```java
private static final long CACHE_TTL_MS = 600000; // 10 minutes
private static volatile long cacheExpiryTime = 0;

private void checkCacheFreshness() throws Exception {
    long now = System.currentTimeMillis();
    
    // Check if cache has expired
    if (now > cacheExpiryTime) {
        synchronized (QuoteManagementServiceWithCache.class) {
            if (now > cacheExpiryTime) {
                // Validate version before extending TTL
                if (validateCacheVersion()) {
                    // Extend TTL
                    cacheExpiryTime = now + CACHE_TTL_MS;
                } else {
                    // Reload cache
                    loadCacheFromS3();
                    cacheExpiryTime = now + CACHE_TTL_MS;
                }
            }
        }
    }
}

private boolean validateCacheVersion() throws Exception {
    try {
        HeadObjectRequest request = HeadObjectRequest.builder()
            .bucket(bucketName)
            .key(CACHE_KEY)
            .build();
            
        var metadata = s3Client.headObject(request);
        int s3Version = Integer.parseInt(metadata.metadata().get("version"));
        
        return cache != null && cache.version == s3Version;
    } catch (Exception e) {
        return false;
    }
}
```

**Pros**:
- Predictable cache behavior
- Reduced S3 requests
- Simple TTL logic
- Graceful expiration

**Cons**:
- Delayed cache updates
- Still requires periodic validation
- TTL management complexity

## Implementation Priority

### Phase 1: Quick Win (Immediate)
1. **Increase Check Interval**: Change from 1 minute to 5 minutes
2. **Add ETag Validation**: Use conditional requests to reduce bandwidth
3. **Add Request Rate Monitoring**: Track cache check frequency

### Phase 2: Event-Driven (Medium Priority)
1. **Implement S3 Event Notifications**: Set up cache invalidation events
2. **Create DynamoDB Invalidation Table**: Fast cross-instance communication
3. **Add Event Handler**: Process cache update events

### Phase 3: Optimization (Low Priority)
1. **Dynamic Intervals**: Adjust based on traffic patterns
2. **Multi-Layer Validation**: Combine multiple invalidation sources
3. **Advanced Caching**: Implement cache warming and preloading

## Testing Strategy

### Performance Testing
- **Baseline**: Measure current S3 request rate
- **After Changes**: Verify 80% reduction in S3 requests
- **Load Testing**: Test under high traffic conditions
- **Latency Testing**: Ensure no regression in response times

### Functional Testing
- **Cache Freshness**: Verify cache updates propagate correctly
- **Multi-Instance**: Test with multiple Lambda instances
- **Manual Refresh**: Ensure admin refresh still works
- **Failure Scenarios**: Test behavior when S3 is unavailable

### Monitoring Metrics
- **S3 Request Rate**: Track HEAD requests over time
- **Cache Hit Rate**: Measure cache effectiveness
- **Response Latency**: Monitor API response times
- **Cache Update Latency**: Time from cache change to propagation

## Cost Analysis

### Current Costs
- **S3 Requests**: ~43,200 HEAD requests/month
- **Lambda Duration**: +50ms per cache check
- **Data Transfer**: ~1KB per HEAD request

### After Optimization
- **S3 Requests**: ~8,640 HEAD requests/month (80% reduction)
- **Lambda Duration**: +10ms per cache check
- **DynamoDB**: ~1,000 reads/month (invalidation table)

### Expected Savings
- **S3 Costs**: 80% reduction in request fees
- **Lambda Costs**: 20% reduction in execution time
- **Overall**: ~60% total cost reduction

## Rollback Plan

If optimization causes issues:
1. **Revert Interval**: Change back to 1-minute checks
2. **Disable ETags**: Remove conditional request logic
3. **Fallback Mode**: Use original implementation
4. **Monitoring**: Add alerts for cache synchronization issues

## Conclusion

The current cache implementation performs unnecessary S3 checks every minute, creating performance overhead and increased costs. By implementing event-driven cache updates and reducing check frequency, we can achieve:

- **80% reduction** in S3 requests
- **Improved response times** during normal operation
- **Immediate cache updates** when changes occur
- **Better scalability** for high-traffic scenarios

The recommended approach is to start with simple interval increases and ETag validation (Phase 1), then implement event-driven updates (Phase 2) for optimal performance.
