# This file configures the Terraform backend to store state in S3
# Note: Backend configuration does not support variables
#
# PREREQUISITES:
# The S3 bucket and DynamoDB table must be created first using the bootstrap configuration
# See: infrastructure/bootstrap/README.md

terraform {
  backend "s3" {
    bucket         = "edwinbulter-terraform-state"
    key            = "quote-lambda-tf-frontend/terraform.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "terraform-locks"
    encrypt        = true
  }
}
