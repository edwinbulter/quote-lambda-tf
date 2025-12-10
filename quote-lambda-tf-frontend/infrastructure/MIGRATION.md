# Migration Guide: Separating Bootstrap from Application Infrastructure

This guide helps you migrate from the old structure (where bootstrap resources were in the main infrastructure) to the new structure (where bootstrap resources are in a separate `bootstrap/` directory).

**Additionally**, this migration renames the S3 bucket to `edwinbulter-terraform-state` so all your projects can share the same bucket for state storage (using different keys).

## Current State

You already have:
- S3 bucket `quote-lambda-tf-frontend-terraform-state` for Terraform state
- DynamoDB table `terraform-locks` for state locking
- Application infrastructure deployed
- Terraform state stored in S3

## Migration Steps

### Step 1: Create New Shared S3 Bucket

First, create the new shared bucket using the bootstrap configuration:

```bash
cd infrastructure/bootstrap/

# Initialize the bootstrap directory
terraform init

# Apply to create the new bucket
terraform apply
```

This creates:
- New S3 bucket: `edwinbulter-terraform-state`
- DynamoDB table: `terraform-locks` (or imports existing one)

### Step 2: Copy State from Old Bucket to New Bucket

Copy the existing state file to the new bucket:

```bash
# Copy the state file from old bucket to new bucket
aws s3 cp \
  s3://quote-lambda-tf-frontend-terraform-state/quote-lambda-tf-frontend/terraform.tfstate \
  s3://edwinbulter-terraform-state/quote-lambda-tf-frontend/terraform.tfstate

# Verify the copy
aws s3 ls s3://edwinbulter-terraform-state/quote-lambda-tf-frontend/
```

### Step 3: Reconfigure Backend to Use New Bucket

The backend configuration has already been updated to use the new bucket. Now reinitialize:

```bash
cd ../  # Back to infrastructure/

# Reconfigure backend to use new bucket
terraform init -reconfigure
```

The `-reconfigure` flag tells Terraform to use the new backend configuration without trying to migrate state (since we already copied it manually).

**⚠️ IMPORTANT**: If you run `terraform plan` now, you'll see it wants to **destroy** the old bootstrap resources (S3 bucket, DynamoDB table, etc.). This is because they're no longer in the main infrastructure configuration. **DO NOT run `terraform apply` yet!**

### Step 4: Remove Bootstrap Resources from Main Infrastructure State

Now we need to remove the OLD bootstrap resources from the main infrastructure's Terraform state **without deleting them from AWS**:

```bash
# Remove old bootstrap resources from state (if they exist)
terraform state rm aws_s3_bucket.terraform_state
terraform state rm aws_s3_bucket_versioning.terraform_state
terraform state rm aws_s3_bucket_server_side_encryption_configuration.terraform_state
terraform state rm aws_dynamodb_table.terraform_locks

# Now verify - should show NO changes to infrastructure
terraform plan
```

After running `terraform state rm`, the `terraform plan` should show **0 to add, 0 to change, 0 to destroy** because:
- The old bootstrap resources are removed from state (no longer managed here)
- The application resources (S3 website bucket, CloudFront) are unchanged
- The new bootstrap resources are managed in the `bootstrap/` directory

### Step 5: Clean Up Old Bucket

After verifying everything works with the new bucket, you can delete the old bucket.

**Note**: Since versioning is enabled, you need to delete all object versions:

```bash
# Delete all objects AND all versions in the old bucket
aws s3api delete-objects \
  --bucket quote-lambda-tf-frontend-terraform-state \
  --delete "$(aws s3api list-object-versions \
    --bucket quote-lambda-tf-frontend-terraform-state \
    --query '{Objects: Versions[].{Key:Key,VersionId:VersionId}}' \
    --output json)"

# Delete all delete markers (if any)
aws s3api delete-objects \
  --bucket quote-lambda-tf-frontend-terraform-state \
  --delete "$(aws s3api list-object-versions \
    --bucket quote-lambda-tf-frontend-terraform-state \
    --query '{Objects: DeleteMarkers[].{Key:Key,VersionId:VersionId}}' \
    --output json)" 2>/dev/null || true

# Now delete the empty bucket
aws s3 rb s3://quote-lambda-tf-frontend-terraform-state
```

**Alternative (simpler but requires manual confirmation):**

```bash
# Force delete bucket with all versions
aws s3 rb s3://quote-lambda-tf-frontend-terraform-state --force
```

**⚠️ Warning**: The `--force` flag will delete all objects and versions without confirmation. Make sure you've copied the state file to the new bucket first!

### Step 6: Update Backend Project (Optional)

If you want to migrate your backend project to use the same shared bucket:

```bash
cd ../../quote-lambda-tf-backend/infrastructure/

# Copy state from old backend bucket to new shared bucket
aws s3 cp \
  s3://quote-lambda-tf-backend-terraform-state/quote-lambda-tf-backend/terraform.tfstate \
  s3://edwinbulter-terraform-state/quote-lambda-tf-backend/terraform.tfstate

# Update backend.tf to use new bucket name
# Then reconfigure
terraform init -reconfigure
terraform plan
```

### Step 7: Clean Up (Optional)

You can now safely delete the old `bootstrap.tf` file from the main infrastructure directory:

```bash
rm infrastructure/bootstrap.tf
```

## Verification

After migration, verify the setup:

### Test Bootstrap

```bash
cd infrastructure/bootstrap/
terraform plan  # Should show no changes
```

### Test Application

```bash
cd ../  # Back to infrastructure/
terraform plan  # Should show no changes
```

### Test Destroy and Recreate

```bash
# Destroy application (state remains in S3)
terraform destroy

# Recreate application
terraform apply
```

## Rollback (If Needed)

If something goes wrong, you can rollback by:

1. Re-importing the bootstrap resources into the main infrastructure
2. Removing the bootstrap directory
3. Restoring the original `bootstrap.tf` file

## Benefits of New Structure

✅ **Destroy application without losing state**: Run `terraform destroy` in `infrastructure/` without affecting state storage

✅ **Independent lifecycle management**: Bootstrap and application have separate lifecycles

✅ **Better organization**: Clear separation of concerns

✅ **Safer operations**: Reduced risk of accidentally destroying state infrastructure
