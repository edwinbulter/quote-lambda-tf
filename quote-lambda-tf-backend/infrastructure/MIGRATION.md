# Migration Guide: Backend to Shared Terraform State

This guide helps you migrate the backend project from using its own S3 bucket (`quote-lambda-tf-backend-terraform-state`) to the shared bucket (`edwinbulter-terraform-state`).

## Current State

You already have:
- S3 bucket `quote-lambda-tf-backend-terraform-state` for Terraform state
- Backend infrastructure deployed (Lambda, API Gateway, DynamoDB)
- Terraform state stored in S3

## Prerequisites

The shared bucket must already exist. If you haven't migrated the frontend yet, do that first:
- See: `../quote-lambda-tf-frontend/infrastructure/MIGRATION.md`

## Migration Steps

### Step 1: Verify Shared Bucket Exists

```bash
# Check if the shared bucket exists
aws s3 ls s3://edwinbulter-terraform-state/

# Should show the frontend state if already migrated
aws s3 ls s3://edwinbulter-terraform-state/quote-lambda-tf-frontend/
```

If the bucket doesn't exist, create it first:
```bash
cd ../quote-lambda-tf-frontend/infrastructure/bootstrap/
terraform init
terraform apply
cd ../../../quote-lambda-tf-backend/infrastructure/
```

### Step 2: Copy State from Old Bucket to New Bucket

Copy the existing state file to the new shared bucket:

```bash
# Copy the state file from old bucket to new bucket
aws s3 cp \
  s3://quote-lambda-tf-backend-terraform-state/quote-lambda-tf-backend/terraform.tfstate \
  s3://edwinbulter-terraform-state/quote-lambda-tf-backend/terraform.tfstate

# Verify the copy
aws s3 ls s3://edwinbulter-terraform-state/quote-lambda-tf-backend/
```

### Step 3: Reconfigure Backend to Use New Bucket

The backend configuration has already been updated to use the new bucket. Now reinitialize:

```bash
# Reconfigure backend to use new bucket
terraform init -reconfigure

# Verify - should show no changes
terraform plan
```

The `-reconfigure` flag tells Terraform to use the new backend configuration without trying to migrate state (since we already copied it manually).

**⚠️ IMPORTANT**: If you run `terraform plan` and see it wants to **destroy** the old bootstrap resources (S3 bucket, DynamoDB table), that's expected. **DO NOT run `terraform apply` yet!** Proceed to Step 4.

### Step 4: Remove Bootstrap Resources from State

Remove the OLD bootstrap resources from the backend infrastructure's Terraform state **without deleting them from AWS**:

```bash
# Remove old bootstrap resources from state (if they exist)
terraform state rm aws_s3_bucket.terraform_state 2>/dev/null || true
terraform state rm aws_s3_bucket_versioning.terraform_state 2>/dev/null || true
terraform state rm aws_s3_bucket_server_side_encryption_configuration.terraform_state 2>/dev/null || true
terraform state rm aws_dynamodb_table.terraform_locks 2>/dev/null || true

# Now verify - should show NO changes to infrastructure
terraform plan
```

After running `terraform state rm`, the `terraform plan` should show **0 to add, 0 to change, 0 to destroy** because:
- The old bootstrap resources are removed from state (no longer managed here)
- The application resources (Lambda, API Gateway, DynamoDB) are unchanged
- The new bootstrap resources are managed in the frontend's `bootstrap/` directory

### Step 5: Clean Up Old Bucket

After verifying everything works with the new bucket, you can delete the old bucket.

**Note**: Since versioning is enabled, you need to delete all object versions:

```bash
# Force delete bucket with all versions
aws s3 rb s3://quote-lambda-tf-backend-terraform-state --force
```

**⚠️ Warning**: Make sure you've copied the state file to the new bucket first!

### Step 6: Verify Everything Works

Test the migration:

```bash
# Should show no changes
terraform plan

# Test destroy and recreate (optional)
terraform destroy
terraform apply
```

## Verification

After migration, verify the setup:

### Check State Location

```bash
# List all state files in shared bucket
aws s3 ls s3://edwinbulter-terraform-state/ --recursive

# Should show:
# quote-lambda-tf-frontend/terraform.tfstate
# quote-lambda-tf-backend/terraform.tfstate
```

### Test Backend Operations

```bash
terraform plan  # Should show no changes
```

## Rollback (If Needed)

If something goes wrong, you can rollback by:

1. Copy state back to old bucket:
   ```bash
   aws s3 cp \
     s3://edwinbulter-terraform-state/quote-lambda-tf-backend/terraform.tfstate \
     s3://quote-lambda-tf-backend-terraform-state/quote-lambda-tf-backend/terraform.tfstate
   ```

2. Update `backend.tf` to use old bucket name

3. Run `terraform init -reconfigure`

## Benefits of New Structure

✅ **Shared state infrastructure**: One S3 bucket and DynamoDB table for all projects

✅ **Destroy application without losing state**: Run `terraform destroy` without affecting state storage

✅ **Independent lifecycle management**: Backend and frontend have separate lifecycles

✅ **Better organization**: Clear separation of concerns

✅ **Cost effective**: Single bucket for all projects
