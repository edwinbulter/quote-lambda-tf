package services

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"time"

	"quote-ovhc-backend/internal/models"
)

// ZenQuotesService handles fetching quotes from ZenQuotes API
type ZenQuotesService struct {
	httpClient *http.Client
	logger     *log.Logger
}

// NewZenQuotesService creates a new ZenQuotes service
func NewZenQuotesService() *ZenQuotesService {
	return &ZenQuotesService{
		httpClient: &http.Client{Timeout: 10 * time.Second},
		logger:     log.New(os.Stdout, "[ZenQuotes] ", log.LstdFlags),
	}
}

// GetMultipleQuotes fetches multiple quotes from ZenQuotes API
func (z *ZenQuotesService) GetMultipleQuotes() ([]models.Quote, error) {
	z.logger.Println("Fetching quotes from ZenQuotes API")

	resp, err := z.httpClient.Get("https://zenquotes.io/api/quotes")
	if err != nil {
		return nil, fmt.Errorf("failed to fetch quotes: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("ZenQuotes API returned status %d", resp.StatusCode)
	}

	var zenQuotes []models.ZenQuoteResponse
	if err := json.NewDecoder(resp.Body).Decode(&zenQuotes); err != nil {
		return nil, fmt.Errorf("failed to decode response: %w", err)
	}

	z.logger.Printf("Fetched %d quotes from ZenQuotes", len(zenQuotes))

	quotes := make([]models.Quote, len(zenQuotes))
	for i, zenQuote := range zenQuotes {
		quotes[i] = models.Quote{
			ID:     0, // Will be set by database
			Text:   zenQuote.Q,
			Author: zenQuote.A,
		}
	}

	return quotes, nil
}
