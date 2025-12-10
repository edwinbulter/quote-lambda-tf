#!/bin/bash

# Script to set up AWS OIDC provider and IAM role for GitHub Actions
# Usage: ./setup-aws-oidc.sh <AWS_ACCOUNT_ID> <GITHUB_USERNAME>

set -e

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <AWS_ACCOUNT_ID> <GITHUB_USERNAME>"
    echo "Example: $0 123456789012 edwinbulter"
    exit 1
fi

AWS_ACCOUNT_ID=$1
GITHUB_USERNAME=$2
REPO_NAME="quote-lambda-java"
ROLE_NAME="GitHubActionsLambdaDeployRole"
POLICY_NAME="LambdaDeployPolicy"
AWS_REGION="eu-central-1"

echo "Setting up AWS OIDC provider and IAM role for GitHub Actions..."
echo "AWS Account ID: $AWS_ACCOUNT_ID"
echo "GitHub Repository: $GITHUB_USERNAME/$REPO_NAME"
echo ""

# Check if OIDC provider already exists
echo "Checking if OIDC provider exists..."
if aws iam list-open-id-connect-providers | grep -q "token.actions.githubusercontent.com"; then
    echo "✓ OIDC provider already exists"
else
    echo "Creating OIDC provider..."
    aws iam create-open-id-connect-provider \
      --url https://token.actions.githubusercontent.com \
      --client-id-list sts.amazonaws.com \
      --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
    echo "✓ OIDC provider created"
fi

# Create trust policy
echo "Creating trust policy..."
cat > /tmp/github-actions-trust-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::${AWS_ACCOUNT_ID}:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:${GITHUB_USERNAME}/${REPO_NAME}:*"
        }
      }
    }
  ]
}
EOF

# Create IAM role
echo "Creating IAM role..."
if aws iam get-role --role-name $ROLE_NAME 2>/dev/null; then
    echo "✓ Role $ROLE_NAME already exists, updating trust policy..."
    aws iam update-assume-role-policy \
      --role-name $ROLE_NAME \
      --policy-document file:///tmp/github-actions-trust-policy.json
else
    aws iam create-role \
      --role-name $ROLE_NAME \
      --assume-role-policy-document file:///tmp/github-actions-trust-policy.json
    echo "✓ Role $ROLE_NAME created"
fi

# Create Lambda deployment policy
echo "Creating Lambda deployment policy..."
cat > /tmp/lambda-deploy-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "lambda:UpdateFunctionCode",
        "lambda:GetFunction",
        "lambda:GetFunctionConfiguration",
        "lambda:PublishVersion",
        "lambda:UpdateAlias",
        "lambda:GetAlias"
      ],
      "Resource": [
        "arn:aws:lambda:${AWS_REGION}:${AWS_ACCOUNT_ID}:function:quotes-lambda-java",
        "arn:aws:lambda:${AWS_REGION}:${AWS_ACCOUNT_ID}:function:quotes-lambda-java:*"
      ]
    }
  ]
}
EOF

# Create or update policy
POLICY_ARN="arn:aws:iam::${AWS_ACCOUNT_ID}:policy/${POLICY_NAME}"
if aws iam get-policy --policy-arn $POLICY_ARN 2>/dev/null; then
    echo "✓ Policy $POLICY_NAME already exists, creating new version..."
    # Delete old versions if needed (max 5 versions allowed)
    VERSIONS=$(aws iam list-policy-versions --policy-arn $POLICY_ARN --query 'Versions[?!IsDefaultVersion].VersionId' --output text)
    for VERSION in $VERSIONS; do
        aws iam delete-policy-version --policy-arn $POLICY_ARN --version-id $VERSION 2>/dev/null || true
    done
    aws iam create-policy-version \
      --policy-arn $POLICY_ARN \
      --policy-document file:///tmp/lambda-deploy-policy.json \
      --set-as-default
else
    aws iam create-policy \
      --policy-name $POLICY_NAME \
      --policy-document file:///tmp/lambda-deploy-policy.json
    echo "✓ Policy $POLICY_NAME created"
fi

# Attach policy to role
echo "Attaching policy to role..."
aws iam attach-role-policy \
  --role-name $ROLE_NAME \
  --policy-arn $POLICY_ARN
echo "✓ Policy attached to role"

# Clean up temporary files
rm /tmp/github-actions-trust-policy.json
rm /tmp/lambda-deploy-policy.json

echo ""
echo "✓ Setup complete!"
echo ""
echo "Next steps:"
echo "1. Add the following secret to your GitHub repository:"
echo "   Name: AWS_ROLE_ARN"
echo "   Value: arn:aws:iam::${AWS_ACCOUNT_ID}:role/${ROLE_NAME}"
echo ""
echo "2. Go to: https://github.com/${GITHUB_USERNAME}/${REPO_NAME}/settings/secrets/actions"
echo "3. Click 'New repository secret'"
echo "4. Add the secret with the name and value above"
echo ""
echo "Role ARN: arn:aws:iam::${AWS_ACCOUNT_ID}:role/${ROLE_NAME}"
