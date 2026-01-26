package service

import (
	"fmt"
	"quote-ovhc-backend/internal/models"
	"quote-ovhc-backend/internal/repository"
)

type UserProgressService struct {
	userProgressRepo *repository.UserProgressRepository
}

func NewUserProgressService(userProgressRepo *repository.UserProgressRepository) *UserProgressService {
	return &UserProgressService{
		userProgressRepo: userProgressRepo,
	}
}

// GetNextQuoteID gets the next quote ID for the user based on their progress
func (s *UserProgressService) GetNextQuoteID(userID int) (int, error) {
	progress, err := s.userProgressRepo.GetUserProgress(userID)
	if err != nil {
		return 0, fmt.Errorf("error getting user progress: %w", err)
	}

	if progress == nil {
		// No progress exists, start from quote ID 1
		return 1, nil
	}

	// Return next quote ID
	return progress.LastQuoteID + 1, nil
}

// UpdateUserProgress updates the user's last quote ID
func (s *UserProgressService) UpdateUserProgress(userID int, quoteID int) error {
	err := s.userProgressRepo.CreateOrUpdateUserProgress(userID, quoteID)
	if err != nil {
		return fmt.Errorf("error updating user progress: %w", err)
	}
	return nil
}

// InitializeUserProgress creates initial progress for a new user
func (s *UserProgressService) InitializeUserProgress(userID int) error {
	err := s.userProgressRepo.InitializeUserProgress(userID)
	if err != nil {
		return fmt.Errorf("error initializing user progress: %w", err)
	}
	return nil
}

// GetUserProgress gets the current user progress
func (s *UserProgressService) GetUserProgress(userID int) (*models.UserProgress, error) {
	progress, err := s.userProgressRepo.GetUserProgress(userID)
	if err != nil {
		return nil, fmt.Errorf("error getting user progress: %w", err)
	}
	return progress, nil
}

// DeleteUserProgress removes user progress when user is deleted
func (s *UserProgressService) DeleteUserProgress(userID int) error {
	err := s.userProgressRepo.DeleteUserProgress(userID)
	if err != nil {
		return fmt.Errorf("error deleting user progress: %w", err)
	}
	return nil
}
