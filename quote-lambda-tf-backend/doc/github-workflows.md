# GitHub Actions Workflows

## Table of Contents

- [Deploy Lambda Workflow](#deploy-lambda-workflow)
  - [Triggers](#triggers)
  - [Workflow Steps](#workflow-steps)
  - [Setup Instructions](#setup-instructions)
    - [Quick Setup (Recommended)](#quick-setup-recommended)
    - [Manual Setup (Alternative)](#manual-setup-alternative)
    - [Add GitHub Secret](#4-add-github-secret)
  - [Testing the Workflow](#testing-the-workflow)
  - [Monitoring](#monitoring)
  - [Troubleshooting](#troubleshooting)

## Deploy Lambda Workflow

This workflow automatically builds, tests, and deploys the Lambda function to AWS.

### Triggers

- **Manual trigger**: Can be triggered manually from the GitHub Actions tab

### Workflow Steps

1. **Build and Test Job**:
   - Checks out the code
   - Sets up Java 21 (Amazon Corretto)
   - Builds the project with Maven
   - Runs unit tests
   - Uploads the JAR artifact

2. **Deploy Job** (only on main branch):
   - Downloads the built JAR artifact
   - Configures AWS credentials using OIDC
   - Updates the Lambda function code
   - Waits for the update to complete
   - Verifies the deployment

### Setup Instructions

#### Quick Setup (Recommended)

Use the automated setup script to configure AWS (steps 1-3):

```bash
# Get your AWS account ID
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

# Run the setup script (replace with your GitHub username)
.github/setup-aws-oidc.sh $AWS_ACCOUNT_ID YOUR_GITHUB_USERNAME
```

The script will output a Role ARN. Copy it and proceed to step 4 below.

#### Manual Setup (Alternative)

If you prefer to set up manually, follow these steps:

##### 1. Configure AWS OIDC Provider

First, create an OIDC provider in AWS for GitHub Actions:

```bash
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
```

##### 2. Create IAM Role for GitHub Actions

Create a file `github-actions-trust-policy.json`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::YOUR_ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:YOUR_GITHUB_USERNAME/quote-lambda-java:*"
        }
      }
    }
  ]
}
```

Replace:
- `YOUR_ACCOUNT_ID` with your AWS account ID
- `YOUR_GITHUB_USERNAME` with your GitHub username

Create the role:

```bash
aws iam create-role \
  --role-name GitHubActionsLambdaDeployRole \
  --assume-role-policy-document file://github-actions-trust-policy.json
```

##### 3. Attach Lambda Update Policy

Create a file `lambda-deploy-policy.json`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "lambda:UpdateFunctionCode",
        "lambda:GetFunction",
        "lambda:PublishVersion"
      ],
      "Resource": "arn:aws:lambda:eu-central-1:YOUR_ACCOUNT_ID:function:quotes-lambda-java"
    }
  ]
}
```

Create and attach the policy:

```bash
aws iam create-policy \
  --policy-name LambdaDeployPolicy \
  --policy-document file://lambda-deploy-policy.json

aws iam attach-role-policy \
  --role-name GitHubActionsLambdaDeployRole \
  --policy-arn arn:aws:iam::YOUR_ACCOUNT_ID:policy/LambdaDeployPolicy
```

#### 4. Add GitHub Secret

Add the following secret to your GitHub repository:

1. Go to your repository on GitHub
2. Navigate to **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Name: `AWS_ROLE_ARN`
5. Value: `arn:aws:iam::YOUR_ACCOUNT_ID:role/GitHubActionsLambdaDeployRole`

### Required GitHub Secrets

The GitHub Actions workflow requires the following secret to be configured:

| Secret Name | Description | Where to Find the Value |
|-------------|-------------|-------------------------|
| `AWS_ROLE_ARN` | IAM Role ARN that GitHub Actions assumes to deploy Lambda | **Option 1**: Output from the setup script (`.github/setup-aws-oidc.sh`)<br>**Option 2**: AWS Console → IAM → Roles → `GitHubActionsLambdaDeployRole` → Copy the ARN<br>**Option 3**: Run command: `aws iam get-role --role-name GitHubActionsLambdaDeployRole --query Role.Arn --output text` |

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

### Testing the Workflow

1. **Test on Pull Request**:
   - Create a new branch
   - Make changes
   - Open a pull request to main
   - The workflow will build and test (but not deploy)

2. **Deploy to Production**:
   - Merge the pull request to main
   - The workflow will automatically build, test, and deploy

3. **Manual Trigger**:
   - Go to **Actions** tab in GitHub
   - Select "Build and Deploy Lambda"
   - Click "Run workflow"

### Monitoring

- View workflow runs in the **Actions** tab
- Check CloudWatch Logs for Lambda execution logs
- Verify deployment in AWS Lambda console

### Troubleshooting

**Issue**: "Error: Not authorized to perform sts:AssumeRoleWithWebIdentity"
- **Solution**: Verify the trust policy has the correct GitHub repository path

**Issue**: "Error: Access Denied when updating Lambda"
- **Solution**: Ensure the IAM role has the `lambda:UpdateFunctionCode` permission

**Issue**: "JAR file not found"
- **Solution**: Check that the Maven build completed successfully and the artifact was uploaded
