# ECS-25: DynamoDB PITR Restore Automation

## Table of Contents
- [Overview](#overview)
- [User Stories](#user-stories)
- [Requirements](#requirements)
- [Architecture](#architecture)
  - [System Design](#system-design)
  - [Data Flow](#data-flow)
  - [Restore Process](#restore-process)
- [Technical Design](#technical-design)
  - [Python Script Design](#python-script-design)
  - [Script Structure](#script-structure)
  - [Concurrency Control](#concurrency-control)
  - [Error Handling](#error-handling)
  - [Monitoring and Logging](#monitoring-and-logging)
- [Security Considerations](#security-considerations)
- [Acceptance Criteria](#acceptance-criteria)
- [Implementation Steps](#implementation-steps)
- [Testing Strategy](#testing-strategy)
- [Estimated Effort](#estimated-effort)

## Overview

Implement an automated DynamoDB Point-In-Time Recovery (PITR) restore mechanism that allows administrators to restore all three application tables to any point within the last 35 days using a single command. The solution will:

1. **Initiate PITR restores** for all 3 tables simultaneously to a specified point in time
2. **Swap data** from restore tables into production tables atomically
3. **Clean up** restore tables after successful swap
4. **Prevent concurrent executions** to avoid data corruption
5. **Provide comprehensive logging** for audit trails

### Tables Involved

The restore script automatically handles table naming based on the environment:

**Development Environment (--environment dev):**
- **quote-lambda-tf-quotes-dev** - Quotes data
- **quote-lambda-tf-user-likes-dev** - User likes data  
- **quote-lambda-tf-user-views-dev** - User views data

**Production Environment (--environment prod):**
- **quote-lambda-tf-quotes** - Quotes data
- **quote-lambda-tf-user-likes** - User likes data
- **quote-lambda-tf-user-views** - User views data

All tables have PITR enabled for the last 35 days. The script automatically appends `-dev` suffix when the environment is set to `dev`, and uses the base table names when environment is `prod`.

## User Stories

### US-1: Restore All Tables to Point in Time
**As an** administrator  
**I want to** restore all 3 DynamoDB tables to a specific point in time within the last 35 days  
**So that** I can recover from data corruption, accidental deletions, or other data issues

**Acceptance Criteria:**
- Single command triggers restore for all 3 tables
- Specify restore point in time (ISO 8601 timestamp)
- Restore completes without manual intervention
- Original tables contain restored data
- Restore tables are cleaned up automatically

### US-2: Prevent Concurrent Restores
**As a** system administrator  
**I want to** ensure only one restore operation runs at a time  
**So that** I avoid data corruption and race conditions

**Acceptance Criteria:**
- Second restore attempt fails if one is already in progress
- Clear error message indicates restore is in progress
- No data corruption from concurrent operations

### US-3: Monitor Restore Progress
**As an** administrator  
**I want to** see detailed logs of the restore operation  
**So that** I can troubleshoot issues and verify successful completion

**Acceptance Criteria:**
- All operations logged to CloudWatch
- Timestamps for each step
- Clear success/failure indicators
- Detailed error messages for failures

## Requirements

### Functional Requirements

1. **PITR Restore Initiation**
   - Trigger PITR restore for all 3 tables to specified point in time
   - Create temporary restore tables with naming convention: `{original-table-name}-restore-{timestamp}`
   - Wait for all restore operations to complete
   - Timeout if restore takes longer than 30 minutes

2. **Data Swap**
   - After all restores complete, swap data from restore tables to production tables
   - Use atomic operations to minimize downtime
   - Verify data integrity before and after swap
   - Rollback mechanism if swap fails

3. **Cleanup**
   - Delete all temporary restore tables after successful swap
   - Preserve restore tables if swap fails (for manual recovery)
   - Clean up on error conditions

4. **Concurrency Control**
   - Use DynamoDB conditional writes or external locking mechanism
   - Prevent overlapping restore operations
   - Clear indication of restore status

5. **Monitoring & Logging**
   - Log all operations to CloudWatch
   - Track restore duration
   - Record item counts before/after restore
   - Alert on failures

### Non-Functional Requirements

1. **Performance**
   - Restore should complete within 30 minutes for typical dataset sizes
   - Minimize application downtime during data swap

2. **Reliability**
   - Atomic operations (all or nothing)
   - Automatic rollback on failure
   - Idempotent operations

3. **Security**
   - ADMIN role required for all restore operations
   - Audit logging of all restore activities
   - Encryption for data at rest and in transit

4. **Scalability**
   - Support tables with millions of items
   - Handle large item sizes efficiently

## Architecture

### System Design

```
┌─────────────────────────────────────────────────────────────┐
│                    Administrator                             │
│              (Initiates restore via CLI/API)                │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                  Restore Trigger                             │
│         (Lambda Function or Python Script)                  │
│                                                              │
│  1. Acquire lock (prevent concurrent restores)             │
│  2. Initiate PITR restore for all 3 tables                │
│  3. Poll for restore completion                            │
│  4. Swap data (atomic operation)                           │
│  5. Delete restore tables                                  │
│  6. Release lock                                           │
└────────────────────────┬────────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│   Quotes     │  │ User Likes   │  │ User Views   │
│   (Restore)  │  │  (Restore)   │  │  (Restore)   │
└──────────────┘  └──────────────┘  └──────────────┘
        │                │                │
        └────────────────┼────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│   Quotes     │  │ User Likes   │  │ User Views   │
│ (Production) │  │(Production)  │  │(Production)  │
└──────────────┘  └──────────────┘  └──────────────┘
        │                │                │
        └────────────────┼────────────────┘
                         │
                         ▼
                  ┌──────────────┐
                  │  CloudWatch  │
                  │     Logs     │
                  └──────────────┘
```

### Data Flow

**Phase 1: Initialization**
1. Acquire distributed lock (prevent concurrent restores)
2. Log restore request with timestamp and restore point
3. Validate restore point is within 35-day PITR window

**Phase 2: PITR Restore**
1. Call `restore_table_to_point_in_time()` for each table:
   - `quote-lambda-tf-quotes` → `quote-lambda-tf-quotes-restore-{timestamp}`
   - `quote-lambda-tf-user-likes-dev` → `quote-lambda-tf-user-likes-dev-restore-{timestamp}`
   - `quote-lambda-tf-user-views-dev` → `quote-lambda-tf-user-views-dev-restore-{timestamp}`
2. Poll table status until all reach `ACTIVE` state
3. Log completion of restore phase

**Phase 3: Data Swap**
1. Verify item counts match between restore and original tables
2. Perform atomic swap:
   - Option A: Scan and batch write all items from restore to production
   - Option B: Use DynamoDB Streams to capture changes and replay
3. Verify data integrity post-swap
4. Log swap completion

**Phase 4: Cleanup**
1. Delete all restore tables
2. Release distributed lock
3. Log final status

### Restore Process

```
Start Restore
    │
    ├─→ Acquire Lock
    │   └─→ Lock acquired? Continue : Fail with "Restore in progress"
    │
    ├─→ Initiate PITR Restores (Parallel)
    │   ├─→ quotes-restore
    │   ├─→ user-likes-dev-restore
    │   └─→ user-views-dev-restore
    │
    ├─→ Poll for Completion (Max 30 min)
    │   └─→ All ACTIVE? Continue : Timeout error
    │
    ├─→ Verify Item Counts
    │   └─→ Counts match? Continue : Rollback
    │
    ├─→ Swap Data (Atomic)
    │   ├─→ Scan restore tables
    │   ├─→ Batch write to production tables
    │   └─→ Verify counts match
    │
    ├─→ Delete Restore Tables
    │   ├─→ Delete quotes-restore
    │   ├─→ Delete user-likes-dev-restore
    │   └─→ Delete user-views-dev-restore
    │
    ├─→ Release Lock
    │
    └─→ Log Success
```

## Technical Design

### Python Script Design

**Script Location:** `scripts/restore_dynamodb_pitr.py`

**Execution Method:**
- Run manually from command line: `python scripts/restore_dynamodb_pitr.py --restore-point 2025-12-19T09:00:00Z --environment dev`
- Run via cron job for scheduled restores
- Run from CI/CD pipeline for automated recovery
- No Lambda timeout constraints - can run for hours if needed

**Dependencies:**
- Python 3.9+
- boto3 (AWS SDK)
- python-dateutil (for ISO 8601 timestamp parsing)
- pytz (for timezone handling)

### Script Structure

**Main Components:**

```
restore_dynamodb_pitr.py
├── Configuration Management
│   ├── Load environment variables
│   ├── Parse command-line arguments
│   └── Validate configuration
├── Lock Management
│   ├── Acquire file-based lock
│   ├── Release lock
│   └── Check lock status
├── PITR Restore Operations
│   ├── Initiate restore for each table
│   ├── Poll restore status
│   └── Handle timeouts
├── Data Verification
│   ├── Count items in original tables
│   ├── Count items in restore tables
│   └── Compare counts
├── Data Swap
│   ├── Scan restore table
│   ├── Batch write to production table
│   └── Verify swap completion
├── Cleanup
│   ├── Delete restore tables
│   └── Handle cleanup errors
├── Logging & Monitoring
│   ├── CloudWatch Logs integration
│   ├── Console output
│   └── Status file tracking
└── Error Handling
    ├── Rollback on failure
    ├── Preserve restore tables
    └── Detailed error reporting
```

**Command-Line Interface:**

```bash
# Basic restore to specific point in time
python scripts/restore_dynamodb_pitr.py \
  --restore-point 2025-12-19T09:00:00Z \
  --environment dev

# Dry run (validate without executing)
python scripts/restore_dynamodb_pitr.py \
  --restore-point 2025-12-19T09:00:00Z \
  --environment dev \
  --dry-run

# Restore with custom timeout
python scripts/restore_dynamodb_pitr.py \
  --restore-point 2025-12-19T09:00:00Z \
  --environment dev \
  --timeout-minutes 45

# Restore with verbose logging
python scripts/restore_dynamodb_pitr.py \
  --restore-point 2025-12-19T09:00:00Z \
  --environment dev \
  --verbose

# List available restore points (last 35 days)
python scripts/restore_dynamodb_pitr.py \
  --list-restore-points \
  --environment dev
```

**Configuration:**

```python
# Environment variables
DYNAMODB_TABLE              # quotes table name (auto-generated based on environment)
DYNAMODB_USER_LIKES_TABLE   # user likes table name (auto-generated based on environment)
DYNAMODB_USER_VIEWS_TABLE   # user views table name (auto-generated based on environment)
AWS_REGION                  # AWS region (default: eu-central-1)
RESTORE_LOCK_FILE           # Lock file path (default: /tmp/dynamodb_restore.lock)
RESTORE_TIMEOUT_MINUTES     # Timeout for restore operations (default: 30)
RESTORE_LOG_GROUP           # CloudWatch log group (default: /aws/dynamodb/restore)
RESTORE_LOG_STREAM          # CloudWatch log stream (default: pitr-restore)
```

**Table Name Generation:**
The script automatically generates table names based on the `--environment` parameter:

- **dev environment**: `quote-lambda-tf-quotes-dev`, `quote-lambda-tf-user-likes-dev`, `quote-lambda-tf-user-views-dev`
- **prod environment**: `quote-lambda-tf-quotes`, `quote-lambda-tf-user-likes`, `quote-lambda-tf-user-views`

This matches the Terraform configuration where tables are named with `-dev` suffix in development environment and without suffix in production.

### Concurrency Control

**Approach: File-Based Locking**

Uses a lock file on the filesystem to prevent concurrent executions. Simple and reliable for single-machine execution.

```python
import os
import time
from pathlib import Path

class RestoreLock:
    def __init__(self, lock_file_path="/tmp/dynamodb_restore.lock"):
        self.lock_file = Path(lock_file_path)
        self.lock_acquired = False
    
    def acquire(self, timeout_seconds=5):
        """
        Acquire lock with timeout.
        Returns True if acquired, False if already locked.
        """
        start_time = time.time()
        
        while time.time() - start_time < timeout_seconds:
            try:
                # Create lock file exclusively (fails if exists)
                fd = os.open(
                    str(self.lock_file),
                    os.O_CREAT | os.O_EXCL | os.O_WRONLY
                )
                with os.fdopen(fd, 'w') as f:
                    f.write(f"{os.getpid()}\n{time.time()}\n")
                self.lock_acquired = True
                logger.info(f"Lock acquired: {self.lock_file}")
                return True
            except FileExistsError:
                # Lock file exists, check if stale
                if self._is_stale():
                    logger.warning("Removing stale lock file")
                    self.lock_file.unlink()
                    continue
                time.sleep(0.5)
        
        logger.error("Failed to acquire lock - restore already in progress")
        return False
    
    def release(self):
        """Release the lock."""
        if self.lock_acquired and self.lock_file.exists():
            self.lock_file.unlink()
            self.lock_acquired = False
            logger.info(f"Lock released: {self.lock_file}")
    
    def _is_stale(self, stale_after_seconds=3600):
        """Check if lock file is stale (older than 1 hour)."""
        if not self.lock_file.exists():
            return False
        mtime = self.lock_file.stat().st_mtime
        return time.time() - mtime > stale_after_seconds
    
    def __enter__(self):
        if not self.acquire():
            raise RuntimeError("Could not acquire restore lock")
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        self.release()
```

**Usage:**

```python
# Context manager usage (automatic cleanup)
try:
    with RestoreLock() as lock:
        perform_restore()
except RuntimeError as e:
    logger.error(f"Cannot start restore: {e}")
    sys.exit(1)
```

### Error Handling

**Error Scenarios & Recovery:**

1. **Lock Acquisition Fails**
   - Error: "Restore already in progress"
   - Action: Exit with error code 1
   - Message: "Another restore operation is running. Please wait or check lock file."

2. **PITR Restore Fails**
   - Error: DynamoDB API error during restore initiation
   - Action: Release lock and exit
   - Cleanup: No restore tables created, nothing to clean up
   - Message: "Failed to initiate PITR restore for {table}: {error}"

3. **Restore Status Polling Timeout**
   - Error: Restore tables not ACTIVE after 30 minutes
   - Action: Release lock and exit
   - Cleanup: Preserve restore tables for manual inspection
   - Message: "Restore operation timed out. Restore tables preserved for manual recovery."

4. **Item Count Mismatch**
   - Error: Restore table has different item count than original
   - Action: Release lock and exit
   - Cleanup: Preserve restore tables for investigation
   - Message: "Item count mismatch detected. Original: {count1}, Restore: {count2}"

5. **Data Swap Fails**
   - Error: Error during batch write to production table
   - Action: Release lock and exit
   - Cleanup: Preserve restore tables for manual recovery
   - Message: "Failed to swap data for {table}: {error}"

6. **Cleanup Fails**
   - Error: Error deleting restore tables
   - Action: Log error but don't fail the operation
   - Cleanup: Mark restore tables for manual cleanup
   - Message: "Warning: Failed to delete restore table {table}. Please delete manually."

**Rollback Strategy:**

- No automatic rollback (original data preserved in production tables)
- Restore tables remain available for 24 hours
- Administrator can manually inspect or delete restore tables
- Restore tables have naming convention: `{original-table}-restore-{timestamp}`

### Monitoring and Logging

**Logging Approach:**

The Python script uses standard Python logging with two handlers:

1. **Console Handler** - Real-time output to stdout/stderr for immediate feedback
2. **File Handler** - Persistent logs written to a local file for audit trail

```python
import logging
from datetime import datetime

# Configure logging
log_file = f"restore_dynamodb_{datetime.now().strftime('%Y%m%d_%H%M%S')}.log"

logger = logging.getLogger('restore_dynamodb')
logger.setLevel(logging.DEBUG)

# Console handler (INFO level)
console_handler = logging.StreamHandler()
console_handler.setLevel(logging.INFO)
console_formatter = logging.Formatter('[%(asctime)s] [%(levelname)s] %(message)s')
console_handler.setFormatter(console_formatter)

# File handler (DEBUG level for detailed logs)
file_handler = logging.FileHandler(log_file)
file_handler.setLevel(logging.DEBUG)
file_formatter = logging.Formatter('[%(asctime)s] [%(levelname)s] [%(funcName)s] %(message)s')
file_handler.setFormatter(file_formatter)

logger.addHandler(console_handler)
logger.addHandler(file_handler)
```

**Log Output Format:**

Console output (real-time progress):
```
[2025-12-19 10:30:00] [INFO] Starting DynamoDB PITR restore
[2025-12-19 10:30:00] [INFO] Restore point: 2025-12-19T09:00:00Z
[2025-12-19 10:30:01] [INFO] Lock acquired successfully
[2025-12-19 10:30:02] [INFO] Initiating PITR restore for quotes-restore
[2025-12-19 10:30:03] [INFO] Initiating PITR restore for user-likes-dev-restore
[2025-12-19 10:30:04] [INFO] Initiating PITR restore for user-views-dev-restore
[2025-12-19 10:35:00] [INFO] quotes-restore is now ACTIVE (4m55s)
[2025-12-19 10:35:02] [INFO] user-likes-dev-restore is now ACTIVE (4m57s)
[2025-12-19 10:35:04] [INFO] user-views-dev-restore is now ACTIVE (4m59s)
[2025-12-19 10:35:05] [INFO] Verifying item counts...
[2025-12-19 10:35:05] [INFO] quotes: original=1234, restore=1234 ✓
[2025-12-19 10:35:06] [INFO] user-likes-dev: original=5678, restore=5678 ✓
[2025-12-19 10:35:07] [INFO] user-views-dev: original=9012, restore=9012 ✓
[2025-12-19 10:35:08] [INFO] Swapping data for quotes (1234 items)
[2025-12-19 10:35:15] [INFO] Swapping data for user-likes-dev (5678 items)
[2025-12-19 10:35:22] [INFO] Swapping data for user-views-dev (9012 items)
[2025-12-19 10:35:23] [INFO] Deleting restore table: quotes-restore
[2025-12-19 10:35:24] [INFO] Deleting restore table: user-likes-dev-restore
[2025-12-19 10:35:25] [INFO] Deleting restore table: user-views-dev-restore
[2025-12-19 10:35:26] [INFO] Lock released
[2025-12-19 10:35:27] [INFO] Restore completed successfully in 3m45s
```

**Log File Location:**

Logs are written to: `restore_dynamodb_YYYYMMDD_HHMMSS.log` in the current working directory

**Status File:**

Create a JSON status file for monitoring restore progress:

```json
{
  "restore_id": "restore-2025-12-19T10-30-00Z",
  "status": "IN_PROGRESS",
  "restore_point_in_time": "2025-12-19T09:00:00Z",
  "started_at": "2025-12-19T10:30:00Z",
  "current_phase": "SWAP_DATA",
  "progress": {
    "quotes": {
      "status": "COMPLETED",
      "original_count": 1234,
      "restored_count": 1234
    },
    "user_likes": {
      "status": "COMPLETED",
      "original_count": 5678,
      "restored_count": 5678
    },
    "user_views": {
      "status": "IN_PROGRESS",
      "original_count": 9012,
      "restored_count": 9012
    }
  }
}
```

**Monitoring Options:**

1. **Real-time console output** - Watch the script run and see progress immediately
2. **Log file** - Review detailed logs after execution for troubleshooting
3. **Status file** - Machine-readable JSON for integration with monitoring systems
4. **Exit codes** - Script returns 0 on success, non-zero on failure for cron/CI/CD integration

## Security Considerations

### Access Control

- Script should be run by authorized administrators only
- AWS credentials required (configured via AWS CLI or environment variables)
- Consider restricting script execution to specific machines/bastion hosts
- Log all restore operations for audit trails

### Data Protection

- **Encryption at rest**: DynamoDB server-side encryption (default)
- **Encryption in transit**: HTTPS for all AWS API calls
- **Temporary tables**: Deleted immediately after swap
- **Audit logging**: All restore operations logged with timestamps

### Sensitive Data

- No passwords or API keys in logs
- Item counts logged but not individual items
- Restore point timestamps logged for audit trail
- Error messages don't expose internal details

### Audit Trail

Log all restore operations with:
- Timestamp (ISO 8601)
- Restore ID
- Restore point in time
- Status (started, completed, failed)
- Duration
- Item counts
- Error messages (if failed)
- Administrator username (from environment or script parameter)

## Acceptance Criteria

### Restore Functionality
- [ ] Single command restores all 3 tables to specified point in time
- [ ] Restore point can be any time within last 35 days
- [ ] PITR restore tables created with correct naming convention
- [ ] Data from restore tables swapped to production tables
- [ ] Restore tables deleted after successful swap
- [ ] Restore completes within 30 minutes for typical datasets

### Concurrency Control
- [ ] Only one restore operation runs at a time
- [ ] Concurrent restore attempt returns clear error message
- [ ] No data corruption from concurrent operations
- [ ] Lock file automatically released on completion or timeout

### Monitoring & Logging
- [ ] All operations logged to CloudWatch
- [ ] Console output shows progress in real-time
- [ ] Timestamps for each phase
- [ ] Item counts logged before/after swap
- [ ] Clear success/failure indicators
- [ ] Detailed error messages for failures
- [ ] Status file tracks restore progress

### Script Execution
- [ ] Script can be run manually from command line
- [ ] Script can be run via cron job for scheduled restores
- [ ] Script can be run from CI/CD pipeline
- [ ] Dry-run mode validates without executing
- [ ] Custom timeout configuration supported
- [ ] Verbose logging mode available

### Error Handling
- [ ] Restore point validation (within 35 days)
- [ ] Item count verification before/after swap
- [ ] Graceful handling of PITR failures
- [ ] Preserve restore tables on failure for manual recovery
- [ ] Clear error messages for troubleshooting
- [ ] Stale lock file detection and cleanup

## Implementation Steps

### Phase 1: Setup & Dependencies (0.5 days)

1. **Create Python script structure**
   - Create `scripts/restore_dynamodb_pitr.py`
   - Create `scripts/requirements.txt` with dependencies:
     - boto3
     - python-dateutil
     - pytz


### Phase 2: Core Restore Logic (2-3 days)

1. **Implement configuration management**
   - Parse command-line arguments
   - Load environment variables
   - Validate configuration

2. **Implement file-based locking**
   - Create `RestoreLock` class
   - Implement lock acquisition with timeout
   - Implement stale lock detection
   - Implement lock release

3. **Implement PITR restore initiation**
   - Call `restore_table_to_point_in_time()` for each table
   - Handle naming convention: `{table}-restore-{timestamp}`
   - Log restore initiation
   - Support parallel restore initiation

4. **Implement status polling**
   - Poll table status until ACTIVE
   - Timeout after configurable duration (default: 30 minutes)
   - Log status changes
   - Update status file with progress

5. **Implement data verification**
   - Count items in original tables (using Scan with Select=COUNT)
   - Count items in restore tables
   - Compare counts and log results
   - Fail if counts don't match

6. **Implement data swap**
   - Scan restore table with pagination
   - Batch write items to production table
   - Verify all items written
   - Log swap progress

### Phase 3: Cleanup & Error Handling (1-2 days)

1. **Implement restore table deletion**
   - Delete all restore tables
   - Handle deletion errors gracefully
   - Log deletion status
   - Don't fail if deletion errors occur

2. **Implement comprehensive error handling**
   - Catch and log all exceptions
   - Preserve restore tables on failure
   - Update status file with error details
   - Release lock on all error paths

3. **Implement rollback strategy**
   - Document manual recovery procedures
   - Preserve restore tables for 24 hours
   - Log restore table names for manual cleanup

### Phase 4: Testing (1-2 days)

1. **Unit tests**
   - Test lock acquisition/release
   - Test lock staleness detection
   - Test PITR restore initiation
   - Test data verification logic
   - Test error handling

2. **Integration tests**
   - Test full restore flow end-to-end
   - Test concurrent restore attempts (should fail)
   - Test timeout handling
   - Test cleanup on failure
   - Test dry-run mode

3. **Manual testing**
   - Test restore to different points in time
   - Verify data integrity after restore
   - Test error scenarios
   - Verify CloudWatch logs
   - Verify status file output
   - Test with different dataset sizes

### Phase 5: Documentation (0.5 days)

1. **Create runbook**
   - How to run the script
   - Command-line options
   - Environment variables
   - Expected output

2. **Create troubleshooting guide**
   - Common errors and solutions
   - How to manually recover from failures
   - How to clean up restore tables
   - How to check lock status

3. **Create operational procedures**
   - Scheduled restore procedures
   - Emergency restore procedures
   - Monitoring and alerting setup

## Testing Strategy

### Unit Tests

```python
def test_acquire_restore_lock():
    """Test lock acquisition."""
    assert acquire_restore_lock() == True
    assert acquire_restore_lock() == False  # Already locked
    release_restore_lock()

def test_initiate_pitr_restore():
    """Test PITR restore initiation."""
    restore_tables = initiate_pitr_restores(
        restore_point_in_time="2025-12-19T09:00:00Z"
    )
    assert len(restore_tables) == 3
    assert all(t.endswith("-restore") for t in restore_tables)

def test_verify_item_counts():
    """Test item count verification."""
    counts = verify_item_counts(original_tables, restore_tables)
    assert counts['match'] == True
    assert counts['quotes']['original'] == counts['quotes']['restore']
```

### Integration Tests

```python
def test_full_restore_flow():
    """Test complete restore operation."""
    # 1. Create test data
    # 2. Trigger restore
    # 3. Verify data swapped
    # 4. Verify restore tables deleted
    # 5. Verify status logged

def test_concurrent_restore_attempts():
    """Test concurrent restore prevention."""
    # 1. Start first restore
    # 2. Attempt second restore (should fail)
    # 3. Verify error message
    # 4. Wait for first to complete
    # 5. Verify second can now run

def test_restore_timeout():
    """Test timeout handling."""
    # 1. Mock slow PITR restore
    # 2. Trigger restore
    # 3. Verify timeout after 30 minutes
    # 4. Verify restore tables preserved
```

### Manual Testing Checklist

- [ ] Restore to 1 day ago - verify data matches
- [ ] Restore to 7 days ago - verify data matches
- [ ] Restore to 30 days ago - verify data matches
- [ ] Attempt restore to 40 days ago - verify error
- [ ] Trigger concurrent restore - verify blocked
- [ ] Verify CloudWatch logs contain all phases
- [ ] Verify item counts logged correctly
- [ ] Verify restore tables deleted after success
- [ ] Verify restore tables preserved on failure
- [ ] Test dry-run mode
- [ ] Test with verbose logging
- [ ] Verify status file output

## Estimated Effort

### Development
- **Setup & Dependencies**: 0.5 days
- **Core Restore Logic**: 2-3 days
- **Cleanup & Error Handling**: 1-2 days
- **Testing**: 1-2 days
- **Documentation**: 0.5 days

**Total: 5-8 days**

### Deployment
- **Code Review**: 1 day
- **Testing in Dev**: 1 day
- **Testing in Prod**: 1 day

**Total: 3 days**

### Post-Deployment
- **Monitoring & Validation**: 3-5 days
- **Runbook & Procedures**: 1 day

**Total: 4-6 days**

---

## Summary

This design provides a robust, automated solution for DynamoDB PITR restore operations using a Python script that:

1. **Simplifies recovery** - Single command restores all 3 tables to any point within 35 days
2. **Prevents data corruption** - File-based locking ensures only one restore at a time
3. **Provides visibility** - Comprehensive CloudWatch logging and status file tracking
4. **Ensures reliability** - Robust error handling with restore table preservation for manual recovery
5. **Maintains security** - Audit logging with timestamps and detailed operation tracking
6. **Avoids timeout constraints** - Python script can run for hours without Lambda's 15-minute limit

**Key Features:**
- **Command-line execution** - Run manually, via cron, or from CI/CD pipelines
- **Flexible configuration** - Environment variables and command-line arguments
- **Dry-run mode** - Validate restore without executing
- **Comprehensive logging** - CloudWatch integration with console output
- **Stale lock detection** - Automatic cleanup of abandoned locks
- **Graceful error handling** - Preserve restore tables on failure for manual recovery
