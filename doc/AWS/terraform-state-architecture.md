# Terraform State Architecture

This document explains how Terraform state is managed across all projects in this repository.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    AWS Account (edwinbulter)                     │
│                                                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │           Bootstrap Infrastructure (One-time setup)         │ │
│  │                                                              │ │
│  │  ┌──────────────────────────┐  ┌─────────────────────────┐ │ │
│  │  │  S3 Bucket               │  │  DynamoDB Table         │ │ │
│  │  │  edwinbulter-terraform-  │  │  terraform-locks        │ │ │
│  │  │  state                   │  │                         │ │ │
│  │  │                          │  │  Provides state locking │ │ │
│  │  │  Stores all state files  │  │  for all projects       │ │ │
│  │  └──────────────────────────┘  └─────────────────────────┘ │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                    Application Projects                     │ │
│  │                                                              │ │
│  │  ┌─────────────────────┐      ┌─────────────────────┐     │ │
│  │  │ Frontend Project    │      │ Backend Project     │     │ │
│  │  │                     │      │                     │     │ │
│  │  │ State Key:          │      │ State Key:          │     │ │
│  │  │ quote-lambda-tf-    │      │ quote-lambda-tf-    │     │ │
│  │  │ frontend/           │      │ backend/            │     │ │
│  │  │ terraform.tfstate   │      │ terraform.tfstate   │     │ │
│  │  │                     │      │                     │     │ │
│  │  │ Resources:          │      │ Resources:          │     │ │
│  │  │ - S3 Bucket         │      │ - Lambda Function   │     │ │
│  │  │ - CloudFront        │      │ - API Gateway       │     │ │
│  │  │                     │      │ - DynamoDB Table    │     │ │
│  │  └─────────────────────┘      └─────────────────────┘     │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## Directory Structure

```
quote-lambda-tf/
├── quote-lambda-tf-frontend/
│   └── infrastructure/
│       ├── bootstrap/              # Bootstrap infrastructure (shared)
│       │   ├── bootstrap.tf        # Creates S3 bucket & DynamoDB table
│       │   ├── backend.tf          # Uses local state
│       │   ├── provider.tf
│       │   └── README.md
│       ├── backend.tf              # Points to shared S3 bucket
│       ├── main.tf                 # Frontend resources
│       └── ...
│
└── quote-lambda-tf-backend/
    └── infrastructure/
        ├── backend.tf              # Points to shared S3 bucket
        ├── main.tf                 # Backend resources
        └── ...
```

## State Management Flow

### 1. Bootstrap (One-time, Shared)

The bootstrap infrastructure is created **once** and shared by all projects:

```bash
cd quote-lambda-tf-frontend/infrastructure/bootstrap/
terraform init
terraform apply
```

**Creates:**
- S3 bucket: `edwinbulter-terraform-state` (shared by all projects)
- DynamoDB table: `terraform-locks` (shared by all projects)

**State stored:** Locally in `bootstrap/terraform.tfstate`

**Note**: This only needs to be done once. All other projects reference this shared infrastructure.

### 2. Frontend Deployment

```bash
cd quote-lambda-tf-frontend/infrastructure/
terraform init
terraform apply
```

**Creates:**
- S3 bucket for website
- CloudFront distribution

**State stored:** `s3://edwinbulter-terraform-state/quote-lambda-tf-frontend/terraform.tfstate`

**Uses:** Shared bootstrap infrastructure (S3 bucket + DynamoDB table)

### 3. Backend Deployment

```bash
cd quote-lambda-tf-backend/infrastructure/
terraform init
terraform apply
```

**Creates:**
- Lambda function
- API Gateway
- DynamoDB table (for quotes, not state locking)

**State stored:** `s3://edwinbulter-terraform-state/quote-lambda-tf-backend/terraform.tfstate`

**Uses:** Shared bootstrap infrastructure (S3 bucket + DynamoDB table)

## Key Benefits

### ✅ Independent Lifecycles

- **Destroy frontend**: `cd quote-lambda-tf-frontend/infrastructure && terraform destroy`
  - Removes frontend resources
  - State remains in S3
  - Bootstrap infrastructure untouched

- **Destroy backend**: `cd quote-lambda-tf-backend/infrastructure && terraform destroy`
  - Removes backend resources
  - State remains in S3
  - Bootstrap infrastructure untouched

### ✅ Shared Infrastructure

- One S3 bucket for all projects
- One DynamoDB table for all state locking
- Centralized state management
- Cost-effective

### ✅ Protected State

- Bootstrap has `prevent_destroy = true`
- State infrastructure cannot be accidentally destroyed
- Versioning enabled for rollback
- Encryption at rest

## Adding New Projects

To add a new project:

1. Create `infrastructure/backend.tf`:
   ```hcl
   terraform {
     backend "s3" {
       bucket         = "edwinbulter-terraform-state"
       key            = "new-project-name/terraform.tfstate"
       region         = "eu-central-1"
       dynamodb_table = "terraform-locks"
       encrypt        = true
     }
   }
   ```

2. No need to create new bootstrap infrastructure - reuse the existing one!

## State File Locations

| Project | State Location |
|---------|---------------|
| Bootstrap | `quote-lambda-tf-frontend/infrastructure/bootstrap/terraform.tfstate` (local) |
| Frontend | `s3://edwinbulter-terraform-state/quote-lambda-tf-frontend/terraform.tfstate` |
| Backend | `s3://edwinbulter-terraform-state/quote-lambda-tf-backend/terraform.tfstate` |
| Future projects | `s3://edwinbulter-terraform-state/[project-name]/terraform.tfstate` |
