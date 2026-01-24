package models

import "time"

// Quote represents a quote with metadata
type Quote struct {
	ID        int       `json:"id" db:"id"`
	Text      string    `json:"text" db:"text"`
	Author    string    `json:"author" db:"author"`
	LikeCount int       `json:"likeCount" db:"like_count"`
	CreatedAt time.Time `json:"createdAt" db:"created_at"`
	Source    string    `json:"source" db:"source"`
}

// ZenQuoteResponse represents the response from ZenQuotes API
type ZenQuoteResponse struct {
	Q string `json:"q"` // Quote text
	A string `json:"a"` // Author
	H string `json:"h"` // HTML (not used)
}
