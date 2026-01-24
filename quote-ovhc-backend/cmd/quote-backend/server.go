package main

import (
	"log"
	"net/http"

	"quote-ovhc-backend/internal/handlers"
	"quote-ovhc-backend/internal/services"
	"quote-ovhc-backend/internal/storage"

	"github.com/gorilla/mux"
)

// Server represents the HTTP server
type Server struct {
	router       *mux.Router
	quoteHandler *handlers.QuoteHandler
}

// NewServer creates a new HTTP server
func NewServer(sqliteRepo *storage.SQLiteRepository, s3Storage *storage.S3Storage, zenQuotes *services.ZenQuotesService) *Server {
	quoteHandler := handlers.NewQuoteHandler(sqliteRepo, s3Storage, zenQuotes)

	server := &Server{
		router:       mux.NewRouter(),
		quoteHandler: quoteHandler,
	}

	server.setupRoutes()
	return server
}

// setupRoutes configures the HTTP routes
func (s *Server) setupRoutes() {
	s.quoteHandler.SetupRoutes(s.router)
}

// Start starts the HTTP server
func (s *Server) Start(port string) error {
	log.Printf("Starting server on port %s", port)
	return http.ListenAndServe(":"+port, s.router)
}
