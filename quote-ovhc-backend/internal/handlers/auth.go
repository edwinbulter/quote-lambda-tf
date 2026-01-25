package handlers

import (
	"encoding/json"
	"log"
	"net/http"
	"quote-ovhc-backend/internal/middleware"
	"quote-ovhc-backend/internal/service"
)

type AuthHandler struct {
	authService *service.AuthService
}

func NewAuthHandler(authService *service.AuthService) *AuthHandler {
	return &AuthHandler{
		authService: authService,
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
	role := r.Context().Value(middleware.RoleKey)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"user_id":  userID,
		"username": username,
		"role":     role,
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
