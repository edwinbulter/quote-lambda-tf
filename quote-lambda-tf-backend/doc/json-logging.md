# JSON Logging Configuration

## Overview

The Lambda function uses JSON-formatted logging to CloudWatch Logs for better searchability and analysis.

## Benefits

### 1. **Single-Line Stack Traces**
Stack traces are included as a single JSON field instead of multiple lines:

**Before (plain text logging):**
```
2025-12-12 15:30:00 ERROR QuoteHandler - Error processing request
java.lang.NullPointerException: Cannot invoke method on null
    at ebulter.quote.lambda.QuoteHandler.handleRequest(QuoteHandler.java:65)
    at com.amazonaws.services.lambda.runtime.api.LambdaRuntime.handleRequest(LambdaRuntime.java:123)
    at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
    ... 15 more
```
Each line appears as a separate CloudWatch log entry, making it hard to search.

**After (JSON logging):**
```json
{
  "@timestamp": "2025-12-12T15:30:00.123+01:00",
  "level": "ERROR",
  "logger_name": "ebulter.quote.lambda.QuoteHandler",
  "message": "Error processing request",
  "stack_trace": "java.lang.NullPointerException: Cannot invoke method on null\n\tat e.q.l.QuoteHandler.handleRequest(QuoteHandler.java:65)\n\tat c.a.s.l.r.a.LambdaRuntime.handleRequest(LambdaRuntime.java:123)\n\t... 15 more",
  "application": "quote-lambda-tf-backend"
}
```
The entire error is in **one CloudWatch log entry** with the stack trace in the `stack_trace` field.

### 2. **Easy CloudWatch Logs Insights Queries**

Find all errors:
```
fields @timestamp, message, stack_trace
| filter level = "ERROR"
| sort @timestamp desc
```

Find specific exceptions:
```
fields @timestamp, message, stack_trace
| filter stack_trace like /NullPointerException/
| sort @timestamp desc
```

Find user actions:
```
fields @timestamp, message
| filter message like /User action/
| parse message "userId=*, email=*, action=*" as userId, email, action
| stats count() by action
```

### 3. **Structured Data**

Every log entry includes:
- `@timestamp` - ISO 8601 timestamp
- `level` - Log level (INFO, WARN, ERROR)
- `logger_name` - Java class name
- `message` - Log message
- `thread_name` - Thread name
- `application` - Custom field: "quote-lambda-tf-backend"
- `stack_trace` - Exception stack trace (if present)

## Configuration

### Dependencies

**File:** `pom.xml`

```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

### Logback Configuration

**File:** `src/main/resources/logback.xml`

```xml
<encoder class="net.logstash.logback.encoder.LogstashEncoder">
    <throwableConverter class="net.logstash.logback.stacktrace.ShortenedThrowableConverter">
        <maxDepthPerThrowable>30</maxDepthPerThrowable>
        <maxLength>2048</maxLength>
        <rootCauseFirst>true</rootCauseFirst>
    </throwableConverter>
    <customFields>{"application":"quote-lambda-tf-backend"}</customFields>
</encoder>
```

## Example Log Entries

### INFO Log
```json
{
  "@timestamp": "2025-12-12T15:51:37.011633+01:00",
  "@version": "1",
  "message": "User action: userId=test-user-id, email=test@example.com, action=LIKE_QUOTE",
  "logger_name": "ebulter.quote.lambda.QuoteHandler",
  "thread_name": "main",
  "level": "INFO",
  "level_value": 20000,
  "application": "quote-lambda-tf-backend"
}
```

### ERROR Log with Stack Trace
```json
{
  "@timestamp": "2025-12-12T15:51:37.123456+01:00",
  "@version": "1",
  "message": "Error checking user role",
  "logger_name": "ebulter.quote.lambda.QuoteHandler",
  "thread_name": "main",
  "level": "ERROR",
  "level_value": 40000,
  "stack_trace": "java.lang.NullPointerException: requestContext is null\n\tat e.q.l.QuoteHandler.hasUserRole(QuoteHandler.java:125)\n\tat e.q.l.QuoteHandler.handleRequest(QuoteHandler.java:56)",
  "application": "quote-lambda-tf-backend"
}
```

## CloudWatch Logs Insights Examples

### Find Authorization Failures
```
fields @timestamp, message
| filter level = "ERROR" and message like /user role/
| sort @timestamp desc
| limit 20
```

### Count Actions by Type
```
fields @timestamp, message
| filter message like /User action/
| parse message "action=*" as action
| stats count() by action
```

### Find Slow Requests
```
fields @timestamp, @duration
| filter @duration > 1000
| sort @duration desc
```

## Resources

- [Logstash Logback Encoder Documentation](https://github.com/logfellow/logstash-logback-encoder)
- [CloudWatch Logs Insights Query Syntax](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/CWL_QuerySyntax.html)
