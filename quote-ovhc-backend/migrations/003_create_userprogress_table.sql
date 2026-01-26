-- Create user_progress table
CREATE TABLE IF NOT EXISTS user_progress (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    last_quote_id INTEGER NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create index for faster lookups
CREATE INDEX IF NOT EXISTS idx_user_progress_user_id ON user_progress(user_id);

-- Create trigger to automatically update updated_at
CREATE TRIGGER IF NOT EXISTS update_user_progress_updated_at
    AFTER UPDATE ON user_progress
    FOR EACH ROW
BEGIN
    UPDATE user_progress SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

-- Verify table was created
SELECT 'user_progress table created successfully' as result;
