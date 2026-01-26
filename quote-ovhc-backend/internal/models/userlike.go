package models

import "time"

type UserLike struct {
	ID        int       `json:"id" db:"id"`
	UserID    int       `json:"user_id" db:"user_id"`
	QuoteID   int       `json:"quote_id" db:"quote_id"`
	Order     int       `json:"order" db:"order_index"`
	CreatedAt time.Time `json:"created_at" db:"created_at"`
}
