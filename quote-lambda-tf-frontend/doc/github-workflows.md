# GitHub Actions Workflows

## Table of Contents

- [Overview](#overview)
- [Deploy Frontend Workflow](#deploy-frontend-workflow)
  - [Triggers](#triggers)
  - [Workflow Steps](#workflow-steps)
  - [Required Secrets](#required-secrets)
- [Playwright Tests Workflow](#playwright-tests-workflow)
  - [Triggers](#triggers-1)
  - [Workflow Steps](#workflow-steps-1)
  - [Required Secrets](#required-secrets-1)
- [Setup Instructions](#setup-instructions)
  - [Configure AWS OIDC for GitHub Actions](#configure-aws-oidc-for-github-actions)
  - [Add GitHub Secrets](#add-github-secrets)
- [Testing the Workflows](#testing-the-workflows)
- [Troubleshooting](#troubleshooting)

## Overview

This project includes two GitHub Actions workflows:
1. **[deploy-frontend.yml](../../.github/workflows/deploy-frontend.yml)** - Builds and deploys the React application to AWS S3
2. **[playwright.yml](../../.github/workflows/playwright.yml)** - Runs end-to-end tests using Playwright

## Deploy Frontend Workflow

This workflow builds the React application and deploys it to AWS S3 with CloudFront cache invalidation.

### Triggers

- **Manual trigger**: Can be triggered manually from the GitHub Actions tab using `workflow_dispatch`

### Workflow Steps

1. **Build**:
   - Checks out the code
   - Sets up Node.js 18
   - Installs dependencies with `npm ci`
   - Builds the production bundle with `npm run build`

2. **Deploy**:
   - Configures AWS credentials using OIDC
   - Syncs the build output to S3
   - Sets correct content types for HTML files
   - Invalidates CloudFront cache (if configured)

### Required Secrets

| Secret Name | Description | Where to Find the Value |
|-------------|-------------|-------------------------|
| `AWS_ROLE_ARN` | IAM Role ARN that GitHub Actions assumes to deploy to S3 and CloudFront | **Option 1**: Output from the setup script (`.github/setup-aws-oidc.sh`)<br>**Option 2**: AWS Console → IAM → Roles → `GitHubActionsLambdaDeployRole` → Copy the ARN<br>**Option 3**: Run command: `aws iam get-role --role-name GitHubActionsLambdaDeployRole --query Role.Arn --output text` |

**Note**: The workflow uses environment variables for configuration (S3 bucket name, AWS region, CloudFront distribution ID). These are defined in the workflow file itself, not as secrets.

## Playwright Tests Workflow

This workflow runs end-to-end tests using Playwright to verify the frontend functionality.

### Triggers

- **Manual trigger**: Can be triggered manually from the GitHub Actions tab using `workflow_dispatch`

### Workflow Steps

1. **Setup**:
   - Checks out the code
   - Sets up Node.js 18
   - Installs dependencies with `npm ci`
   - Installs Playwright browsers

2. **Test**:
   - Runs Playwright tests
   - Uploads test results and traces as artifacts (on failure)

### Required Secrets

**No secrets required** - The Playwright workflow runs tests against the deployed application and doesn't need AWS credentials.

## Setup Instructions

### Configure AWS OIDC for GitHub Actions

The deployment workflow uses OpenID Connect (OIDC) to authenticate with AWS, which is more secure than using long-lived access keys.

#### Quick Setup (Recommended)

Use the automated setup script [setup-aws-oidc.sh](../../.github/setup-aws-oidc.sh):

```bash
# Get your AWS account ID
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

# Run the setup script (replace with your GitHub username)
.github/setup-aws-oidc.sh $AWS_ACCOUNT_ID YOUR_GITHUB_USERNAME
```

The script will:
1. Create an OIDC provider in AWS (if it doesn't exist)
2. Create an IAM role `GitHubActionsLambdaDeployRole`
3. Attach policies for Lambda, S3, and CloudFront access
4. Output the Role ARN to add to GitHub secrets

#### Update IAM Role for S3 and CloudFront

After running the setup script, add S3 and CloudFront permissions to the role:

```bash
# Add S3 and CloudFront permissions
aws iam put-role-policy \
  --role-name GitHubActionsLambdaDeployRole \
  --policy-name S3CloudFrontDeployPolicy \
  --policy-document '{
    "Version": "2012-10-17",
    "Statement": [
      {
        "Effect": "Allow",
        "Action": [
          "s3:PutObject",
          "s3:GetObject",
          "s3:DeleteObject",
          "s3:ListBucket"
        ],
        "Resource": [
          "arn:aws:s3:::quote-lambda-frontend",
          "arn:aws:s3:::quote-lambda-frontend/*"
        ]
      },
      {
        "Effect": "Allow",
        "Action": [
          "cloudfront:CreateInvalidation",
          "cloudfront:GetInvalidation"
        ],
        "Resource": "*"
      }
    ]
  }'
```

### Add GitHub Secrets

#### Required Secrets

The workflows require the following secret to be configured:

| Secret Name | Description | Where to Find the Value |
|-------------|-------------|-------------------------|
| `AWS_ROLE_ARN` | IAM Role ARN that GitHub Actions assumes for AWS operations | See [Configure AWS OIDC](#configure-aws-oidc-for-github-actions) above |

#### How to Add Secrets to GitHub

1. Navigate to your repository on GitHub
2. Go to **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Enter the secret name and value from the table above
5. Click **Add secret**

#### Finding Your AWS Role ARN

If you've already created the IAM role but lost the ARN, you can retrieve it using:

```bash
# Get the Role ARN
aws iam get-role \
  --role-name GitHubActionsLambdaDeployRole \
  --query Role.Arn \
  --output text
```

Or find it in the AWS Console:
1. Go to **IAM** → **Roles**
2. Search for `GitHubActionsLambdaDeployRole`
3. Click on the role name
4. Copy the **ARN** displayed at the top of the page

The ARN format will be: `arn:aws:iam::123456789012:role/GitHubActionsLambdaDeployRole`

## Testing the Workflows

### Deploy Frontend Workflow

1. Go to **Actions** tab in GitHub
2. Select **"Deploy Frontend to AWS S3"** workflow
3. Click **"Run workflow"**
4. Select the branch (usually `main`)
5. Click **"Run workflow"**

The workflow will:
- Build the React application
- Deploy to S3
- Invalidate CloudFront cache
- Display the website URL in the logs

### Playwright Tests Workflow

1. Go to **Actions** tab in GitHub
2. Select **"Playwright Tests"** workflow
3. Click **"Run workflow"**
4. Select the branch
5. Click **"Run workflow"**

The workflow will:
- Install dependencies and Playwright browsers
- Run all Playwright tests
- Upload test results and traces if tests fail

## Troubleshooting

### Issue: "Error: Not authorized to perform sts:AssumeRoleWithWebIdentity"

**Cause**: The IAM role's trust policy doesn't allow GitHub Actions from your repository to assume it.

**Solution**: 
1. Verify the trust policy has the correct GitHub repository path
2. Run the setup script again to update the trust policy:
   ```bash
   .github/setup-aws-oidc.sh $AWS_ACCOUNT_ID YOUR_GITHUB_USERNAME
   ```

### Issue: "Error: Access Denied when uploading to S3"

**Cause**: The IAM role doesn't have permissions to write to the S3 bucket.

**Solution**: 
1. Ensure the S3 policy is attached to the role (see [Update IAM Role](#update-iam-role-for-s3-and-cloudfront))
2. Verify the bucket name in the workflow matches your actual S3 bucket
3. Check the bucket name in the workflow env section (line 8 of deploy-frontend.yml)

### Issue: "CloudFront invalidation failed"

**Cause**: The CloudFront distribution ID is incorrect or the role lacks permissions.

**Solution**:
1. Verify the CloudFront distribution ID in the workflow env section
2. Ensure the CloudFront policy is attached to the role
3. If you don't have CloudFront, leave the `CLOUDFRONT_DISTRIBUTION_ID` empty in the workflow

### Issue: "Build failed" or "npm ci failed"

**Cause**: Dependencies are out of sync or there's a build error in the code.

**Solution**:
1. Run `npm ci` and `npm run build` locally to reproduce the error
2. Check the workflow logs for specific error messages
3. Ensure `package-lock.json` is committed to the repository

### Issue: Playwright tests fail

**Cause**: The application might not be deployed or there are actual test failures.

**Solution**:
1. Ensure the frontend is deployed and accessible
2. Check the test artifacts uploaded by the workflow for screenshots and traces
3. Run tests locally: `npm run test:e2e`
4. Update the base URL in `playwright.config.ts` if needed

## Security Best Practices

1. **Use OIDC instead of access keys**: The workflows use OIDC for temporary credentials
2. **Principle of least privilege**: The IAM role only has permissions for S3 and CloudFront
3. **No secrets in code**: All sensitive values are in GitHub secrets or environment variables
4. **Audit regularly**: Review CloudTrail logs for GitHub Actions activity
5. **Rotate credentials**: Even with OIDC, periodically review and update IAM policies

## Additional Resources

- [GitHub Actions OIDC with AWS](https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/configuring-openid-connect-in-amazon-web-services)
- [AWS IAM Roles](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles.html)
- [Playwright Documentation](https://playwright.dev/)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
