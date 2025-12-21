# ECS-30 - S3 Backend Optimization for Quotes Management

## Table of Contents
- [Goal](#goal)
- [Current State](#current-state)
- [Proposed Solution](#proposed-solution)
- [S3 JSON Structure Design](#s3-json-structure-design)
- [Lambda Caching Strategy](#lambda-caching-strategy)
- [Cache Invalidation Strategy](#cache-invalidation-strategy)
- [Implementation Steps](#implementation-steps)
- [Performance Improvements](#performance-improvements)
- [Cost Analysis](#cost-analysis)
- [Testing Strategy](#testing-strategy)
- [Conclusion](#conclusion)

## Goal

Implement S3-based caching to eliminate DynamoDB full table scans, reducing pagination response times from 50-100ms to <10ms while maintaining the existing REST API contract with the frontend.

## Current State

### Performance Issues:
- DynamoDB full table scan on every pagination request
- In-memory sorting after full data retrieval
- Inconsistent ordering across pages
- High DynamoDB read costs (~$1.50/month)

### Current API Endpoints:
```
GET /api/v1/admin/quotes?page=1&pageSize=50&sortBy=id&sortOrder=asc
GET /api/v1/quotes/{id}
POST /api/v1/admin/quotes/add
```

## Proposed Solution

### Architecture:
```
DynamoDB (Source) → Lambda → S3 (Cache) → Lambda (Memory) → Frontend
```

### Flow:
1. Lambda loads quotes from S3 JSON into memory on startup
2. Frontend continues using existing REST endpoints
3. Backend serves requests from in-memory cache
4. Cache refreshed when quotes are added or missing

## S3 JSON Structure Design

### File Location:
```
s3://quote-lambda-cache/quotes-cache.json
```

### JSON Structure:
```json
{
  "version": 1,
  "lastUpdated": "2024-01-01T12:00:00Z",
  "totalCount": 1000,
  "quotes": [
    {
      "id": 1,
      "quoteText": "To be or not to be",
      "author": "William Shakespeare",
      "likeCount": 156
    }
  ],
  "sortedIndices": {
    "id": {
      "asc": [1, 2, 3, 4, 5],
      "desc": [5, 4, 3, 2, 1]
    },
    "quoteText": {
      "asc": [3, 1, 5, 2, 4],
      "desc": [4, 2, 5, 1, 3]
    },
    "author": {
      "asc": [5, 3, 1, 4, 2],
      "desc": [2, 4, 1, 3, 5]
    },
    "likeCount": {
      "asc": [2, 5, 3, 4, 1],
      "desc": [1, 4, 3, 5, 2]
    }
  }
}
```

### Benefits:
- **Pre-computed Indices**: No sorting needed at runtime
- **ID-based Pagination**: Direct array access
- **Compact Storage**: Only IDs in indices, full objects in quotes array
- **Version Control**: Easy cache invalidation

## Lambda Caching Strategy

### Static Variable Caching:
```java
public class QuoteHandler {
    // Persist across warm Lambda invocations
    private static volatile QuotesCache cache = null;
    private static final String CACHE_BUCKET = "quote-lambda-cache";
    private static final String CACHE_KEY = "quotes-cache.json";
    
    public static class QuotesCache {
        public int version;
        public List<Quote> quotes;
        public Map<String, Map<String, List<Integer>>> sortedIndices;
        public Map<Integer, Quote> quoteMap; // Fast ID lookup
    }
    
    private static void ensureCacheLoaded() {
        if (cache == null) {
            synchronized (QuoteHandler.class) {
                if (cache == null) {
                    loadCacheFromS3();
                }
            }
        }
    }
    
    private static void loadCacheFromS3() {
        try {
            // Download from S3
            S3Object s3Object = s3Client.getObject(CACHE_BUCKET, CACHE_KEY);
            String jsonContent = IOUtils.toString(s3Object.getObjectContent());
            
            // Parse and build cache
            QuotesCacheData data = parseJson(jsonContent);
            cache = new QuotesCache();
            cache.version = data.getVersion();
            cache.quotes = data.getQuotes();
            cache.sortedIndices = data.getSortedIndices();
            
            // Build ID lookup map
            cache.quoteMap = cache.quotes.stream()
                .collect(Collectors.toMap(Quote::getId, q -> q));
                
        } catch (Exception e) {
            logger.error("Failed to load cache from S3, falling back to DynamoDB", e);
            cache = null;
        }
    }
}
```

### Cache Invalidation Strategy:

#### Version-Based Invalidation:
```java
private static void checkCacheFreshness() {
    try {
        // Quick HEAD request to check version
        ObjectMetadata metadata = s3Client.getObjectMetadata(CACHE_BUCKET, CACHE_KEY);
        int s3Version = Integer.parseInt(metadata.getUserMetaDataOf("version"));
        
        if (cache == null || cache.version != s3Version) {
            synchronized (QuoteHandler.class) {
                if (cache == null || cache.version != s3Version) {
                    loadCacheFromS3();
                }
            }
        }
    } catch (Exception e) {
        logger.warn("Failed to check cache freshness", e);
    }
}
```

#### Cache Refresh Triggers:
1. **Missing Cache**: First Lambda invocation
2. **Version Mismatch**: S3 version different from memory
3. **Quote Addition**: After new quotes are added
4. **Manual Refresh**: Admin endpoint `/admin/cache/refresh`

## Implementation Steps

### Phase 1: Cache Generation Service

#### 1. Create CacheBuilder Service:
```java
@Service
public class QuotesCacheBuilder {
    
    public void buildAndUploadCache() {
        // 1. Load all quotes from DynamoDB
        List<Quote> allQuotes = quoteRepository.getAllQuotes();
        
        // 2. Sort quotes and build indices
        QuotesCacheData cacheData = new QuotesCacheData();
        cacheData.setVersion(getNextVersion());
        cacheData.setLastUpdated(Instant.now());
        cacheData.setTotalCount(allQuotes.size());
        cacheData.setQuotes(allQuotes);
        
        // 3. Build sorted indices
        Map<String, Map<String, List<Integer>>> indices = new HashMap<>();
        
        // Sort by ID
        indices.put("id", Map.of(
            "asc", allQuotes.stream().map(Quote::getId).collect(Collectors.toList()),
            "desc", allQuotes.stream()
                .sorted(Comparator.comparing(Quote::getId).reversed())
                .map(Quote::getId).collect(Collectors.toList())
        ));
        
        // Sort by quoteText
        indices.put("quoteText", Map.of(
            "asc", allQuotes.stream()
                .sorted(Comparator.comparing(Quote::getQuoteText))
                .map(Quote::getId).collect(Collectors.toList()),
            "desc", allQuotes.stream()
                .sorted(Comparator.comparing(Quote::getQuoteText).reversed())
                .map(Quote::getId).collect(Collectors.toList())
        ));
        
        // Sort by author
        indices.put("author", Map.of(
            "asc", allQuotes.stream()
                .sorted(Comparator.comparing(Quote::getAuthor))
                .map(Quote::getId).collect(Collectors.toList()),
            "desc", allQuotes.stream()
                .sorted(Comparator.comparing(Quote::getAuthor).reversed())
                .map(Quote::getId).collect(Collectors.toList())
        ));
        
        // Sort by likeCount (with ID tiebreaker)
        indices.put("likeCount", Map.of(
            "asc", allQuotes.stream()
                .sorted(Comparator.comparing(Quote::getLikeCount)
                    .thenComparing(Quote::getId))
                .map(Quote::getId).collect(Collectors.toList()),
            "desc", allQuotes.stream()
                .sorted(Comparator.comparing(Quote::getLikeCount).reversed()
                    .thenComparing(Quote::getId))
                .map(Quote::getId).collect(Collectors.toList())
        ));
        
        cacheData.setSortedIndices(indices);
        
        // 4. Upload to S3
        String json = objectMapper.writeValueAsString(cacheData);
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(CACHE_BUCKET)
            .key(CACHE_KEY)
            .content(json)
            .metadata("version", String.valueOf(cacheData.getVersion()))
            .build();
            
        s3Client.putObject(request);
        
        logger.info("Cache built and uploaded with {} quotes", allQuotes.size());
    }
    
    private int getNextVersion() {
        try {
            ObjectMetadata metadata = s3Client.getObjectMetadata(CACHE_BUCKET, CACHE_KEY);
            return Integer.parseInt(metadata.getUserMetaDataOf("version")) + 1;
        } catch (NoSuchKeyException e) {
            return 1;
        }
    }
}
```

#### 2. Add Cache Generation Trigger:
```java
// In QuoteHandler - addQuotes endpoint
public QuoteAddResponse fetchAndAddNewQuotes() {
    // 1. Add quotes to DynamoDB
    List<Quote> newQuotes = zenApiService.fetchNewQuotes();
    quoteRepository.batchInsert(newQuotes);
    
    // 2. Rebuild and upload cache
    cacheBuilder.buildAndUploadCache();
    
    // 3. Clear in-memory cache (will reload on next request)
    QuoteHandler.clearCache();
    
    return new QuoteAddResponse(newQuotes.size());
}

// Admin endpoint for manual refresh
@POST
@Path("/admin/cache/refresh")
public Response refreshCache() {
    cacheBuilder.buildAndUploadCache();
    QuoteHandler.clearCache();
    return Response.ok("Cache refreshed").build();
}
```

### Phase 2: Update QuoteManagementService

#### 1. Modify getQuotesWithPagination:
```java
public QuotePageResponse getQuotesWithPagination(int page, int pageSize, 
        String quoteText, String author, String sortBy, String sortOrder) {
    
    // Try cache first
    try {
        return getFromCache(page, pageSize, quoteText, author, sortBy, sortOrder);
    } catch (Exception e) {
        logger.warn("Cache miss, falling back to DynamoDB", e);
        return getFromDynamoDB(page, pageSize, quoteText, author, sortBy, sortOrder);
    }
}

private QuotePageResponse getFromCache(int page, int pageSize, 
        String quoteText, String author, String sortBy, String sortOrder) {
    
    QuoteHandler.ensureCacheLoaded();
    QuoteHandler.checkCacheFreshness();
    
    QuotesCache cache = QuoteHandler.getCache();
    
    // Get sorted indices for the requested sort
    List<Integer> sortedIds = cache.sortedIndices
        .get(sortBy)
        .get(sortOrder);
    
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
    
    int totalPages = (int) Math.ceil((double) filteredIds.size() / pageSize);
    
    return new QuotePageResponse(
        pageQuotes,
        filteredIds.size(),
        page,
        pageSize,
        totalPages
    );
}
```

### Phase 3: Infrastructure Setup

#### 1. Create S3 Bucket with Terraform:
```hcl
# infrastructure/s3-cache.tf
resource "aws_s3_bucket" "quote_cache" {
  bucket = local.environment == "prod" ? var.cache_bucket_name : "${var.cache_bucket_name}-${local.environment}"
  
  tags = {
    Name        = local.environment == "prod" ? var.cache_bucket_name : "${var.cache_bucket_name}-${local.environment}"
    Environment = local.environment
    Project     = "quote-lambda-tf"
  }
}

resource "aws_s3_bucket_versioning" "quote_cache" {
  bucket = aws_s3_bucket.quote_cache.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "quote_cache" {
  bucket = aws_s3_bucket.quote_cache.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "quote_cache" {
  bucket = aws_s3_bucket.quote_cache.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
```

#### 2. Add S3 Permissions to Existing Lambda IAM Policy:
```hcl
# Update the existing lambda_policy in infrastructure/lambda.tf
resource "aws_iam_policy" "lambda_policy" {
  name        = local.environment == "prod" ? "${var.project_name}-lambda-policy" : "${var.project_name}-lambda-policy-${local.environment}"
  description = "IAM policy for Lambda to access DynamoDB, Cognito, and S3"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:UpdateItem",
          "dynamodb:DeleteItem",
          "dynamodb:Scan",
          "dynamodb:Query"
        ]
        Resource = [
          aws_dynamodb_table.quotes_table.arn,
          "${aws_dynamodb_table.quotes_table.arn}/index/*",
          aws_dynamodb_table.user_likes_table.arn,
          "${aws_dynamodb_table.user_likes_table.arn}/index/*",
          aws_dynamodb_table.user_progress.arn,
          "${aws_dynamodb_table.user_progress.arn}/index/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "cognito-idp:ListUsers",
          "cognito-idp:AdminListGroupsForUser",
          "cognito-idp:AdminAddUserToGroup",
          "cognito-idp:AdminRemoveUserFromGroup",
          "cognito-idp:AdminGetUser",
          "cognito-idp:AdminDeleteUser"
        ]
        Resource = aws_cognito_user_pool.quote_app.arn
      },
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:GetObjectMetadata",
          "s3:DeleteObject"
        ]
        Resource = "${aws_s3_bucket.quote_cache.arn}/*"
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      }
    ]
  })
}
```

#### 3. Add Variables to Terraform:
```hcl
# infrastructure/variables.tf
variable "cache_bucket_name" {
  description = "S3 bucket name for quotes cache"
  type        = string
  default     = "quote-lambda-cache"
}
```

#### 4. Update Outputs:
```hcl
# infrastructure/outputs.tf
output "cache_bucket_name" {
  description = "S3 bucket name for quotes cache"
  value       = aws_s3_bucket.quote_cache.id
}

output "cache_bucket_arn" {
  description = "S3 bucket ARN for quotes cache"
  value       = aws_s3_bucket.quote_cache.arn
}
```

#### 5. Terraform Deployment Commands:
```bash
# Initialize Terraform
cd infrastructure
terraform init

# Create workspaces
terraform workspace new dev
# prod workspace is created by default

# Deploy to dev workspace
terraform workspace select dev
terraform plan -var-file="dev.tfvars"
terraform apply -var-file="dev.tfvars"

# Deploy to prod workspace (default)
terraform workspace select default
terraform plan -var-file="prod.tfvars"
terraform apply -var-file="prod.tfvars"

# Import existing bucket if it exists (run in appropriate workspace)
terraform import aws_s3_bucket.quote_cache quote-lambda-cache
```

## Performance Improvements

### Before S3 Cache:
- **Pagination**: 50-100ms (DynamoDB scan + sort)
- **Sorting**: O(N log N) per request
- **DynamoDB Reads**: 1000+ per page
- **Consistency**: Random ordering across pages

### After S3 Cache:
- **Pagination**: <10ms (in-memory)
- **Sorting**: O(1) (pre-computed)
- **DynamoDB Reads**: 0 (after initial cache)
- **Consistency**: Perfect ordering across pages

### Performance Gains:
- **10x faster pagination**
- **100% reduction in DynamoDB reads**
- **Consistent user experience**
- **Lower Lambda execution time**

## Cost Analysis

### Current Costs:
- **DynamoDB**: $1.50/month
- **Lambda**: $0.30/month
- **Total**: $1.80/month

### After Optimization:
- **DynamoDB**: $0.05/month (only for quote additions)
- **S3**: $0.02/month (storage + requests)
- **Lambda**: $0.20/month (faster execution)
- **Total**: $0.27/month

### Savings:
- **85% cost reduction** ($1.53/month saved)
- **Better performance** at lower cost
- **Scalable solution** that doesn't degrade with more quotes

## Testing Strategy

### Unit Tests:
1. **Cache Builder**: Test JSON generation and S3 upload
2. **Cache Loading**: Test S3 download and parsing
3. **Pagination**: Test all sort orders and filter combinations
4. **Cache Invalidation**: Test version-based refresh

### Integration Tests:
1. **End-to-End Flow**: Quote addition → Cache update → Frontend request
2. **Cache Miss**: Fallback to DynamoDB when cache unavailable
3. **Concurrent Requests**: Thread safety of static cache
4. **Cold Start**: Cache initialization on first request

### Performance Tests:
1. **Load Testing**: 1000 concurrent pagination requests
2. **Memory Usage**: Monitor Lambda memory consumption
3. **Cache Size**: Test with 10K, 50K, 100K quotes

### Monitoring Metrics:
- Cache hit/miss ratio
- S3 request count
- Lambda execution time
- Memory usage
- Error rates

## Conclusion

The S3-based caching solution provides:
- **Dramatic performance improvements** (10x faster pagination)
- **Significant cost savings** (85% reduction)
- **Minimal code changes** (frontend unchanged)
- **Scalable architecture** that handles growth
- **Simple implementation** with clear fallback strategy

This optimization transforms the quotes management system from a DynamoDB-bound application to a high-performance, cost-effective solution.
