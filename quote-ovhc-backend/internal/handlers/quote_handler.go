package handlers

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"

	"quote-ovhc-backend/internal/middleware"
	"quote-ovhc-backend/internal/models"
	"quote-ovhc-backend/internal/service"
	"quote-ovhc-backend/internal/services"
	"quote-ovhc-backend/internal/storage"

	"github.com/gorilla/mux"
)

// QuoteHandler handles HTTP requests for quotes
type QuoteHandler struct {
	sqliteRepo          *storage.SQLiteRepository
	s3Storage           *storage.S3Storage
	zenQuotes           *services.ZenQuotesService
	userProgressService *service.UserProgressService
	logger              *log.Logger
}

// NewQuoteHandler creates a new quote handler
func NewQuoteHandler(sqliteRepo *storage.SQLiteRepository, s3Storage *storage.S3Storage, zenQuotes *services.ZenQuotesService, userProgressService *service.UserProgressService) *QuoteHandler {
	return &QuoteHandler{
		sqliteRepo:          sqliteRepo,
		s3Storage:           s3Storage,
		zenQuotes:           zenQuotes,
		userProgressService: userProgressService,
		logger:              log.New(os.Stdout, "[Handler] ", log.LstdFlags),
	}
}

// SetupRoutes configures the HTTP routes
func (h *QuoteHandler) SetupRoutes(router *mux.Router) {
	h.logger.Printf("Setting up quote handler routes...")

	// Public quote route (for unauthenticated users - returns random quotes)
	h.logger.Printf("Registering public quote route: /api/v1/quote/public")
	router.HandleFunc("/api/v1/quote/public", h.GetRandomQuoteHandler).Methods("GET")

	// Other routes
	router.HandleFunc("/api/v1/quote", h.GetUniqueQuoteHandler).Methods("POST")
	router.HandleFunc("/debug/quotes", h.DebugQuotesHandler).Methods("GET")
	router.HandleFunc("/debug/sql", h.DebugSQLHandler).Methods("GET")
	router.HandleFunc("/health", h.HealthHandler).Methods("GET")

	h.logger.Printf("Quote handler routes setup completed")
}

// GetRandomQuoteHandler handles GET /quote requests
func (h *QuoteHandler) GetRandomQuoteHandler(w http.ResponseWriter, r *http.Request) {
	h.logger.Printf("Received request for quote")

	// Check if user is authenticated
	userIDValue := r.Context().Value(middleware.UserIDKey)
	if userIDValue != nil {
		// User is authenticated, get next quote based on progress
		userID, ok := userIDValue.(int)
		if !ok {
			h.logger.Printf("Invalid user ID in context")
			http.Error(w, "Invalid user context", http.StatusInternalServerError)
			return
		}

		h.logger.Printf("Authenticated user %d requesting quote", userID)

		// Get next quote ID for this user
		nextQuoteID, err := h.userProgressService.GetNextQuoteID(userID)
		if err != nil {
			h.logger.Printf("Error getting next quote ID for user %d: %v", userID, err)
			http.Error(w, "Failed to get user progress", http.StatusInternalServerError)
			return
		}

		h.logger.Printf("Next quote ID for user %d: %d", userID, nextQuoteID)

		// Try to get specific quote by ID
		quote, err := h.sqliteRepo.GetQuoteByID(nextQuoteID)
		if err != nil {
			h.logger.Printf("Error getting quote %d: %v", nextQuoteID, err)
			http.Error(w, "Failed to get quote", http.StatusInternalServerError)
			return
		}

		if quote == nil {
			h.logger.Printf("Quote %d not found, falling back to random quote", nextQuoteID)
			// Fall back to random quote if specific quote doesn't exist
			quote, err = h.sqliteRepo.GetRandomQuote()
			if err != nil {
				h.logger.Printf("Error getting random quote: %v", err)
				http.Error(w, "Failed to get quote", http.StatusInternalServerError)
				return
			}
			if quote == nil {
				h.logger.Printf("No quotes available")
				http.Error(w, "No quotes available", http.StatusNotFound)
				return
			}
		}

		// Update user progress
		err = h.userProgressService.UpdateUserProgress(userID, quote.ID)
		if err != nil {
			h.logger.Printf("Warning: Failed to update user progress for user %d: %v", userID, err)
			// Don't fail the request, just log the error
		}

		h.logger.Printf("Returning quote %d for authenticated user %d: %s by %s", quote.ID, userID, quote.Text, quote.Author)

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)

		if err := json.NewEncoder(w).Encode(quote); err != nil {
			h.logger.Printf("Failed to encode quote: %v", err)
			http.Error(w, "Failed to encode response", http.StatusInternalServerError)
			return
		}
	} else {
		// User is not authenticated, return random quote
		h.logger.Printf("Unauthenticated user requesting random quote")

		quote, err := h.sqliteRepo.GetRandomQuote()
		if err != nil {
			h.logger.Printf("Error getting random quote: %v", err)
			http.Error(w, "Failed to get quote", http.StatusInternalServerError)
			return
		}
		if quote == nil {
			h.logger.Printf("No quotes available")
			http.Error(w, "No quotes available", http.StatusNotFound)
			return
		}

		h.logger.Printf("Returning random quote %d: %s by %s", quote.ID, quote.Text, quote.Author)

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)

		if err := json.NewEncoder(w).Encode(quote); err != nil {
			h.logger.Printf("Failed to encode quote: %v", err)
			http.Error(w, "Failed to encode response", http.StatusInternalServerError)
			return
		}
	}
}

// GetUniqueQuoteHandler handles POST /quote requests
func (h *QuoteHandler) GetUniqueQuoteHandler(w http.ResponseWriter, r *http.Request) {
	h.logger.Printf("Received request for unique quote")

	// Parse request body to get array of IDs to exclude
	var excludeIDs []int
	if err := json.NewDecoder(r.Body).Decode(&excludeIDs); err != nil {
		h.logger.Printf("Failed to decode request body: %v", err)
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	// Convert slice to map for efficient lookup
	excludeMap := make(map[int]bool)
	for _, id := range excludeIDs {
		excludeMap[id] = true
	}

	h.logger.Printf("Getting unique quote excluding %d IDs", len(excludeMap))

	quote, err := h.GetUniqueQuoteWithFallback(excludeMap)
	if err != nil {
		h.logger.Printf("Error getting unique quote: %v", err)
		http.Error(w, "Failed to get quote", http.StatusInternalServerError)
		return
	}
	if quote == nil {
		h.logger.Printf("No quotes available after exclusion")
		http.Error(w, "No quotes available", http.StatusNotFound)
		return
	}

	h.logger.Printf("Returning quote %d: %s by %s", quote.ID, quote.Text, quote.Author)

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)

	if err := json.NewEncoder(w).Encode(quote); err != nil {
		h.logger.Printf("Failed to encode quote: %v", err)
		http.Error(w, "Failed to encode response", http.StatusInternalServerError)
		return
	}
}

// GetUniqueQuoteWithFallback gets a unique quote with API fallback
func (h *QuoteHandler) GetUniqueQuoteWithFallback(excludeIDs map[int]bool) (*models.Quote, error) {
	quote, err := h.sqliteRepo.GetUniqueQuote(excludeIDs)
	if err != nil {
		return nil, fmt.Errorf("failed to get unique quote: %w", err)
	}

	// If no quotes available after exclusion, fetch more from API
	if quote == nil {
		h.logger.Printf("All quotes excluded, fetching more quotes from API")
		if err := h.FetchMoreQuotesFromAPI(); err != nil {
			return nil, fmt.Errorf("failed to add more quotes: %w", err)
		}

		// Try again with the new quotes
		return h.GetUniqueQuoteWithFallback(excludeIDs)
	}

	return quote, nil
}

// FetchMoreQuotesFromAPI fetches more quotes from ZenQuotes API when all existing quotes are excluded
func (h *QuoteHandler) FetchMoreQuotesFromAPI() error {
	h.logger.Printf("All quotes excluded, fetching more quotes from ZenQuotes API")

	quotes, err := h.zenQuotes.GetMultipleQuotes()
	if err != nil {
		return fmt.Errorf("failed to fetch more quotes from API: %w", err)
	}

	// Get next available ID
	nextID, err := h.sqliteRepo.GetNextAvailableID()
	if err != nil {
		return fmt.Errorf("failed to get next available ID: %w", err)
	}

	// Add fetched quotes to database
	addedCount := 0
	for i, quote := range quotes {
		quote.ID = nextID + i
		if err := h.sqliteRepo.AddQuote(quote); err != nil {
			h.logger.Printf("Failed to add fetched quote %d: %v", quote.ID, err)
			continue
		}
		addedCount++
	}

	h.logger.Printf("Successfully added %d more quotes from ZenQuotes API (IDs %d-%d)", addedCount, nextID, nextID+addedCount-1)

	// Save the new quotes to S3
	if err := h.SaveToS3(); err != nil {
		h.logger.Printf("Warning: Failed to save new quotes to S3: %v", err)
	}

	return nil
}

// SaveToS3 saves the current quotes to S3 (both database file and JSON backup)
func (h *QuoteHandler) SaveToS3() error {
	// Upload the SQLite database file
	if err := h.s3Storage.UploadDatabase(); err != nil {
		h.logger.Printf("Warning: Failed to upload database to S3: %v", err)
		// Continue with JSON backup even if database upload fails
	}

	// Also save JSON backup for compatibility
	quotes, err := h.sqliteRepo.GetAllQuotes()
	if err != nil {
		return fmt.Errorf("failed to get quotes for S3 backup: %w", err)
	}

	if err := h.s3Storage.SaveJSONBackup(quotes); err != nil {
		return fmt.Errorf("failed to save JSON backup to S3: %w", err)
	}

	h.logger.Println("Successfully saved both database file and JSON backup to S3")
	return nil
}

// DebugQuotesHandler handles GET /debug/quotes requests - shows all quotes in memory
func (h *QuoteHandler) DebugQuotesHandler(w http.ResponseWriter, r *http.Request) {
	h.logger.Printf("Received debug request for all quotes")

	quotes, err := h.sqliteRepo.GetAllQuotes()
	if err != nil {
		h.logger.Printf("Failed to get all quotes: %v", err)
		http.Error(w, "Failed to retrieve quotes", http.StatusInternalServerError)
		return
	}

	h.logger.Printf("Returning %d quotes", len(quotes))

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)

	if err := json.NewEncoder(w).Encode(quotes); err != nil {
		h.logger.Printf("Failed to encode quotes: %v", err)
		http.Error(w, "Failed to encode response", http.StatusInternalServerError)
		return
	}
}

// DebugSQLHandler handles GET /debug/sql requests - allows SQL queries
func (h *QuoteHandler) DebugSQLHandler(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query().Get("q")
	if query == "" {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]string{
			"message": "Use ?q=SELECT * FROM quotes to query the database",
			"example": "/debug/sql?q=SELECT * FROM quotes ORDER BY id LIMIT 5",
		})
		return
	}

	h.logger.Printf("Executing SQL query: %s", query)

	rows, err := h.sqliteRepo.ExecuteQuery(query)
	if err != nil {
		h.logger.Printf("SQL query failed: %v", err)
		http.Error(w, fmt.Sprintf("Query failed: %v", err), http.StatusBadRequest)
		return
	}
	defer rows.Close()

	// Get column names
	columns, err := rows.Columns()
	if err != nil {
		http.Error(w, "Failed to get columns", http.StatusInternalServerError)
		return
	}

	// Prepare result
	var results []map[string]interface{}
	for rows.Next() {
		// Create slice of interfaces for scanning
		values := make([]interface{}, len(columns))
		valuePtrs := make([]interface{}, len(columns))
		for i := range columns {
			valuePtrs[i] = &values[i]
		}

		// Scan row
		if err := rows.Scan(valuePtrs...); err != nil {
			h.logger.Printf("Failed to scan row: %v", err)
			continue
		}

		// Create map for this row
		row := make(map[string]interface{})
		for i, col := range columns {
			val := values[i]
			if b, ok := val.([]byte); ok {
				row[col] = string(b)
			} else {
				row[col] = val
			}
		}
		results = append(results, row)
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"columns": columns,
		"rows":    results,
		"count":   len(results),
	})
}

// HealthHandler handles health check requests
func (h *QuoteHandler) HealthHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(map[string]string{"status": "healthy"})
}
