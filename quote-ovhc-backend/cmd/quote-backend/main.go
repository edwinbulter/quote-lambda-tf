package main

import (
	"context"
	"database/sql"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/s3"

	"quote-ovhc-backend/internal/auth"
	"quote-ovhc-backend/internal/handlers"
	"quote-ovhc-backend/internal/models"
	"quote-ovhc-backend/internal/repository"
	"quote-ovhc-backend/internal/service"
	"quote-ovhc-backend/internal/services"
	"quote-ovhc-backend/internal/storage"

	_ "github.com/mattn/go-sqlite3"
)

// QuoteStore orchestrates all storage operations
type QuoteStore struct {
	sqliteRepo *storage.SQLiteRepository
	s3Storage  *storage.S3Storage
	zenQuotes  *services.ZenQuotesService
}

// NewQuoteStore creates a new quote store with all dependencies
func NewQuoteStore(sqliteRepo *storage.SQLiteRepository, s3Storage *storage.S3Storage, zenQuotes *services.ZenQuotesService) *QuoteStore {
	return &QuoteStore{
		sqliteRepo: sqliteRepo,
		s3Storage:  s3Storage,
		zenQuotes:  zenQuotes,
	}
}

// Initialize sets up the quote store with data
func (qs *QuoteStore) Initialize() error {
	// Load existing quotes from S3 JSON backup (using LegacyQuote for backward compatibility)
	if legacyQuotes, err := qs.s3Storage.LoadJSONBackup(); err == nil && legacyQuotes != nil {
		for _, legacyQuote := range legacyQuotes {
			// Convert legacy quote to new simplified schema (only id, text, author)
			simplifiedQuote := models.Quote{
				ID:     legacyQuote.ID,
				Text:   legacyQuote.Text,
				Author: legacyQuote.Author,
			}
			if err := qs.sqliteRepo.AddQuote(simplifiedQuote); err != nil {
				log.Printf("Failed to insert quote %d from S3: %v", legacyQuote.ID, err)
			}
		}
		log.Printf("Loaded %d quotes from S3 into SQLite database", len(legacyQuotes))
	}

	// If no quotes exist, fetch from ZenQuotes API
	count, err := qs.sqliteRepo.GetQuoteCount()
	if err != nil {
		return fmt.Errorf("failed to get quote count: %w", err)
	}
	if count == 0 {
		if err := qs.fetchQuotesFromAPI(); err != nil {
			log.Printf("Warning: Failed to fetch initial quotes from API: %v", err)
		}
	}

	return nil
}

// fetchQuotesFromAPI fetches quotes from ZenQuotes API when database is empty
func (qs *QuoteStore) fetchQuotesFromAPI() error {
	log.Printf("Database is empty, fetching initial quotes from ZenQuotes API")

	quotes, err := qs.zenQuotes.GetMultipleQuotes()
	if err != nil {
		return fmt.Errorf("failed to fetch quotes from API: %w", err)
	}

	// Get next available ID
	nextID, err := qs.sqliteRepo.GetNextAvailableID()
	if err != nil {
		return fmt.Errorf("failed to get next available ID: %w", err)
	}

	// Add fetched quotes to database
	addedCount := 0
	for i, quote := range quotes {
		quote.ID = nextID + i
		if err := qs.sqliteRepo.AddQuote(quote); err != nil {
			log.Printf("Failed to add fetched quote %d: %v", quote.ID, err)
			continue
		}
		addedCount++
	}

	log.Printf("Successfully added %d quotes from ZenQuotes API", addedCount)

	// Save to S3
	if err := qs.SaveToS3(); err != nil {
		log.Printf("Warning: Failed to save initial quotes to S3: %v", err)
	}

	return nil
}

// SaveToS3 saves the current quotes to S3
func (qs *QuoteStore) SaveToS3() error {
	// Upload the SQLite database file
	if err := qs.s3Storage.UploadDatabase(); err != nil {
		log.Printf("Warning: Failed to upload database to S3: %v", err)
	}

	// Also save JSON backup for compatibility
	quotes, err := qs.sqliteRepo.GetAllQuotes()
	if err != nil {
		return fmt.Errorf("failed to get quotes for S3 backup: %w", err)
	}

	if err := qs.s3Storage.SaveJSONBackup(quotes); err != nil {
		return fmt.Errorf("failed to save JSON backup to S3: %w", err)
	}

	log.Printf("Successfully saved both database file and JSON backup to S3")
	return nil
}

// initializeS3Client initializes the S3 client for OVHcloud Object Storage
func initializeS3Client() (*s3.Client, error) {
	// Get configuration from environment variables
	region := getEnv("S3_REGION", "GRA")
	endpoint := getEnv("S3_ENDPOINT", "https://s3.gra.cloud.ovh.net")
	accessKey := getEnv("S3_ACCESS_KEY", "")
	secretKey := getEnv("S3_SECRET_KEY", "")

	if accessKey == "" || secretKey == "" {
		return nil, fmt.Errorf("S3 credentials not set in environment variables")
	}

	cfg, err := config.LoadDefaultConfig(context.TODO(),
		config.WithRegion(region),
		config.WithCredentialsProvider(aws.CredentialsProviderFunc(func(ctx context.Context) (aws.Credentials, error) {
			return aws.Credentials{
				AccessKeyID:     accessKey,
				SecretAccessKey: secretKey,
			}, nil
		})),
		config.WithEndpointResolverWithOptions(aws.EndpointResolverWithOptionsFunc(
			func(service, region string, options ...interface{}) (aws.Endpoint, error) {
				return aws.Endpoint{
					URL:           endpoint,
					SigningRegion: region,
				}, nil
			},
		)),
	)

	if err != nil {
		return nil, fmt.Errorf("failed to load AWS config: %w", err)
	}

	return s3.NewFromConfig(cfg), nil
}

// getEnv gets an environment variable or returns a default value
func createUserProgressTableFallback(db *sql.DB) error {
	// Check if table already exists
	var count int
	err := db.QueryRow("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='user_progress'").Scan(&count)
	if err != nil {
		return fmt.Errorf("failed to check if user_progress table exists: %w", err)
	}

	if count > 0 {
		log.Printf("user_progress table already exists")
		return nil
	}

	log.Printf("Creating user_progress table as fallback...")

	// Create the table
	createTableSQL := `
	CREATE TABLE IF NOT EXISTS user_progress (
		id INTEGER PRIMARY KEY AUTOINCREMENT,
		user_id INTEGER NOT NULL,
		last_quote_id INTEGER NOT NULL DEFAULT 0,
		created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
		updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
		FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
	);`

	_, err = db.Exec(createTableSQL)
	if err != nil {
		return fmt.Errorf("failed to create user_progress table: %w", err)
	}

	// Create index
	_, err = db.Exec("CREATE INDEX IF NOT EXISTS idx_user_progress_user_id ON user_progress(user_id)")
	if err != nil {
		return fmt.Errorf("failed to create user_progress index: %w", err)
	}

	log.Printf("user_progress table created successfully via fallback")
	return nil
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

// ensureDefaultAdminUser creates a default admin user if no admin users exist
func ensureDefaultAdminUser(authService *service.AuthService, authRepo *repository.AuthRepository, passwordService *auth.PasswordService) error {
	// Check if any admin users exist
	users, err := authRepo.GetAllUsers()
	if err != nil {
		return fmt.Errorf("failed to get users: %w", err)
	}

	// Check if any user has admin role
	hasAdmin := false
	for _, user := range users {
		for _, role := range user.Roles {
			if role == models.RoleAdmin {
				hasAdmin = true
				break
			}
		}
		if hasAdmin {
			break
		}
	}

	if !hasAdmin {
		log.Println("No admin users found, creating default admin user...")

		// Create default admin user registration request
		defaultPassword := "Hello-admin!"
		registerReq := &service.RegisterRequest{
			Username: "admin",
			Email:    "admin@quote-backend.local",
			Password: defaultPassword,
		}

		// Register the user
		_, err = authService.Register(registerReq)
		if err != nil {
			return fmt.Errorf("failed to create default admin user: %w", err)
		}

		// Get the created user's ID
		createdUser, err := authRepo.GetUserByUsername("admin")
		if err != nil {
			return fmt.Errorf("failed to get created admin user: %w", err)
		}

		// Assign admin role
		err = authRepo.AddUserRole(createdUser.ID, models.RoleAdmin)
		if err != nil {
			return fmt.Errorf("failed to assign admin role: %w", err)
		}

		log.Printf("Default admin user created successfully (username: admin, password: %s)", defaultPassword)
		log.Println("IMPORTANT: Please change the default admin password immediately!")
	} else {
		log.Println("Admin user(s) already exist, skipping default admin creation")
	}

	return nil
}

func main() {
	log.Println("Starting Quote Backend for OVHcloud")

	// Initialize S3 client
	s3Client, err := initializeS3Client()
	if err != nil {
		log.Fatalf("Failed to initialize S3 client: %v", err)
	}

	// Get bucket name from environment
	bucket := getEnv("S3_BUCKET", "quote-storage")
	log.Printf("Using S3 bucket: %s", bucket)

	// Open SQLite database
	db, err := sql.Open("sqlite3", "./quotes.db")
	if err != nil {
		log.Fatalf("Failed to open database: %v", err)
	}
	defer db.Close()

	// Test connection
	if err := db.Ping(); err != nil {
		log.Fatalf("Failed to connect to database: %v", err)
	}

	// Initialize repositories and services
	sqliteRepo := storage.NewSQLiteRepository(db)
	s3Storage := storage.NewS3Storage(s3Client, bucket)
	zenQuotes := services.NewZenQuotesService()

	// Initialize database schema
	if err := sqliteRepo.InitSchema(); err != nil {
		log.Fatalf("Failed to initialize schema: %v", err)
	}

	// Try to download database from S3 first
	if err := s3Storage.DownloadDatabase(); err != nil {
		log.Printf("Warning: Could not download database from S3: %v (will create new one)", err)
	}

	// Run authentication migrations AFTER S3 download
	if err := runMigrations(db); err != nil {
		log.Printf("Warning: Failed to run auth migrations: %v", err)
	}

	// Create quote store
	quoteStore := NewQuoteStore(sqliteRepo, s3Storage, zenQuotes)

	// Initialize with data
	if err := quoteStore.Initialize(); err != nil {
		log.Fatalf("Failed to initialize quote store: %v", err)
	}

	// Upload database to S3 to persist for future deployments
	if err := s3Storage.UploadDatabase(); err != nil {
		log.Printf("Warning: Failed to upload database to S3: %v", err)
	} else {
		log.Println("Successfully uploaded database to S3")
	}

	// Get count from database
	count, err := sqliteRepo.GetQuoteCount()
	if err != nil {
		log.Fatalf("Failed to get quote count: %v", err)
	}
	log.Printf("Quote store initialized with %d quotes", count)

	// Initialize authentication services
	jwtSecret := getEnv("JWT_SECRET", "your-super-secret-jwt-key-change-in-production")
	jwtService := auth.NewJWTService(jwtSecret, time.Hour)
	passwordService := auth.NewPasswordService()
	authRepo := repository.NewAuthRepository(db)
	authService := service.NewAuthService(authRepo, jwtService, passwordService)
	adminService := service.NewAdminService(sqliteRepo, zenQuotes)
	authHandler := handlers.NewAuthHandler(authService, adminService, jwtService)

	// Check and create default admin user if none exists
	if err := ensureDefaultAdminUser(authService, authRepo, passwordService); err != nil {
		log.Printf("Warning: Failed to ensure default admin user: %v", err)
	}

	// Initialize user progress service
	userProgressRepo := repository.NewUserProgressRepository(db)
	userProgressService := service.NewUserProgressService(userProgressRepo)

	// Initialize user like service
	userLikeRepo := repository.NewUserLikeRepository(db)
	userLikeService := service.NewUserLikeService(userLikeRepo, sqliteRepo)

	// Fallback: Create user_progress table manually if migration didn't work
	err = createUserProgressTableFallback(db)
	if err != nil {
		log.Printf("Warning: Failed to create user_progress table fallback: %v", err)
	}

	// Create and start server
	server := NewServer(sqliteRepo, s3Storage, zenQuotes, authHandler, jwtService, userProgressService, userLikeService)
	port := getEnv("PORT", "8080")

	log.Printf("Server ready to accept requests on port %s", port)
	if err := server.Start(port); err != nil {
		log.Fatalf("Failed to start server: %v", err)
	}
}
