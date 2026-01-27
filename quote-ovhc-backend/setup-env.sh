#!/bin/bash

# Environment Variables Setup Script for Quote Backend
# This script sets up all required environment variables for the OVHcloud Quote Backend

echo "🚀 Setting up environment variables for Quote Backend..."

# Function to read from secrets file
read_secrets() {
    local secrets_file=".secrets"
    
    if [ -f "$secrets_file" ]; then
        echo "� Reading S3 credentials from $secrets_file..."
        
        # Read credentials from secrets file
        while IFS='=' read -r key value; do
            # Skip comments and empty lines
            [[ $key =~ ^[[:space:]]*# ]] && continue
            [[ -z $key ]] && continue
            
            # Remove quotes and whitespace
            key=$(echo "$key" | xargs)
            value=$(echo "$value" | sed 's/^["'\'']//' | sed 's/["'\'']$//' | xargs)
            
            case "$key" in
                "S3_ACCESS_KEY")
                    export S3_ACCESS_KEY="$value"
                    ;;
                "S3_SECRET_KEY")
                    export S3_SECRET_KEY="$value"
                    ;;
                "JWT_SECRET")
                    export JWT_SECRET="$value"
                    ;;
            esac
        done < "$secrets_file"
        
        echo "✅ Secrets loaded from $secrets_file"
    else
        echo "⚠️  Secrets file '$secrets_file' not found"
        echo "   Creating template secrets file..."
        
        # Create secrets template
        cat > "$secrets_file" << EOF
# S3 Credentials (replace with your actual S3 credentials from OVHcloud)
# IMPORTANT: Generate these from the S3-compatible container, not Swift!
S3_ACCESS_KEY=YOUR_S3_ACCESS_KEY_HERE
S3_SECRET_KEY=YOUR_S3_SECRET_KEY_HERE

# JWT Secret (optional - uses default if not set)
JWT_SECRET=your-super-secret-jwt-key-change-in-production
EOF
        
        echo "📝 Created $secrets_file template"
        echo "   Please edit this file with your actual credentials"
        echo "   Then run this script again"
        echo ""
        return 1
    fi
}

# OVHcloud Object Storage Configuration
echo "📦 Setting up OVHcloud Object Storage variables..."

export S3_ENDPOINT="https://s3.gra.cloud.ovh.net"
export S3_REGION="GRA"
export S3_BUCKET="quote-storage"

# Read S3 credentials from secrets file
if ! read_secrets; then
    echo "❌ Please update the .secrets file with your credentials and try again"
    return 1 2>/dev/null || exit 1
fi

# Server Configuration
echo "🌐 Setting up server configuration..."
export PORT="8080"

# Display current settings
echo ""
echo "✅ Environment variables set:"
echo "   S3_ENDPOINT: $S3_ENDPOINT"
echo "   S3_REGION: $S3_REGION"
echo "   S3_BUCKET: $S3_BUCKET"
echo "   S3_ACCESS_KEY: ${S3_ACCESS_KEY:0:8}..."
echo "   S3_SECRET_KEY: ${S3_SECRET_KEY:0:8}..."
echo "   PORT: $PORT"

# Check for S3 credentials
if [ -z "$S3_ACCESS_KEY" ] || [ "$S3_ACCESS_KEY" = "YOUR_S3_ACCESS_KEY_HERE" ] || [ -z "$S3_SECRET_KEY" ] || [ "$S3_SECRET_KEY" = "YOUR_S3_SECRET_KEY_HERE" ]; then
    echo ""
    echo "⚠️  Warning: S3 credentials not properly set!"
    echo ""
    echo "🔑 To set S3 credentials:"
    echo "   1. Edit the .secrets file"
    echo "   2. Replace YOUR_S3_ACCESS_KEY_HERE with your actual access key"
    echo "   3. Replace YOUR_S3_SECRET_KEY_HERE with your actual secret key"
    echo "   4. Run this script again"
    echo ""
    echo "📖 Get credentials from OVHcloud Manager:"
    echo "   1. Go to OVHcloud Manager"
    echo "   2. Navigate to Public Cloud -> Your Project"
    echo "   3. Click on Object Storage -> quote-storage"
    echo "   4. Click on Users tab"
    echo "   5. Find your user and view S3 credentials"
else
    echo ""
    echo "✅ S3 credentials found:"
    echo "   S3_ACCESS_KEY: ${S3_ACCESS_KEY:0:8}..."
    echo "   S3_SECRET_KEY: ${S3_SECRET_KEY:0:8}..."
fi

echo ""
echo "🎯 Ready to start the application!"
echo ""
echo "📝 To run the application:"
echo "   ./quote-backend"
echo ""
echo "🧪 To test the application:"
echo "   curl http://localhost:8080/health"
echo "   curl http://localhost:8080/quote"
echo ""

# Function to export variables to current shell
export_env() {
    echo "🔄 Exporting environment variables to current shell..."
    export S3_ENDPOINT="https://s3.gra.cloud.ovh.net"
    export S3_REGION="GRA"
    export S3_BUCKET="quote-storage"
    export PORT="8080"
    
    # Try to read secrets
    if [ -f ".secrets" ]; then
        echo "🔐 Loading secrets from .secrets file..."
        while IFS='=' read -r key value; do
            [[ $key =~ ^[[:space:]]*# ]] && continue
            [[ -z $key ]] && continue
            
            key=$(echo "$key" | xargs)
            value=$(echo "$value" | sed 's/^["'\'']//' | sed 's/["'\'']$//' | xargs)
            
            case "$key" in
                "S3_ACCESS_KEY")
                    export S3_ACCESS_KEY="$value"
                    ;;
                "S3_SECRET_KEY")
                    export S3_SECRET_KEY="$value"
                    ;;
                "JWT_SECRET")
                    export JWT_SECRET="$value"
                    ;;
            esac
        done < ".secrets"
        echo "✅ Variables exported with secrets!"
    else
        echo "⚠️  No .secrets file found. Please create it first."
        echo "✅ Basic variables exported (without secrets)!"
    fi
}

# Function to create .env file
create_env_file() {
    echo "📝 Creating .env file..."
    
    # Start with non-secret variables
    cat > .env << EOF
# OVHcloud Object Storage Configuration
S3_ENDPOINT=https://s3.gra.cloud.ovh.net
S3_REGION=GRA
S3_BUCKET=quote-storage

# Server Configuration
PORT=8080
EOF
    
    # Add secrets if available
    if [ -f ".secrets" ]; then
        echo "🔐 Adding secrets from .secrets file to .env..."
        while IFS='=' read -r key value; do
            [[ $key =~ ^[[:space:]]*# ]] && continue
            [[ -z $key ]] && continue
            
            key=$(echo "$key" | xargs)
            value=$(echo "$value" | sed 's/^["'\'']//' | sed 's/["'\'']$//' | xargs)
            
            case "$key" in
                "S3_ACCESS_KEY"|"S3_SECRET_KEY"|"JWT_SECRET")
                    echo "$key=$value" >> .env
                    ;;
            esac
        done < ".secrets"
        echo "✅ .env file created with secrets!"
    else
        echo "# Add your secrets here or use .secrets file" >> .env
        echo "S3_ACCESS_KEY=YOUR_S3_ACCESS_KEY_HERE" >> .env
        echo "S3_SECRET_KEY=YOUR_S3_SECRET_KEY_HERE" >> .env
        echo "JWT_SECRET=your-super-secret-jwt-key-change-in-production" >> .env
        echo "✅ .env file created! Please update S3 credentials."
    fi
}

# Function to display help
show_help() {
    echo "📖 Environment Setup Script Usage:"
    echo ""
    echo "Usage: source setup-env.sh"
    echo "   or"
    echo "   . setup-env.sh"
    echo ""
    echo "Options:"
    echo "  --export     Export variables to current shell"
    echo "  --env-file   Create .env file with variables"
    echo "  --help       Show this help message"
    echo ""
    echo "🔐 Security Features:"
    echo "  - S3 credentials are read from .secrets file"
    echo "  - .secrets file is automatically added to .gitignore"
    echo "  - Template .secrets file created if missing"
    echo "  - No secrets exposed in shell history or git"
    echo ""
    echo "📋 Setup Steps:"
    echo "  1. Run 'source setup-env.sh' (creates .secrets template)"
    echo "  2. Edit .secrets file with your actual credentials"
    echo "  3. Run 'source setup-env.sh' again to load secrets"
    echo ""
    echo "Examples:"
    echo "  source setup-env.sh           # Load variables from .secrets"
    echo "  ./setup-env.sh --export       # Export to current shell"
    echo "  ./setup-env.sh --env-file     # Create .env file"
}

# Handle command line arguments
case "${1:-}" in
    --export)
        export_env
        ;;
    --env-file)
        create_env_file
        ;;
    --help)
        show_help
        ;;
    *)
        echo "💡 Tip: Use 'source setup-env.sh' to load variables in your current shell"
        echo "   or run './setup-env.sh --help' for more options"
        ;;
esac
