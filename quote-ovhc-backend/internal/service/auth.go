package service

import (
	"errors"
	"fmt"
	"quote-ovhc-backend/internal/auth"
	"quote-ovhc-backend/internal/models"
	"quote-ovhc-backend/internal/repository"
	"strings"
)

type AuthService struct {
	authRepo        *repository.AuthRepository
	jwtService      *auth.JWTService
	passwordService *auth.PasswordService
}

func NewAuthService(
	authRepo *repository.AuthRepository,
	jwtService *auth.JWTService,
	passwordService *auth.PasswordService,
) *AuthService {
	return &AuthService{
		authRepo:        authRepo,
		jwtService:      jwtService,
		passwordService: passwordService,
	}
}

type RegisterRequest struct {
	Username string `json:"username" validate:"required,min=3,max=50"`
	Email    string `json:"email" validate:"required,email"`
	Password string `json:"password" validate:"required,min=6"`
}

type LoginRequest struct {
	LoginIdentifier string `json:"loginIdentifier" validate:"required"`
	Password        string `json:"password" validate:"required"`
}

type ChangePasswordRequest struct {
	CurrentPassword    string `json:"currentPassword" validate:"required"`
	NewPassword        string `json:"newPassword" validate:"required,min=6"`
	ConfirmNewPassword string `json:"confirmNewPassword" validate:"required"`
}

type DeleteUserRequest struct {
	Password string `json:"password" validate:"required"`
}

type RegisterResponse struct {
	User    *models.User `json:"user"`
	Message string       `json:"message"`
}

type AuthResponse struct {
	Token                  string       `json:"token"`
	User                   *models.User `json:"user"`
	ExpiresIn              int64        `json:"expires_in"`
	DefaultPasswordWarning *string      `json:"default_password_warning,omitempty"`
}

func (s *AuthService) Register(req *RegisterRequest) (*RegisterResponse, error) {
	// Check if user already exists
	exists, err := s.authRepo.UserExists(req.Username, req.Email)
	if err != nil {
		return nil, fmt.Errorf("error checking user existence: %w", err)
	}
	if exists {
		return nil, errors.New("user with this username or email already exists")
	}

	// Hash password
	passwordHash, err := s.passwordService.HashPassword(req.Password)
	if err != nil {
		return nil, fmt.Errorf("error hashing password: %w", err)
	}

	// Create user (roles will be automatically assigned in repository)
	user := &models.User{
		Username:     req.Username,
		Email:        req.Email,
		PasswordHash: passwordHash,
		IsActive:     true,
	}

	err = s.authRepo.CreateUser(user)
	if err != nil {
		return nil, fmt.Errorf("error creating user: %w", err)
	}

	return &RegisterResponse{
		User:    user,
		Message: "User registered successfully",
	}, nil
}

func (s *AuthService) Login(req *LoginRequest) (*AuthResponse, error) {
	// Get user by identifier (username or email)
	user, err := s.authRepo.GetUserByIdentifier(req.LoginIdentifier)
	if err != nil {
		return nil, errors.New("invalid login identifier or password")
	}

	// Check password
	err = s.passwordService.CheckPassword(req.Password, user.PasswordHash)
	if err != nil {
		return nil, errors.New("invalid login identifier or password")
	}

	// Generate token with roles
	token, err := s.jwtService.GenerateToken(user.ID, user.Username, user.Roles)
	if err != nil {
		return nil, fmt.Errorf("error generating token: %w", err)
	}

	// Check if user is admin and using default password
	var defaultPasswordWarning *string
	if user.Username == "admin" && req.Password == "Hello-admin!" {
		warning := "You are using the default admin password. Please change it immediately for security."
		defaultPasswordWarning = &warning
	}

	return &AuthResponse{
		Token:                  token,
		User:                   user,
		ExpiresIn:              3600, // 1 hour in seconds
		DefaultPasswordWarning: defaultPasswordWarning,
	}, nil
}

func (s *AuthService) ChangePassword(userID int, req *ChangePasswordRequest) error {
	// Validate new passwords match
	if req.NewPassword != req.ConfirmNewPassword {
		return errors.New("new passwords do not match")
	}

	// Get user to verify current password
	user, err := s.authRepo.GetUserByID(userID)
	if err != nil {
		return fmt.Errorf("user not found: %w", err)
	}

	// Verify current password
	err = s.passwordService.CheckPassword(req.CurrentPassword, user.PasswordHash)
	if err != nil {
		return errors.New("current password is incorrect")
	}

	// Hash new password
	newPasswordHash, err := s.passwordService.HashPassword(req.NewPassword)
	if err != nil {
		return fmt.Errorf("error hashing new password: %w", err)
	}

	// Change password
	err = s.authRepo.ChangePassword(userID, req.CurrentPassword, newPasswordHash)
	if err != nil {
		return fmt.Errorf("error changing password: %w", err)
	}

	return nil
}

func (s *AuthService) DeleteUser(userID int, req *DeleteUserRequest) error {
	// Get user to verify password
	user, err := s.authRepo.GetUserByID(userID)
	if err != nil {
		return fmt.Errorf("user not found: %w", err)
	}

	// Verify password
	err = s.passwordService.CheckPassword(req.Password, user.PasswordHash)
	if err != nil {
		return errors.New("password is incorrect")
	}

	// Delete user
	err = s.authRepo.DeleteUser(userID, req.Password)
	if err != nil {
		return fmt.Errorf("error deleting user: %w", err)
	}

	return nil
}

func (s *AuthService) GetAllUsers(userID int) ([]*models.AdminUserInfo, error) {
	// Verify requesting user is admin
	user, err := s.authRepo.GetUserByID(userID)
	if err != nil {
		return nil, fmt.Errorf("user not found: %w", err)
	}

	// Check if user has admin role
	isAdmin := false
	for _, role := range user.Roles {
		if role == models.RoleAdmin {
			isAdmin = true
			break
		}
	}

	if !isAdmin {
		return nil, errors.New("access denied: only administrators can view all users")
	}

	// Get all users
	users, err := s.authRepo.GetAllUsers()
	if err != nil {
		return nil, fmt.Errorf("error retrieving users: %w", err)
	}

	// Convert to AdminUserInfo format
	adminUsers := make([]*models.AdminUserInfo, len(users))
	for i, user := range users {
		adminUsers[i] = models.NewAdminUserInfo(user)
	}

	return adminUsers, nil
}

// UpdateUserRole updates a user's role
func (s *AuthService) UpdateUserRole(adminUserID int, username, newRole string) error {
	// Validate role
	validRoles := map[string]bool{"user": true, "admin": true}
	if !validRoles[strings.ToLower(newRole)] {
		return fmt.Errorf("invalid role: %s. Valid roles are: user, admin", newRole)
	}

	// Get target user
	targetUser, err := s.authRepo.GetUserByUsername(username)
	if err != nil {
		return fmt.Errorf("user not found: %s", username)
	}

	// Update user role
	return s.authRepo.UpdateUserRole(targetUser.ID, newRole)
}

// RemoveUserRole removes a user's role
func (s *AuthService) RemoveUserRole(adminUserID int, username, roleToRemove string) error {
	// Validate role
	validRoles := map[string]bool{"user": true, "admin": true}
	if !validRoles[strings.ToLower(roleToRemove)] {
		return fmt.Errorf("invalid role: %s. Valid roles are: user, admin", roleToRemove)
	}

	// Get target user
	targetUser, err := s.authRepo.GetUserByUsername(username)
	if err != nil {
		return fmt.Errorf("user not found: %s", username)
	}

	// Remove user role
	return s.authRepo.RemoveUserRole(targetUser.ID, roleToRemove)
}

// DeleteUserAccount deletes a user account and all associated data
func (s *AuthService) DeleteUserAccount(adminUserID int, username string) error {
	// Get target user
	targetUser, err := s.authRepo.GetUserByUsername(username)
	if err != nil {
		return fmt.Errorf("user not found: %s", username)
	}

	// Verify admin user has permission to delete
	adminUser, err := s.authRepo.GetUserByID(adminUserID)
	if err != nil {
		return fmt.Errorf("admin user not found: %w", err)
	}

	// Check if admin user has admin role
	isAdmin := false
	for _, role := range adminUser.Roles {
		if role == models.RoleAdmin {
			isAdmin = true
			break
		}
	}

	if !isAdmin {
		return errors.New("access denied: only administrators can delete user accounts")
	}

	// Delete user account directly (bypass password verification for admin)
	err = s.authRepo.DeleteUser(targetUser.ID, "")
	if err != nil {
		return fmt.Errorf("error deleting user: %w", err)
	}

	return nil
}
