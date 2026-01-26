package service

import (
	"fmt"
	"log"
	"math"
	"quote-ovhc-backend/internal/models"
	"quote-ovhc-backend/internal/services"
	"quote-ovhc-backend/internal/storage"
	"strings"
)

// AdminService handles admin-specific operations
type AdminService struct {
	sqliteRepo *storage.SQLiteRepository
	zenQuotes  *services.ZenQuotesService
}

// NewAdminService creates a new admin service
func NewAdminService(sqliteRepo *storage.SQLiteRepository, zenQuotes *services.ZenQuotesService) *AdminService {
	return &AdminService{
		sqliteRepo: sqliteRepo,
		zenQuotes:  zenQuotes,
	}
}

// GetQuotesWithPagination retrieves quotes with pagination and filtering
func (s *AdminService) GetQuotesWithPagination(page, pageSize int, quoteText, author, sortBy, sortOrder string) (*models.QuotePageResponse, error) {
	// Validate and set defaults
	if page < 1 {
		page = 1
	}
	if pageSize < 1 {
		pageSize = 10
	}
	if pageSize > 100 {
		pageSize = 100 // Max page size
	}

	// Get quotes and total count
	quotes, totalCount, err := s.sqliteRepo.GetQuotesWithPagination(page, pageSize, quoteText, author, sortBy, sortOrder)
	if err != nil {
		return nil, fmt.Errorf("failed to get quotes: %w", err)
	}

	// Calculate total pages
	totalPages := int(math.Ceil(float64(totalCount) / float64(pageSize)))

	// Convert quotes to the expected format
	responseQuotes := make([]models.Quote, len(quotes))
	copy(responseQuotes, quotes)

	return &models.QuotePageResponse{
		Quotes:     responseQuotes,
		TotalCount: totalCount,
		Page:       page,
		PageSize:   pageSize,
		TotalPages: totalPages,
	}, nil
}

// FetchQuotes fetches new quotes from external API and adds them to database
func (s *AdminService) FetchQuotes(requestingUsername string) (*models.QuoteAddResponse, error) {
	log.Printf("Fetching and adding new quotes (requested by %s)", requestingUsername)

	// Get current total quotes before fetching
	currentQuotes, err := s.sqliteRepo.GetAllQuotes()
	if err != nil {
		return nil, fmt.Errorf("failed to get current quotes count: %w", err)
	}
	currentTotal := len(currentQuotes)

	// Fetch quotes from ZenQuotes API
	quotes, err := s.zenQuotes.GetMultipleQuotes()
	if err != nil {
		return &models.QuoteAddResponse{
			QuotesAdded: 0,
			TotalQuotes: currentTotal,
			Message:     fmt.Sprintf("Failed to fetch quotes from API: %v", err),
		}, nil
	}

	if len(quotes) == 0 {
		return &models.QuoteAddResponse{
			QuotesAdded: 0,
			TotalQuotes: currentTotal,
			Message:     "No new quotes available from API",
		}, nil
	}

	// Get next available ID
	nextID, err := s.sqliteRepo.GetNextAvailableID()
	if err != nil {
		return &models.QuoteAddResponse{
			QuotesAdded: 0,
			TotalQuotes: currentTotal,
			Message:     fmt.Sprintf("Failed to get next available ID: %v", err),
		}, nil
	}

	// Add fetched quotes to database
	addedCount := 0
	skippedCount := 0
	for i, quote := range quotes {
		// Check if quote already exists
		exists, err := s.sqliteRepo.QuoteExists(quote.Text, quote.Author)
		if err != nil {
			log.Printf("Failed to check if quote exists: %v", err)
			continue
		}

		if exists {
			skippedCount++
			log.Printf("Skipping duplicate quote: \"%s\" - %s", quote.Text, quote.Author)
			continue
		}

		quote.ID = nextID + i
		if err := s.sqliteRepo.AddQuote(quote); err != nil {
			log.Printf("Failed to add fetched quote %d: %v", quote.ID, err)
			continue
		}
		addedCount++
	}

	newTotal := currentTotal + addedCount

	log.Printf("Quote fetch completed: %d added, %d skipped (duplicates), %d total quotes", addedCount, skippedCount, newTotal)

	var message string
	if addedCount > 0 && skippedCount > 0 {
		message = fmt.Sprintf("Successfully added %d new quotes (skipped %d duplicates)", addedCount, skippedCount)
	} else if addedCount > 0 {
		message = fmt.Sprintf("Successfully added %d new quotes", addedCount)
	} else if skippedCount > 0 {
		message = fmt.Sprintf("No new quotes added (all %d were duplicates)", skippedCount)
	} else {
		message = "No new quotes were added"
	}

	return &models.QuoteAddResponse{
		QuotesAdded: addedCount,
		TotalQuotes: newTotal,
		Message:     message,
	}, nil
}

// GetTotalLikes retrieves the total count of likes across all quotes
func (s *AdminService) GetTotalLikes() (int, error) {
	return s.sqliteRepo.GetTotalLikes()
}

// ParseQueryParams parses and validates query parameters
func (s *AdminService) ParseQueryParams(pageStr, pageSizeStr, quoteText, author, sortBy, sortOrder string) (int, int, string, string, string, string, error) {
	// Parse page
	page := 1
	if pageStr != "" {
		if p, err := parseInt(pageStr); err == nil && p > 0 {
			page = p
		}
	}

	// Parse pageSize
	pageSize := 10
	if pageSizeStr != "" {
		if ps, err := parseInt(pageSizeStr); err == nil && ps > 0 {
			if ps > 100 {
				ps = 100 // Max page size
			}
			pageSize = ps
		}
	}

	// Normalize filter parameters
	quoteText = strings.TrimSpace(quoteText)
	author = strings.TrimSpace(author)
	sortBy = strings.TrimSpace(sortBy)
	sortOrder = strings.TrimSpace(sortOrder)

	// Validate sortBy
	validSortFields := map[string]bool{"id": true, "text": true, "author": true, "likecount": true, "created_at": true}
	if sortBy != "" && !validSortFields[strings.ToLower(sortBy)] {
		sortBy = "id" // Default
	}

	// Validate sortOrder
	sortOrder = strings.ToLower(sortOrder)
	if sortOrder != "asc" && sortOrder != "desc" {
		sortOrder = "asc" // Default
	}

	return page, pageSize, quoteText, author, sortBy, sortOrder, nil
}

// parseInt is a helper function to parse integers
func parseInt(s string) (int, error) {
	var result int
	_, err := fmt.Sscanf(s, "%d", &result)
	return result, err
}
