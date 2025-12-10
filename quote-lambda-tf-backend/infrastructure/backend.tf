# This file configures the Terraform backend to store state in S3
terraform {
  backend "s3" {
    bucket         = "quote-lambda-java-terraform-state"
    key            = "quote-lambda-java/terraform.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "terraform-locks"
    encrypt        = true
  }
}