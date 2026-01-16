# Application Insights Setup - Azure Functions Backend

## 🎯 What Was Added

Application Insights has been re-enabled in Terraform to provide comprehensive logging and monitoring for the Azure Functions backend.

## 📋 Terraform Changes

### 1. Application Insights Resource
```hcl
resource "azurerm_application_insights" "app_insights" {
  name                = "quote-backend-ai"
  location            = azurerm_resource_group.rg.location
  resource_group_name = azurerm_resource_group.rg.name
  workspace_id        = azurerm_log_analytics_workspace.workspace.id
  application_type    = "web"
}
```

### 2. Function App Integration
```hcl
app_settings = {
  "APPINSIGHTS_INSTRUMENTATIONKEY" = azurerm_application_insights.app_insights.instrumentation_key
  "APPLICATIONINSIGHTS_CONNECTION_STRING" = azurerm_application_insights.app_insights.connection_string
  # ... other settings
}
```

### 3. New Outputs
```hcl
output "application_insights_connection_string" { ... }
output "application_insights_instrumentation_key" { ... }
```

## 🚀 Benefits

- ✅ **Automatic Logging** - All function calls are automatically logged
- ✅ **Performance Monitoring** - Track execution times and bottlenecks
- ✅ **Error Tracking** - Detailed error information and stack traces
- ✅ **Dependency Tracking** - External API calls and database operations
- ✅ **Live Metrics** - Real-time monitoring dashboard
- ✅ **Custom Events** - Application-specific logging

## 🔧 How to Use

### 1. Apply Changes
```bash
cd quote-azure-backend/infrastructure
terraform apply
```

### 2. View Logs in Azure Portal
1. Navigate to **Application Insights** → **quote-backend-ai**
2. Click on **Logs** (under Monitoring)
3. Write KQL queries to analyze data

### 3. Common KQL Queries

#### All Function Logs
```kql
traces
| where message contains "quote"
| order by timestamp desc
```

#### Error Logs Only
```kql
exceptions
| order by timestamp desc
```

#### Performance Metrics
```kql
requests
| where name contains "quote"
| summarize avg(duration) by name
```

#### Custom Application Logs
```kql
traces
| where severityLevel > 2  # Warning and Error
| project timestamp, message, severityLevel
```

### 4. Live Monitoring
1. Go to **Application Insights** → **quote-backend-ai**
2. Click **Live Metrics** for real-time monitoring
3. See incoming requests, response times, and error rates

### 5. Search Specific Logs
1. Click **Search** (under Investigate)
2. Search for specific terms like "Getting all quotes"
3. Filter by time range and severity level

## 📊 What Gets Logged Automatically

### Function Execution
- Function name and execution time
- Request/response data
- Success/failure status
- Execution duration

### Dependencies
- Azure Table Storage operations
- HTTP requests to external APIs
- Database queries

### Exceptions
- Full stack traces
- Error messages
- Context information

### Custom Logging
- Application-specific events
- Business logic tracking
- Debug information

## 🛠️ Custom Logging in Code

```csharp
// In your Azure Functions
_logger.LogInformation("Getting quotes for user {UserId}", userId);
_logger.LogWarning("Rate limit exceeded for user {UserId}", userId);
_logger.LogError(ex, "Failed to process quote request");
```

## 🔍 Troubleshooting

### No Logs Appearing
1. Check Application Insights connection string in Function App settings
2. Verify Function App is restarted after changes
3. Check Log Analytics workspace is properly linked

### Logs Not Updating
1. Wait 2-5 minutes for propagation
2. Check time range in queries
3. Verify Function App is running

### Missing Custom Logs
1. Ensure ILogger is properly injected
2. Check log level settings in app settings
3. Verify code is actually executing

## 📋 Next Steps

1. **Apply Terraform changes** to enable Application Insights
2. **Test your API endpoints** to generate logs
3. **Check Azure Portal** to verify logging is working
4. **Set up alerts** for critical errors or performance issues

## 🚨 Important Notes

- **Data Retention**: Logs are retained for 30 days (configurable)
- **Cost**: Application Insights has costs based on data ingestion
- **Performance**: Minimal impact on function performance
- **Privacy**: Ensure no sensitive data is logged

Your Azure Functions backend now has comprehensive logging and monitoring capabilities! 🚀
