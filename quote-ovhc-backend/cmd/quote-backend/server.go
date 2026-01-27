package main

import (
	"encoding/json"
	"log"
	"net/http"
	"quote-ovhc-backend/internal/auth"
	"quote-ovhc-backend/internal/handlers"
	"quote-ovhc-backend/internal/middleware"
	"quote-ovhc-backend/internal/service"
	"quote-ovhc-backend/internal/services"
	"quote-ovhc-backend/internal/storage"

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
func NewServer(sqliteRepo *storage.SQLiteRepository, s3Storage *storage.S3Storage, zenQuotes *services.ZenQuotesService, authHandler *handlers.AuthHandler, jwtService *auth.JWTService, userProgressService *service.UserProgressService, userLikeService *service.UserLikeService) *Server {
	quoteHandler := handlers.NewQuoteHandler(sqliteRepo, s3Storage, zenQuotes, userProgressService, userLikeService)

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
			"/debug/create-userprogress",
			"/api/v1/quote",
			"/api/v1/quote/public",
			"/api/v1/quote/viewed",
			"/api/v1/quote/progress",
			"/api/v1/quote/{id}/like",
			"/api/v1/quote/{id}/unlike",
			"/api/v1/quote/liked",
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

	// Management routes (admin-only but under /api/v1/manage/*)
	manage := protected.PathPrefix("/manage").Subrouter()
	manage.Use(middleware.RequireAdmin())
	manage.HandleFunc("/users", s.authHandler.GetAllUsers).Methods("GET")
	manage.HandleFunc("/quotes", s.authHandler.GetAdminQuotes).Methods("GET")
	manage.HandleFunc("/quotes/fetch", s.authHandler.FetchQuotes).Methods("POST")
	manage.HandleFunc("/stats", s.authHandler.GetStats).Methods("GET")
	manage.HandleFunc("/users/role", s.authHandler.UpdateUserRole).Methods("PUT")
	manage.HandleFunc("/users/role", s.authHandler.RemoveUserRole).Methods("DELETE")
	manage.HandleFunc("/users/account", s.authHandler.DeleteUserAccount).Methods("DELETE")

	// Quote routes
	log.Printf("Setting up quote handler...")
	s.quoteHandler.SetupRoutes(s.router)

	// Protected quote route (for authenticated users with progress tracking)
	log.Printf("Setting up protected quote route: /api/v1/quote")
	protected.HandleFunc("/quote", s.quoteHandler.GetRandomQuoteHandler).Methods("GET")

	// Protected viewed quotes history endpoint
	log.Printf("Setting up viewed quotes route: /api/v1/quote/viewed")
	protected.HandleFunc("/quote/viewed", s.quoteHandler.GetViewedQuotesHandler).Methods("GET")

	// Protected progress endpoint
	log.Printf("Setting up progress route: /api/v1/quote/progress")
	protected.HandleFunc("/quote/progress", s.quoteHandler.GetProgressHandler).Methods("GET")

	// Protected like quote endpoint
	log.Printf("Setting up like quote route: /api/v1/quote/{id}/like")
	protected.HandleFunc("/quote/{id}/like", s.quoteHandler.LikeQuoteHandler).Methods("POST")

	// Protected unlike quote endpoint
	log.Printf("Setting up unlike quote route: /api/v1/quote/{id}/unlike")
	protected.HandleFunc("/quote/{id}/unlike", s.quoteHandler.UnlikeQuoteHandler).Methods("DELETE")

	// Protected get liked quotes endpoint
	log.Printf("Setting up liked quotes route: /api/v1/quote/liked")
	protected.HandleFunc("/quote/liked", s.quoteHandler.GetLikedQuotesHandler).Methods("GET")

	// Protected reorder quote endpoint
	log.Printf("Setting up reorder quote route: /api/v1/quote/{id}/reorder")
	protected.HandleFunc("/quote/{id}/reorder", s.quoteHandler.ReorderQuoteHandler).Methods("PUT")
}

// Start starts the HTTP server
func (s *Server) Start(port string) error {
	log.Printf("Starting server on port %s", port)
	return http.ListenAndServe(":"+port, s.router)
}
