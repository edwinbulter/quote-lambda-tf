# This file configures the Terraform backend to store state in S3
# Note: Backend configuration does not support variables
terraform {
  backend "s3" {
    bucket         = "quote-lambda-tf-backend-terraform-state"
    key            = "quote-lambda-tf-backend/terraform.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "terraform-locks"
    encrypt        = true
  }
}