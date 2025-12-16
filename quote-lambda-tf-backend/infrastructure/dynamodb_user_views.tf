# DynamoDB table for user views
resource "aws_dynamodb_table" "user_views" {
  name         = local.environment == "prod" ? "quote-lambda-tf-user-views" : "quote-lambda-tf-user-views-${local.environment}"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "username"
  range_key    = "viewedAt"

  attribute {
    name = "username"
    type = "S"
  }

  attribute {
    name = "viewedAt"
    type = "N"
  }

  attribute {
    name = "quoteId"
    type = "N"
  }

  # GSI to query by quoteId (optional, for analytics)
  global_secondary_index {
    name            = "QuoteIdIndex"
    hash_key        = "quoteId"
    range_key       = "viewedAt"
    projection_type = "ALL"
  }

  tags = {
    Name        = local.environment == "prod" ? "quote-lambda-tf-user-views" : "quote-lambda-tf-user-views-${local.environment}"
    Environment = local.environment
    Project     = "quote-lambda-tf"
  }
}

# Output the table name
output "dynamodb_user_views_table_name" {
  value       = aws_dynamodb_table.user_views.name
  description = "Name of the DynamoDB user views table"
}
