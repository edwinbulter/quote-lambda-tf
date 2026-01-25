package service

import (
	"errors"
	"fmt"
	"quote-ovhc-backend/internal/auth"
	"quote-ovhc-backend/internal/models"
	"quote-ovhc-backend/internal/repository"
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

type AuthResponse struct {
	Token     string       `json:"token"`
	User      *models.User `json:"user"`
	ExpiresIn int64        `json:"expires_in"`
}

func (s *AuthService) Register(req *RegisterRequest) (*AuthResponse, error) {
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

	// Generate token with roles
	token, err := s.jwtService.GenerateToken(user.ID, user.Username, user.Roles)
	if err != nil {
		return nil, fmt.Errorf("error generating token: %w", err)
	}

	return &AuthResponse{
		Token:     token,
		User:      user,
		ExpiresIn: 3600, // 1 hour in seconds
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

	return &AuthResponse{
		Token:     token,
		User:      user,
		ExpiresIn: 3600, // 1 hour in seconds
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
	// Hash password for verification
	passwordHash, err := s.passwordService.HashPassword(req.Password)
	if err != nil {
		return fmt.Errorf("error hashing password: %w", err)
	}

	// Delete user
	err = s.authRepo.DeleteUser(userID, passwordHash)
	if err != nil {
		return fmt.Errorf("error deleting user: %w", err)
	}

	return nil
}
