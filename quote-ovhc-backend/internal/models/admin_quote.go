package models

import "time"

// QuotePageResponse represents the paginated response for quotes
type QuotePageResponse struct {
	Quotes     []Quote `json:"quotes"`
	TotalCount int     `json:"totalCount"`
	Page       int     `json:"page"`
	PageSize   int     `json:"pageSize"`
	TotalPages int     `json:"totalPages"`
}

// QuoteWithLikeCount represents a quote with like count for admin responses
type QuoteWithLikeCount struct {
	ID        int       `json:"id"`
	QuoteText string    `json:"quoteText"`
	Author    string    `json:"author"`
	LikeCount int       `json:"likeCount"`
	CreatedAt time.Time `json:"createdAt"`
}

// QuoteAddResponse represents the response when adding quotes
type QuoteAddResponse struct {
	QuotesAdded int    `json:"quotesAdded"`
	TotalQuotes int    `json:"totalQuotes"`
	Message     string `json:"message"`
}

// UserRoleRequest represents the request to update user role
type UserRoleRequest struct {
	Username string `json:"username"`
	Role     string `json:"role"`
}

// AdminQuoteRequest represents the query parameters for admin quotes endpoint
type AdminQuoteRequest struct {
	Page      int    `json:"page"`
	PageSize  int    `json:"pageSize"`
	QuoteText string `json:"quoteText"`
	Author    string `json:"author"`
	SortBy    string `json:"sortBy"`
	SortOrder string `json:"sortOrder"`
}
