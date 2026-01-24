package storage

import (
	"database/sql"
	"fmt"
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
		author TEXT NOT NULL,
		like_count INTEGER DEFAULT 0,
		created_at DATETIME NOT NULL,
		source TEXT NOT NULL
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
	err := r.db.QueryRow(`
		SELECT id, text, author, like_count, created_at, source 
		FROM quotes 
		ORDER BY RANDOM() 
		LIMIT 1
	`).Scan(&quote.ID, &quote.Text, &quote.Author, &quote.LikeCount, &quote.CreatedAt, &quote.Source)

	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get random quote: %w", err)
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
		SELECT id, text, author, like_count, created_at, source 
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

	err := r.db.QueryRow(query, args...).Scan(&quote.ID, &quote.Text, &quote.Author, &quote.LikeCount, &quote.CreatedAt, &quote.Source)
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
		INSERT INTO quotes (id, text, author, like_count, created_at, source)
		VALUES (?, ?, ?, ?, ?, ?)
		ON CONFLICT(id) DO UPDATE SET
			text = excluded.text,
			author = excluded.author,
			like_count = excluded.like_count,
			created_at = excluded.created_at,
			source = excluded.source
	`

	_, err := r.db.Exec(query, quote.ID, quote.Text, quote.Author, quote.LikeCount, quote.CreatedAt, quote.Source)
	return err
}

// GetAllQuotes retrieves all quotes from the database
func (r *SQLiteRepository) GetAllQuotes() ([]models.Quote, error) {
	r.mutex.RLock()
	defer r.mutex.RUnlock()

	rows, err := r.db.Query(`
		SELECT id, text, author, like_count, created_at, source 
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
		err := rows.Scan(&quote.ID, &quote.Text, &quote.Author, &quote.LikeCount, &quote.CreatedAt, &quote.Source)
		if err != nil {
			return nil, fmt.Errorf("failed to scan quote: %w", err)
		}
		quotes = append(quotes, quote)
	}

	return quotes, nil
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

// ExecuteQuery executes a custom SQL query (for debugging)
func (r *SQLiteRepository) ExecuteQuery(query string) (*sql.Rows, error) {
	r.mutex.RLock()
	defer r.mutex.RUnlock()

	return r.db.Query(query)
}
