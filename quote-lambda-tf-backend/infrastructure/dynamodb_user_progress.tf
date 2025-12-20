# DynamoDB table for storing user progress (sequential navigation)
resource "aws_dynamodb_table" "user_progress" {
  name         = local.environment == "prod" ? "quote-lambda-tf-user-progress" : "quote-lambda-tf-user-progress-${local.environment}"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "username"

  attribute {
    name = "username"
    type = "S"
  }

  attribute {
    name = "lastQuoteId"
    type = "N"
  }

  attribute {
    name = "updatedAt"
    type = "N"
  }

  # GSI to query by lastQuoteId (optional, for analytics)
  global_secondary_index {
    name            = "LastQuoteIdIndex"
    hash_key        = "lastQuoteId"
    range_key       = "updatedAt"
    projection_type = "ALL"
  }

  # Enable point-in-time recovery for data protection
  point_in_time_recovery {
    enabled = true
  }

  # Server-side encryption
  server_side_encryption {
    enabled = true
  }

  tags = {
    Name        = local.environment == "prod" ? "quote-lambda-tf-user-progress" : "quote-lambda-tf-user-progress-${local.environment}"
    Environment = local.environment
    Project     = "quote-lambda-tf"
    ManagedBy   = "Terraform"
    Purpose     = "User progress tracking for sequential navigation"
  }
}

# Output the table name
output "dynamodb_user_progress_table_name" {
  value       = aws_dynamodb_table.user_progress.name
  description = "Name of the DynamoDB user progress table"
}

# Output the table ARN
output "dynamodb_user_progress_table_arn" {
  value       = aws_dynamodb_table.user_progress.arn
  description = "ARN of the DynamoDB user progress table"
}
