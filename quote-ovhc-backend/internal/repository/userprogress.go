package repository

import (
	"database/sql"
	"quote-ovhc-backend/internal/models"
	"time"
)

type UserProgressRepository struct {
	db *sql.DB
}

func NewUserProgressRepository(db *sql.DB) *UserProgressRepository {
	return &UserProgressRepository{db: db}
}

// GetUserProgress gets the user's progress by user ID
func (r *UserProgressRepository) GetUserProgress(userID int) (*models.UserProgress, error) {
	query := `
		SELECT id, user_id, last_quote_id, created_at, updated_at
		FROM user_progress
		WHERE user_id = ?
	`

	var progress models.UserProgress
	err := r.db.QueryRow(query, userID).Scan(
		&progress.ID,
		&progress.UserID,
		&progress.LastQuoteID,
		&progress.CreatedAt,
		&progress.UpdatedAt,
	)

	if err != nil {
		if err == sql.ErrNoRows {
			// Return nil if no progress exists for this user
			return nil, nil
		}
		return nil, err
	}

	return &progress, nil
}

// CreateOrUpdateUserProgress creates new progress or updates existing one
func (r *UserProgressRepository) CreateOrUpdateUserProgress(userID int, lastQuoteID int) error {
	// First try to update existing record
	updateQuery := `
		UPDATE user_progress
		SET last_quote_id = ?, updated_at = ?
		WHERE user_id = ?
	`

	result, err := r.db.Exec(updateQuery, lastQuoteID, time.Now(), userID)
	if err != nil {
		return err
	}

	rowsAffected, err := result.RowsAffected()
	if err != nil {
		return err
	}

	// If no rows were updated, create new record
	if rowsAffected == 0 {
		insertQuery := `
			INSERT INTO user_progress (user_id, last_quote_id, created_at, updated_at)
			VALUES (?, ?, ?, ?)
		`

		_, err = r.db.Exec(insertQuery, userID, lastQuoteID, time.Now(), time.Now())
		if err != nil {
			return err
		}
	}

	return nil
}

// InitializeUserProgress creates initial progress for a new user
func (r *UserProgressRepository) InitializeUserProgress(userID int) error {
	query := `
		INSERT INTO user_progress (user_id, last_quote_id, created_at, updated_at)
		VALUES (?, 0, ?, ?)
	`

	_, err := r.db.Exec(query, userID, time.Now(), time.Now())
	return err
}

// DeleteUserProgress removes user progress when user is deleted
func (r *UserProgressRepository) DeleteUserProgress(userID int) error {
	query := `DELETE FROM user_progress WHERE user_id = ?`
	_, err := r.db.Exec(query, userID)
	return err
}
