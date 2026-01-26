package storage

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"time"

	"quote-ovhc-backend/internal/models"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/s3"
)

// LegacyQuote represents the old quote schema with extra fields
// Used only for loading from S3 backup during schema transition
type LegacyQuote struct {
	ID        int       `json:"id" db:"id"`
	Text      string    `json:"text" db:"text"`
	Author    string    `json:"author" db:"author"`
	LikeCount int       `json:"likeCount" db:"like_count"`
	CreatedAt time.Time `json:"createdAt" db:"created_at"`
	Source    string    `json:"source" db:"source"`
}

// S3Storage handles S3 persistence operations
type S3Storage struct {
	client *s3.Client
	bucket string
	logger *log.Logger
}

// NewS3Storage creates a new S3 storage instance
func NewS3Storage(client *s3.Client, bucket string) *S3Storage {
	return &S3Storage{
		client: client,
		bucket: bucket,
		logger: log.New(os.Stdout, "[S3] ", log.LstdFlags),
	}
}

// DownloadDatabase downloads the SQLite database file from S3
func (s *S3Storage) DownloadDatabase() error {
	result, err := s.client.GetObject(context.TODO(), &s3.GetObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String("quotes.db"),
	})
	if err != nil {
		return fmt.Errorf("failed to download database from S3: %w", err)
	}
	defer result.Body.Close()

	// Create file to store the database
	file, err := os.Create("quotes.db")
	if err != nil {
		return fmt.Errorf("failed to create database file: %w", err)
	}
	defer file.Close()

	// Copy S3 content to file
	_, err = file.ReadFrom(result.Body)
	if err != nil {
		return fmt.Errorf("failed to write database file: %w", err)
	}

	s.logger.Println("Successfully downloaded database from S3")
	return nil
}

// UploadDatabase uploads the SQLite database file to S3
func (s *S3Storage) UploadDatabase() error {
	file, err := os.Open("quotes.db")
	if err != nil {
		return fmt.Errorf("failed to open database file: %w", err)
	}
	defer file.Close()

	_, err = s.client.PutObject(context.TODO(), &s3.PutObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String("quotes.db"),
		Body:   file,
	})

	if err != nil {
		return fmt.Errorf("failed to upload database to S3: %w", err)
	}

	s.logger.Println("Successfully uploaded database to S3")
	return nil
}

// SaveJSONBackup saves quotes as JSON backup to S3 (legacy compatibility)
func (s *S3Storage) SaveJSONBackup(quotes []models.Quote) error {
	quotesData, err := json.MarshalIndent(quotes, "", "  ")
	if err != nil {
		return fmt.Errorf("failed to marshal quotes: %w", err)
	}

	_, err = s.client.PutObject(context.TODO(), &s3.PutObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String("quotes.json"),
		Body:   bytes.NewReader(quotesData),
	})

	if err != nil {
		return fmt.Errorf("failed to save JSON backup to S3: %w", err)
	}

	s.logger.Println("Successfully saved JSON backup to S3")
	return nil
}

// LoadJSONBackup loads quotes from JSON backup from S3
// Returns LegacyQuote to handle old schema during transition
func (s *S3Storage) LoadJSONBackup() ([]LegacyQuote, error) {
	result, err := s.client.GetObject(context.TODO(), &s3.GetObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String("quotes.json"),
	})

	if err != nil {
		// If file doesn't exist, that's okay - we'll start fresh
		s.logger.Printf("No existing JSON backup found in S3: %v", err)
		return nil, nil
	}
	defer result.Body.Close()

	var legacyQuotes []LegacyQuote
	if err := json.NewDecoder(result.Body).Decode(&legacyQuotes); err != nil {
		return nil, fmt.Errorf("failed to decode quotes from S3: %w", err)
	}

	s.logger.Printf("Loaded %d quotes from JSON backup in S3", len(legacyQuotes))
	return legacyQuotes, nil
}
