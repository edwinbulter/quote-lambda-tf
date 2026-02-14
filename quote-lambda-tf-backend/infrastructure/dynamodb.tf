# DynamoDB table for storing quotes
resource "aws_dynamodb_table" "quotes_table" {
  name           = local.environment == "prod" ? var.quotes_table_name : "${var.quotes_table_name}-${local.environment}"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "id"

  attribute {
    name = "id"
    type = "N"
  }

  # Global Secondary Index for querying by author
  attribute {
    name = "author"
    type = "S"
  }

  global_secondary_index {
    name            = "AuthorIndex"
    hash_key        = "author"
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
    Name      = var.project_name
    ManagedBy = "Terraform"
  }
}

# Autoscaling for quotes table - REMOVED (using on-demand billing)
# Autoscaling for quotes GSI - REMOVED (using on-demand billing)

# DynamoDB table for storing user likes
resource "aws_dynamodb_table" "user_likes_table" {
  name           = local.environment == "prod" ? "quote-lambda-tf-user-likes" : "quote-lambda-tf-user-likes-${local.environment}"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "username"
  range_key      = "quoteId"

  attribute {
    name = "username"
    type = "S"
  }

  attribute {
    name = "quoteId"
    type = "N"
  }

  attribute {
    name = "likedAt"
    type = "N"
  }

  # Global Secondary Index for querying by quote (to get all users who liked it)
  global_secondary_index {
    name            = "QuoteIdIndex"
    hash_key        = "quoteId"
    range_key       = "likedAt"
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
    Name      = "${var.project_name}-user-likes"
    ManagedBy = "Terraform"
  }
}

# Autoscaling for GSI read capacity - REMOVED (using on-demand billing)
# Autoscaling for GSI write capacity - REMOVED (using on-demand billing)
