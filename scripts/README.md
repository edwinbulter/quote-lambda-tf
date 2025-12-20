# Project Scripts

This directory contains utility scripts for managing the Quote Lambda TF project.

## Table of Contents

- [Available Scripts](#available-scripts)
  - [setup-workspaces.sh](#setup-workspacessh)
  - [generate-frontend-config.sh](#generate-frontend-configsh)
  - [restore-dynamodb-pitr/](#restore-dynamodb-pitr)

## Available Scripts

### `setup-workspaces.sh`

Interactive script for setting up Terraform workspaces for multi-environment deployments (development and production).

**Purpose:** Automates the initial setup of separate development and production environments using Terraform workspaces, ensuring proper isolation and configuration.

#### Usage

```bash
# From the repository root
./scripts/setup-workspaces.sh
```

#### What It Does

1. Reinitializes Terraform backends with workspace support
2. Verifies your production environment (default workspace)
3. Creates a development workspace
4. Deploys development infrastructure for both backend and frontend
5. Saves development URLs to files for easy reference

The script is interactive and will ask for confirmation before making changes.

#### Prerequisites

- Terraform installed and configured
- AWS credentials configured
- Production infrastructure already deployed (or ready to verify)
- Run from the repository root directory

#### Output Files

- `dev-api-url.txt` - Development API Gateway URL
- `dev-cloudfront-url.txt` - Development CloudFront URL

#### Documentation

For detailed information about multi-environment setup and workspace management, see:
- [Multi-Environment Setup Guide](../doc/multi-environment-setup.md)

---

### `generate-frontend-config.sh`

Automatically generates frontend environment configuration files from Terraform outputs.

**Purpose:** Keeps your frontend configuration in sync with your deployed infrastructure, eliminating manual copy-paste errors and ensuring consistency between environments.

#### Usage

```bash
# Generate development environment config
./scripts/generate-frontend-config.sh dev

# Generate production environment config
./scripts/generate-frontend-config.sh prod
```

#### What It Does

1. Selects the appropriate Terraform workspace (`dev` or `default` for prod)
2. Retrieves outputs from Terraform:
   - User Pool ID
   - User Pool Client ID
   - Cognito Domain
   - API Gateway URL
   - AWS Region
3. Generates `.env.development` or `.env.production` in the frontend directory
4. Displays the configuration for verification

#### Prerequisites

- Terraform infrastructure must be deployed (`terraform apply` completed)
- You must be in the project root directory
- Terraform must be initialized in the backend infrastructure directory

#### Output

The script creates environment files in `quote-lambda-tf-frontend/`:

**`.env.development`** (for dev environment):
```bash
VITE_AWS_REGION=eu-central-1
VITE_COGNITO_USER_POOL_ID=eu-central-1_XrKxJWy5u
VITE_COGNITO_CLIENT_ID=7lkohh6t96igkm9q16rdchansh
VITE_COGNITO_DOMAIN=quote-lambda-tf-backend-dev.auth.eu-central-1.amazoncognito.com
VITE_API_URL=https://sy5vvqbh93.execute-api.eu-central-1.amazonaws.com
```

**`.env.production`** (for prod environment):
```bash
VITE_AWS_REGION=eu-central-1
VITE_COGNITO_USER_POOL_ID=eu-central-1_ABC123XYZ
VITE_COGNITO_CLIENT_ID=abc123xyz456
VITE_COGNITO_DOMAIN=quote-lambda-tf-backend.auth.eu-central-1.amazoncognito.com
VITE_API_URL=https://prod123abc.execute-api.eu-central-1.amazonaws.com
```

#### Security Notes

- ✅ Generated `.env` files are excluded from version control via `.gitignore`
- ✅ Example files (`.env.*.example`) are safe to commit
- ✅ The script adds a warning header to generated files
- ⚠️ Never commit actual `.env.development` or `.env.production` files

#### Workflow Integration

**After deploying infrastructure:**
```bash
cd quote-lambda-tf-backend/infrastructure
terraform workspace select dev
terraform apply

# Generate frontend config
cd ../..
./scripts/generate-frontend-config.sh dev
```

**Before deploying frontend:**
```bash
# Ensure config is up to date
./scripts/generate-frontend-config.sh dev

# Run frontend
cd quote-lambda-tf-frontend
npm run dev
```

**In CI/CD pipelines:**
```yaml
# Example GitHub Actions workflow
- name: Generate frontend config
  run: ./scripts/generate-frontend-config.sh prod

- name: Build frontend
  run: |
    cd quote-lambda-tf-frontend
    npm run build
```

#### Troubleshooting

**Error: "No Terraform state found"**
- Solution: Run `terraform apply` in the backend infrastructure directory first

**Error: "Failed to retrieve all required Terraform outputs"**
- Solution: Ensure all resources are created in Terraform
- Check that you're in the correct workspace

**Error: "Environment must be 'dev' or 'prod'"**
- Solution: Use either `dev` or `prod` as the argument

#### Manual Alternative

If you prefer not to use the script, you can manually create the `.env` files:

1. Get Terraform outputs:
   ```bash
   cd quote-lambda-tf-backend/infrastructure
   terraform workspace select dev
   terraform output
   ```

2. Copy the example file:
   ```bash
   cd ../../quote-lambda-tf-frontend
   cp .env.development.example .env.development
   ```

3. Fill in the values from step 1

---

### `restore-dynamodb-pitr/`

Python script for automated DynamoDB Point-In-Time Recovery (PITR) restore operations.

**Purpose:** Restore all three DynamoDB tables (quotes, user-likes, user-progress) to any point within the last 35 days using a single command. Handles the complete restore workflow including table creation, data verification, data swap, and cleanup.

#### Features

- **Single Command Restore** - Restores all 3 tables to a specified point in time
- **Timezone Support** - Accepts both local time (CET/CEST) and UTC timestamps
- **Smart Table Reuse** - Detects and reuses existing restore tables from previous runs
- **Automatic Cleanup** - Removes old restore tables from different restore points
- **Concurrency Control** - File-based locking prevents concurrent restore operations
- **Comprehensive Logging** - Detailed console and file logging for audit trails
- **Dry Run Mode** - Validate restore without executing changes
- **Progress Tracking** - Real-time status updates during restore operations

#### Installation

```bash
cd scripts/restore-dynamodb-pitr
pip3 install -r requirements.txt
```

**Dependencies:**
- Python 3.9+
- boto3 (AWS SDK)
- python-dateutil (ISO 8601 timestamp parsing)
- pytz (timezone handling)

#### Usage

```bash
# Basic restore using local CET time
python3 scripts/restore-dynamodb-pitr/restore_dynamodb_pitr.py \
  --restore-point 2025-12-19T20:10:00 \
  --environment dev

# Restore using UTC time explicitly
python3 scripts/restore-dynamodb-pitr/restore_dynamodb_pitr.py \
  --restore-point 2025-12-19T19:10:00Z \
  --environment dev

# Dry run (validate without executing)
python3 scripts/restore-dynamodb-pitr/restore_dynamodb_pitr.py \
  --restore-point 2025-12-19T20:10:00 \
  --environment dev \
  --dry-run

# Restore with custom timeout (default: 30 minutes)
python3 scripts/restore-dynamodb-pitr/restore_dynamodb_pitr.py \
  --restore-point 2025-12-19T20:10:00 \
  --environment dev \
  --timeout-minutes 45

# Restore with verbose logging
python3 scripts/restore-dynamodb-pitr/restore_dynamodb_pitr.py \
  --restore-point 2025-12-19T20:10:00 \
  --environment dev \
  --verbose
```

#### How It Works

The script follows a multi-phase restore process:

**Phase 1: Initialization**
1. Parses restore point timestamp and converts to UTC
2. Checks for existing restore tables matching the restore point
3. Reuses existing tables if found, or cleans up old tables from different restore points
4. Validates restore point is within the 35-day PITR window
5. Acquires file-based lock to prevent concurrent operations

**Phase 2: PITR Restore**
1. Initiates PITR restore for all 3 tables (if not reusing existing tables)
2. Creates restore tables with naming: `{table-name}-restore-{timestamp}`
3. Polls table status every 10 seconds until all reach ACTIVE state
4. Logs progress with elapsed time

**Phase 3: Data Verification**
1. Counts items in both original and restore tables
2. Logs item counts for comparison (informational only)
3. Warns if restore has more items than original (unexpected)

**Phase 4: Data Swap**
1. Clears all items from production tables
2. Copies all items from restore tables to production tables
3. Uses batch operations (25 items per batch) for efficiency
4. Logs number of items copied

**Phase 5: Cleanup**
1. Deletes all restore tables
2. Releases file lock
3. Logs completion status

#### Key Design Decisions

**1. Restore Point Timestamp in Table Names**
- Restore tables are named using the restore point timestamp (not current time)
- Example: `quote-lambda-tf-quotes-dev-restore-20251219201000`
- Makes it easy to identify which restore point the tables belong to
- Enables reuse of existing restore tables when re-running the same restore

**2. Smart Table Reuse**
- Script detects existing restore tables matching the restore point
- If found and ACTIVE, skips restore initiation and proceeds to data swap
- If found but not ACTIVE, waits for them to complete
- Cleans up old restore tables from different restore points automatically

**3. Timezone Handling**
- Accepts timestamps without timezone suffix (assumes CET/CEST)
- Accepts timestamps with 'Z' suffix (explicit UTC)
- Converts all times to UTC internally for AWS API calls
- Logs both local and UTC times for clarity

**4. Item Count Verification**
- Restore tables typically have fewer items than current tables (expected)
- Script logs the difference but doesn't fail the restore
- Only warns if restore has MORE items than original (unexpected scenario)

**5. Batch Operations**
- Uses DynamoDB batch_write_item API for efficiency
- Processes 25 items per batch (DynamoDB limit)
- Handles pagination for large tables automatically

**6. Concurrency Control**
- File-based locking at `/tmp/dynamodb_restore.lock`
- Prevents concurrent restore operations
- Detects and removes stale locks (older than 1 hour)
- Automatic lock release on completion or error

#### Prerequisites

- AWS credentials configured (via AWS CLI or environment variables)
- DynamoDB tables must have PITR enabled
- Appropriate IAM permissions:
  - `dynamodb:RestoreTableToPointInTime`
  - `dynamodb:DescribeTable`
  - `dynamodb:DescribeContinuousBackups`
  - `dynamodb:Scan`
  - `dynamodb:BatchWriteItem`
  - `dynamodb:DeleteTable`
  - `dynamodb:ListTables`

#### Output Files

- **Log file**: `restore_dynamodb_YYYYMMDD_HHMMSS.log` - Detailed operation logs
- **Status file**: `restore_status_{restore-id}.json` - Machine-readable status tracking
- **Lock file**: `/tmp/dynamodb_restore.lock` - Prevents concurrent operations

#### Environment Support

The script automatically handles table naming based on environment:

**Development (`--environment dev`):**
- `quote-lambda-tf-quotes-dev`
- `quote-lambda-tf-user-likes-dev`
- `quote-lambda-tf-user-progress-dev`

**Production (`--environment prod`):**
- `quote-lambda-tf-quotes`
- `quote-lambda-tf-user-likes`
- `quote-lambda-tf-user-progress`

#### Troubleshooting

**Error: "Restore already in progress"**
- Solution: Another restore is running. Wait for it to complete or check `/tmp/dynamodb_restore.lock`

**Error: "Restore point is X days old, max is 35 days"**
- Solution: PITR only supports restoring to points within the last 35 days

**Error: "Invalid restore time"**
- Solution: Restore point is before PITR was enabled or in the future

**Timeout after 30 minutes**
- Solution: Increase timeout with `--timeout-minutes` or check AWS console for table status

**Script hangs during polling**
- Solution: Check DynamoDB console to verify restore tables are being created

#### Security Notes

- ✅ Script requires AWS credentials with appropriate permissions
- ✅ All operations logged for audit trail
- ✅ Lock file prevents concurrent operations
- ✅ Restore tables deleted after successful swap
- ⚠️ Run only by authorized administrators
- ⚠️ Test in development environment first

#### Documentation

For detailed technical design and implementation details, see:
- [ECS-25: DynamoDB PITR Restore Automation](../doc/tasks/ECS-25-backup-and-restore.md)

---

## Future Scripts

Additional scripts may be added here for:
- Database migrations
- Deployment automation
- Testing utilities
- Infrastructure validation
