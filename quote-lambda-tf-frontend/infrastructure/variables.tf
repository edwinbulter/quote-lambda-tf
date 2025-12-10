variable "aws_region" {
  type        = string
  default     = "eu-central-1"
}

variable "environment" {
  type        = string
  default     = "prod"
  description = "Environment name (dev, prod)"
}

variable "bucket_name" {
  type        = string
  default     = "quote-lambda-tf-frontend"
}