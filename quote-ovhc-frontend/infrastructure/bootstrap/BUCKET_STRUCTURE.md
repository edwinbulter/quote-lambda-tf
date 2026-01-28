# Shared Terraform State Bucket Structure

## Bucket Name
`edwinbulter-terraform-state`

## Purpose
This S3 bucket stores Terraform state files for **all your projects**. Each project uses a unique key path to isolate its state.

## Bucket Structure

```
s3://edwinbulter-terraform-state/
├── quote-lambda-tf-frontend/
│   └── terraform.tfstate          # Frontend infrastructure state
├── quote-lambda-tf-backend/
│   └── terraform.tfstate          # Backend infrastructure state
└── [future-project]/
    └── terraform.tfstate          # Future projects...
```

## State Locking

All projects share the same DynamoDB table for state locking:
- **Table Name**: `terraform-locks`
- **Purpose**: Prevents concurrent Terraform operations that could corrupt state
- **How it works**: When you run `terraform apply`, a lock is created with the state file path as the key

## Adding New Projects

To add a new project to use this shared bucket:

1. In your new project's `backend.tf`:
   ```hcl
   terraform {
     backend "s3" {
       bucket         = "edwinbulter-terraform-state"
       key            = "your-project-name/terraform.tfstate"  # Unique per project
       region         = "eu-central-1"
       dynamodb_table = "terraform-locks"
       encrypt        = true
     }
   }
   ```

2. Initialize Terraform:
   ```bash
   terraform init
   ```

That's it! No need to create new buckets or DynamoDB tables.

## Benefits

✅ **One-time setup**: Bootstrap infrastructure created once, used by all projects

✅ **Consistent configuration**: All projects use the same state management setup

✅ **Easy to manage**: View all state files in one place

✅ **Cost effective**: Single bucket and DynamoDB table for all projects

## Security

- **Encryption**: Server-side encryption (AES256) enabled
- **Versioning**: State file versioning enabled for rollback capability
- **Locking**: DynamoDB prevents concurrent modifications
- **Lifecycle**: `prevent_destroy` protects against accidental deletion
