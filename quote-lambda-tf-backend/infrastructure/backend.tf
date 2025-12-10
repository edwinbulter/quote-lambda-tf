# This file configures the Terraform backend to store state in S3
# Note: Backend configuration does not support variables
#
# SETUP INSTRUCTIONS:
# 1. Comment out the backend block below (lines 9-15)
# 2. Run: terraform init
# 3. Run: terraform apply (this creates the S3 bucket and DynamoDB table)
# 4. Uncomment the backend block below
# 5. Run: terraform init -migrate-state
# 6. Answer "yes" when prompted to migrate state to S3

terraform {
  backend "s3" {
    bucket         = "quote-lambda-tf-backend-terraform-state"
    key            = "quote-lambda-tf-backend/terraform.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "terraform-locks"
    encrypt        = true
  }
}