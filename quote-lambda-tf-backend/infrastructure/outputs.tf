output "lambda_function_name" {
  description = "Name of the Lambda function"
  value       = aws_lambda_function.quote_lambda.function_name
}

output "lambda_invoke_arn" {
  description = "ARN to invoke the Lambda function"
  value       = aws_lambda_function.quote_lambda.invoke_arn
}

output "dynamodb_table_name" {
  description = "Name of the DynamoDB table"
  value       = aws_dynamodb_table.quotes_table.name
}

output "dynamodb_table_arn" {
  description = "ARN of the DynamoDB table"
  value       = aws_dynamodb_table.quotes_table.arn
}

output "aws_region" {
  description = "AWS region where resources are deployed"
  value       = var.aws_region
}

output "user_likes_table_name" {
  description = "Name of the DynamoDB user likes table"
  value       = aws_dynamodb_table.user_likes_table.name
}

output "user_likes_table_arn" {
  description = "ARN of the DynamoDB user likes table"
  value       = aws_dynamodb_table.user_likes_table.arn
}
