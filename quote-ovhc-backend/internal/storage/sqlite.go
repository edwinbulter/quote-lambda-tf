package storage

import (
	"database/sql"
	"fmt"
	"strings"
	"sync"

	"quote-ovhc-backend/internal/models"
)

// SQLiteRepository handles database operations
type SQLiteRepository struct {
	db    *sql.DB
	mutex sync.RWMutex
}

// NewSQLiteRepository creates a new SQLite repository
func NewSQLiteRepository(db *sql.DB) *SQLiteRepository {
	return &SQLiteRepository{
		db:    db,
		mutex: sync.RWMutex{},
	}
}

// InitSchema creates the quotes table if it doesn't exist
func (r *SQLiteRepository) InitSchema() error {
	query := `
	CREATE TABLE IF NOT EXISTS quotes (
		id INTEGER PRIMARY KEY AUTOINCREMENT,
		text TEXT NOT NULL,
		author TEXT NOT NULL
	);
	CREATE INDEX IF NOT EXISTS idx_quotes_id ON quotes(id);
	`

	_, err := r.db.Exec(query)
	return err
}

// GetQuoteCount returns the number of quotes in the database
func (r *SQLiteRepository) GetQuoteCount() (int, error) {
	var count int
	err := r.db.QueryRow("SELECT COUNT(*) FROM quotes").Scan(&count)
	return count, err
}

// GetRandomQuote returns a random quote from the database
func (r *SQLiteRepository) GetRandomQuote() (*models.Quote, error) {
	r.mutex.RLock()
	defer r.mutex.RUnlock()

	var quote models.Quote
	query := `
		SELECT id, text, author 
		FROM quotes 
		ORDER BY RANDOM() 
		LIMIT 1
	`
	err := r.db.QueryRow(query).Scan(&quote.ID, &quote.Text, &quote.Author)

	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get random quote: %w", err)
	}

	return &quote, nil
}

// GetQuoteByID returns a quote by its ID
func (r *SQLiteRepository) GetQuoteByID(id int) (*models.Quote, error) {
	r.mutex.RLock()
	defer r.mutex.RUnlock()

	var quote models.Quote
	err := r.db.QueryRow(`
		SELECT id, text, author 
		FROM quotes 
		WHERE id = ?
	`, id).Scan(&quote.ID, &quote.Text, &quote.Author)

	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get quote by ID %d: %w", id, err)
	}

	return &quote, nil
}

// GetUniqueQuote returns a random quote excluding the specified IDs
func (r *SQLiteRepository) GetUniqueQuote(excludeIDs map[int]bool) (*models.Quote, error) {
	r.mutex.RLock()

	// First, try to get a quote not in the exclude list
	var quote models.Quote
	var args []interface{}

	// Build query to exclude IDs
	query := `
		SELECT id, text, author 
		FROM quotes 
		WHERE id NOT IN (`

	for id := range excludeIDs {
		if len(args) > 0 {
			query += ","
		}
		query += "?"
		args = append(args, id)
	}

	if len(args) == 0 {
		// No exclusions, get any random quote
		r.mutex.RUnlock()
		return r.GetRandomQuote()
	}

	query += `) ORDER BY RANDOM() LIMIT 1`

	err := r.db.QueryRow(query, args...).Scan(&quote.ID, &quote.Text, &quote.Author)
	r.mutex.RUnlock()

	if err == sql.ErrNoRows {
		return nil, nil // All quotes excluded
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get unique quote: %w", err)
	}

	return &quote, nil
}

// AddQuote inserts a new quote into the database
func (r *SQLiteRepository) AddQuote(quote models.Quote) error {
	r.mutex.Lock()
	defer r.mutex.Unlock()

	query := `
		INSERT INTO quotes (id, text, author)
		VALUES (?, ?, ?)
		ON CONFLICT(id) DO UPDATE SET
			text = excluded.text,
			author = excluded.author
	`

	_, err := r.db.Exec(query, quote.ID, quote.Text, quote.Author)
	return err
}

// GetQuotesWithPagination retrieves quotes with pagination and filtering
func (r *SQLiteRepository) GetQuotesWithPagination(page, pageSize int, quoteText, author, sortBy, sortOrder string) ([]models.Quote, int, error) {
	r.mutex.RLock()
	defer r.mutex.RUnlock()

	// Build WHERE clause
	whereClause := "WHERE 1=1"
	args := []interface{}{}
	argIndex := 1

	if quoteText != "" {
		whereClause += " AND text LIKE ?"
		args = append(args, "%"+quoteText+"%")
		argIndex++
	}

	if author != "" {
		whereClause += " AND author LIKE ?"
		args = append(args, "%"+author+"%")
		argIndex++
	}

	// Build ORDER BY clause
	orderBy := "ORDER BY id"
	validSortFields := map[string]bool{"id": true, "text": true, "author": true}
	validSortOrders := map[string]bool{"asc": true, "desc": true}

	if sortBy != "" && validSortFields[sortBy] {
		order := "ASC"
		if sortOrder != "" && validSortOrders[sortOrder] {
			order = strings.ToUpper(sortOrder)
		}
		orderBy = "ORDER BY " + sortBy + " " + order
	}

	// Get total count
	countQuery := "SELECT COUNT(*) FROM quotes " + whereClause
	var totalCount int
	err := r.db.QueryRow(countQuery, args...).Scan(&totalCount)
	if err != nil {
		return nil, 0, fmt.Errorf("failed to count quotes: %w", err)
	}

	// Calculate pagination
	offset := (page - 1) * pageSize
	if offset < 0 {
		offset = 0
	}

	// Get paginated results
	query := fmt.Sprintf(`
		SELECT id, text, author 
		FROM quotes 
		%s 
		%s 
		LIMIT ? OFFSET ?
	`, whereClause, orderBy)

	args = append(args, pageSize, offset)

	rows, err := r.db.Query(query, args...)
	if err != nil {
		return nil, 0, fmt.Errorf("failed to query quotes: %w", err)
	}
	defer rows.Close()

	var quotes []models.Quote
	for rows.Next() {
		var quote models.Quote
		err := rows.Scan(&quote.ID, &quote.Text, &quote.Author)
		if err != nil {
			return nil, 0, fmt.Errorf("failed to scan quote: %w", err)
		}
		quotes = append(quotes, quote)
	}

	return quotes, totalCount, nil
}

// GetAllQuotes retrieves all quotes from the database
func (r *SQLiteRepository) GetAllQuotes() ([]models.Quote, error) {
	r.mutex.RLock()
	defer r.mutex.RUnlock()

	rows, err := r.db.Query(`
		SELECT id, text, author 
		FROM quotes 
		ORDER BY id
	`)
	if err != nil {
		return nil, fmt.Errorf("failed to query quotes: %w", err)
	}
	defer rows.Close()

	var quotes []models.Quote
	for rows.Next() {
		var quote models.Quote
		err := rows.Scan(&quote.ID, &quote.Text, &quote.Author)
		if err != nil {
			return nil, fmt.Errorf("failed to scan quote: %w", err)
		}
		quotes = append(quotes, quote)
	}

	return quotes, nil
}

// GetTotalLikes retrieves the total count of likes across all quotes
func (r *SQLiteRepository) GetTotalLikes() (int, error) {
	r.mutex.RLock()
	defer r.mutex.RUnlock()

	var totalLikes int
	err := r.db.QueryRow("SELECT COUNT(*) FROM user_likes").Scan(&totalLikes)
	if err != nil {
		return 0, fmt.Errorf("failed to get total likes: %w", err)
	}

	return totalLikes, nil
}

// QuoteExists checks if a quote with the same text and author already exists
func (r *SQLiteRepository) QuoteExists(text, author string) (bool, error) {
	r.mutex.RLock()
	defer r.mutex.RUnlock()

	var count int
	query := `SELECT COUNT(*) FROM quotes WHERE text = ? AND author = ?`
	err := r.db.QueryRow(query, text, author).Scan(&count)
	if err != nil {
		return false, fmt.Errorf("failed to check quote existence: %w", err)
	}

	return count > 0, nil
}

// GetNextAvailableID gets the next available ID from the database
func (r *SQLiteRepository) GetNextAvailableID() (int, error) {
	quotes, err := r.GetAllQuotes()
	if err != nil {
		return 0, fmt.Errorf("failed to get existing quotes: %w", err)
	}

	if len(quotes) == 0 {
		return 1, nil
	}

	maxID := 0
	for _, quote := range quotes {
		if quote.ID > maxID {
			maxID = quote.ID
		}
	}

	return maxID + 1, nil
}
