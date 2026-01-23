# Main Terraform configuration for OVHcloud Quote Backend

# Data source to get project information
data "ovh_cloud_project" "project" {
  service_name = var.project_id
}

# Output project information
output "project_name" {
  description = "OVHcloud project name"
  value       = data.ovh_cloud_project.project.description
}

output "project_id" {
  description = "OVHcloud project ID"
  value       = var.project_id
}

# Local variables for common configurations
locals {
  common_tags = {
    project     = "quote-backend"
    environment = "production"
    provider    = "ovhcloud"
  }
}
