# This file configures the Terraform backend to store state in S3
# Note: Backend configuration does not support variables
#
# PREREQUISITES:
# The S3 bucket and DynamoDB table must be created first using the bootstrap configuration
# See: ../quote-lambda-tf-frontend/infrastructure/bootstrap/README.md
#
# The bootstrap infrastructure is shared across all projects and only needs to be created once.

terraform {
  backend "s3" {
    bucket         = "edwinbulter-terraform-state"
    key            = "quote-lambda-tf-backend/terraform.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "terraform-locks"
    encrypt        = true
  }
}