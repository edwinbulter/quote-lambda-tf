# HTTP API Gateway
resource "aws_apigatewayv2_api" "quote_api" {
  name          = var.environment == "prod" ? "${var.project_name}-api" : "${var.project_name}-api-${var.environment}"
  protocol_type = "HTTP"
  description   = "HTTP API for ${var.project_name} (${var.environment})"
  
  cors_configuration {
    allow_origins = ["*"]
    allow_methods = ["GET", "POST", "PATCH", "OPTIONS"]
    allow_headers = ["content-type"]
    max_age       = 300
  }
}

# API Gateway stage
resource "aws_apigatewayv2_stage" "api_stage" {
  api_id      = aws_apigatewayv2_api.quote_api.id
  name        = "$default"
  auto_deploy = true

  # Enable CloudWatch logging
  default_route_settings {
    detailed_metrics_enabled = true
    logging_level            = "INFO"
    throttling_burst_limit   = 100
    throttling_rate_limit    = 100
  }

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api_gateway.arn
    format          = jsonencode({
      requestId               = "$context.requestId"
      sourceIp                = "$context.identity.sourceIp"
      requestTime             = "$context.requestTime"
      protocol                = "$context.protocol"
      httpMethod              = "$context.httpMethod"
      resourcePath            = "$context.resourcePath"
      routeKey                = "$context.routeKey"
      status                  = "$context.status"
      responseLength          = "$context.responseLength"
      integrationErrorMessage = "$context.integrationErrorMessage"
    })
  }
}

# API Gateway integration with Lambda (using alias for SnapStart)
resource "aws_apigatewayv2_integration" "lambda_integration" {
  api_id           = aws_apigatewayv2_api.quote_api.id
  integration_type = "AWS_PROXY"

  connection_type    = "INTERNET"
  description       = "Lambda integration for ${var.project_name}"
  integration_method = "POST"
  integration_uri    = aws_lambda_alias.quote_lambda_live.invoke_arn
}

# API Gateway route for all methods
resource "aws_apigatewayv2_route" "api_route" {
  api_id    = aws_apigatewayv2_api.quote_api.id
  route_key = "ANY /{proxy+}"
  target    = "integrations/${aws_apigatewayv2_integration.lambda_integration.id}"
}

# CloudWatch Log Group for API Gateway
resource "aws_cloudwatch_log_group" "api_gateway" {
  name              = var.environment == "prod" ? "/aws/api-gw/${var.project_name}" : "/aws/api-gw/${var.project_name}-${var.environment}"
  retention_in_days = 30
}

output "api_gateway_url" {
  description = "The URL of the API Gateway"
  value       = aws_apigatewayv2_stage.api_stage.invoke_url
}
