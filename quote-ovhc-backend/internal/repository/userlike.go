package repository

import (
	"database/sql"
	"fmt"
	"quote-ovhc-backend/internal/models"
	"time"
)

type UserLikeRepository struct {
	db *sql.DB
}

func NewUserLikeRepository(db *sql.DB) *UserLikeRepository {
	return &UserLikeRepository{db: db}
}

// UserLikedQuote checks if a user has already liked a specific quote
func (r *UserLikeRepository) UserLikedQuote(userID, quoteID int) (bool, error) {
	var count int
	err := r.db.QueryRow(`
		SELECT COUNT(*) FROM user_likes 
		WHERE user_id = ? AND quote_id = ?
	`, userID, quoteID).Scan(&count)

	if err != nil {
		return false, fmt.Errorf("failed to check if user liked quote: %w", err)
	}

	return count > 0, nil
}

// LikeQuote adds a like from a user to a quote
func (r *UserLikeRepository) LikeQuote(userID, quoteID int) error {
	// Check if already liked
	alreadyLiked, err := r.UserLikedQuote(userID, quoteID)
	if err != nil {
		return err
	}

	if alreadyLiked {
		return fmt.Errorf("user has already liked this quote")
	}

	// Get the next order index for this user
	nextOrder, err := r.getNextOrderIndex(userID)
	if err != nil {
		return fmt.Errorf("failed to get next order index: %w", err)
	}

	// Add the like with order
	_, err = r.db.Exec(`
		INSERT INTO user_likes (user_id, quote_id, order_index, created_at)
		VALUES (?, ?, ?, ?)
	`, userID, quoteID, nextOrder, time.Now())

	if err != nil {
		return fmt.Errorf("failed to like quote: %w", err)
	}

	return nil
}

// UnlikeQuote removes a like from a user to a quote
func (r *UserLikeRepository) UnlikeQuote(userID, quoteID int) error {
	_, err := r.db.Exec(`
		DELETE FROM user_likes 
		WHERE user_id = ? AND quote_id = ?
	`, userID, quoteID)

	if err != nil {
		return fmt.Errorf("failed to unlike quote: %w", err)
	}

	return nil
}

// GetQuoteLikesCount returns the number of likes for a specific quote
func (r *UserLikeRepository) GetQuoteLikesCount(quoteID int) (int, error) {
	var count int
	err := r.db.QueryRow(`
		SELECT COUNT(*) FROM user_likes 
		WHERE quote_id = ?
	`, quoteID).Scan(&count)

	if err != nil {
		return 0, fmt.Errorf("failed to get quote likes count: %w", err)
	}

	return count, nil
}

// GetUserLikedQuotes returns all quotes liked by a specific user
func (r *UserLikeRepository) GetUserLikedQuotes(userID int) ([]*models.UserLike, error) {
	rows, err := r.db.Query(`
		SELECT id, user_id, quote_id, order_index, created_at 
		FROM user_likes 
		WHERE user_id = ?
		ORDER BY order_index ASC
	`, userID)

	if err != nil {
		return nil, fmt.Errorf("failed to get user liked quotes: %w", err)
	}
	defer rows.Close()

	var likes []*models.UserLike
	for rows.Next() {
		var like models.UserLike
		err := rows.Scan(&like.ID, &like.UserID, &like.QuoteID, &like.Order, &like.CreatedAt)
		if err != nil {
			return nil, fmt.Errorf("failed to scan user like: %w", err)
		}
		likes = append(likes, &like)
	}

	return likes, nil
}

// DeleteUserLikes removes all likes for a user (when user is deleted)
func (r *UserLikeRepository) DeleteUserLikes(userID int) error {
	_, err := r.db.Exec(`
		DELETE FROM user_likes 
		WHERE user_id = ?
	`, userID)

	if err != nil {
		return fmt.Errorf("failed to delete user likes: %w", err)
	}

	return nil
}

// getNextOrderIndex gets the next order index for a user's liked quotes
func (r *UserLikeRepository) getNextOrderIndex(userID int) (int, error) {
	var maxOrder int
	err := r.db.QueryRow(`
		SELECT COALESCE(MAX(order_index), 0) 
		FROM user_likes 
		WHERE user_id = ?
	`, userID).Scan(&maxOrder)

	if err != nil {
		return 0, fmt.Errorf("failed to get max order index: %w", err)
	}

	return maxOrder + 1, nil
}

// ReorderLikedQuote reorders a user's liked quote to a new position
func (r *UserLikeRepository) ReorderLikedQuote(userID, quoteID, newOrder int) error {
	// Start transaction
	tx, err := r.db.Begin()
	if err != nil {
		return fmt.Errorf("failed to begin transaction: %w", err)
	}
	defer tx.Rollback()

	// Get current order of the quote to move
	var currentOrder int
	err = tx.QueryRow(`
		SELECT order_index 
		FROM user_likes 
		WHERE user_id = ? AND quote_id = ?
	`, userID, quoteID).Scan(&currentOrder)

	if err != nil {
		if err == sql.ErrNoRows {
			return fmt.Errorf("quote not found in user's likes")
		}
		return fmt.Errorf("failed to get current order: %w", err)
	}

	// If no change needed, return early
	if currentOrder == newOrder {
		tx.Commit()
		return nil
	}

	if newOrder > currentOrder {
		// Moving down: decrement orders between currentOrder and newOrder
		_, err = tx.Exec(`
			UPDATE user_likes 
			SET order_index = order_index - 1 
			WHERE user_id = ? AND order_index > ? AND order_index <= ?
		`, userID, currentOrder, newOrder)
	} else {
		// Moving up: increment orders between newOrder and currentOrder
		_, err = tx.Exec(`
			UPDATE user_likes 
			SET order_index = order_index + 1 
			WHERE user_id = ? AND order_index >= ? AND order_index < ?
		`, userID, newOrder, currentOrder)
	}

	if err != nil {
		return fmt.Errorf("failed to update affected orders: %w", err)
	}

	// Set the moved item to new order
	_, err = tx.Exec(`
		UPDATE user_likes 
		SET order_index = ? 
		WHERE user_id = ? AND quote_id = ?
	`, newOrder, userID, quoteID)

	if err != nil {
		return fmt.Errorf("failed to update moved quote order: %w", err)
	}

	return tx.Commit()
}
