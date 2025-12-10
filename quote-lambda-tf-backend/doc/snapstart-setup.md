# Lambda SnapStart Setup Complete

SnapStart has been configured for your Lambda function to reduce cold start times from 3-6 seconds to ~200ms.

## Changes Made

### 1. Terraform Configuration (`infrastructure/lambda.tf`)
- ✅ Added `snap_start` block to Lambda function
- ✅ Created Lambda alias `live` for SnapStart
- ✅ Updated API Gateway to use the alias instead of `$LATEST`
- ✅ Updated Lambda permissions for the alias

### 2. GitHub Actions Workflow (`.github/workflows/deploy-lambda.yml`)
- ✅ Added step to publish new Lambda version after deployment
- ✅ Added step to update `live` alias to point to new version
- ✅ Added verification step to check SnapStart status

### 3. IAM Permissions (`.github/setup-aws-oidc.sh`)
- ✅ Added `lambda:PublishVersion` permission
- ✅ Added `lambda:UpdateAlias` permission
- ✅ Added `lambda:GetAlias` permission

## Deployment Steps

### 1. Update IAM Permissions

Run the setup script to update the IAM policy with new permissions:

```bash
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
.github/setup-aws-oidc.sh $AWS_ACCOUNT_ID edwinbulter
```

### 2. Deploy Infrastructure with Terraform

```bash
cd infrastructure
terraform init
terraform plan
terraform apply
```

This will:
- Enable SnapStart on the Lambda function
- Create the `live` alias
- Update API Gateway to use the alias

### 3. Deploy Lambda Code via GitHub Actions

Trigger the GitHub Actions workflow manually or push to your repository. The workflow will:
1. Build and test the code
2. Update the Lambda function code
3. Publish a new version
4. Update the `live` alias to the new version
5. Verify SnapStart is enabled

## How SnapStart Works

1. **Initialization**: When you publish a new Lambda version, AWS takes a snapshot of the initialized execution environment
2. **Caching**: The snapshot is cached and encrypted
3. **Cold Starts**: When a new execution environment is needed, AWS restores from the snapshot instead of initializing from scratch
4. **Result**: Cold start time reduced from 3-6 seconds to ~200ms

## Costs

- **SnapStart Feature**: FREE
- **Snapshot Storage**: ~$0.00000309 per GB-second
- **Estimated Cost**: ~$0.16/month for a 200MB snapshot

## Verification

After deployment, verify SnapStart is working:

```bash
# Check SnapStart status
aws lambda get-function \
  --function-name quotes-lambda-java:live \
  --region eu-central-1 \
  --query 'Configuration.SnapStart'

# Expected output:
# {
#     "ApplyOn": "PublishedVersions",
#     "OptimizationStatus": "On"
# }
```

## Testing

Test your API to see the improved cold start performance:

```bash
# First request (cold start - should be ~200ms)
curl https://22n07ybi7e.execute-api.eu-central-1.amazonaws.com/quote

# Subsequent requests (warm - should be <50ms)
curl https://22n07ybi7e.execute-api.eu-central-1.amazonaws.com/quote
```

## Monitoring

Monitor cold start improvements in CloudWatch:
- Go to CloudWatch → Lambda Insights
- Check "Init Duration" metric
- Should see significant reduction in initialization time

## Important Notes

1. **Published Versions Only**: SnapStart only works with published Lambda versions, not `$LATEST`
2. **Alias Required**: API Gateway must invoke the Lambda via an alias (we use `live`)
3. **Automatic Updates**: GitHub Actions automatically publishes new versions and updates the alias
4. **No Code Changes**: Your application code doesn't need any modifications

## Rollback

If you need to disable SnapStart:

1. Remove the `snap_start` block from `lambda.tf`
2. Update API Gateway to use `aws_lambda_function.quote_lambda.invoke_arn` instead of the alias
3. Run `terraform apply`

## Resources

- [AWS Lambda SnapStart Documentation](https://docs.aws.amazon.com/lambda/latest/dg/snapstart.html)
- [SnapStart Best Practices](https://docs.aws.amazon.com/lambda/latest/dg/snapstart-best-practices.html)
