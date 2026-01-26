package models

import "time"

type UserProgress struct {
	ID          int       `json:"id" db:"id"`
	UserID      int       `json:"user_id" db:"user_id"`
	LastQuoteID int       `json:"last_quote_id" db:"last_quote_id"`
	CreatedAt   time.Time `json:"created_at" db:"created_at"`
	UpdatedAt   time.Time `json:"updated_at" db:"updated_at"`
}

// TableName returns the database table name for UserProgress
func (UserProgress) TableName() string {
	return "user_progress"
}
