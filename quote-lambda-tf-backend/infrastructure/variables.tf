variable "aws_region" {
  type        = string
  default     = "eu-central-1"
  description = "AWS region to deploy resources"
}

variable "project_name" {
  type        = string
  default     = "quotes-lambda-java"
  description = "Project name used for resource naming"
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
