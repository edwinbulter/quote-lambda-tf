# HTTP API Gateway
resource "aws_apigatewayv2_api" "quote_api" {
  name          = local.environment == "prod" ? "${var.project_name}-api" : "${var.project_name}-api-${local.environment}"
  protocol_type = "HTTP"
  description   = "HTTP API for ${var.project_name} (${local.environment})"
  
  cors_configuration {
    allow_origins     = ["*"]
    allow_methods     = ["GET", "POST", "PATCH", "DELETE", "OPTIONS"]
    allow_headers     = ["content-type", "authorization"]
    expose_headers    = ["content-type", "authorization"]
    allow_credentials = false
    max_age           = 300
  }
}

# API Gateway stage
resource "aws_apigatewayv2_stage" "api_stage" {
  api_id      = aws_apigatewayv2_api.quote_api.id
  name        = "$default"
  auto_deploy = true

  # Enable CloudWatch logging and throttling
  default_route_settings {
    detailed_metrics_enabled = true
    throttling_burst_limit   = 100
    throttling_rate_limit    = 100
    # Note: logging_level is not supported in default_route_settings for HTTP APIs
    # Access logs are configured via access_log_settings below
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

# API Gateway route for all methods (no authorization - handled in Lambda)
resource "aws_apigatewayv2_route" "api_route" {
  api_id    = aws_apigatewayv2_api.quote_api.id
  route_key = "ANY /{proxy+}"
  target    = "integrations/${aws_apigatewayv2_integration.lambda_integration.id}"
}

# CloudWatch Log Group for API Gateway
resource "aws_cloudwatch_log_group" "api_gateway" {
  name              = local.environment == "prod" ? "/aws/api-gw/${var.project_name}" : "/aws/api-gw/${var.project_name}-${local.environment}"
  retention_in_days = 30
}

output "api_gateway_url" {
  description = "The URL of the API Gateway"
  value       = aws_apigatewayv2_stage.api_stage.invoke_url
}
