package middleware

import (
	"context"
	"log"
	"net/http"
	"quote-ovhc-backend/internal/auth"
	"strings"
)

type contextKey string

const (
	UserIDKey   contextKey = "user_id"
	UsernameKey contextKey = "username"
	RoleKey     contextKey = "role"
)

// JWTMiddleware creates a middleware for JWT validation using Gorilla Mux
func JWTMiddleware(jwtService *auth.JWTService) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			log.Printf("JWT Debug - Processing request: %s %s", r.Method, r.URL.Path)

			authHeader := r.Header.Get("Authorization")
			if authHeader == "" {
				log.Printf("JWT Debug - No Authorization header found")
				http.Error(w, "Authorization header required", http.StatusUnauthorized)
				return
			}

			// Extract token from "Bearer <token>"
			tokenString := strings.Replace(authHeader, "Bearer ", "", 1)
			log.Printf("JWT Debug - Received token: %s...", tokenString[:min(50, len(tokenString))])

			// Validate token
			claims, err := jwtService.ValidateToken(tokenString)
			if err != nil {
				log.Printf("JWT Debug - Token validation failed: %v", err)
				http.Error(w, "Invalid token", http.StatusUnauthorized)
				return
			}

			log.Printf("JWT Debug - Token validated successfully")
			log.Printf("JWT Debug - Claims: UserID=%d, Username=%s, Role=%s", claims.UserID, claims.Username, claims.Role)

			// Set user context
			ctx := context.WithValue(r.Context(), UserIDKey, claims.UserID)
			ctx = context.WithValue(ctx, UsernameKey, claims.Username)
			ctx = context.WithValue(ctx, RoleKey, claims.Role)

			log.Printf("JWT Debug - Context set, proceeding to next handler")
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

// Helper function to get minimum of two ints
func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}

// RequireRole creates a middleware that requires a specific role
func RequireRole(requiredRole string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			role, ok := r.Context().Value(RoleKey).(string)
			if !ok || role != requiredRole {
				http.Error(w, "Insufficient permissions", http.StatusForbidden)
				return
			}

			next.ServeHTTP(w, r)
		})
	}
}

// RequireAdmin creates a middleware that requires admin role
func RequireAdmin() func(http.Handler) http.Handler {
	return RequireRole("admin")
}
