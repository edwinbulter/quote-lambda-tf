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
	}

	// Try different possible locations for the migration files
	for _, migrationFile := range migrationFiles {
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
	}

	// Verify tables were created
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

	log.Printf("Database migrations completed successfully. Users: %d, User roles: %d", count, roleCount)
	return nil
}
