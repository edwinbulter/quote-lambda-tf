-- Database Setup for User Roles
-- This script helps initialize the UserRoles table with admin users

-- Note: This is for reference. The actual implementation uses Azure Table Storage.
-- Use the HTTP endpoints or Azure CLI to manage roles.

-- Example: Assign first admin user via REST API
-- POST /api/admin/userrole/{user-object-id}/role
-- {
--   "role": "ADMIN",
--   "email": "admin@example.com"
-- }

-- Get all users with roles:
-- GET /api/admin/userrole

-- Check if user is admin:
-- GET /api/admin/userrole/{user-object-id}/role
