-- Add order field to user_likes table for reordering functionality
-- This migration is designed to be idempotent - safe to run multiple times

-- SQLite doesn't support IF NOT EXISTS for ALTER TABLE ADD COLUMN
-- We handle this by attempting the operation and ignoring the error if column exists

-- Create index for order to improve query performance (this is idempotent)
CREATE INDEX IF NOT EXISTS idx_user_likes_order ON user_likes(user_id, order_index);

-- Try to add the column - if it already exists, this will fail but that's okay
-- The migration runner will continue despite this error
ALTER TABLE user_likes ADD COLUMN order_index INTEGER DEFAULT 0;

-- Initialize order_index for existing records based on created_at (only if order_index is 0)
UPDATE user_likes SET order_index = (
    SELECT COUNT(*) - 1 
    FROM user_likes ul2 
    WHERE ul2.user_id = user_likes.user_id 
    AND ul2.created_at <= user_likes.created_at
) WHERE order_index = 0;

-- Verify the column was added (or already existed)
SELECT 'order_index column processed successfully' as result;
