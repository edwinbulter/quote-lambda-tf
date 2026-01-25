# JWT Authentication Implementation Guide

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
  - [Authentication Flow](#authentication-flow)
  - [Database Schema](#database-schema)
    - [Users Table](#users-table)
    - [UserRoles Table](#userroles-table)
- [Implementation Steps](#implementation-steps)
  - [1. Database Models](#1-database-models)
    - [User Model](#user-model)
  - [2. JWT Service](#2-jwt-service)
  - [3. Password Service](#3-password-service)
  - [4. Authentication Repository](#4-authentication-repository)
  - [5. Authentication Service](#5-authentication-service)
  - [6. Authentication Middleware](#6-authentication-middleware)
  - [7. Authentication Handlers](#7-authentication-handlers)
  - [8. Database Migration](#8-database-migration)
  - [9. Integration with Main Application](#9-integration-with-main-application)
  - [10. Environment Variables](#10-environment-variables)
  - [11. API Testing](#11-api-testing)
  - [12. Dependencies](#12-dependencies)
- [Security Considerations](#security-considerations)
- [Testing](#testing)
- [Next Steps](#next-steps)

## Overview

This document describes how to implement self-hosted JWT authentication for the Quote OVHcloud Backend, following the same authentication pattern as the Quote Azure Backend. The implementation uses the same SQLite database to store users and user roles, with JWT tokens for secure authentication.

## Architecture

### Authentication Flow
```
Client → Register/Login → JWT Token → Protected Endpoints
```

### Database Schema
The authentication system uses the same tables as the Azure backend:

#### Users Table
```sql
CREATE TABLE users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) DEFAULT 'user',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT 1
);
```

#### UserRoles Table (for future extensibility)
```sql
CREATE TABLE user_roles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

## Implementation Steps

### 1. Database Models

#### User Model (`internal/models/user.go`)
```go
package models

import (
    "time"
    "database/sql/driver"
    "encoding/json"
    "errors"
)

type User struct {
    ID           int       `json:"id" db:"id"`
    Username     string    `json:"username" db:"username"`
    Email        string    `json:"email" db:"email"`
    PasswordHash string    `json:"-" db:"password_hash"`
    Role         string    `json:"role" db:"role"`
    CreatedAt    time.Time `json:"created_at" db:"created_at"`
    UpdatedAt    time.Time `json:"updated_at" db:"updated_at"`
    IsActive     bool      `json:"is_active" db:"is_active"`
}

type UserRole struct {
    ID        int       `json:"id" db:"id"`
    UserID    int       `json:"user_id" db:"user_id"`
    Role      string    `json:"role" db:"role"`
    CreatedAt time.Time `json:"created_at" db:"created_at"`
}

// User roles constants
const (
    RoleAdmin  = "admin"
    RoleUser   = "user"
    RoleGuest  = "guest"
)

type Role []string

func (r *Role) Scan(value interface{}) error {
    bytes, ok := value.([]byte)
    if !ok {
        return errors.New("type assertion to []byte failed")
    }
    return json.Unmarshal(bytes, &r)
}

func (r Role) Value() (driver.Value, error) {
    return json.Marshal(r)
}
```

### 2. JWT Service

#### JWT Service (`internal/auth/jwt.go`)
```go
package auth

import (
    "fmt"
    "time"
    "github.com/golang-jwt/jwt/v5"
)

type JWTService struct {
    secretKey string
    expiry    time.Duration
}

type Claims struct {
    UserID   int    `json:"user_id"`
    Username string `json:"username"`
    Role     string `json:"role"`
    jwt.RegisteredClaims
}

func NewJWTService(secretKey string, expiry time.Duration) *JWTService {
    return &JWTService{
        secretKey: secretKey,
        expiry:    expiry,
    }
}

func (j *JWTService) GenerateToken(userID int, username, role string) (string, error) {
    claims := &Claims{
        UserID:   userID,
        Username: username,
        Role:     role,
        RegisteredClaims: jwt.RegisteredClaims{
            ExpiresAt: jwt.NewNumericDate(time.Now().Add(j.expiry)),
            IssuedAt:  jwt.NewNumericDate(time.Now()),
            NotBefore: jwt.NewNumericDate(time.Now()),
        },
    }

    token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
    return token.SignedString([]byte(j.secretKey))
}

func (j *JWTService) ValidateToken(tokenString string) (*Claims, error) {
    token, err := jwt.ParseWithClaims(tokenString, &Claims{}, func(token *jwt.Token) (interface{}, error) {
        if _, ok := token.Method.(*jwt.SigningMethodHMAC); !ok {
            return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
        }
        return []byte(j.secretKey), nil
    })

    if err != nil {
        return nil, err
    }

    if claims, ok := token.Claims.(*Claims); ok && token.Valid {
        return claims, nil
    }

    return nil, fmt.Errorf("invalid token")
}
```

### 3. Password Service

#### Password Service (`internal/auth/password.go`)
```go
package auth

import (
    "golang.org/x/crypto/bcrypt"
)

type PasswordService struct{}

func NewPasswordService() *PasswordService {
    return &PasswordService{}
}

func (p *PasswordService) HashPassword(password string) (string, error) {
    bytes, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
    return string(bytes), err
}

func (p *PasswordService) CheckPassword(password, hash string) error {
    return bcrypt.CompareHashAndPassword([]byte(hash), []byte(password))
}
```

### 4. Authentication Repository

#### Auth Repository (`internal/repository/auth.go`)
```go
package repository

import (
    "database/sql"
    "time"
    "github.com/your-org/quote-ovhc-backend/internal/models"
)

type AuthRepository struct {
    db *sql.DB
}

func NewAuthRepository(db *sql.DB) *AuthRepository {
    return &AuthRepository{db: db}
}

func (r *AuthRepository) CreateUser(user *models.User) error {
    query := `
        INSERT INTO users (username, email, password_hash, role, created_at, updated_at, is_active)
        VALUES (?, ?, ?, ?, ?, ?, ?)
    `
    
    now := time.Now()
    user.CreatedAt = now
    user.UpdatedAt = now
    
    result, err := r.db.Exec(query, 
        user.Username, 
        user.Email, 
        user.PasswordHash, 
        user.Role, 
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
    return nil
}

func (r *AuthRepository) GetUserByUsername(username string) (*models.User, error) {
    query := `
        SELECT id, username, email, password_hash, role, created_at, updated_at, is_active
        FROM users
        WHERE username = ? AND is_active = 1
    `
    
    var user models.User
    err := r.db.QueryRow(query, username).Scan(
        &user.ID,
        &user.Username,
        &user.Email,
        &user.PasswordHash,
        &user.Role,
        &user.CreatedAt,
        &user.UpdatedAt,
        &user.IsActive,
    )
    
    if err != nil {
        return nil, err
    }
    
    return &user, nil
}

func (r *AuthRepository) GetUserByEmail(email string) (*models.User, error) {
    query := `
        SELECT id, username, email, password_hash, role, created_at, updated_at, is_active
        FROM users
        WHERE email = ? AND is_active = 1
    `
    
    var user models.User
    err := r.db.QueryRow(query, email).Scan(
        &user.ID,
        &user.Username,
        &user.Email,
        &user.PasswordHash,
        &user.Role,
        &user.CreatedAt,
        &user.UpdatedAt,
        &user.IsActive,
    )
    
    if err != nil {
        return nil, err
    }
    
    return &user, nil
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

func (r *AuthRepository) CreateAdminUser(username, email, passwordHash string) error {
    user := &models.User{
        Username:     username,
        Email:        email,
        PasswordHash: passwordHash,
        Role:         models.RoleAdmin,
        IsActive:     true,
    }
    
    return r.CreateUser(user)
}
```

### 5. Authentication Service

#### Auth Service (`internal/service/auth.go`)
```go
package service

import (
    "errors"
    "fmt"
    "github.com/your-org/quote-ovhc-backend/internal/auth"
    "github.com/your-org/quote-ovhc-backend/internal/models"
    "github.com/your-org/quote-ovhc-backend/internal/repository"
)

type AuthService struct {
    authRepo       *repository.AuthRepository
    jwtService     *auth.JWTService
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
    Username string `json:"username" validate:"required"`
    Password string `json:"password" validate:"required"`
}

type AuthResponse struct {
    Token    string      `json:"token"`
    User     *models.User `json:"user"`
    ExpiresIn int64      `json:"expires_in"`
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
    
    // Create user
    user := &models.User{
        Username:     req.Username,
        Email:        req.Email,
        PasswordHash: passwordHash,
        Role:         models.RoleUser,
        IsActive:     true,
    }
    
    err = s.authRepo.CreateUser(user)
    if err != nil {
        return nil, fmt.Errorf("error creating user: %w", err)
    }
    
    // Generate token
    token, err := s.jwtService.GenerateToken(user.ID, user.Username, user.Role)
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
    // Get user by username
    user, err := s.authRepo.GetUserByUsername(req.Username)
    if err != nil {
        return nil, errors.New("invalid username or password")
    }
    
    // Check password
    err = s.passwordService.CheckPassword(req.Password, user.PasswordHash)
    if err != nil {
        return nil, errors.New("invalid username or password")
    }
    
    // Generate token
    token, err := s.jwtService.GenerateToken(user.ID, user.Username, user.Role)
    if err != nil {
        return nil, fmt.Errorf("error generating token: %w", err)
    }
    
    return &AuthResponse{
        Token:     token,
        User:      user,
        ExpiresIn: 3600, // 1 hour in seconds
    }, nil
}

func (s *AuthService) CreateAdminUser(username, email, password string) error {
    // Hash password
    passwordHash, err := s.passwordService.HashPassword(password)
    if err != nil {
        return fmt.Errorf("error hashing password: %w", err)
    }
    
    // Create admin user
    err = s.authRepo.CreateAdminUser(username, email, passwordHash)
    if err != nil {
        return fmt.Errorf("error creating admin user: %w", err)
    }
    
    return nil
}
```

### 6. Authentication Middleware

#### JWT Middleware (`internal/middleware/auth.go`)
```go
package middleware

import (
    "net/http"
    "strings"
    "github.com/gin-gonic/gin"
    "github.com/your-org/quote-ovhc-backend/internal/auth"
)

func JWTMiddleware(jwtService *auth.JWTService) gin.HandlerFunc {
    return func(c *gin.Context) {
        authHeader := c.GetHeader("Authorization")
        if authHeader == "" {
            c.JSON(http.StatusUnauthorized, gin.H{"error": "Authorization header required"})
            c.Abort()
            return
        }
        
        // Extract token from "Bearer <token>"
        tokenString := strings.Replace(authHeader, "Bearer ", "", 1)
        
        // Validate token
        claims, err := jwtService.ValidateToken(tokenString)
        if err != nil {
            c.JSON(http.StatusUnauthorized, gin.H{"error": "Invalid token"})
            c.Abort()
            return
        }
        
        // Set user context
        c.Set("user_id", claims.UserID)
        c.Set("username", claims.Username)
        c.Set("role", claims.Role)
        
        c.Next()
    }
}

func RequireRole(requiredRole string) gin.HandlerFunc {
    return func(c *gin.Context) {
        role, exists := c.Get("role")
        if !exists {
            c.JSON(http.StatusForbidden, gin.H{"error": "Role not found"})
            c.Abort()
            return
        }
        
        userRole, ok := role.(string)
        if !ok || userRole != requiredRole {
            c.JSON(http.StatusForbidden, gin.H{"error": "Insufficient permissions"})
            c.Abort()
            return
        }
        
        c.Next()
    }
}

func RequireAdmin() gin.HandlerFunc {
    return RequireRole("admin")
}
```

### 7. Authentication Handlers

#### Auth Handlers (`internal/handlers/auth.go`)
```go
package handlers

import (
    "net/http"
    "github.com/gin-gonic/gin"
    "github.com/your-org/quote-ovhc-backend/internal/service"
    "github.com/your-org/quote-ovhc-backend/internal/models"
)

type AuthHandler struct {
    authService *service.AuthService
}

func NewAuthHandler(authService *service.AuthService) *AuthHandler {
    return &AuthHandler{
        authService: authService,
    }
}

func (h *AuthHandler) Register(c *gin.Context) {
    var req service.RegisterRequest
    if err := c.ShouldBindJSON(&req); err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
        return
    }
    
    response, err := h.authService.Register(&req)
    if err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
        return
    }
    
    c.JSON(http.StatusCreated, response)
}

func (h *AuthHandler) Login(c *gin.Context) {
    var req service.LoginRequest
    if err := c.ShouldBindJSON(&req); err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
        return
    }
    
    response, err := h.authService.Login(&req)
    if err != nil {
        c.JSON(http.StatusUnauthorized, gin.H{"error": err.Error()})
        return
    }
    
    c.JSON(http.StatusOK, response)
}

func (h *AuthHandler) SeedAdminUser(c *gin.Context) {
    masterKey := c.Query("code")
    if masterKey != "YOUR_MASTER_KEY_HERE" {
        c.JSON(http.StatusForbidden, gin.H{"error": "Invalid master key"})
        return
    }
    
    username := c.Query("username")
    email := c.Query("email")
    password := c.Query("password")
    
    if username == "" || email == "" || password == "" {
        c.JSON(http.StatusBadRequest, gin.H{"error": "username, email, and password required"})
        return
    }
    
    err := h.authService.CreateAdminUser(username, email, password)
    if err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
        return
    }
    
    c.JSON(http.StatusOK, gin.H{"message": "Admin user created successfully"})
}

func (h *AuthHandler) GetProfile(c *gin.Context) {
    userID, _ := c.Get("user_id")
    username, _ := c.Get("username")
    role, _ := c.Get("role")
    
    c.JSON(http.StatusOK, gin.H{
        "user_id":  userID,
        "username": username,
        "role":     role,
    })
}
```

### 8. Database Migration

#### Migration Script (`migrations/001_create_auth_tables.sql`)
```sql
-- Create users table
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) DEFAULT 'user',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT 1
);

-- Create user_roles table (for future use)
CREATE TABLE IF NOT EXISTS user_roles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_active ON users(is_active);
CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON user_roles(user_id);
```

### 9. Integration with Main Application

#### Update Main Application (`cmd/quote-backend/main.go`)
```go
package main

import (
    "log"
    "time"
    "github.com/gin-gonic/gin"
    "github.com/your-org/quote-ovhc-backend/internal/auth"
    "github.com/your-org/quote-ovhc-backend/internal/handlers"
    "github.com/your-org/quote-ovhc-backend/internal/middleware"
    "github.com/your-org/quote-ovhc-backend/internal/repository"
    "github.com/your-org/quote-ovhc-backend/internal/service"
)

func main() {
    // Initialize database (existing code)
    db := initializeDatabase()
    
    // Run migrations
    if err := runMigrations(db); err != nil {
        log.Fatal("Failed to run migrations:", err)
    }
    
    // Initialize services
    jwtService := auth.NewJWTService(getJWTSecret(), time.Hour)
    passwordService := auth.NewPasswordService()
    authRepo := repository.NewAuthRepository(db)
    authService := service.NewAuthService(authRepo, jwtService, passwordService)
    
    // Initialize handlers
    authHandler := handlers.NewAuthHandler(authService)
    
    // Setup router
    router := gin.Default()
    
    // Public routes
    public := router.Group("/api/v1")
    {
        public.POST("/auth/register", authHandler.Register)
        public.POST("/auth/login", authHandler.Login)
        public.POST("/seed-users", authHandler.SeedAdminUser)
    }
    
    // Protected routes
    protected := router.Group("/api/v1")
    protected.Use(middleware.JWTMiddleware(jwtService))
    {
        protected.GET("/auth/profile", authHandler.GetProfile)
        // Add other protected routes here
    }
    
    // Admin routes
    admin := protected.Group("/admin")
    admin.Use(middleware.RequireAdmin())
    {
        // Add admin-only routes here
    }
    
    // Start server
    log.Println("Server starting on :8080")
    router.Run(":8080")
}

func getJWTSecret() string {
    // Get from environment variable or use default
    if secret := os.Getenv("JWT_SECRET"); secret != "" {
        return secret
    }
    return "your-super-secret-jwt-key-change-in-production"
}

func runMigrations(db *sql.DB) error {
    // Read and execute migration file
    migration, err := ioutil.ReadFile("migrations/001_create_auth_tables.sql")
    if err != nil {
        return err
    }
    
    _, err = db.Exec(string(migration))
    return err
}
```

### 10. Environment Variables

Add to your `.env` file:
```bash
# JWT Configuration
JWT_SECRET=your-super-secret-jwt-key-change-in-production
JWT_EXPIRY=1h

# Master Key for admin seeding
MASTER_KEY=your-super-secret-master-key
```

### 11. API Testing

Update your `test-api.http` file:
```http
### Register User
POST {{baseUrl}}/api/v1/auth/register
Content-Type: application/json

{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123"
}

### Login User
POST {{baseUrl}}/api/v1/auth/login
Content-Type: application/json

{
    "username": "testuser",
    "password": "password123"
}

### Seed Admin User
POST {{baseUrl}}/api/v1/seed-users?code={{master_key}}&username=admin&email=admin@example.com&password=admin123

### Login Admin
POST {{baseUrl}}/api/v1/auth/login
Content-Type: application/json

{
    "username": "admin",
    "password": "admin123"
}

### Get Profile (Protected)
GET {{baseUrl}}/api/v1/auth/profile
Authorization: Bearer {{auth_token}}
```

### 12. Dependencies

Add to your `go.mod`:
```bash
go get github.com/golang-jwt/jwt/v5
go get github.com/gin-gonic/gin
go get golang.org/x/crypto/bcrypt
go get github.com/go-playground/validator/v10
```

## Security Considerations

1. **JWT Secret**: Use a strong, random secret key in production
2. **Password Hashing**: Use bcrypt with appropriate cost factor
3. **Token Expiry**: Set reasonable token expiration times
4. **HTTPS**: Always use HTTPS in production
5. **Input Validation**: Validate all user inputs
6. **Rate Limiting**: Implement rate limiting for auth endpoints
7. **Master Key**: Keep the master key secure and rotate regularly

## Testing

1. Run the application
2. Create admin user using seed endpoint
3. Register a new user
4. Login to get JWT token
5. Use token to access protected endpoints
6. Test role-based access control

## Next Steps

1. Add password reset functionality
2. Implement refresh tokens
3. Add email verification
4. Implement rate limiting
5. Add audit logging
6. Set up monitoring and alerting

This implementation provides a secure, scalable authentication system that follows the same patterns as the Azure backend while being optimized for the OVHcloud environment.
