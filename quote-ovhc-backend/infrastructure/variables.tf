variable "ovh_application_key" {
  description = "OVHcloud Application Key"
  type        = string
  sensitive   = true
}

variable "ovh_application_secret" {
  description = "OVHcloud Application Secret"
  type        = string
  sensitive   = true
}

variable "ovh_consumer_key" {
  description = "OVHcloud Consumer Key"
  type        = string
  sensitive   = true
}

variable "project_id" {
  description = "OVHcloud Project ID"
  type        = string
}

# Note: OpenStack variables removed - VM created manually
# No longer need openstack_user_name and openstack_password

variable "webapp_name" {
  description = "Name of the Web App"
  type        = string
  default     = "quote-backend"
}

variable "database_name" {
  description = "Name of the MongoDB database"
  type        = string
  default     = "quote-db"
}
