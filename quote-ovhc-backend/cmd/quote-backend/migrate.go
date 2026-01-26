package main

import (
	"database/sql"
	"io/ioutil"
	"log"
	"os"
	"path/filepath"
)

// runMigrations executes the database migration files
func runMigrations(db *sql.DB) error {
	log.Printf("Starting database migrations...")

	// Get the directory of the executable
	exePath, err := os.Executable()
	if err != nil {
		return err
	}
	exeDir := filepath.Dir(exePath)

	// List of migration files to run in order
	migrationFiles := []string{
		"001_create_auth_tables.sql",
		"002_update_auth_schema.sql",
		"003_create_userprogress_table.sql",
		"004_cleanup_tables.sql",
		"005_create_user_likes_table.sql",
		"006_add_order_to_user_likes.sql",
	}

	// Try different possible locations for the migration files
	for _, migrationFile := range migrationFiles {
		log.Printf("Processing migration file: %s", migrationFile)

		migrationPaths := []string{
			filepath.Join(exeDir, "migrations", migrationFile),
			"migrations/" + migrationFile,
			"./migrations/" + migrationFile,
		}

		var migration []byte
		var migrationErr error
		var usedPath string

		for _, path := range migrationPaths {
			migration, migrationErr = ioutil.ReadFile(path)
			if migrationErr == nil {
				usedPath = path
				log.Printf("Found migration file at: %s", usedPath)
				break
			}
		}

		if migrationErr != nil {
			log.Printf("Could not find migration file %s. Tried paths: %v", migrationFile, migrationPaths)
			return migrationErr
		}

		log.Printf("Running migration: %s from: %s", migrationFile, usedPath)

		// Execute migration
		_, err = db.Exec(string(migration))
		if err != nil {
			log.Printf("Migration execution failed for %s: %v", migrationFile, err)
			return err
		}

		log.Printf("Migration %s completed successfully", migrationFile)
	}

	// Verify tables were created
	log.Printf("Verifying created tables...")

	var count int
	err = db.QueryRow("SELECT COUNT(*) FROM users").Scan(&count)
	if err != nil {
		log.Printf("Failed to verify users table: %v", err)
		return err
	}

	// Verify user_roles table
	var roleCount int
	err = db.QueryRow("SELECT COUNT(*) FROM user_roles").Scan(&roleCount)
	if err != nil {
		log.Printf("Failed to verify user_roles table: %v", err)
		return err
	}

	// Verify userprogress table
	var progressCount int
	err = db.QueryRow("SELECT COUNT(*) FROM user_progress").Scan(&progressCount)
	if err != nil {
		log.Printf("Failed to verify user_progress table: %v", err)
		return err
	}

	log.Printf("Database migrations completed successfully. Users: %d, User roles: %d, User progress: %d", count, roleCount, progressCount)
	return nil
}
