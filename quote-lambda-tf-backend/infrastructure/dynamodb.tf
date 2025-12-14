# DynamoDB table for storing quotes
resource "aws_dynamodb_table" "quotes_table" {
  name           = local.environment == "prod" ? var.quotes_table_name : "${var.quotes_table_name}-${local.environment}"
  billing_mode   = "PROVISIONED"
  read_capacity  = var.dynamodb_read_capacity
  write_capacity = var.dynamodb_write_capacity
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
    name               = "AuthorIndex"
    hash_key           = "author"
    projection_type    = "ALL"
    read_capacity     = var.dynamodb_read_capacity
    write_capacity    = var.dynamodb_write_capacity
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

# Autoscaling for read capacity
resource "aws_appautoscaling_target" "dynamodb_table_read_target" {
  max_capacity       = 100
  min_capacity       = var.dynamodb_read_capacity
  resource_id        = "table/${aws_dynamodb_table.quotes_table.name}"
  scalable_dimension = "dynamodb:table:ReadCapacityUnits"
  service_namespace  = "dynamodb"
}

resource "aws_appautoscaling_policy" "dynamodb_table_read_policy" {
  name               = "DynamoDBReadCapacityUtilization:${aws_appautoscaling_target.dynamodb_table_read_target.resource_id}"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.dynamodb_table_read_target.resource_id
  scalable_dimension = aws_appautoscaling_target.dynamodb_table_read_target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.dynamodb_table_read_target.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "DynamoDBReadCapacityUtilization"
    }
    target_value = 70.0
  }
}

# Autoscaling for write capacity
resource "aws_appautoscaling_target" "dynamodb_table_write_target" {
  max_capacity       = 100
  min_capacity       = var.dynamodb_write_capacity
  resource_id        = "table/${aws_dynamodb_table.quotes_table.name}"
  scalable_dimension = "dynamodb:table:WriteCapacityUnits"
  service_namespace  = "dynamodb"
}

resource "aws_appautoscaling_policy" "dynamodb_table_write_policy" {
  name               = "DynamoDBWriteCapacityUtilization:${aws_appautoscaling_target.dynamodb_table_write_target.resource_id}"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.dynamodb_table_write_target.resource_id
  scalable_dimension = aws_appautoscaling_target.dynamodb_table_write_target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.dynamodb_table_write_target.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "DynamoDBWriteCapacityUtilization"
    }
    target_value = 70.0
  }
}

# DynamoDB table for storing user likes
resource "aws_dynamodb_table" "user_likes_table" {
  name           = local.environment == "prod" ? "quote-lambda-tf-user-likes" : "quote-lambda-tf-user-likes-${local.environment}"
  billing_mode   = "PROVISIONED"
  read_capacity  = var.dynamodb_read_capacity
  write_capacity = var.dynamodb_write_capacity
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
    name               = "QuoteIdIndex"
    hash_key           = "quoteId"
    range_key          = "likedAt"
    projection_type    = "ALL"
    read_capacity      = var.dynamodb_read_capacity
    write_capacity     = var.dynamodb_write_capacity
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

# Autoscaling for read capacity - user_likes_table
resource "aws_appautoscaling_target" "user_likes_table_read_target" {
  max_capacity       = 100
  min_capacity       = var.dynamodb_read_capacity
  resource_id        = "table/${aws_dynamodb_table.user_likes_table.name}"
  scalable_dimension = "dynamodb:table:ReadCapacityUnits"
  service_namespace  = "dynamodb"
}

resource "aws_appautoscaling_policy" "user_likes_table_read_policy" {
  name               = "DynamoDBReadCapacityUtilization:${aws_appautoscaling_target.user_likes_table_read_target.resource_id}"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.user_likes_table_read_target.resource_id
  scalable_dimension = aws_appautoscaling_target.user_likes_table_read_target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.user_likes_table_read_target.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "DynamoDBReadCapacityUtilization"
    }
    target_value = 70.0
  }
}

# Autoscaling for write capacity - user_likes_table
resource "aws_appautoscaling_target" "user_likes_table_write_target" {
  max_capacity       = 100
  min_capacity       = var.dynamodb_write_capacity
  resource_id        = "table/${aws_dynamodb_table.user_likes_table.name}"
  scalable_dimension = "dynamodb:table:WriteCapacityUnits"
  service_namespace  = "dynamodb"
}

resource "aws_appautoscaling_policy" "user_likes_table_write_policy" {
  name               = "DynamoDBWriteCapacityUtilization:${aws_appautoscaling_target.user_likes_table_write_target.resource_id}"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.user_likes_table_write_target.resource_id
  scalable_dimension = aws_appautoscaling_target.user_likes_table_write_target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.user_likes_table_write_target.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "DynamoDBWriteCapacityUtilization"
    }
    target_value = 70.0
  }
}

# Autoscaling for GSI read capacity
resource "aws_appautoscaling_target" "user_likes_gsi_read_target" {
  max_capacity       = 100
  min_capacity       = var.dynamodb_read_capacity
  resource_id        = "table/${aws_dynamodb_table.user_likes_table.name}/index/QuoteIdIndex"
  scalable_dimension = "dynamodb:index:ReadCapacityUnits"
  service_namespace  = "dynamodb"
}

resource "aws_appautoscaling_policy" "user_likes_gsi_read_policy" {
  name               = "DynamoDBReadCapacityUtilization:${aws_appautoscaling_target.user_likes_gsi_read_target.resource_id}"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.user_likes_gsi_read_target.resource_id
  scalable_dimension = aws_appautoscaling_target.user_likes_gsi_read_target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.user_likes_gsi_read_target.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "DynamoDBReadCapacityUtilization"
    }
    target_value = 70.0
  }
}

# Autoscaling for GSI write capacity
resource "aws_appautoscaling_target" "user_likes_gsi_write_target" {
  max_capacity       = 100
  min_capacity       = var.dynamodb_write_capacity
  resource_id        = "table/${aws_dynamodb_table.user_likes_table.name}/index/QuoteIdIndex"
  scalable_dimension = "dynamodb:index:WriteCapacityUnits"
  service_namespace  = "dynamodb"
}

resource "aws_appautoscaling_policy" "user_likes_gsi_write_policy" {
  name               = "DynamoDBWriteCapacityUtilization:${aws_appautoscaling_target.user_likes_gsi_write_target.resource_id}"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.user_likes_gsi_write_target.resource_id
  scalable_dimension = aws_appautoscaling_target.user_likes_gsi_write_target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.user_likes_gsi_write_target.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "DynamoDBWriteCapacityUtilization"
    }
    target_value = 70.0
  }
}
