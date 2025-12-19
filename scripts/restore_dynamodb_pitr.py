#!/usr/bin/env python3
"""
DynamoDB PITR (Point-In-Time Recovery) Restore Script

This script restores all three DynamoDB tables to a specified point in time
within the last 35 days. It handles:
- Concurrent execution prevention via file-based locking
- PITR restore initiation for all tables
- Status polling until restore completes
- Data verification (item count matching)
- Atomic data swap from restore tables to production tables
- Cleanup of temporary restore tables
- Comprehensive logging and status tracking
"""

import argparse
import json
import logging
import os
import sys
import time
from datetime import datetime, timedelta
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import boto3
from botocore.exceptions import ClientError
from dateutil import parser as date_parser


class RestoreLock:
    """File-based lock to prevent concurrent restore operations."""

    def __init__(self, lock_file_path: str = "/tmp/dynamodb_restore.lock"):
        self.lock_file = Path(lock_file_path)
        self.lock_acquired = False

    def acquire(self, timeout_seconds: int = 5) -> bool:
        """
        Acquire lock with timeout.
        Returns True if acquired, False if already locked.
        """
        start_time = time.time()

        while time.time() - start_time < timeout_seconds:
            try:
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
                if self._is_stale():
                    logger.warning("Removing stale lock file")
                    try:
                        self.lock_file.unlink()
                    except Exception:
                        pass
                    continue
                time.sleep(0.5)

        logger.error("Failed to acquire lock - restore already in progress")
        return False

    def release(self) -> None:
        """Release the lock."""
        if self.lock_acquired and self.lock_file.exists():
            try:
                self.lock_file.unlink()
                self.lock_acquired = False
                logger.info(f"Lock released: {self.lock_file}")
            except Exception as e:
                logger.warning(f"Failed to release lock: {e}")

    def _is_stale(self, stale_after_seconds: int = 3600) -> bool:
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


class DynamoDBPITRRestore:
    """Main class for DynamoDB PITR restore operations."""

    def __init__(
        self,
        environment: str,
        region: str = "eu-central-1",
        timeout_minutes: int = 30,
        lock_file: str = "/tmp/dynamodb_restore.lock",
        dry_run: bool = False,
        verbose: bool = False
    ):
        self.environment = environment
        self.region = region
        self.timeout_minutes = timeout_minutes
        self.lock_file = lock_file
        self.dry_run = dry_run
        self.verbose = verbose

        self.dynamodb = boto3.client('dynamodb', region_name=region)
        self.restore_id = f"restore-{datetime.now().strftime('%Y-%m-%dT%H-%M-%SZ')}"
        self.status_file = f"restore_status_{self.restore_id}.json"

        self._configure_table_names()

    def _configure_table_names(self) -> None:
        """Configure table names based on environment."""
        base_names = {
            'quotes': 'quote-lambda-tf-quotes',
            'user_likes': 'quote-lambda-tf-user-likes',
            'user_views': 'quote-lambda-tf-user-views'
        }

        self.tables = {}
        for key, base_name in base_names.items():
            if self.environment == 'dev':
                self.tables[key] = f"{base_name}-dev"
            else:
                self.tables[key] = base_name

        logger.info(f"Configured tables for {self.environment} environment:")
        for key, name in self.tables.items():
            logger.info(f"  {key}: {name}")

    def run(self, restore_point_in_time: str) -> bool:
        """Execute the complete restore operation."""
        try:
            logger.info(f"Starting DynamoDB PITR restore (ID: {self.restore_id})")
            logger.info(f"Restore point: {restore_point_in_time}")

            if self.dry_run:
                logger.info("DRY RUN MODE - No changes will be made")

            self._update_status("INITIALIZING", restore_point_in_time)

            # Validate restore point
            if not self._validate_restore_point(restore_point_in_time):
                logger.error("Restore point validation failed")
                self._update_status("FAILED", restore_point_in_time, "Invalid restore point")
                return False

            # Acquire lock
            with RestoreLock(self.lock_file) as lock:
                # Initiate PITR restores
                if not self._initiate_pitr_restores(restore_point_in_time):
                    logger.error("Failed to initiate PITR restores")
                    self._update_status("FAILED", restore_point_in_time, "Failed to initiate PITR restores")
                    return False

                # Poll for restore completion
                restore_tables = self._get_restore_table_names()
                if not self._poll_restore_completion(restore_tables):
                    logger.error("Restore operation timed out")
                    self._update_status("FAILED", restore_point_in_time, "Restore operation timed out")
                    return False

                # Verify item counts
                if not self._verify_item_counts(restore_tables):
                    logger.error("Item count verification failed")
                    self._update_status("FAILED", restore_point_in_time, "Item count mismatch")
                    return False

                # Swap data
                if not self._swap_data(restore_tables):
                    logger.error("Data swap failed")
                    self._update_status("FAILED", restore_point_in_time, "Data swap failed")
                    return False

                # Delete restore tables
                if not self._delete_restore_tables(restore_tables):
                    logger.warning("Some restore tables could not be deleted")

            logger.info("Restore completed successfully")
            self._update_status("COMPLETED", restore_point_in_time)
            return True

        except Exception as e:
            logger.error(f"Unexpected error during restore: {e}", exc_info=True)
            self._update_status("FAILED", restore_point_in_time, str(e))
            return False

    def _validate_restore_point(self, restore_point_in_time: str) -> bool:
        """Validate that restore point is within 35 days."""
        try:
            restore_dt = date_parser.isoparse(restore_point_in_time)
            now = datetime.now(restore_dt.tzinfo) if restore_dt.tzinfo else datetime.now()
            age = now - restore_dt
            max_age = timedelta(days=35)

            if age > max_age:
                logger.error(f"Restore point is {age.days} days old, max is 35 days")
                return False

            if age < timedelta(0):
                logger.error("Restore point is in the future")
                return False

            logger.info(f"Restore point is {age.days} days old (within 35-day limit)")
            return True
        except Exception as e:
            logger.error(f"Failed to parse restore point: {e}")
            return False

    def _initiate_pitr_restores(self, restore_point_in_time: str) -> bool:
        """Initiate PITR restore for all tables."""
        try:
            restore_tables = self._get_restore_table_names()

            for table_key, original_table in self.tables.items():
                restore_table = restore_tables[table_key]

                if self.dry_run:
                    logger.info(f"[DRY RUN] Would restore {original_table} to {restore_table}")
                    continue

                logger.info(f"Initiating PITR restore for {original_table} -> {restore_table}")

                self.dynamodb.restore_table_to_point_in_time(
                    SourceTableName=original_table,
                    TargetTableName=restore_table,
                    RestoreDateTime=date_parser.isoparse(restore_point_in_time),
                    BillingModeOverride='PAY_PER_REQUEST'
                )

                logger.info(f"PITR restore initiated for {restore_table}")

            return True
        except ClientError as e:
            logger.error(f"Failed to initiate PITR restore: {e}")
            return False

    def _get_restore_table_names(self) -> Dict[str, str]:
        """Get restore table names with timestamp suffix."""
        timestamp = datetime.now().strftime('%Y%m%d%H%M%S')
        return {
            'quotes': f"{self.tables['quotes']}-restore-{timestamp}",
            'user_likes': f"{self.tables['user_likes']}-restore-{timestamp}",
            'user_views': f"{self.tables['user_views']}-restore-{timestamp}"
        }

    def _poll_restore_completion(self, restore_tables: Dict[str, str]) -> bool:
        """Poll restore tables until they reach ACTIVE status."""
        try:
            start_time = time.time()
            timeout_seconds = self.timeout_minutes * 60
            poll_interval = 10  # seconds

            while time.time() - start_time < timeout_seconds:
                all_active = True

                for table_key, restore_table in restore_tables.items():
                    if self.dry_run:
                        continue

                    try:
                        response = self.dynamodb.describe_table(TableName=restore_table)
                        status = response['Table']['TableStatus']

                        if status != 'ACTIVE':
                            all_active = False
                            elapsed = int(time.time() - start_time)
                            logger.info(f"{restore_table} status: {status} ({elapsed}s)")
                        else:
                            elapsed = int(time.time() - start_time)
                            logger.info(f"{restore_table} is now ACTIVE ({elapsed}s)")

                    except ClientError as e:
                        if e.response['Error']['Code'] == 'ResourceNotFoundException':
                            all_active = False
                        else:
                            raise

                if all_active:
                    logger.info("All restore tables are ACTIVE")
                    return True

                time.sleep(poll_interval)

            logger.error(f"Restore operation timed out after {self.timeout_minutes} minutes")
            return False

        except Exception as e:
            logger.error(f"Error polling restore status: {e}")
            return False

    def _verify_item_counts(self, restore_tables: Dict[str, str]) -> bool:
        """Verify that restore tables have same item count as original tables."""
        try:
            logger.info("Verifying item counts...")

            for table_key, original_table in self.tables.items():
                restore_table = restore_tables[table_key]

                if self.dry_run:
                    logger.info(f"[DRY RUN] Would verify counts for {original_table}")
                    continue

                original_count = self._count_items(original_table)
                restore_count = self._count_items(restore_table)

                if original_count != restore_count:
                    logger.error(
                        f"Item count mismatch for {table_key}: "
                        f"original={original_count}, restore={restore_count}"
                    )
                    return False

                logger.info(
                    f"{table_key}: original={original_count}, restore={restore_count} ✓"
                )

            return True
        except Exception as e:
            logger.error(f"Error verifying item counts: {e}")
            return False

    def _count_items(self, table_name: str) -> int:
        """Count items in a table using Scan with Select=COUNT."""
        try:
            response = self.dynamodb.scan(
                TableName=table_name,
                Select='COUNT'
            )
            return response['Count']
        except ClientError as e:
            logger.error(f"Failed to count items in {table_name}: {e}")
            raise

    def _swap_data(self, restore_tables: Dict[str, str]) -> bool:
        """Swap data from restore tables to production tables."""
        try:
            logger.info("Swapping data from restore tables to production tables...")

            for table_key, original_table in self.tables.items():
                restore_table = restore_tables[table_key]

                if self.dry_run:
                    logger.info(f"[DRY RUN] Would swap data for {original_table}")
                    continue

                if not self._swap_table_data(original_table, restore_table):
                    logger.error(f"Failed to swap data for {table_key}")
                    return False

            return True
        except Exception as e:
            logger.error(f"Error swapping data: {e}")
            return False

    def _swap_table_data(self, original_table: str, restore_table: str) -> bool:
        """Swap data for a single table."""
        try:
            logger.info(f"Swapping data for {original_table}")

            # Clear original table
            logger.info(f"Clearing {original_table}...")
            self._clear_table(original_table)

            # Copy data from restore table to original table
            logger.info(f"Copying data from {restore_table} to {original_table}...")
            items_written = self._copy_table_data(restore_table, original_table)

            logger.info(f"Swapped data for {original_table} ({items_written} items)")
            return True

        except Exception as e:
            logger.error(f"Error swapping table data: {e}")
            return False

    def _clear_table(self, table_name: str) -> None:
        """Delete all items from a table."""
        try:
            # Get table schema to identify keys
            response = self.dynamodb.describe_table(TableName=table_name)
            key_schema = response['Table']['KeySchema']
            key_names = [key['AttributeName'] for key in key_schema]

            # Scan and delete all items
            scan_response = self.dynamodb.scan(TableName=table_name)
            items = scan_response.get('Items', [])

            with self.dynamodb.batch_write_item() as batch:
                for item in items:
                    key = {key_name: item[key_name] for key_name in key_names}
                    batch.delete_item(TableName=table_name, Key=key)

            # Handle pagination
            while 'LastEvaluatedKey' in scan_response:
                scan_response = self.dynamodb.scan(
                    TableName=table_name,
                    ExclusiveStartKey=scan_response['LastEvaluatedKey']
                )
                items = scan_response.get('Items', [])

                with self.dynamodb.batch_write_item() as batch:
                    for item in items:
                        key = {key_name: item[key_name] for key_name in key_names}
                        batch.delete_item(TableName=table_name, Key=key)

            logger.info(f"Cleared {table_name}")

        except Exception as e:
            logger.error(f"Error clearing table {table_name}: {e}")
            raise

    def _copy_table_data(self, source_table: str, target_table: str) -> int:
        """Copy all data from source table to target table."""
        try:
            items_written = 0
            scan_response = self.dynamodb.scan(TableName=source_table)

            with self.dynamodb.batch_write_item() as batch:
                for item in scan_response.get('Items', []):
                    batch.put_item(TableName=target_table, Item=item)
                    items_written += 1

            # Handle pagination
            while 'LastEvaluatedKey' in scan_response:
                scan_response = self.dynamodb.scan(
                    TableName=source_table,
                    ExclusiveStartKey=scan_response['LastEvaluatedKey']
                )

                with self.dynamodb.batch_write_item() as batch:
                    for item in scan_response.get('Items', []):
                        batch.put_item(TableName=target_table, Item=item)
                        items_written += 1

            return items_written

        except Exception as e:
            logger.error(f"Error copying table data: {e}")
            raise

    def _delete_restore_tables(self, restore_tables: Dict[str, str]) -> bool:
        """Delete all restore tables."""
        try:
            logger.info("Deleting restore tables...")
            all_deleted = True

            for table_key, restore_table in restore_tables.items():
                if self.dry_run:
                    logger.info(f"[DRY RUN] Would delete {restore_table}")
                    continue

                try:
                    logger.info(f"Deleting restore table: {restore_table}")
                    self.dynamodb.delete_table(TableName=restore_table)
                    logger.info(f"Deleted {restore_table}")
                except ClientError as e:
                    logger.warning(f"Failed to delete {restore_table}: {e}")
                    all_deleted = False

            return all_deleted

        except Exception as e:
            logger.error(f"Error deleting restore tables: {e}")
            return False

    def _update_status(
        self,
        status: str,
        restore_point_in_time: str,
        error_message: Optional[str] = None
    ) -> None:
        """Update status file."""
        try:
            status_data = {
                "restore_id": self.restore_id,
                "status": status,
                "restore_point_in_time": restore_point_in_time,
                "started_at": datetime.now().isoformat(),
                "environment": self.environment,
                "tables": self.tables
            }

            if error_message:
                status_data["error_message"] = error_message

            with open(self.status_file, 'w') as f:
                json.dump(status_data, f, indent=2)

            logger.info(f"Status updated: {status}")

        except Exception as e:
            logger.warning(f"Failed to update status file: {e}")


def setup_logging(verbose: bool = False) -> None:
    """Configure logging with console and file handlers."""
    log_file = f"restore_dynamodb_{datetime.now().strftime('%Y%m%d_%H%M%S')}.log"

    logger_obj = logging.getLogger('restore_dynamodb')
    logger_obj.setLevel(logging.DEBUG)

    # Console handler (INFO level, or DEBUG if verbose)
    console_handler = logging.StreamHandler()
    console_level = logging.DEBUG if verbose else logging.INFO
    console_handler.setLevel(console_level)
    console_formatter = logging.Formatter('[%(asctime)s] [%(levelname)s] %(message)s')
    console_handler.setFormatter(console_formatter)

    # File handler (DEBUG level for detailed logs)
    file_handler = logging.FileHandler(log_file)
    file_handler.setLevel(logging.DEBUG)
    file_formatter = logging.Formatter('[%(asctime)s] [%(levelname)s] [%(funcName)s] %(message)s')
    file_handler.setFormatter(file_formatter)

    logger_obj.addHandler(console_handler)
    logger_obj.addHandler(file_handler)

    logger_obj.info(f"Logging to file: {log_file}")


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(
        description='DynamoDB PITR Restore Script',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog='''
Examples:
  # Restore dev environment to 1 day ago
  python restore_dynamodb_pitr.py --restore-point 2025-12-19T09:00:00Z --environment dev

  # Dry run (validate without executing)
  python restore_dynamodb_pitr.py --restore-point 2025-12-19T09:00:00Z --environment dev --dry-run

  # Restore with custom timeout
  python restore_dynamodb_pitr.py --restore-point 2025-12-19T09:00:00Z --environment dev --timeout-minutes 45

  # Restore with verbose logging
  python restore_dynamodb_pitr.py --restore-point 2025-12-19T09:00:00Z --environment dev --verbose
        '''
    )

    parser.add_argument(
        '--restore-point',
        required=True,
        help='Restore point in time (ISO 8601 format, e.g., 2025-12-19T09:00:00Z)'
    )

    parser.add_argument(
        '--environment',
        required=True,
        choices=['dev', 'prod'],
        help='Target environment (dev or prod)'
    )

    parser.add_argument(
        '--region',
        default='eu-central-1',
        help='AWS region (default: eu-central-1)'
    )

    parser.add_argument(
        '--timeout-minutes',
        type=int,
        default=30,
        help='Timeout for restore operations in minutes (default: 30)'
    )

    parser.add_argument(
        '--lock-file',
        default='/tmp/dynamodb_restore.lock',
        help='Lock file path (default: /tmp/dynamodb_restore.lock)'
    )

    parser.add_argument(
        '--dry-run',
        action='store_true',
        help='Validate without executing'
    )

    parser.add_argument(
        '--verbose',
        action='store_true',
        help='Enable verbose logging'
    )

    args = parser.parse_args()

    # Setup logging
    setup_logging(verbose=args.verbose)

    try:
        restore = DynamoDBPITRRestore(
            environment=args.environment,
            region=args.region,
            timeout_minutes=args.timeout_minutes,
            lock_file=args.lock_file,
            dry_run=args.dry_run,
            verbose=args.verbose
        )

        success = restore.run(args.restore_point)
        sys.exit(0 if success else 1)

    except Exception as e:
        logger.error(f"Fatal error: {e}", exc_info=True)
        sys.exit(1)


if __name__ == '__main__':
    logger = logging.getLogger('restore_dynamodb')
    main()
