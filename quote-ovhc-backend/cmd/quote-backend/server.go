package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"quote-ovhc-backend/internal/auth"
	"quote-ovhc-backend/internal/handlers"
	"quote-ovhc-backend/internal/middleware"
	"quote-ovhc-backend/internal/service"
	"quote-ovhc-backend/internal/services"
	"quote-ovhc-backend/internal/storage"
	"strings"

	"github.com/gorilla/mux"
)

// Server represents the HTTP server
type Server struct {
	router       *mux.Router
	quoteHandler *handlers.QuoteHandler
	authHandler  *handlers.AuthHandler
	jwtService   *auth.JWTService
}

// NewServer creates a new HTTP server
func NewServer(sqliteRepo *storage.SQLiteRepository, s3Storage *storage.S3Storage, zenQuotes *services.ZenQuotesService, authHandler *handlers.AuthHandler, jwtService *auth.JWTService, userProgressService *service.UserProgressService) *Server {
	quoteHandler := handlers.NewQuoteHandler(sqliteRepo, s3Storage, zenQuotes, userProgressService)

	server := &Server{
		router:       mux.NewRouter(),
		quoteHandler: quoteHandler,
		authHandler:  authHandler,
		jwtService:   jwtService,
	}

	server.setupRoutes()
	return server
}

// setupRoutes configures the HTTP routes
func (s *Server) setupRoutes() {
	// Debug endpoint to verify router is working
	s.router.HandleFunc("/debug/routes", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		availableRoutes := []string{
			"/debug/routes",
			"/debug/jwt",
			"/debug/create-userprogress",
			"/api/v1/quote",
			"/api/v1/quote/public",
			"/api/v1/auth/register",
			"/api/v1/auth/login",
			"/api/v1/auth/profile",
			"/api/v1/auth/change-password",
			"/api/v1/auth/unregister",
			"/auth/change-password",
			"/auth/unregister",
		}
		json.NewEncoder(w).Encode(map[string]interface{}{
			"message":          "Router is working",
			"available_routes": availableRoutes,
		})
	}).Methods("GET")

	// Debug endpoint for JWT inspection
	s.router.HandleFunc("/debug/jwt", func(w http.ResponseWriter, r *http.Request) {
		authHeader := r.Header.Get("Authorization")
		if authHeader == "" {
			http.Error(w, "Authorization header required", http.StatusUnauthorized)
			return
		}

		tokenString := strings.TrimPrefix(authHeader, "Bearer ")

		claims, err := s.jwtService.ValidateToken(tokenString)
		if err != nil {
			json.NewEncoder(w).Encode(map[string]interface{}{
				"error":  err.Error(),
				"claims": nil,
			})
			return
		}

		json.NewEncoder(w).Encode(map[string]interface{}{
			"error":        nil,
			"claims":       claims,
			"user_id_type": fmt.Sprintf("%T", claims.UserID),
		})
	}).Methods("GET")

	// Debug endpoint to manually create userprogress table
	s.router.HandleFunc("/debug/create-userprogress", func(w http.ResponseWriter, r *http.Request) {
		// This is a debug endpoint - in production you'd use migrations
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]string{
			"message":   "Use the migration runner to create the userprogress table",
			"migration": "003_create_userprogress_table.sql",
		})
	}).Methods("POST")

	// Authentication routes (public)
	s.router.HandleFunc("/api/v1/auth/register", s.authHandler.Register).Methods("POST")
	s.router.HandleFunc("/api/v1/auth/login", s.authHandler.Login).Methods("POST")

	// Protected routes
	protected := s.router.PathPrefix("/api/v1").Subrouter()
	protected.Use(middleware.JWTMiddleware(s.jwtService))
	protected.HandleFunc("/auth/profile", s.authHandler.GetProfile).Methods("GET")
	protected.HandleFunc("/auth/change-password", s.authHandler.ChangePassword).Methods("POST")
	protected.HandleFunc("/auth/unregister", s.authHandler.DeleteUser).Methods("DELETE")

	// Alternative routes to match Azure backend (without /api/v1 prefix) - also protected
	altProtected := s.router.PathPrefix("/auth").Subrouter()
	altProtected.Use(middleware.JWTMiddleware(s.jwtService))
	altProtected.HandleFunc("/change-password", s.authHandler.ChangePassword).Methods("POST")
	altProtected.HandleFunc("/unregister", s.authHandler.DeleteUser).Methods("DELETE")

	// Admin routes
	admin := protected.PathPrefix("/admin").Subrouter()
	admin.Use(middleware.RequireAdmin())
	// Add admin-only routes here

	// Quote routes
	log.Printf("Setting up quote handler...")
	s.quoteHandler.SetupRoutes(s.router)

	// Protected quote route (for authenticated users with progress tracking)
	log.Printf("Setting up protected quote route: /api/v1/quote")
	protected.HandleFunc("/quote", s.quoteHandler.GetRandomQuoteHandler).Methods("GET")
}

// Start starts the HTTP server
func (s *Server) Start(port string) error {
	log.Printf("Starting server on port %s", port)
	return http.ListenAndServe(":"+port, s.router)
}
