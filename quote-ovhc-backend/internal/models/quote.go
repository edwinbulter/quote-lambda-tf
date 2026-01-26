package models

// Quote represents a quote with metadata
type Quote struct {
	ID     int    `json:"id" db:"id"`
	Text   string `json:"text" db:"text"`
	Author string `json:"author" db:"author"`
}

// ZenQuoteResponse represents the response from ZenQuotes API
type ZenQuoteResponse struct {
	Q string `json:"q"` // Quote text
	A string `json:"a"` // Author
	H string `json:"h"` // HTML (not used)
}
