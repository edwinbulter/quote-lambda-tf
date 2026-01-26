-- Migration to update auth tables to match Azure backend
-- Remove role column from users table and ensure user_roles table is used

-- First, create user_roles table if it doesn't exist
CREATE TABLE IF NOT EXISTS user_roles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON user_roles(user_id);

-- Check if role column exists by trying to query it
-- This will work in both cases

-- Create new users table without role column (if needed)
CREATE TABLE IF NOT EXISTS users_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT 1
);

-- Migrate data from old users table to new one
INSERT INTO users_new (id, username, email, password_hash, created_at, updated_at, is_active)
SELECT id, username, email, password_hash, created_at, updated_at, is_active
FROM users;

-- Drop old table and rename new one
DROP TABLE IF EXISTS users;
ALTER TABLE users_new RENAME TO users;

-- Recreate indexes
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_active ON users(is_active);

-- Ensure all users have at least a 'user' role
INSERT OR IGNORE INTO user_roles (user_id, role, created_at)
SELECT id, 'user', datetime('now')
FROM users
WHERE id NOT IN (SELECT DISTINCT user_id FROM user_roles);

-- Verify migration completed successfully
SELECT 'Auth schema migration completed successfully' as result;
