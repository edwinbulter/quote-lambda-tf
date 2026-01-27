# Quote Backend - OVHcloud Implementation

A Go backend service for managing quotes with SQLite database and S3 persistence, designed to run on OVHcloud VM instances.

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
  - [Data Flow](#data-flow)
  - [Persistence Strategy](#persistence-strategy)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [S3 Credentials](#s3-credentials)
  - [JWT Setup](#jwt-setup)
  - [Installation](#installation)
- [API Endpoints](#api-endpoints)
  - [Authentication Endpoints](#authentication-endpoints)
    - [POST /api/v1/auth/register](#post-apiv1authregister)
    - [POST /api/v1/auth/login](#post-apiv1authlogin)
    - [POST /api/v1/auth/change-password](#post-apiv1authchange-password)
    - [DELETE /api/v1/auth/unregister](#delete-apiv1authunregister)
  - [Quote Endpoints](#quote-endpoints)
    - [GET /api/v1/quote](#get-apiv1quote)
    - [GET /api/v1/quote/public](#get-apiv1quotepublic)
    - [POST /api/v1/quote](#post-apiv1quote)
    - [GET /api/v1/quote/viewed](#get-apiv1quoteviewed)
    - [GET /api/v1/quote/progress](#get-apiv1quoteprogress)
    - [POST /api/v1/quote/{id}/like](#post-apiv1quoteidlike)
    - [DELETE /api/v1/quote/{id}/unlike](#delete-apiv1quoteidunlike)
    - [GET /api/v1/quote/liked](#get-apiv1quoteliked)
    - [PUT /api/v1/quote/{id}/reorder](#put-apiv1quoteidreorder)
  - [Admin Endpoints](#admin-endpoints)
    - [GET /api/v1/manage/users](#get-apiv1manageusers)
    - [GET /api/v1/manage/quotes](#get-apiv1managequotes)
    - [POST /api/v1/manage/quotes/fetch](#post-apiv1managequotesfetch)
    - [GET /api/v1/manage/stats](#get-apiv1managestats)
    - [PUT /api/v1/manage/users/role](#put-apiv1manageusersrole)
    - [DELETE /api/v1/manage/users/role](#delete-apiv1manageusersrole)
    - [DELETE /api/v1/manage/users/account](#delete-apiv1manageusersaccount)
  - [System Endpoints](#system-endpoints)
    - [GET /health](#get-health)
- [SSL/HTTPS Setup](#sslhttps-setup)
- [Data Persistence](#data-persistence)
  - [S3 Storage Structure](#s3-storage-structure)
  - [SQLite Database Schema](#sqlite-database-schema)
  - [Persistence Strategy](#persistence-strategy-1)
- [Development](#development)
  - [Local Development](#local-development)
  - [GoLand IDE Setup](#goland-ide-setup)
    - [Development Workflow](#development-workflow)
    - [GoLand Run Configuration](#goland-run-configuration)
    - [GoLand Development Features](#goland-development-features)
    - [Benefits of GoLand Development](#benefits-of-goland-development)
  - [Database Debugging](#database-debugging)
  - [Testing](#testing)
    - [HTTP Client Testing](#http-client-testing)
      - [Test File Features](#test-file-features)
      - [Usage Instructions](#usage-instructions)
      - [Test Categories](#test-categories)
    - [Local Testing](#local-testing)
    - [Remote Testing](#remote-testing)
- [Deployment](#deployment)
  - [Deployment on OVHcloud VM](#deployment-on-ovhcloud-vm)
  - [Monitoring](#monitoring)
- [Troubleshooting](#troubleshooting)


## Features

- **SQLite database** with full SQL support and S3 persistence
- **Multiple endpoints**: `GET /quote`, `POST /quote` (with exclusions), debug endpoints
- **OVHcloud Object Storage** integration (S3-compatible)
- **Automatic backup** of database file and JSON to S3 on data changes
- **Health check endpoint**: `GET /health`
- **Sample data** initialization on first run
- **IntelliJ/IDE connectivity** to local database file
- **HTTPS support** with Nginx reverse proxy and Let's Encrypt SSL certificates

## Architecture

### Data Flow
1. **SQLite Database**: Local `quotes.db` file with full SQL support
2. **Development Persistence**: Local database file for IDE connectivity
3. **Production Persistence**: Automatic S3 backup of database file
4. **Dual Storage**: Both `quotes.db` (SQLite) and `quotes.json` (legacy) in S3
5. **Automatic Sync**: Load from S3 on startup, save on changes

### Persistence Strategy

#### Development Environment
- **Local SQLite**: `quotes.db` file for fast access and IDE connectivity
- **IntelliJ Integration**: Connect directly to database file for debugging
- **S3 Backup**: Automatic sync to cloud for safety

#### Production Environment
- **Startup**: Download latest `quotes.db` from S3
- **Runtime**: Local SQLite for optimal performance
- **Persistence**: Auto-upload database changes to S3
- **Disaster Recovery**: Complete database backup in S3 survives VM loss
- **Legacy Support**: JSON backup maintained for compatibility

### Cost Optimization
- **VM**: €5.49/month (D2-2 Discovery instance)
- **Storage**: ~€0.01/month (Object Storage)
- **Total**: ~€5.50/month

## Getting Started

### Prerequisites
- Go 1.21 or higher
- OVHcloud Object Storage container
- S3 credentials from OVHcloud

### S3 Credentials

The application uses a secure `.secrets` file to store sensitive credentials. This file is automatically added to `.gitignore` to ensure secrets are never committed to the repository.

#### Secure Setup with .secrets File

**Step 1: Create .secrets file**
```bash
# Run the setup script (creates .secrets template if missing)
source setup-env.sh
```

**Step 2: Edit the .secrets file**
```bash
# Edit the secrets file with your actual credentials
nano .secrets
```

**Step 3: Update credentials**
```bash
# .secrets file content
# S3 Credentials (replace with your actual S3 credentials from OVHcloud)
# IMPORTANT: Generate these from the S3-compatible container, not Swift!
S3_ACCESS_KEY=YOUR_S3_ACCESS_KEY_HERE
S3_SECRET_KEY=YOUR_S3_SECRET_KEY_HERE

# JWT Secret (optional - uses default if not set)
JWT_SECRET=your-super-secret-jwt-key-change-in-production
```

**Step 4: Load environment variables**
```bash
# Source the script to load secrets into your shell
source setup-env.sh
```

#### Security Features

- 🔒 **Automatic .gitignore**: `.secrets` file is never committed to Git
- 🔒 **Template creation**: Script creates template if file doesn't exist
- 🔒 **Secure loading**: Credentials loaded from file, not exposed in shell history
- 🔒 **Clear placeholders**: Easy to identify what needs to be updated

#### Environment Variables

The setup script automatically loads these variables from the `.secrets` file:

**Option 1: Use the setup script (Recommended)**
```bash
# Creates .secrets template and loads variables
source setup-env.sh

# Alternative options
./setup-env.sh --export      # Export to current shell
./setup-env.sh --env-file    # Create .env file
./setup-env.sh --help        # Show all options
```

**Option 2: Set manually**
```bash
# OVHcloud Object Storage Configuration
export S3_ENDPOINT="https://s3.gra.cloud.ovh.net"
export S3_REGION="GRA"
export S3_BUCKET="quote-storage"
export S3_ACCESS_KEY="your-access-key"
export S3_SECRET_KEY="your-secret-key"

# Server Configuration
export PORT="8080"
```

#### Get S3 Credentials

1. Go to OVHcloud Manager
2. Navigate to Public Cloud → Your Project
3. Click on Object Storage → quote-storage
4. Click on Users tab
5. Find your user and view S3 credentials

**Important**: Use the S3-compatible credentials, not Swift credentials!

### JWT Setup

The application uses JSON Web Tokens (JWT) for authentication and authorization. JWT tokens are used to secure API endpoints and manage user roles.

#### Environment Variables

Set the JWT secret key for token generation and validation:

```bash
# JWT Configuration (Optional - uses default if not set)
export JWT_SECRET="your-super-secret-jwt-key-change-in-production"
```

**Security Notes:**
- Use a strong, random secret key in production
- The default secret should only be used for development
- JWT tokens expire after 1 hour by default

#### Default Admin User

The application automatically creates a default admin user on startup if no admin users exist:

- **Username:** `admin`
- **Default Password:** `Hello-admin!`
- **Role:** `admin`

**Security Warning:** When logging in with the default admin password, the API response includes a warning message prompting you to change the password immediately.

#### Multi-Role Support

The JWT system supports multiple roles per user:
- Users can have multiple roles (e.g., `["admin", "moderator", "user"]`)
- Role-based access control checks against all user roles
- JWT tokens contain the complete roles array for accurate permission validation

#### Authentication Flow

1. **Login:** POST `/api/v1/auth/login` with credentials
2. **Token:** Receive JWT token with user roles
3. **Authorization:** Include `Authorization: Bearer <token>` header
4. **Validation:** Middleware validates token and checks roles

#### For Detailed Information

For comprehensive documentation on JWT implementation, token structure, middleware, and security considerations, see:

📖 **[JWT Authentication Implementation](doc/JWT-Authentication-Implementation.md)**

This document covers:
- JWT token structure and claims
- Middleware implementation details
- Role-based access control
- Security best practices
- Token validation and error handling
- Multi-role user support

### Installation

1. **Clone the repository**:
```bash
cd quote-ovhc-backend
```

2. **Install dependencies**:
```bash
go mod tidy
```

3. **Set up environment variables**:
```bash
source setup-env.sh
```

4. **Run the application**:
```bash
go run main.go
```

5. **Build for production**:
```bash
go build -o quote-backend
./quote-backend
```

## API Endpoints

### Authentication Endpoints

#### POST /api/v1/auth/register
Register a new user account.

**Request Body:**
```json
{
  "username": "testuser",
  "email": "testuser@example.com",
  "password": "password123"
}
```

**Response:**
```json
{
  "message": "User registered successfully",
  "user": {
    "id": 1,
    "username": "testuser",
    "email": "testuser@example.com",
    "roles": ["user"]
  }
}
```

**Status Codes:**
- `201 Created`: User registered successfully
- `400 Bad Request`: Invalid request data
- `409 Conflict`: User already exists

#### POST /api/v1/auth/login
Authenticate user and receive JWT token.

**Request Body:**
```json
{
  "loginIdentifier": "testuser",
  "password": "password123"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": 1,
    "username": "testuser",
    "email": "testuser@example.com",
    "roles": ["user"]
  },
  "expires_in": 3600,
  "default_password_warning": "You are using the default admin password. Please change it immediately for security."
}
```

**Status Codes:**
- `200 OK`: Login successful
- `401 Unauthorized`: Invalid credentials
- `404 Not Found`: User not found

#### POST /api/v1/auth/change-password
Change user password (requires authentication).

**Headers:**
```
Authorization: Bearer <jwt_token>
```

**Request Body:**
```json
{
  "currentPassword": "password123",
  "newPassword": "newPassword123",
  "confirmNewPassword": "newPassword123"
}
```

**Response:**
```json
{
  "message": "Password changed successfully"
}
```

**Status Codes:**
- `200 OK`: Password changed successfully
- `400 Bad Request`: Invalid request data
- `401 Unauthorized`: Invalid current password

#### DELETE /api/v1/auth/unregister
Delete user account and all associated data (requires authentication).

**Headers:**
```
Authorization: Bearer <jwt_token>
```

**Request Body:**
```json
{
  "password": "password123"
}
```

**Response:**
```json
{
  "message": "User account deleted successfully"
}
```

**Status Codes:**
- `200 OK`: Account deleted successfully
- `401 Unauthorized`: Invalid password

### Quote Endpoints

#### GET /api/v1/quote
Get a random quote (requires authentication). Tracks user progress and view history.

**Headers:**
```
Authorization: Bearer <jwt_token>
```

**Response:**
```json
{
  "id": 1,
  "text": "Be yourself; everyone else is already taken.",
  "author": "Oscar Wilde"
}
```

**Status Codes:**
- `200 OK`: Quote returned successfully
- `401 Unauthorized`: Authentication required
- `404 Not Found`: No quotes available

#### GET /api/v1/quote/public
Get a random quote without authentication (public access).

**Response:**
```json
{
  "id": 1,
  "text": "Be yourself; everyone else is already taken.",
  "author": "Oscar Wilde"
}
```

**Status Codes:**
- `200 OK`: Quote returned successfully
- `404 Not Found`: No quotes available

#### POST /api/v1/quote
Get a random quote excluding specified IDs (unauthenticated). Automatically fetches more quotes if all existing quotes are excluded.

**Request Body:**
```json
[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
```

**Response:**
```json
{
  "id": 15,
  "text": "The only way to do great work is to love what you do.",
  "author": "Steve Jobs"
}
```

**Status Codes:**
- `200 OK`: Quote returned successfully
- `400 Bad Request`: Invalid request body
- `404 Not Found`: No quotes available

#### GET /api/v1/quote/viewed
Get user's quote viewing history (requires authentication).

**Headers:**
```
Authorization: Bearer <jwt_token>
```

**Response:**
```json
[
  {
    "id": 1,
    "text": "Be yourself; everyone else is already taken.",
    "author": "Oscar Wilde"
  },
  {
    "id": 2,
    "text": "The only way to do great work is to love what you do.",
    "author": "Steve Jobs"
  }
]
```

**Status Codes:**
- `200 OK`: View history returned successfully
- `401 Unauthorized`: Authentication required

#### GET /api/v1/quote/progress
Get user's quote viewing progress (requires authentication).

**Headers:**
```
Authorization: Bearer <jwt_token>
```

**Response:**
```json
{
  "totalQuotes": 100,
  "viewedQuotes": 25,
  "progressPercentage": 25.0,
  "lastViewedQuoteId": 25
}
```

**Status Codes:**
- `200 OK`: Progress returned successfully
- `401 Unauthorized`: Authentication required

#### POST /api/v1/quote/{id}/like
Like a specific quote (requires authentication).

**Headers:**
```
Authorization: Bearer <jwt_token>
```

**Response:**
```json
{
  "message": "Quote liked successfully",
  "likeCount": 5
}
```

**Status Codes:**
- `200 OK`: Quote liked successfully
- `401 Unauthorized`: Authentication required
- `404 Not Found`: Quote not found

#### DELETE /api/v1/quote/{id}/unlike
Unlike a specific quote (requires authentication).

**Headers:**
```
Authorization: Bearer <jwt_token>
```

**Response:**
```json
{
  "message": "Quote unliked successfully",
  "likeCount": 4
}
```

**Status Codes:**
- `200 OK`: Quote unliked successfully
- `401 Unauthorized`: Authentication required
- `404 Not Found`: Quote not found

#### GET /api/v1/quote/liked
Get all quotes liked by the user (requires authentication).

**Headers:**
```
Authorization: Bearer <jwt_token>
```

**Response:**
```json
[
  {
    "id": 1,
    "text": "Be yourself; everyone else is already taken.",
    "author": "Oscar Wilde"
  }
]
```

**Status Codes:**
- `200 OK`: Liked quotes returned successfully
- `401 Unauthorized`: Authentication required

#### PUT /api/v1/quote/{id}/reorder
Move a quote to a specific position in user's view order (requires authentication).

**Headers:**
```
Authorization: Bearer <jwt_token>
```

**Request Body:**
```json
{
  "newPosition": 2
}
```

**Response:**
```json
{
  "message": "Quote reordered successfully"
}
```

**Status Codes:**
- `200 OK`: Quote reordered successfully
- `400 Bad Request`: Invalid position
- `401 Unauthorized`: Authentication required

### Admin Endpoints

#### GET /api/v1/manage/users
Get all users (requires admin role).

**Headers:**
```
Authorization: Bearer <admin_jwt_token>
```

**Response:**
```json
[
  {
    "id": 1,
    "username": "testuser",
    "email": "testuser@example.com",
    "roles": ["user"],
    "createdAt": "2024-01-01T12:00:00Z"
  }
]
```

**Status Codes:**
- `200 OK`: Users returned successfully
- `401 Unauthorized`: Authentication required
- `403 Forbidden`: Admin role required

#### GET /api/v1/manage/quotes
Get quotes with pagination and filtering (requires admin role).

**Headers:**
```
Authorization: Bearer <admin_jwt_token>
```

**Query Parameters:**
- `page` (optional): Page number (default: 1)
- `pageSize` (optional): Items per page (default: 10)
- `sortBy` (optional): Sort field (default: id)
- `sortOrder` (optional): Sort order (asc/desc, default: asc)

**Response:**
```json
{
  "quotes": [
    {
      "id": 1,
      "text": "Be yourself; everyone else is already taken.",
      "author": "Oscar Wilde"
    }
  ],
  "pagination": {
    "page": 1,
    "pageSize": 10,
    "totalItems": 100,
    "totalPages": 10
  }
}
```

**Status Codes:**
- `200 OK`: Quotes returned successfully
- `401 Unauthorized`: Authentication required
- `403 Forbidden`: Admin role required

#### POST /api/v1/manage/quotes/fetch
Fetch new quotes from ZenQuotes API (requires admin role).

**Headers:**
```
Authorization: Bearer <admin_jwt_token>
```

**Response:**
```json
{
  "message": "Quotes fetched successfully",
  "quotesAdded": 10,
  "duplicatesSkipped": 2
}
```

**Status Codes:**
- `200 OK`: Quotes fetched successfully
- `401 Unauthorized`: Authentication required
- `403 Forbidden`: Admin role required

#### GET /api/v1/manage/stats
Get system statistics (requires admin role).

**Headers:**
```
Authorization: Bearer <admin_jwt_token>
```

**Response:**
```json
{
  "totalQuotes": 100,
  "totalUsers": 25,
  "totalLikes": 500,
  "activeUsers": 15,
  "quotesAddedToday": 5
}
```

**Status Codes:**
- `200 OK`: Statistics returned successfully
- `401 Unauthorized`: Authentication required
- `403 Forbidden`: Admin role required

#### PUT /api/v1/manage/users/role
Assign role to user (requires admin role).

**Headers:**
```
Authorization: Bearer <admin_jwt_token>
```

**Request Body:**
```json
{
  "username": "testuser",
  "role": "admin"
}
```

**Response:**
```json
{
  "message": "Role assigned successfully"
}
```

**Status Codes:**
- `200 OK`: Role assigned successfully
- `400 Bad Request`: Invalid request data
- `401 Unauthorized`: Authentication required
- `403 Forbidden`: Admin role required

#### DELETE /api/v1/manage/users/role
Remove role from user (requires admin role).

**Headers:**
```
Authorization: Bearer <admin_jwt_token>
```

**Request Body:**
```json
{
  "username": "testuser",
  "role": "admin"
}
```

**Response:**
```json
{
  "message": "Role removed successfully"
}
```

**Status Codes:**
- `200 OK`: Role removed successfully
- `400 Bad Request`: Invalid request data
- `401 Unauthorized`: Authentication required
- `403 Forbidden`: Admin role required

#### DELETE /api/v1/manage/users/account
Delete user account (requires admin role).

**Headers:**
```
Authorization: Bearer <admin_jwt_token>
```

**Request Body:**
```json
{
  "username": "testuser"
}
```

**Response:**
```json
{
  "message": "User account deleted successfully"
}
```

**Status Codes:**
- `200 OK`: Account deleted successfully
- `401 Unauthorized`: Authentication required
- `403 Forbidden`: Admin role required

### System Endpoints

#### GET /health
Health check endpoint for monitoring.

**Response:**
```json
{
  "status": "healthy"
}
```

**Status Codes:**
- `200 OK`: Service is healthy

## SSL/HTTPS Setup

### Overview
The Quote Backend supports HTTPS through Nginx reverse proxy with Let's Encrypt SSL certificates. The setup is automated via GitHub Actions deployment.

### Backend URL
- **Production**: `https://quote-ovhc-backend.mooo.com`
- **HTTP (redirects to HTTPS)**: `http://quote-ovhc-backend.mooo.com`
- **Direct backend (for debugging)**: `http://51.255.60.246:8080`

### SSL Configuration
- **Reverse Proxy**: Nginx routes HTTPS traffic to the backend
- **SSL Certificates**: Let's Encrypt (free, auto-renewing)
- **Domain**: FreeDNS subdomain pointing to OVHcloud VM
- **Ports**: 80 (HTTP), 443 (HTTPS), 8080 (backend direct)

### Quick Setup Summary
1. **FreeDNS**: Free subdomain (`quote-ovhc-backend.mooo.com`) points to VM IP
2. **Nginx**: Reverse proxy handles HTTPS termination
3. **Let's Encrypt**: Automatic SSL certificate generation and renewal
4. **GitHub Actions**: Automated deployment with HTTPS setup

### For Detailed Instructions
See the complete SSL setup guide: **[doc/HTTPS-Setup.md](doc/HTTPS-Setup.md)**

This document includes:
- FreeDNS domain registration
- Nginx configuration
- SSL certificate setup
- Troubleshooting guide
- Manual setup instructions

## Data Persistence

### S3 Storage Structure
```
quote-storage/
├── quotes.db          # SQLite database file (primary)
└── quotes.json        # JSON backup (legacy compatibility)
```

### SQLite Database Schema
```sql
CREATE TABLE quotes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    text TEXT NOT NULL,
    author TEXT NOT NULL,
    like_count INTEGER DEFAULT 0,
    created_at DATETIME NOT NULL,
    source TEXT NOT NULL
);
```

### Persistence Strategy

#### Development Environment
- **Local File**: `quotes.db` stored locally
- **IDE Connectivity**: IntelliJ can connect directly to database file
- **Auto-sync**: Changes automatically backed up to S3

#### Production Environment
- **Startup**: Downloads latest `quotes.db` from S3
- **Runtime**: Uses local SQLite for performance
- **Persistence**: Changes automatically uploaded to S3
- **Backup Strategy**: Both SQLite file and JSON backup maintained
- **Disaster Recovery**: Complete database survives VM restart/failure

## Development

### Local Development

#### Development Workflow

**1. Setup Environment**
```bash
# Create .secrets file
source setup-env.sh
# Edit .secrets with your credentials
```

**2. Configure GoLand**
- Set up Run Configuration as described above
- Add breakpoints as needed
- Use Database tool window to connect to `quotes.db`

**3. Development Cycle**
- Click **Run** (▶️) to start application
- Use **Debug** (🐛) for debugging sessions
- Test endpoints with HTTP Client or curl
- View logs in GoLand console

**4. Testing Endpoints**
```bash
# Health check
curl http://localhost:8080/health

# Get quote
curl http://localhost:8080/quote
```

#### GoLand IDE Setup

For an enhanced development experience and better debugging capabilities, GoLand (JetBrains) can be configured with a custom Run Configuration. This provides integrated debugging, hot reload, and seamless environment variable management.

**Step 1: Create Run Configuration**
1. Open GoLand
2. Go to **Run** → **Edit Configurations...**
3. Click **+** → **Go Build**

**Step 2: Configure Run Settings**
```
Name: Quote Backend
Run kind: Directory
Directory: quote-ovhc-backend/cmd/quote-backend
Output directory: quote-ovhc-backend
Program arguments: 
```

**Step 3: Set Environment Variables**
Add the following environment variables in the **Environment variables** section:

```bash
# OVHcloud Object Storage Configuration
S3_ENDPOINT=https://s3.gra.cloud.ovh.net
S3_REGION=GRA
S3_BUCKET=quote-storage

# S3 Credentials (replace with your actual credentials)
S3_ACCESS_KEY=your-actual-access-key
S3_SECRET_KEY=your-actual-secret-key

# Server Configuration
PORT=8080

# JWT Configuration (optional)
JWT_SECRET=your-super-secret-jwt-key-change-in-production
```

### Database Debugging

#### IntelliJ Integration
1. **Add Data Source** → **SQLite**
2. **File path**: `/path/to/project/quotes.db`
3. **JDBC URL**: `jdbc:sqlite:/path/to/project/quotes.db`
4. **Test Connection** - should show `quotes` table

## Deployment on OVHcloud VM

### 1. Create VM (Manual)
Follow the manual VM creation guide in the documentation:
- Name: `quote-backend-vm`
- Flavor: D2-2 (Discovery)
- Image: Ubuntu 22.04 LTS
- Region: GRA
- Network: Public mode

### 2. Connect to VM
```bash
ssh root@YOUR_VM_IP
```

### 3. Install Go
```bash
wget https://go.dev/dl/go1.21.0.linux-amd64.tar.gz
tar -C /usr/local -xzf go1.21.0.linux-amd64.tar.gz
echo 'export PATH=$PATH:/usr/local/go/bin' >> ~/.bashrc
source ~/.bashrc
```

### 4. Deploy Application
```bash
# Create application directory
mkdir /opt/quote-backend
cd /opt/quote-backend

# Upload your application files (use scp)
# scp ./* root@YOUR_VM_IP:/opt/quote-backend/

# Install dependencies
go mod tidy

# Build and run
go build -o quote-backend
./quote-backend
```

## Testing

### HTTP Client Testing

The project includes a comprehensive HTTP client test file at `doc/test-ovhc-api.http` that contains tests for all API endpoints. This file can be used with IDE HTTP clients (like IntelliJ IDEA, VS Code with REST Client extension) to test the complete functionality of the application.

#### Test File Features

**📋 Complete Endpoint Coverage**
- ✅ **Authentication endpoints**: Register, login, password change, unregister
- ✅ **Quote endpoints**: Get quotes, like/unlike, view history, progress tracking
- ✅ **Admin endpoints**: User management, quote management, statistics
- ✅ **Health checks**: Application health monitoring

**🔐 Authentication Testing**
- Tests user registration and login flows
- JWT token generation and validation
- Role-based access control (admin vs regular user)
- Password change functionality
- Account deletion (self and admin)

**👥 Multi-User Scenarios**
- Regular user operations (testuser)
- Admin user operations (admin-1)
- Default admin user (admin with default password)
- Permission validation (non-admin accessing admin endpoints)

**📊 Data Management**
- Quote fetching from ZenQuotes API
- User progress tracking
- Like/unlike functionality
- Quote reordering
- View history management

#### Usage Instructions

**1. Set up Environment**
```bash
# Set the baseUrl variable in your HTTP client
# For local testing:
baseUrl = http://localhost:8080

# For remote testing:
baseUrl = http://YOUR_VM_IP:8080
```

**2. Run Tests Sequentially**
The tests are numbered and designed to be run in order:
1. **Health check** (0.9) - Verify server is running
2. **User registration** (1) - Create test user
3. **Login** (2) - Authenticate and get JWT token
4. **Protected operations** (3-10) - Test authenticated endpoints
5. **Admin operations** (11-21) - Test admin functionality
6. **Default admin** (22-24) - Test default admin user

**3. Token Management**
The test file automatically extracts and stores JWT tokens:
- `testuserToken` - Regular user authentication
- `adminToken` - Admin user authentication
- `defaultAdminToken` - Default admin user authentication

#### Test Categories

**Authentication Tests**
- User registration with validation
- Login with username/email
- JWT token generation and parsing
- Password change with validation
- Account deletion (self and admin)

**Quote Management Tests**
- Get random quotes (authenticated/public)
- Quote exclusions via POST
- Like/unlike functionality
- View history tracking
- Progress monitoring
- Quote reordering

**Admin Management Tests**
- User listing and role management
- Quote management with pagination
- Statistics and analytics
- User account deletion by admin
- Role assignment/removal

**Security Tests**
- Permission validation
- Role-based access control
- Unauthorized access prevention

### Local Testing
```bash
# Test the endpoint
curl http://localhost:8080/quote

# Test health check
curl http://localhost:8080/health
```

### Remote Testing
```bash
# Test from your local machine
curl http://YOUR_VM_IP:8080/quote
```

## Monitoring

### Logs
```bash
# View application logs
journalctl -u quote-backend -f

# Or if running directly
tail -f /var/log/quote-backend.log
```

### Health Monitoring
```bash
# Health check
curl http://localhost:8080/health
```

## Troubleshooting

### Common Issues

1. **S3 Connection Error**:
   - Verify S3 credentials
   - Check bucket name and region
   - Ensure network connectivity

2. **No Quotes Available**:
   - Check S3 bucket permissions
   - Verify quotes.json file exists
   - Application will create sample quotes automatically

3. **Port Already in Use**:
   ```bash
   # Check what's using the port
   lsof -i :8080
   
   # Kill the process
   kill -9 PID
   ```

### Debug Mode
Set log level for debugging:
```bash
export LOG_LEVEL=debug
./quote-backend
```

