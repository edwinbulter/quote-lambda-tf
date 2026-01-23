# Note: OVHcloud Web Apps are not currently supported in Terraform
# Web Apps need to be created manually through the OVHcloud Manager
# This file is kept for reference and future Terraform provider updates

# Placeholder for future Web Apps Terraform support
# resource "ovh_cloud_project_webapp" "quote_backend" {
#   service_name = var.project_id
#   name         = var.webapp_name
#   # Additional configuration will be added when available
# }

# For now, Web Apps must be created manually:
# 1. Go to OVHcloud Manager
# 2. Navigate to Public Cloud -> Your Project -> Web Apps
# 3. Create a new Web App with Go runtime
# 4. Configure environment variables and deployment settings

output "webapp_manual_setup_note" {
  description = "Note about manual Web Apps setup"
  value       = "Web Apps must be created manually through OVHcloud Manager - Terraform support not yet available"
}
