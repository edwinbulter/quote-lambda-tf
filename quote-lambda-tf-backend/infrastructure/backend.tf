# This file configures the Terraform backend to store state in S3
terraform {
  backend "s3" {
    bucket         = "${var.project_name}-terraform-state"
    key            = "${var.project_name}/terraform.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "terraform-locks"
    encrypt        = true
  }
}