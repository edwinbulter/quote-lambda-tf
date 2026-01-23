# Note: Using in-memory database with manual Object Storage setup
# OVHcloud Terraform provider doesn't easily support Object Storage resources
# Data will be persisted manually through OVHcloud Manager and Go application

# Output configuration for in-memory database approach
output "in_memory_db_info" {
  description = "In-memory database configuration"
  value = {
    approach       = "in-memory"
    persistence     = "manual Object Storage setup"
    cost_estimate   = "~$0.01/month (storage) + $6.50/month (Web App)"
    recommendation   = "Use Go's in-memory maps with S3-compatible storage"
  }
}

output "manual_storage_setup_guide" {
  description = "Guide for manual Object Storage setup"
  value = [
    "1. Go to OVHcloud Manager: https://www.ovh.com/auth/",
    "2. Navigate to Public Cloud -> Your Project",
    "3. Click on 'Object Storage' in the left menu",
    "4. Click 'Create Object Storage'",
    "5. Configure: Name: quote-storage, Region: GRA, Type: Public",
    "6. Click 'Create'",
    "7. Create Container: quotes-data (for quotes.json files)",
    "8. Generate S3-compatible credentials for Go application"
  ]
}

output "storage_access_info" {
  description = "Storage access information for Go application"
  value = {
    approach     = "S3-compatible Object Storage",
    endpoint    = "https://s3.gra.cloud.ovh.net",
    region      = "GRA",
    container   = "quotes-data",
    note        = "Use AWS SDK with OVHcloud S3 endpoint"
  }
}
