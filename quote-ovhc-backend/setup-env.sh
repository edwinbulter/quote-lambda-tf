#!/bin/bash

# Environment Variables Setup Script for Quote Backend
# This script sets up all required environment variables for the OVHcloud Quote Backend

echo "🚀 Setting up environment variables for Quote Backend..."

# OVHcloud Object Storage Configuration
echo "📦 Setting up OVHcloud Object Storage variables..."

export S3_ENDPOINT="https://s3.gra.cloud.ovh.net"
export S3_REGION="GRA"
export S3_BUCKET="quote-storage"

# S3 Credentials (replace with your actual S3 credentials from OVHcloud)
# IMPORTANT: Generate these from the S3-compatible container, not Swift!
export S3_ACCESS_KEY="YOUR_S3_ACCESS_KEY_HERE"
export S3_SECRET_KEY="YOUR_S3_SECRET_KEY_HERE"

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
if [ -z "$S3_ACCESS_KEY" ] || [ -z "$S3_SECRET_KEY" ]; then
    echo ""
    echo "⚠️  Warning: S3 credentials not set!"
    echo ""
    echo "🔑 To set S3 credentials, run:"
    echo "   export S3_ACCESS_KEY=\"your-access-key\""
    echo "   export S3_SECRET_KEY=\"your-secret-key\""
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
    echo "✅ Variables exported!"
}

# Function to create .env file
create_env_file() {
    echo "📝 Creating .env file..."
    cat > .env << EOF
# OVHcloud Object Storage Configuration
S3_ENDPOINT=https://s3.gra.cloud.ovh.net
S3_REGION=GRA
S3_BUCKET=quote-storage

# S3 Credentials (replace with your actual S3 credentials from OVHcloud)
# IMPORTANT: Generate these from the S3-compatible container, not Swift!
S3_ACCESS_KEY=YOUR_S3_ACCESS_KEY_HERE
S3_SECRET_KEY=YOUR_S3_SECRET_KEY_HERE

# Server Configuration
PORT=8080
EOF
    echo "✅ .env file created! Please update S3 credentials."
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
    echo "Examples:"
    echo "  source setup-env.sh           # Load variables in current shell"
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
