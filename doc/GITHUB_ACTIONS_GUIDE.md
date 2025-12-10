# GitHub Actions Deployment Guide

This guide explains how to use the updated GitHub Actions workflows to deploy to development and production environments.

## Overview

Both workflows now support **manual environment selection**:
- **Backend**: `deploy-lambda.yml`
- **Frontend**: `deploy-frontend.yml`

When you trigger a workflow, you'll be asked to choose between `prod` or `dev` environment.

## How to Deploy

### 1. Navigate to GitHub Actions

1. Go to your repository on GitHub
2. Click on the **Actions** tab
3. Select the workflow you want to run:
   - **Build and Deploy Lambda** (for backend)
   - **Deploy Frontend to AWS S3** (for frontend)

### 2. Trigger the Workflow

1. Click **Run workflow** button (top right)
2. You'll see a dropdown menu with:
   - **Use workflow from**: Select branch (usually `main`)
   - **Environment to deploy to**: Select `prod` or `dev`
3. Click **Run workflow** to start

### 3. Monitor the Deployment

- Watch the workflow progress in real-time
- Check the logs for any errors
- Verify the deployment in the final step

## Deployment Targets

### Production (prod)

**Backend:**
- Lambda function: `quote-lambda-tf-backend`
- DynamoDB table: `quote-lambda-tf-quotes`
- API Gateway: `quote-lambda-tf-backend-api`

**Frontend:**
- S3 bucket: `quote-lambda-tf-frontend`
- CloudFront: `d5ly3miadik75.cloudfront.net`

### Development (dev)

**Backend:**
- Lambda function: `quote-lambda-tf-backend-dev`
- DynamoDB table: `quote-lambda-tf-quotes-dev`
- API Gateway: `quote-lambda-tf-backend-api-dev`

**Frontend:**
- S3 bucket: `quote-lambda-tf-frontend-dev`
- CloudFront: `[dev-distribution-url].cloudfront.net`

## Workflow Details

### Backend Workflow (deploy-lambda.yml)

**What it does:**
1. Builds the Java Lambda function with Maven
2. Runs tests
3. Deploys to the selected environment
4. Publishes a new Lambda version
5. Updates the 'live' alias for SnapStart
6. Verifies the deployment

**Environment-specific behavior:**
- Sets Lambda function name based on environment
- Deploys to correct Lambda function
- Shows environment in job name

### Frontend Workflow (deploy-frontend.yml)

**What it does:**
1. Installs Node.js dependencies
2. Builds the React application
3. Deploys to the selected S3 bucket
4. Sets correct content types
5. Invalidates CloudFront cache (if configured)
6. Shows deployment URLs

**Environment-specific behavior:**
- Sets S3 bucket name based on environment
- Uses correct CloudFront distribution ID
- Shows environment in job name

## Setting Up Dev CloudFront Distribution ID

After deploying your development frontend infrastructure, update the workflow:

1. Get the CloudFront distribution ID:
   ```bash
   cd quote-lambda-tf-frontend/infrastructure/
   terraform workspace select dev
   terraform output cloudfront_distribution_id
   ```

2. Update `.github/workflows/deploy-frontend.yml`:
   ```yaml
   env:
     AWS_REGION: eu-central-1
     CLOUDFRONT_DISTRIBUTION_ID_PROD: E1MX4CB72GG2ZM
     CLOUDFRONT_DISTRIBUTION_ID_DEV: 'YOUR_DEV_DISTRIBUTION_ID'  # Add here
   ```

3. Commit and push the change

## Typical Workflow

### Daily Development

1. Make changes to your code
2. Commit and push to your branch
3. Go to GitHub Actions
4. Run **Build and Deploy Lambda** → Select `dev`
5. Run **Deploy Frontend to AWS S3** → Select `dev`
6. Test in development environment

### Deploying to Production

1. Ensure all changes are tested in dev
2. Merge your changes to `main` branch
3. Go to GitHub Actions
4. Run **Build and Deploy Lambda** → Select `prod`
5. Run **Deploy Frontend to AWS S3** → Select `prod`
6. Verify production deployment

## Troubleshooting

### Workflow fails with "Resource not found"

**Cause**: The environment infrastructure doesn't exist yet.

**Solution**: Deploy the infrastructure first using Terraform:
```bash
cd quote-lambda-tf-backend/infrastructure/
terraform workspace select dev
terraform apply -var="environment=dev"
```

### Wrong environment deployed

**Cause**: Selected wrong environment in dropdown.

**Solution**: 
1. Check the workflow run details - it shows the environment in the job name
2. Re-run the workflow with correct environment selection

### CloudFront cache not invalidated

**Cause**: CloudFront distribution ID not set for the environment.

**Solution**: Update the workflow file with the correct distribution ID (see "Setting Up Dev CloudFront Distribution ID" above).

### Lambda deployment fails

**Cause**: Lambda function doesn't exist in the selected environment.

**Solution**: Deploy the backend infrastructure first:
```bash
cd quote-lambda-tf-backend/infrastructure/
terraform workspace select dev  # or default for prod
terraform apply -var="environment=dev"  # or prod
```

## Security Notes

- ✅ Uses OIDC for AWS authentication (no long-lived credentials)
- ✅ Requires manual trigger (no automatic deployments)
- ✅ Shows environment name in job for visibility
- ✅ Uses GitHub environments for additional protection (optional)

## GitHub Environments (Optional)

You can add protection rules for environments:

1. Go to **Settings** → **Environments**
2. Create environments: `prod` and `dev`
3. For `prod`, add protection rules:
   - Required reviewers
   - Wait timer
   - Deployment branches (e.g., only `main`)

This adds an extra approval step before deploying to production.

## Summary

✅ **Manual Control**: Choose environment when deploying
✅ **Safe**: Can't accidentally deploy to wrong environment
✅ **Visible**: Environment shown in job name
✅ **Flexible**: Deploy to dev for testing, prod for release

## Quick Reference

| Action | Steps |
|--------|-------|
| **Deploy Backend to Dev** | Actions → Build and Deploy Lambda → Run workflow → Select `dev` |
| **Deploy Backend to Prod** | Actions → Build and Deploy Lambda → Run workflow → Select `prod` |
| **Deploy Frontend to Dev** | Actions → Deploy Frontend → Run workflow → Select `dev` |
| **Deploy Frontend to Prod** | Actions → Deploy Frontend → Run workflow → Select `prod` |

Happy deploying! 🚀
