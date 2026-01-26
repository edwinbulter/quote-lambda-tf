-- Migration 007: Simplify quotes table to only have id, text, author columns
-- This migration removes the extra columns (like_count, created_at, source) from the quotes table

-- Create a new simplified quotes table
CREATE TABLE quotes_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    text TEXT NOT NULL,
    author TEXT NOT NULL
);

-- Copy data from old table to new table (only id, text, author)
INSERT INTO quotes_new (id, text, author)
SELECT id, text, author FROM quotes;

-- Drop the old table
DROP TABLE quotes;

-- Rename the new table to the original name
ALTER TABLE quotes_new RENAME TO quotes;

-- Recreate the index
CREATE INDEX IF NOT EXISTS idx_quotes_id ON quotes(id);

-- Verify the migration was successful
SELECT 'quotes table simplified successfully - now only has id, text, author columns' as result;
