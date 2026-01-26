package handlers

import (
	"encoding/json"
	"log"
	"net/http"
	"quote-ovhc-backend/internal/auth"
	"quote-ovhc-backend/internal/middleware"
	"quote-ovhc-backend/internal/models"
	"quote-ovhc-backend/internal/service"
	"time"

	"github.com/gorilla/mux"
)

type AuthHandler struct {
	authService  *service.AuthService
	adminService *service.AdminService
	jwtService   *auth.JWTService
}

func NewAuthHandler(authService *service.AuthService, adminService *service.AdminService, jwtService *auth.JWTService) *AuthHandler {
	return &AuthHandler{
		authService:  authService,
		adminService: adminService,
		jwtService:   jwtService,
	}
}

func (h *AuthHandler) Register(w http.ResponseWriter, r *http.Request) {
	var req service.RegisterRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	response, err := h.authService.Register(&req)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(response)
}

func (h *AuthHandler) Login(w http.ResponseWriter, r *http.Request) {
	var req service.LoginRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	response, err := h.authService.Login(&req)
	if err != nil {
		http.Error(w, err.Error(), http.StatusUnauthorized)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

func (h *AuthHandler) GetProfile(w http.ResponseWriter, r *http.Request) {
	// Get user info from context (set by JWT middleware) using correct keys
	userID := r.Context().Value(middleware.UserIDKey)
	username := r.Context().Value(middleware.UsernameKey)
	roles := r.Context().Value(middleware.RolesKey)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"user_id":  userID,
		"username": username,
		"roles":    roles,
	})
}

func (h *AuthHandler) ChangePassword(w http.ResponseWriter, r *http.Request) {
	log.Printf("ChangePassword Debug - Starting ChangePassword handler")

	// Get user ID from context using correct key
	userIDValue := r.Context().Value(middleware.UserIDKey)
	log.Printf("ChangePassword Debug - UserID from context: %v (type: %T)", userIDValue, userIDValue)

	userID, ok := userIDValue.(int)
	if !ok {
		log.Printf("ChangePassword Debug - Could not convert userID to int, ok: %v", ok)
		http.Error(w, "User not found", http.StatusUnauthorized)
		return
	}

	log.Printf("ChangePassword Debug - Successfully extracted userID: %d", userID)

	var req service.ChangePasswordRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		log.Printf("ChangePassword Debug - Failed to decode request: %v", err)
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	log.Printf("ChangePassword Debug - Request decoded successfully, calling service")

	err := h.authService.ChangePassword(userID, &req)
	if err != nil {
		log.Printf("ChangePassword Debug - Service error: %v", err)
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	log.Printf("ChangePassword Debug - Password changed successfully")

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"message": "Password changed successfully"})
}

func (h *AuthHandler) DeleteUser(w http.ResponseWriter, r *http.Request) {
	// Get user ID from context using correct key
	userID, ok := r.Context().Value(middleware.UserIDKey).(int)
	if !ok {
		http.Error(w, "User not found", http.StatusUnauthorized)
		return
	}

	var req service.DeleteUserRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	err := h.authService.DeleteUser(userID, &req)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"message": "User deleted successfully"})
}

func (h *AuthHandler) GetAllUsers(w http.ResponseWriter, r *http.Request) {
	// Get user ID from context using correct key
	userID, ok := r.Context().Value(middleware.UserIDKey).(int)
	if !ok {
		http.Error(w, "User not found", http.StatusUnauthorized)
		return
	}

	users, err := h.authService.GetAllUsers(userID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusForbidden)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(users)
}

func (h *AuthHandler) GetAdminQuotes(w http.ResponseWriter, r *http.Request) {
	// For now, we'll skip the admin check since the middleware already handles it
	// The RequireAdmin middleware in the route setup ensures only admins can access this endpoint

	// Parse query parameters
	query := r.URL.Query()
	pageStr := query.Get("page")
	pageSizeStr := query.Get("pageSize")
	quoteText := query.Get("quoteText")
	author := query.Get("author")
	sortBy := query.Get("sortBy")
	sortOrder := query.Get("sortOrder")

	// Parse and validate parameters
	page, pageSize, quoteText, author, sortBy, sortOrder, err := h.adminService.ParseQueryParams(
		pageStr, pageSizeStr, quoteText, author, sortBy, sortOrder)
	if err != nil {
		http.Error(w, "Invalid parameters", http.StatusBadRequest)
		return
	}

	// Get quotes
	response, err := h.adminService.GetQuotesWithPagination(page, pageSize, quoteText, author, sortBy, sortOrder)
	if err != nil {
		http.Error(w, "Failed to get quotes", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

func (h *AuthHandler) FetchQuotes(w http.ResponseWriter, r *http.Request) {
	// Get requesting username from context (or use "system" as fallback)
	username := "system" // In a real implementation, this would come from JWT token

	// Fetch quotes
	result, err := h.adminService.FetchQuotes(username)
	if err != nil {
		http.Error(w, "Failed to fetch quotes", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(result)
}

func (h *AuthHandler) GetStats(w http.ResponseWriter, r *http.Request) {
	// Get total likes
	totalLikes, err := h.adminService.GetTotalLikes()
	if err != nil {
		http.Error(w, "Failed to get stats", http.StatusInternalServerError)
		return
	}

	stats := map[string]interface{}{
		"totalLikes": totalLikes,
		"timestamp":  time.Now().UTC().Format(time.RFC3339),
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(stats)
}

func (h *AuthHandler) UpdateUserRole(w http.ResponseWriter, r *http.Request) {
	var req models.UserRoleRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	// Validate request
	if req.Username == "" {
		http.Error(w, "Username is required", http.StatusBadRequest)
		return
	}

	if req.Role == "" {
		http.Error(w, "Role is required", http.StatusBadRequest)
		return
	}

	// Get user ID from context
	userID, ok := r.Context().Value(middleware.UserIDKey).(int)
	if !ok {
		http.Error(w, "User not found", http.StatusUnauthorized)
		return
	}

	// Update user role
	err := h.authService.UpdateUserRole(userID, req.Username, req.Role)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	response := map[string]interface{}{
		"message":  "User role updated successfully",
		"username": req.Username,
		"role":     req.Role,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

func (h *AuthHandler) RemoveUserRole(w http.ResponseWriter, r *http.Request) {
	var req models.UserRoleRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	// Validate request
	if req.Username == "" {
		http.Error(w, "Username is required", http.StatusBadRequest)
		return
	}

	if req.Role == "" {
		http.Error(w, "Role is required", http.StatusBadRequest)
		return
	}

	// Get user ID from context
	userID, ok := r.Context().Value(middleware.UserIDKey).(int)
	if !ok {
		http.Error(w, "User not found", http.StatusUnauthorized)
		return
	}

	// Remove user role
	err := h.authService.RemoveUserRole(userID, req.Username, req.Role)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	response := map[string]interface{}{
		"message":  "User role removed successfully",
		"username": req.Username,
		"role":     req.Role,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

func (h *AuthHandler) DeleteUserAccount(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Username string `json:"username"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	// Validate request
	if req.Username == "" {
		http.Error(w, "Username is required", http.StatusBadRequest)
		return
	}

	// Get user ID from context
	userID, ok := r.Context().Value(middleware.UserIDKey).(int)
	if !ok {
		http.Error(w, "User not found", http.StatusUnauthorized)
		return
	}

	// Delete user account
	err := h.authService.DeleteUserAccount(userID, req.Username)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	response := map[string]interface{}{
		"message":  "User account deleted successfully",
		"username": req.Username,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// SetupRoutes configures the authentication routes
func (h *AuthHandler) SetupRoutes(router *mux.Router) {
	log.Printf("Setting up auth handler routes...")

	// Public auth routes
	router.HandleFunc("/api/v1/auth/register", h.Register).Methods("POST")
	router.HandleFunc("/api/v1/auth/login", h.Login).Methods("POST")

	// Protected auth routes (require JWT authentication)
	protectedRouter := router.PathPrefix("").Subrouter()
	protectedRouter.Use(middleware.JWTMiddleware(h.jwtService))

	protectedRouter.HandleFunc("/api/v1/auth/profile", h.GetProfile).Methods("GET")
	protectedRouter.HandleFunc("/api/v1/auth/change-password", h.ChangePassword).Methods("POST")
	protectedRouter.HandleFunc("/api/v1/auth/unregister", h.DeleteUser).Methods("DELETE")

	// Admin-only management routes
	adminRouter := router.PathPrefix("").Subrouter()
	adminRouter.Use(middleware.JWTMiddleware(h.jwtService))
	adminRouter.Use(middleware.RequireAdmin())

	adminRouter.HandleFunc("/api/v1/manage/users", h.GetAllUsers).Methods("GET")
	adminRouter.HandleFunc("/api/v1/manage/quotes", h.GetAdminQuotes).Methods("GET")
	adminRouter.HandleFunc("/api/v1/manage/quotes/fetch", h.FetchQuotes).Methods("POST")
	adminRouter.HandleFunc("/api/v1/manage/stats", h.GetStats).Methods("GET")
	adminRouter.HandleFunc("/api/v1/manage/users/role", h.UpdateUserRole).Methods("PUT")
	adminRouter.HandleFunc("/api/v1/manage/users/role", h.RemoveUserRole).Methods("DELETE")
	adminRouter.HandleFunc("/api/v1/manage/users/account", h.DeleteUserAccount).Methods("DELETE")

	log.Printf("Auth handler routes setup completed")
}
