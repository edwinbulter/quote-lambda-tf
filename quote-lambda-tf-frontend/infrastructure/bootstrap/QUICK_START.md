# Quick Start Guide

## For New Deployments

If you're setting up from scratch:

```bash
# 1. Deploy bootstrap infrastructure
cd infrastructure/bootstrap/
terraform init
terraform apply

# 2. Deploy application infrastructure
cd ../
terraform init
terraform apply
```

## For Existing Deployments

If you already have infrastructure deployed, see [../MIGRATION.md](../MIGRATION.md) for migration steps.

## Daily Operations

### Deploy/Update Application

```bash
cd infrastructure/
terraform apply
```

### Destroy Application (Keep State)

```bash
cd infrastructure/
terraform destroy
```

The Terraform state remains in S3, so you can recreate the application later.

### Recreate Application

```bash
cd infrastructure/
terraform apply
```

Since state is preserved, Terraform knows the previous configuration.

## Important Notes

⚠️ **Never run `terraform destroy` in the `bootstrap/` directory unless you want to completely remove all infrastructure including state storage.**

✅ **Always run `terraform destroy` in the main `infrastructure/` directory to safely destroy just the application.**
