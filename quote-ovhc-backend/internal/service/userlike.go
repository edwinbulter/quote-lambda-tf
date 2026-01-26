package service

import (
	"fmt"
	"quote-ovhc-backend/internal/models"
	"quote-ovhc-backend/internal/repository"
	"quote-ovhc-backend/internal/storage"
)

type UserLikeService struct {
	userLikeRepo *repository.UserLikeRepository
	quoteRepo    *storage.SQLiteRepository
}

func NewUserLikeService(userLikeRepo *repository.UserLikeRepository, quoteRepo *storage.SQLiteRepository) *UserLikeService {
	return &UserLikeService{
		userLikeRepo: userLikeRepo,
		quoteRepo:    quoteRepo,
	}
}

// LikeQuote handles the business logic for liking a quote
func (s *UserLikeService) LikeQuote(userID, quoteID int) error {
	// Check if quote exists
	quote, err := s.quoteRepo.GetQuoteByID(quoteID)
	if err != nil {
		return fmt.Errorf("failed to check if quote exists: %w", err)
	}

	if quote == nil {
		return fmt.Errorf("quote not found")
	}

	// Add the like (will fail if already liked due to unique constraint)
	err = s.userLikeRepo.LikeQuote(userID, quoteID)
	if err != nil {
		return fmt.Errorf("failed to like quote: %w", err)
	}

	return nil
}

// UnlikeQuote handles the business logic for unliking a quote
func (s *UserLikeService) UnlikeQuote(userID, quoteID int) error {
	// Remove the like
	err := s.userLikeRepo.UnlikeQuote(userID, quoteID)
	if err != nil {
		return fmt.Errorf("failed to unlike quote: %w", err)
	}

	return nil
}

// UserLikedQuote checks if a user has liked a specific quote
func (s *UserLikeService) UserLikedQuote(userID, quoteID int) (bool, error) {
	return s.userLikeRepo.UserLikedQuote(userID, quoteID)
}

// GetQuoteLikesCount returns the number of likes for a quote
func (s *UserLikeService) GetQuoteLikesCount(quoteID int) (int, error) {
	return s.userLikeRepo.GetQuoteLikesCount(quoteID)
}

// GetUserLikedQuotes returns all quotes liked by a user
func (s *UserLikeService) GetUserLikedQuotes(userID int) ([]*models.UserLike, error) {
	return s.userLikeRepo.GetUserLikedQuotes(userID)
}

// DeleteUserLikes removes all likes for a user (when user is deleted)
func (s *UserLikeService) DeleteUserLikes(userID int) error {
	return s.userLikeRepo.DeleteUserLikes(userID)
}

// ReorderLikedQuote reorders a user's liked quote to a new position
func (s *UserLikeService) ReorderLikedQuote(userID, quoteID, newOrder int) error {
	if newOrder <= 0 {
		return fmt.Errorf("order must be a positive integer")
	}

	// Check if user has liked this quote
	alreadyLiked, err := s.UserLikedQuote(userID, quoteID)
	if err != nil {
		return fmt.Errorf("failed to check if user liked quote: %w", err)
	}

	if !alreadyLiked {
		return fmt.Errorf("quote not found in user's likes")
	}

	// Perform the reorder operation
	err = s.userLikeRepo.ReorderLikedQuote(userID, quoteID, newOrder)
	if err != nil {
		return fmt.Errorf("failed to reorder liked quote: %w", err)
	}

	return nil
}
