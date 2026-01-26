package repository

import (
	"database/sql"
	"errors"
	"quote-ovhc-backend/internal/models"
	"strings"
	"time"
)

type AuthRepository struct {
	db *sql.DB
}

func NewAuthRepository(db *sql.DB) *AuthRepository {
	return &AuthRepository{db: db}
}

func (r *AuthRepository) CreateUser(user *models.User) error {
	query := `
		INSERT INTO users (username, email, password_hash, created_at, updated_at, is_active)
		VALUES (?, ?, ?, ?, ?, ?)
	`

	now := time.Now()
	user.CreatedAt = now
	user.UpdatedAt = now

	result, err := r.db.Exec(query,
		user.Username,
		user.Email,
		user.PasswordHash,
		user.CreatedAt,
		user.UpdatedAt,
		user.IsActive,
	)

	if err != nil {
		return err
	}

	id, err := result.LastInsertId()
	if err != nil {
		return err
	}

	user.ID = int(id)

	// Create corresponding user_roles entry with default 'user' role
	err = r.AddUserRole(user.ID, models.RoleUser)
	if err != nil {
		return err
	}

	return nil
}

func (r *AuthRepository) AddUserRole(userID int, role string) error {
	query := `
		INSERT INTO user_roles (user_id, role, created_at)
		VALUES (?, ?, ?)
	`

	_, err := r.db.Exec(query, userID, role, time.Now())
	return err
}

func (r *AuthRepository) GetUserRoles(userID int) ([]string, error) {
	query := `
		SELECT role FROM user_roles WHERE user_id = ?
	`

	rows, err := r.db.Query(query, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var roles []string
	for rows.Next() {
		var role string
		err := rows.Scan(&role)
		if err != nil {
			return nil, err
		}
		roles = append(roles, role)
	}

	return roles, nil
}

func (r *AuthRepository) GetUserByUsername(username string) (*models.User, error) {
	query := `
		SELECT id, username, email, password_hash, created_at, updated_at, is_active
		FROM users
		WHERE username = ? AND is_active = 1
	`

	var user models.User
	err := r.db.QueryRow(query, username).Scan(
		&user.ID,
		&user.Username,
		&user.Email,
		&user.PasswordHash,
		&user.CreatedAt,
		&user.UpdatedAt,
		&user.IsActive,
	)

	if err != nil {
		return nil, err
	}

	// Get user roles from user_roles table
	roles, err := r.GetUserRoles(user.ID)
	if err != nil {
		return nil, err
	}
	user.Roles = roles

	return &user, nil
}

func (r *AuthRepository) GetUserByEmail(email string) (*models.User, error) {
	query := `
		SELECT id, username, email, password_hash, created_at, updated_at, is_active
		FROM users
		WHERE email = ? AND is_active = 1
	`

	var user models.User
	err := r.db.QueryRow(query, email).Scan(
		&user.ID,
		&user.Username,
		&user.Email,
		&user.PasswordHash,
		&user.CreatedAt,
		&user.UpdatedAt,
		&user.IsActive,
	)

	if err != nil {
		return nil, err
	}

	// Get user roles from user_roles table
	roles, err := r.GetUserRoles(user.ID)
	if err != nil {
		return nil, err
	}
	user.Roles = roles

	return &user, nil
}

func (r *AuthRepository) GetUserByID(userID int) (*models.User, error) {
	query := `
		SELECT id, username, email, password_hash, created_at, updated_at, is_active
		FROM users
		WHERE id = ? AND is_active = 1
	`

	var user models.User
	err := r.db.QueryRow(query, userID).Scan(
		&user.ID,
		&user.Username,
		&user.Email,
		&user.PasswordHash,
		&user.CreatedAt,
		&user.UpdatedAt,
		&user.IsActive,
	)

	if err != nil {
		return nil, err
	}

	// Get user roles from user_roles table
	roles, err := r.GetUserRoles(user.ID)
	if err != nil {
		return nil, err
	}
	user.Roles = roles

	return &user, nil
}

func (r *AuthRepository) GetUserByIdentifier(identifier string) (*models.User, error) {
	// First try to find by username
	user, err := r.GetUserByUsername(identifier)
	if err == nil {
		return user, nil
	}

	// If not found by username, try by email
	user, err = r.GetUserByEmail(identifier)
	if err == nil {
		return user, nil
	}

	// If neither found, return error
	return nil, errors.New("user not found")
}

func (r *AuthRepository) UserExists(username, email string) (bool, error) {
	query := `
		SELECT COUNT(*) 
		FROM users 
		WHERE (username = ? OR email = ?) AND is_active = 1
	`

	var count int
	err := r.db.QueryRow(query, username, email).Scan(&count)
	if err != nil {
		return false, err
	}

	return count > 0, nil
}

func (r *AuthRepository) ChangePassword(userID int, currentPassword, newPasswordHash string) error {
	// First get the stored password hash
	query := `
		SELECT password_hash FROM users 
		WHERE id = ? AND is_active = 1
	`

	var storedPasswordHash string
	err := r.db.QueryRow(query, userID).Scan(&storedPasswordHash)
	if err != nil {
		return err
	}

	// Update password
	updateQuery := `
		UPDATE users 
		SET password_hash = ?, updated_at = ?
		WHERE id = ?
	`

	_, err = r.db.Exec(updateQuery, newPasswordHash, time.Now(), userID)
	return err
}

func (r *AuthRepository) DeleteUser(userID int, password string) error {
	// Start transaction
	tx, err := r.db.Begin()
	if err != nil {
		return err
	}
	defer tx.Rollback()

	// Delete user roles first (foreign key constraint)
	_, err = tx.Exec("DELETE FROM user_roles WHERE user_id = ?", userID)
	if err != nil {
		return err
	}

	// Delete user
	_, err = tx.Exec("DELETE FROM users WHERE id = ?", userID)
	if err != nil {
		return err
	}

	// Commit transaction
	return tx.Commit()
}

func (r *AuthRepository) GetAllUsers() ([]*models.User, error) {
	query := `
		SELECT id, username, email, password_hash, created_at, updated_at, is_active
		FROM users
		ORDER BY created_at DESC
	`

	rows, err := r.db.Query(query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var users []*models.User
	for rows.Next() {
		var user models.User
		err := rows.Scan(
			&user.ID,
			&user.Username,
			&user.Email,
			&user.PasswordHash,
			&user.CreatedAt,
			&user.UpdatedAt,
			&user.IsActive,
		)
		if err != nil {
			return nil, err
		}

		// Get user roles from user_roles table
		roles, err := r.GetUserRoles(user.ID)
		if err != nil {
			return nil, err
		}
		user.Roles = roles

		users = append(users, &user)
	}

	return users, nil
}

// UpdateUserRole updates a user's role
func (r *AuthRepository) UpdateUserRole(userID int, newRole string) error {
	query := `
		INSERT OR REPLACE INTO user_roles (user_id, role)
		VALUES (?, ?)
	`

	_, err := r.db.Exec(query, userID, strings.ToLower(newRole))
	return err
}

// RemoveUserRole removes a user's role
func (r *AuthRepository) RemoveUserRole(userID int, roleToRemove string) error {
	query := `
		DELETE FROM user_roles 
		WHERE user_id = ? AND role = ?
	`

	_, err := r.db.Exec(query, userID, strings.ToLower(roleToRemove))
	return err
}
