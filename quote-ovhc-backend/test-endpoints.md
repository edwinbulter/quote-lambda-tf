# API Endpoint Testing Guide

## Testing the Quote Backend

### Base URL
```
http://localhost:8080
```

### Available Endpoints

#### 1. GET /quote
Returns a random quote from the in-memory database.

**Request:**
```bash
curl -X GET http://localhost:8080/quote
```

**Expected Response (200 OK):**
```json
{
  "id": 1,
  "text": "Be yourself; everyone else is already taken.",
  "author": "Oscar Wilde",
  "likeCount": 0,
  "createdAt": "2024-01-01T12:00:00Z",
  "source": "Local"
}
```

**Possible Responses:**
- `200 OK`: Successfully returned a quote
- `404 Not Found`: No quotes available in database

#### 2. GET /health
Health check endpoint for monitoring and load balancer checks.

**Request:**
```bash
curl -X GET http://localhost:8080/health
```

**Expected Response (200 OK):**
```json
{
  "status": "healthy"
}
```

### Testing Scenarios

#### Scenario 1: Basic Functionality
```bash
# Navigate to src folder
cd src

# Start the application
go run main.go

# Test random quote endpoint
curl http://localhost:8080/quote

# Test health endpoint
curl http://localhost:8080/health
```

#### Scenario 2: Multiple Requests
```bash
# Request multiple random quotes
for i in {1..5}; do
  echo "Request $i:"
  curl -s http://localhost:8080/quote | jq '.id, .author'
  echo "---"
done
```

#### Scenario 3: Error Handling
```bash
# Test with invalid endpoint (should return 404)
curl -i http://localhost:8080/invalid

# Test with wrong method (should return 405)
curl -X POST http://localhost:8080/quote
```

### Environment Setup for Testing

#### Required Environment Variables
```bash
# OVHcloud Object Storage Configuration
export S3_ENDPOINT="https://s3.gra.cloud.ovh.net"
export S3_REGION="GRA"
export S3_BUCKET="quote-storage"
export S3_ACCESS_KEY="your-access-key"
export S3_SECRET_KEY="your-secret-key"

# Server Configuration
export PORT="8080"
```

#### Test without S3 (for local development)
If you want to test without S3, the application will still work but won't persist data:
```bash
# Navigate to src folder
cd src

# Run with minimal setup
export PORT="8080"
go run main.go
```

### Expected Behavior

#### First Run
1. Application starts and attempts to load from S3
2. If no quotes found in S3, creates 5 sample quotes
3. Saves sample quotes to S3 (if credentials provided)
4. Server starts on port 8080
5. Ready to accept requests

#### Subsequent Runs
1. Application starts and loads existing quotes from S3
2. Server starts on port 8080
3. Ready to serve existing quotes

### Performance Testing

#### Load Testing with curl
```bash
# Simple load test
for i in {1..100}; do
  curl -s http://localhost:8080/quote > /dev/null
done

# Measure response times
time curl -s http://localhost:8080/quote
```

#### Concurrent Requests
```bash
# Test with multiple concurrent processes
for i in {1..10}; do
  curl -s http://localhost:8080/quote &
done
wait
```

### Monitoring and Debugging

#### Application Logs
The application logs important events:
- Server startup
- S3 connection status
- Quote requests and responses
- Error conditions

#### Health Monitoring
```bash
# Continuous health check
while true; do
  response=$(curl -s http://localhost:8080/health)
  echo "$(date): $response"
  sleep 5
done
```

### Integration with OVHcloud

#### After VM Deployment
Once deployed on OVHcloud VM:
```bash
# Test from external client
curl http://YOUR_VM_IP:8080/quote

# Test health check
curl http://YOUR_VM_IP:8080/health
```

#### Firewall Considerations
Ensure port 8080 is open on the VM firewall:
```bash
# Check firewall status
sudo ufw status

# Allow port 8080 if needed
sudo ufw allow 8080
```

### Troubleshooting

#### Common Issues and Solutions

1. **Connection Refused**
   - Check if application is running
   - Verify port is correct
   - Check firewall settings

2. **S3 Connection Errors**
   - Verify credentials
   - Check bucket name
   - Ensure network connectivity

3. **No Quotes Available**
   - Check S3 bucket permissions
   - Verify application logs
   - Application should create sample quotes automatically

#### Debug Commands
```bash
# Check if port is listening
netstat -tlnp | grep :8080

# Check application processes
ps aux | grep quote-backend

# Test S3 connectivity
aws s3 ls --endpoint-url https://s3.gra.cloud.ovh.net
```
