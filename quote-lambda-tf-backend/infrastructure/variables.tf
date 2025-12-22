variable "aws_region" {
  type        = string
  default     = "eu-central-1"
  description = "AWS region to deploy resources"
}

variable "environment" {
  type        = string
  default     = "prod"
  description = "Environment name (dev, prod)"
}

variable "project_name" {
  type        = string
  default     = "quote-lambda-tf-backend"
  description = "Project name used for resource naming"
}

variable "quotes_table_name" {
  type        = string
  default     = "quote-lambda-tf-quotes"
  description = "DynamoDB table name for storing quotes"
}

variable "lambda_memory_size" {
  type        = number
  default     = 512
  description = "Memory size for Lambda function in MB"
}

variable "lambda_timeout" {
  type        = number
  default     = 30
  description = "Timeout for Lambda function in seconds"
}

variable "dynamodb_read_capacity" {
  type        = number
  default     = 5
  description = "DynamoDB read capacity units"
}

variable "dynamodb_write_capacity" {
  type        = number
  default     = 5
  description = "DynamoDB write capacity units"
}

variable "cache_bucket_name" {
  description = "S3 bucket name for quotes cache"
  type        = string
  default     = "quote-lambda-cache"
}

# Google OAuth Configuration
variable "google_oauth_client_id" {
  type        = string
  description = "Google OAuth Client ID for Cognito authentication"
  sensitive   = true
}

variable "google_oauth_client_secret" {
  type        = string
  description = "Google OAuth Client Secret for Cognito authentication"
  sensitive   = true
}