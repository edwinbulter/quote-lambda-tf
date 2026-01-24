# Quote Backend - OVHcloud Implementation

A Go backend service for managing quotes with SQLite database and S3 persistence, designed to run on OVHcloud VM instances.

## Features

- **SQLite database** with full SQL support and S3 persistence
- **Multiple endpoints**: `GET /quote`, `POST /quote` (with exclusions), debug endpoints
- **OVHcloud Object Storage** integration (S3-compatible)
- **Automatic backup** of database file and JSON to S3 on data changes
- **Health check endpoint**: `GET /health`
- **Debug endpoints**: `/debug/quotes` (view all), `/debug/sql` (SQL queries)
- **Sample data** initialization on first run
- **IntelliJ/IDE connectivity** to local database file

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
- **Real-time Queries**: Use `/debug/sql` endpoint for ad-hoc SQL queries
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

## Quick Start

### Prerequisites
- Go 1.21 or higher
- OVHcloud Object Storage container
- S3 credentials from OVHcloud

### Environment Variables

Set these environment variables before running:

**Option 1: Use the setup script (Recommended)**
```bash
# Load variables in your current shell
source setup-env.sh

# Or create a .env file
./setup-env.sh --env-file
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

**Get S3 Credentials:**
1. Go to OVHcloud Manager
2. Navigate to Public Cloud → Your Project
3. Click on Object Storage → quote-storage
4. Click on Users tab
5. Find your user and view S3 credentials

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

### GET /quote
Returns a random quote from the SQLite database.

**Response:**
```json
{
  "id": 1,
  "text": "Be yourself; everyone else is already taken.",
  "author": "Oscar Wilde",
  "likeCount": 0,
  "createdAt": "2024-01-01T12:00:00Z",
  "source": "Local"
}
```

**Status Codes:**
- `200 OK`: Quote returned successfully
- `404 Not Found`: No quotes available

### POST /quote
Returns a random quote excluding specified IDs. Automatically adds more quotes if all existing quotes are excluded.

**Request Body:**
```json
[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
```

**Response:**
```json
{
  "id": 15,
  "text": "The only way to do great work is to love what you do.",
  "author": "Steve Jobs",
  "likeCount": 0,
  "createdAt": "2024-01-01T12:00:00Z",
  "source": "Local"
}
```

**Status Codes:**
- `200 OK`: Quote returned successfully
- `400 Bad Request`: Invalid request body
- `404 Not Found`: No quotes available
- `500 Internal Server Error`: Database error

### GET /health
Health check endpoint for monitoring.

**Response:**
```json
{
  "status": "healthy"
}
```

### GET /debug/quotes
Returns all quotes in the database (for debugging).

**Response:**
```json
[
  {
    "id": 1,
    "text": "Be yourself; everyone else is already taken.",
    "author": "Oscar Wilde",
    "likeCount": 0,
    "createdAt": "2024-01-01T12:00:00Z",
    "source": "Local"
  }
]
```

### GET /debug/sql
Execute SQL queries against the database.

**Query Parameter:** `q` - SQL query to execute

**Examples:**
```bash
# Count all quotes
curl "http://localhost:8080/debug/sql?q=SELECT%20COUNT(*)%20FROM%20quotes"

# Get first 5 quotes
curl "http://localhost:8080/debug/sql?q=SELECT%20*%20FROM%20quotes%20LIMIT%205"
```

**Response:**
```json
{
  "columns": ["id", "text", "author", "like_count", "created_at", "source"],
  "count": 5,
  "rows": [
    {"id": 1, "text": "Be yourself...", "author": "Oscar Wilde", ...}
  ]
}
```

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
- **Debug Access**: Full SQL query capabilities via `/debug/sql`

#### Production Environment
- **Startup**: Downloads latest `quotes.db` from S3
- **Runtime**: Uses local SQLite for performance
- **Persistence**: Changes automatically uploaded to S3
- **Backup Strategy**: Both SQLite file and JSON backup maintained
- **Disaster Recovery**: Complete database survives VM restart/failure

## Development

### Database Debugging

#### IntelliJ Integration
1. **Add Data Source** → **SQLite**
2. **File path**: `/path/to/project/quotes.db`
3. **JDBC URL**: `jdbc:sqlite:/path/to/project/quotes.db`
4. **Test Connection** - should show `quotes` table

#### SQL Debug Endpoint
```bash
# View database schema
curl "http://localhost:8080/debug/sql?q=SELECT%20sql%20FROM%20sqlite_master%20WHERE%20type='table'"

# Count quotes by author
curl "http://localhost:8080/debug/sql?q=SELECT%20author,%20COUNT(*)%20FROM%20quotes%20GROUP%20BY%20author"

# Find quotes with most likes
curl "http://localhost:8080/debug/sql?q=SELECT%20*%20FROM%20quotes%20ORDER%20BY%20like_count%20DESC%20LIMIT%205"
```

#### Adding New Endpoints
1. Add handler method to `Server` struct
2. Register route in `setupRoutes()`
3. Test with curl or Postman

### Sample Data
The application automatically initializes with 5 sample quotes on first run if no existing data is found in S3. Additional quotes are automatically added when all existing quotes are excluded via POST request.

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

### 5. Set up systemd service (Optional)
```bash
cat > /etc/systemd/system/quote-backend.service << EOF
[Unit]
Description=Quote Backend Service
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/quote-backend
ExecStart=/opt/quote-backend/quote-backend
Restart=always
Environment=PORT=8080
Environment=S3_ENDPOINT=https://s3.gra.cloud.ovh.net
Environment=S3_REGION=GRA
Environment=S3_BUCKET=quote-storage
Environment=S3_ACCESS_KEY=[your-access-key]
Environment=S3_SECRET_KEY=[your-secret-key]

[Install]
WantedBy=multi-user.target
EOF

systemctl enable quote-backend
systemctl start quote-backend
```

## Testing

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

## Development

### Adding New Endpoints
1. Add handler method to `Server` struct
2. Register route in `setupRoutes()`
3. Test with curl or Postman

### Sample Data
The application automatically initializes with 5 sample quotes on first run if no existing data is found in S3.

## License

This project is part of a learning exercise for cloud deployment comparisons.
