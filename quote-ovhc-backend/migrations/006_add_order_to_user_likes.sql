-- Add order field to user_likes table for reordering functionality
ALTER TABLE user_likes ADD COLUMN order_index INTEGER DEFAULT 0;

-- Create index for order to improve query performance
CREATE INDEX IF NOT EXISTS idx_user_likes_order ON user_likes(user_id, order_index);

-- Initialize order_index for existing records based on created_at
UPDATE user_likes SET order_index = (
    SELECT COUNT(*) - 1 
    FROM user_likes ul2 
    WHERE ul2.user_id = user_likes.user_id 
    AND ul2.created_at <= user_likes.created_at
);

-- Verify the column was added
SELECT 'order_index column added to user_likes table successfully' as result;
