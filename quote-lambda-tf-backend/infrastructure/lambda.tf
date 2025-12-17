# IAM role for Lambda execution
resource "aws_iam_role" "lambda_execution_role" {
  name = local.environment == "prod" ? "${var.project_name}-lambda-role" : "${var.project_name}-lambda-role-${local.environment}"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

# IAM policy for Lambda to access DynamoDB and Cognito
resource "aws_iam_policy" "lambda_policy" {
  name        = local.environment == "prod" ? "${var.project_name}-lambda-policy" : "${var.project_name}-lambda-policy-${local.environment}"
  description = "IAM policy for Lambda to access DynamoDB and Cognito"
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:UpdateItem",
          "dynamodb:DeleteItem",
          "dynamodb:Scan",
          "dynamodb:Query"
        ]
        Resource = [
          aws_dynamodb_table.quotes_table.arn,
          "${aws_dynamodb_table.quotes_table.arn}/index/*",
          aws_dynamodb_table.user_likes_table.arn,
          "${aws_dynamodb_table.user_likes_table.arn}/index/*",
          aws_dynamodb_table.user_views.arn,
          "${aws_dynamodb_table.user_views.arn}/index/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "cognito-idp:ListUsers",
          "cognito-idp:AdminListGroupsForUser",
          "cognito-idp:AdminAddUserToGroup",
          "cognito-idp:AdminRemoveUserFromGroup",
          "cognito-idp:AdminGetUser",
          "cognito-idp:AdminDeleteUser"
        ]
        Resource = aws_cognito_user_pool.quote_app.arn
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      }
    ]
  })
}

# Attach the policy to the IAM role
resource "aws_iam_role_policy_attachment" "lambda_policy_attachment" {
  role       = aws_iam_role.lambda_execution_role.name
  policy_arn = aws_iam_policy.lambda_policy.arn
}

# Lambda function
resource "aws_lambda_function" "quote_lambda" {
  function_name = local.environment == "prod" ? var.project_name : "${var.project_name}-${local.environment}"
  role          = aws_iam_role.lambda_execution_role.arn
  handler       = "ebulter.quote.lambda.QuoteHandler::handleRequest"
  runtime       = "java21"
  memory_size   = var.lambda_memory_size
  timeout       = var.lambda_timeout
  publish       = true
  
  filename         = "${path.module}/../target/${var.project_name}-1.2.0-SNAPSHOT.jar"
  source_code_hash = filebase64sha256("${path.module}/../target/${var.project_name}-1.2.0-SNAPSHOT.jar")
  
  environment {
    variables = {
      DYNAMODB_TABLE            = aws_dynamodb_table.quotes_table.name
      DYNAMODB_USER_LIKES_TABLE = aws_dynamodb_table.user_likes_table.name
      DYNAMODB_USER_VIEWS_TABLE = aws_dynamodb_table.user_views.name
      USER_POOL_ID              = aws_cognito_user_pool.quote_app.id
    }
  }

  # Enable SnapStart for faster cold starts
  snap_start {
    apply_on = "PublishedVersions"
  }

  depends_on = [
    aws_iam_role_policy_attachment.lambda_policy_attachment
  ]
}

# Publish a Lambda version for SnapStart
resource "aws_lambda_alias" "quote_lambda_live" {
  name             = "live"
  description      = "Live alias for SnapStart"
  function_name    = aws_lambda_function.quote_lambda.function_name
  function_version = aws_lambda_function.quote_lambda.version
}

# Allow API Gateway to invoke the Lambda alias
resource "aws_lambda_permission" "api_gateway_invoke" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_alias.quote_lambda_live.function_name
  qualifier     = aws_lambda_alias.quote_lambda_live.name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.quote_api.execution_arn}/*/*"
}

# Output the IAM role ARN for GitHub Actions to assume
output "lambda_function_arn" {
  description = "ARN of the Lambda function for GitHub Actions"
  value       = aws_lambda_function.quote_lambda.arn
}

# Output the IAM role ARN for GitHub Actions to assume
output "github_actions_role_arn" {
  description = "ARN of the IAM role that GitHub Actions can assume"
  value       = aws_iam_role.lambda_execution_role.arn
}
