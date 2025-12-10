terraform {
  backend "s3" {
    bucket         = "quote-lambda-frontend-terraform-state"
    key            = "quote-lambda-frontend/terraform.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "terraform-locks"
    encrypt        = true
  }
}


# Uncomment and run 'terraform apply' to create the S3 bucket and DynamoDB table
# After creation, uncomment the backend block above and run 'terraform init -migrate-state'

# S3 bucket for storing the Terraform state
resource "aws_s3_bucket" "terraform_state" {
  bucket = "quote-lambda-frontend-terraform-state"
  
  # Prevent accidental deletion of this S3 bucket
  lifecycle {
    prevent_destroy = true
  }
}

# Enable versioning on the S3 bucket
resource "aws_s3_bucket_versioning" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id
  
  versioning_configuration {
    status = "Enabled"
  }
}

# Enable server-side encryption
resource "aws_s3_bucket_server_side_encryption_configuration" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id
  
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# DynamoDB table for state locking
resource "aws_dynamodb_table" "terraform_locks" {
  name         = "terraform-locks"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"
  
  attribute {
    name = "LockID"
    type = "S"
  }
}

