# S3 bucket for quotes cache
resource "aws_s3_bucket" "quote_cache" {
  bucket = local.environment == "prod" ? var.cache_bucket_name : "${var.cache_bucket_name}-${local.environment}"
  
  tags = {
    Name        = local.environment == "prod" ? var.cache_bucket_name : "${var.cache_bucket_name}-${local.environment}"
    Environment = local.environment
    Project     = "quote-lambda-tf"
  }
}

resource "aws_s3_bucket_versioning" "quote_cache" {
  bucket = aws_s3_bucket.quote_cache.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "quote_cache" {
  bucket = aws_s3_bucket.quote_cache.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "quote_cache" {
  bucket = aws_s3_bucket.quote_cache.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
