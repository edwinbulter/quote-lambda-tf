package handlers

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"strconv"

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
	userLikeService     *service.UserLikeService
	logger              *log.Logger
}

// NewQuoteHandler creates a new quote handler
func NewQuoteHandler(sqliteRepo *storage.SQLiteRepository, s3Storage *storage.S3Storage, zenQuotes *services.ZenQuotesService, userProgressService *service.UserProgressService, userLikeService *service.UserLikeService) *QuoteHandler {
	return &QuoteHandler{
		sqliteRepo:          sqliteRepo,
		s3Storage:           s3Storage,
		zenQuotes:           zenQuotes,
		userProgressService: userProgressService,
		userLikeService:     userLikeService,
		logger:              log.New(os.Stdout, "[Handler] ", log.LstdFlags),
	}
}

// SetupRoutes configures the HTTP routes
func (h *QuoteHandler) SetupRoutes(router *mux.Router) {
	h.logger.Printf("Setting up quote handler routes...")

	// Public quote route (for unauthenticated users - returns random quotes)
	h.logger.Printf("Registering public quote route: /api/v1/quote/public")
	router.HandleFunc("/api/v1/quote/public", h.GetRandomQuoteHandler).Methods("GET")

	// Authenticated quote routes
	router.HandleFunc("/api/v1/quote/viewed", h.GetViewedQuotesHandler).Methods("GET")
	router.HandleFunc("/api/v1/quote/progress", h.GetProgressHandler).Methods("GET")
	router.HandleFunc("/api/v1/quote/{id}/like", h.LikeQuoteHandler).Methods("POST")
	router.HandleFunc("/api/v1/quote/{id}/unlike", h.UnlikeQuoteHandler).Methods("DELETE")
	router.HandleFunc("/api/v1/quote/liked", h.GetLikedQuotesHandler).Methods("GET")
	// Note: reorder route is handled in server.go protected subrouter

	// Other routes
	router.HandleFunc("/api/v1/quote", h.GetUniqueQuoteHandler).Methods("POST")
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

// HealthHandler handles health check requests
func (h *QuoteHandler) HealthHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(map[string]string{"status": "healthy"})
}

// GetViewedQuotesHandler handles GET /api/v1/quote/viewed requests
// Returns the user's quote viewing history
func (h *QuoteHandler) GetViewedQuotesHandler(w http.ResponseWriter, r *http.Request) {
	h.logger.Printf("Received request for viewed quotes history")

	// Get user ID from context (set by JWT middleware)
	userIDValue := r.Context().Value(middleware.UserIDKey)
	if userIDValue == nil {
		h.logger.Printf("User ID not found in context")
		http.Error(w, "User not authenticated", http.StatusUnauthorized)
		return
	}

	// Convert user ID to int
	var userID int
	switch v := userIDValue.(type) {
	case int:
		userID = v
	case float64:
		userID = int(v)
	case string:
		parsedID, err := strconv.Atoi(v)
		if err != nil {
			h.logger.Printf("Failed to convert user ID string to int: %v", err)
			http.Error(w, "Invalid user ID", http.StatusInternalServerError)
			return
		}
		userID = parsedID
	default:
		h.logger.Printf("User ID has unexpected type: %T", userIDValue)
		http.Error(w, "Invalid user ID", http.StatusInternalServerError)
		return
	}

	h.logger.Printf("Getting viewed quotes for user ID: %d", userID)

	// Get user progress to find the last quote ID
	progress, err := h.userProgressService.GetUserProgress(userID)
	if err != nil {
		h.logger.Printf("Failed to get user progress: %v", err)
		http.Error(w, "Failed to get user progress", http.StatusInternalServerError)
		return
	}

	if progress == nil || progress.LastQuoteID == 0 {
		// User hasn't viewed any quotes yet - return empty array
		h.logger.Printf("User %d hasn't viewed any quotes yet", userID)
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode([]interface{}{})
		return
	}

	// Get all quotes from 1 to last_quote_id
	viewedQuotes := make([]map[string]interface{}, 0)
	for quoteID := 1; quoteID <= progress.LastQuoteID; quoteID++ {
		quote, err := h.sqliteRepo.GetQuoteByID(quoteID)
		if err != nil {
			h.logger.Printf("Failed to get quote ID %d: %v", quoteID, err)
			continue // Skip this quote and continue
		}

		viewedQuote := map[string]interface{}{
			"id":     quote.ID,
			"text":   quote.Text,
			"author": quote.Author,
		}

		viewedQuotes = append(viewedQuotes, viewedQuote)
	}

	h.logger.Printf("Returning %d viewed quotes for user %d", len(viewedQuotes), userID)

	// Return only the viewed quotes array
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(viewedQuotes)
}

// GetProgressHandler handles GET /api/v1/quote/progress requests
// Returns the user's current progress information
func (h *QuoteHandler) GetProgressHandler(w http.ResponseWriter, r *http.Request) {
	h.logger.Printf("Received request for user progress")

	// Get user ID from context (set by JWT middleware)
	userIDValue := r.Context().Value(middleware.UserIDKey)
	if userIDValue == nil {
		h.logger.Printf("User ID not found in context")
		http.Error(w, "User not authenticated", http.StatusUnauthorized)
		return
	}

	// Convert user ID to int
	var userID int
	switch v := userIDValue.(type) {
	case int:
		userID = v
	case float64:
		userID = int(v)
	case string:
		parsedID, err := strconv.Atoi(v)
		if err != nil {
			h.logger.Printf("Failed to convert user ID string to int: %v", err)
			http.Error(w, "Invalid user ID", http.StatusInternalServerError)
			return
		}
		userID = parsedID
	default:
		h.logger.Printf("User ID has unexpected type: %T", userIDValue)
		http.Error(w, "Invalid user ID", http.StatusInternalServerError)
		return
	}

	h.logger.Printf("Getting progress for user ID: %d", userID)

	// Get user progress
	progress, err := h.userProgressService.GetUserProgress(userID)
	if err != nil {
		h.logger.Printf("Failed to get user progress: %v", err)
		http.Error(w, "Failed to get user progress", http.StatusInternalServerError)
		return
	}

	// Get username from context (set by JWT middleware)
	usernameValue := r.Context().Value(middleware.UsernameKey)
	var username string
	if usernameValue != nil {
		username = fmt.Sprintf("%v", usernameValue)
	}

	// Prepare response in Azure backend format
	response := map[string]interface{}{
		"Username":    username,
		"LastQuoteId": 0,
		"UpdatedAt":   nil,
	}

	if progress != nil {
		response["LastQuoteId"] = progress.LastQuoteID
		response["UpdatedAt"] = progress.UpdatedAt
	}

	h.logger.Printf("Returning progress for user %s: last_quote_id=%d", username, response["LastQuoteId"])

	// Return the progress information
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// LikeQuoteHandler handles POST /api/v1/quote/{id}/like requests
// Increments the like count for a specific quote with user-specific tracking
func (h *QuoteHandler) LikeQuoteHandler(w http.ResponseWriter, r *http.Request) {
	h.logger.Printf("Received request to like quote")

	// Get user ID from context (set by JWT middleware)
	userIDValue := r.Context().Value(middleware.UserIDKey)
	if userIDValue == nil {
		h.logger.Printf("User ID not found in context")
		http.Error(w, "User not authenticated", http.StatusUnauthorized)
		return
	}

	// Convert user ID to int
	var userID int
	switch v := userIDValue.(type) {
	case int:
		userID = v
	case float64:
		userID = int(v)
	case string:
		parsedID, err := strconv.Atoi(v)
		if err != nil {
			h.logger.Printf("Failed to convert user ID string to int: %v", err)
			http.Error(w, "Invalid user ID", http.StatusInternalServerError)
			return
		}
		userID = parsedID
	default:
		h.logger.Printf("User ID has unexpected type: %T", userIDValue)
		http.Error(w, "Invalid user ID", http.StatusInternalServerError)
		return
	}

	// Get quote ID from URL parameters
	vars := mux.Vars(r)
	quoteIDStr := vars["id"]
	if quoteIDStr == "" {
		h.logger.Printf("Quote ID not found in URL")
		http.Error(w, "Quote ID is required", http.StatusBadRequest)
		return
	}

	quoteID, err := strconv.Atoi(quoteIDStr)
	if err != nil {
		h.logger.Printf("Invalid quote ID: %s", quoteIDStr)
		http.Error(w, "Invalid quote ID", http.StatusBadRequest)
		return
	}

	h.logger.Printf("User %d liking quote ID: %d", userID, quoteID)

	// Check if user already liked this quote
	alreadyLiked, err := h.userLikeService.UserLikedQuote(userID, quoteID)
	if err != nil {
		h.logger.Printf("Failed to check if user liked quote ID %d: %v", quoteID, err)
		http.Error(w, "Failed to check like status", http.StatusInternalServerError)
		return
	}

	if alreadyLiked {
		h.logger.Printf("User %d already liked quote ID %d", userID, quoteID)
		http.Error(w, "User has already liked this quote", http.StatusConflict)
		return
	}

	// Add the like using user-specific service
	err = h.userLikeService.LikeQuote(userID, quoteID)
	if err != nil {
		h.logger.Printf("Failed to like quote ID %d: %v", quoteID, err)
		http.Error(w, "Failed to like quote", http.StatusInternalServerError)
		return
	}

	// Get updated quote information
	quote, err := h.sqliteRepo.GetQuoteByID(quoteID)
	if err != nil {
		h.logger.Printf("Failed to get quote ID %d: %v", quoteID, err)
		http.Error(w, "Failed to get quote", http.StatusInternalServerError)
		return
	}

	if quote == nil {
		h.logger.Printf("Quote ID %d not found", quoteID)
		http.Error(w, "Quote not found", http.StatusNotFound)
		return
	}

	// Get total likes count for this quote
	likesCount, err := h.userLikeService.GetQuoteLikesCount(quoteID)
	if err != nil {
		h.logger.Printf("Failed to get likes count for quote ID %d: %v", quoteID, err)
		http.Error(w, "Failed to get likes count", http.StatusInternalServerError)
		return
	}

	h.logger.Printf("Successfully liked quote ID %d, total likes: %d", quoteID, likesCount)

	// Return response in Azure backend format
	response := map[string]interface{}{
		"Id":        quote.ID,
		"LikeCount": likesCount, // Use user-specific like count
		"Text":      quote.Text,
		"Author":    quote.Author,
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(response)
}

// UnlikeQuoteHandler handles DELETE /api/v1/quote/{id}/unlike requests
// Removes a user's like from a specific quote
func (h *QuoteHandler) UnlikeQuoteHandler(w http.ResponseWriter, r *http.Request) {
	h.logger.Printf("Received request to unlike quote")

	// Get user ID from context (set by JWT middleware)
	userIDValue := r.Context().Value(middleware.UserIDKey)
	if userIDValue == nil {
		h.logger.Printf("User ID not found in context")
		http.Error(w, "User not authenticated", http.StatusUnauthorized)
		return
	}

	// Convert user ID to int
	var userID int
	switch v := userIDValue.(type) {
	case int:
		userID = v
	case float64:
		userID = int(v)
	case string:
		parsedID, err := strconv.Atoi(v)
		if err != nil {
			h.logger.Printf("Failed to convert user ID string to int: %v", err)
			http.Error(w, "Invalid user ID", http.StatusInternalServerError)
			return
		}
		userID = parsedID
	default:
		h.logger.Printf("User ID has unexpected type: %T", userIDValue)
		http.Error(w, "Invalid user ID", http.StatusInternalServerError)
		return
	}

	// Get quote ID from URL parameters
	vars := mux.Vars(r)
	quoteIDStr := vars["id"]
	if quoteIDStr == "" {
		h.logger.Printf("Quote ID not found in URL")
		http.Error(w, "Quote ID is required", http.StatusBadRequest)
		return
	}

	quoteID, err := strconv.Atoi(quoteIDStr)
	if err != nil {
		h.logger.Printf("Invalid quote ID: %s", quoteIDStr)
		http.Error(w, "Invalid quote ID", http.StatusBadRequest)
		return
	}

	h.logger.Printf("User %d unliking quote ID: %d", userID, quoteID)

	// Check if user has already liked this quote
	alreadyLiked, err := h.userLikeService.UserLikedQuote(userID, quoteID)
	if err != nil {
		h.logger.Printf("Failed to check if user liked quote ID %d: %v", quoteID, err)
		http.Error(w, "Failed to check like status", http.StatusInternalServerError)
		return
	}

	if !alreadyLiked {
		h.logger.Printf("User %d has not liked quote ID %d", userID, quoteID)
		http.Error(w, "User has not liked this quote", http.StatusConflict)
		return
	}

	// Remove the like using user-specific service
	err = h.userLikeService.UnlikeQuote(userID, quoteID)
	if err != nil {
		h.logger.Printf("Failed to unlike quote ID %d: %v", quoteID, err)
		http.Error(w, "Failed to unlike quote", http.StatusInternalServerError)
		return
	}

	// Get updated quote information
	quote, err := h.sqliteRepo.GetQuoteByID(quoteID)
	if err != nil {
		h.logger.Printf("Failed to get quote ID %d: %v", quoteID, err)
		http.Error(w, "Failed to get quote", http.StatusInternalServerError)
		return
	}

	if quote == nil {
		h.logger.Printf("Quote ID %d not found", quoteID)
		http.Error(w, "Quote not found", http.StatusNotFound)
		return
	}

	// Get total likes count for this quote
	likesCount, err := h.userLikeService.GetQuoteLikesCount(quoteID)
	if err != nil {
		h.logger.Printf("Failed to get likes count for quote ID %d: %v", quoteID, err)
		http.Error(w, "Failed to get likes count", http.StatusInternalServerError)
		return
	}

	h.logger.Printf("Successfully unliked quote ID %d, total likes: %d", quoteID, likesCount)

	// Return response in Azure backend format
	response := map[string]interface{}{
		"Id":        quote.ID,
		"LikeCount": likesCount, // Use user-specific like count
		"Text":      quote.Text,
		"Author":    quote.Author,
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(response)
}

// GetLikedQuotesHandler handles GET /api/v1/quote/liked requests
// Returns all quotes that the authenticated user has liked
func (h *QuoteHandler) GetLikedQuotesHandler(w http.ResponseWriter, r *http.Request) {
	h.logger.Printf("Received request for user's liked quotes")

	// Get user ID from context (set by JWT middleware)
	userIDValue := r.Context().Value(middleware.UserIDKey)
	if userIDValue == nil {
		h.logger.Printf("User ID not found in context")
		http.Error(w, "User not authenticated", http.StatusUnauthorized)
		return
	}

	// Convert user ID to int
	var userID int
	switch v := userIDValue.(type) {
	case int:
		userID = v
	case float64:
		userID = int(v)
	case string:
		parsedID, err := strconv.Atoi(v)
		if err != nil {
			h.logger.Printf("Failed to convert user ID string to int: %v", err)
			http.Error(w, "Invalid user ID", http.StatusInternalServerError)
			return
		}
		userID = parsedID
	default:
		h.logger.Printf("User ID has unexpected type: %T", userIDValue)
		http.Error(w, "Invalid user ID", http.StatusInternalServerError)
		return
	}

	h.logger.Printf("Getting liked quotes for user ID: %d", userID)

	// Get user's liked quotes
	userLikes, err := h.userLikeService.GetUserLikedQuotes(userID)
	if err != nil {
		h.logger.Printf("Failed to get user liked quotes: %v", err)
		http.Error(w, "Failed to get liked quotes", http.StatusInternalServerError)
		return
	}

	// If no liked quotes, return empty array
	if len(userLikes) == 0 {
		h.logger.Printf("User %d has not liked any quotes yet", userID)
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode([]interface{}{})
		return
	}

	// Fetch full quote details for each liked quote
	likedQuotes := make([]map[string]interface{}, 0)
	for _, userLike := range userLikes {
		// Get the full quote details
		quote, err := h.sqliteRepo.GetQuoteByID(userLike.QuoteID)
		if err != nil {
			h.logger.Printf("Failed to get quote ID %d: %v", userLike.QuoteID, err)
			continue // Skip this quote and continue
		}

		if quote == nil {
			h.logger.Printf("Quote ID %d not found, skipping", userLike.QuoteID)
			continue // Skip this quote and continue
		}

		// Get like count for this quote
		likeCount, err := h.userLikeService.GetQuoteLikesCount(userLike.QuoteID)
		if err != nil {
			h.logger.Printf("Failed to get like count for quote ID %d: %v", userLike.QuoteID, err)
			likeCount = 0 // Default to 0 if error
		}

		likedQuote := map[string]interface{}{
			"id":        quote.ID,
			"text":      quote.Text,
			"author":    quote.Author,
			"likeCount": likeCount,
			"likedAt":   userLike.CreatedAt, // When the user liked this quote
		}

		likedQuotes = append(likedQuotes, likedQuote)
	}

	h.logger.Printf("Returning %d liked quotes for user %d", len(likedQuotes), userID)

	// Return the liked quotes as just an array (Azure backend format)
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(likedQuotes)
}

// ReorderQuoteHandler handles PUT /api/v1/quote/{id}/reorder requests
// Reorders a user's liked quote to a new position
func (h *QuoteHandler) ReorderQuoteHandler(w http.ResponseWriter, r *http.Request) {
	h.logger.Printf("Received request to reorder quote")

	// Get user ID from context (set by JWT middleware)
	userIDValue := r.Context().Value(middleware.UserIDKey)
	if userIDValue == nil {
		h.logger.Printf("User ID not found in context")
		http.Error(w, "User not authenticated", http.StatusUnauthorized)
		return
	}

	// Convert user ID to int
	var userID int
	switch v := userIDValue.(type) {
	case int:
		userID = v
	case float64:
		userID = int(v)
	case string:
		parsedID, err := strconv.Atoi(v)
		if err != nil {
			h.logger.Printf("Failed to convert user ID string to int: %v", err)
			http.Error(w, "Invalid user ID", http.StatusInternalServerError)
			return
		}
		userID = parsedID
	default:
		h.logger.Printf("User ID has unexpected type: %T", userIDValue)
		http.Error(w, "Invalid user ID", http.StatusInternalServerError)
		return
	}

	// Get quote ID from URL parameters
	vars := mux.Vars(r)
	quoteIDStr := vars["id"]
	if quoteIDStr == "" {
		h.logger.Printf("Quote ID not found in URL")
		http.Error(w, "Quote ID is required", http.StatusBadRequest)
		return
	}

	quoteID, err := strconv.Atoi(quoteIDStr)
	if err != nil {
		h.logger.Printf("Invalid quote ID: %s", quoteIDStr)
		http.Error(w, "Invalid quote ID", http.StatusBadRequest)
		return
	}

	// Parse request body to get new position
	var requestBody struct {
		NewPosition int `json:"newPosition"`
		Order       int `json:"Order"`
	}

	if err := json.NewDecoder(r.Body).Decode(&requestBody); err != nil {
		h.logger.Printf("Failed to decode request body: %v", err)
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	// Use newPosition if available, otherwise use Order (matching C# implementation)
	newPosition := requestBody.NewPosition
	if newPosition == 0 {
		newPosition = requestBody.Order
	}

	if newPosition <= 0 {
		h.logger.Printf("Invalid position: %d", newPosition)
		http.Error(w, "Position must be a positive integer", http.StatusBadRequest)
		return
	}

	h.logger.Printf("User %d reordering quote ID %d to position %d", userID, quoteID, newPosition)

	// Perform the reorder operation
	err = h.userLikeService.ReorderLikedQuote(userID, quoteID, newPosition)
	if err != nil {
		h.logger.Printf("Failed to reorder quote ID %d: %v", quoteID, err)
		if err.Error() == "quote not found in user's likes" {
			http.Error(w, "Quote not found in user's likes", http.StatusNotFound)
		} else {
			http.Error(w, "Failed to reorder quote", http.StatusInternalServerError)
		}
		return
	}

	h.logger.Printf("Successfully reordered quote ID %d to position %d for user %d", quoteID, newPosition, userID)

	// Return No Content status (matching C# implementation)
	w.WriteHeader(http.StatusNoContent)
}
