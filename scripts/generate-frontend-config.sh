#!/bin/bash

# Script to generate frontend environment configuration from Terraform outputs
# Usage: ./scripts/generate-frontend-config.sh <dev|prod>

set -e

ENVIRONMENT=$1
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_INFRA_DIR="$PROJECT_ROOT/quote-lambda-tf-backend/infrastructure"
FRONTEND_DIR="$PROJECT_ROOT/quote-lambda-tf-frontend"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

if [ -z "$ENVIRONMENT" ]; then
  echo -e "${RED}Error: Environment not specified${NC}"
  echo "Usage: ./scripts/generate-frontend-config.sh <dev|prod>"
  exit 1
fi

if [ "$ENVIRONMENT" != "dev" ] && [ "$ENVIRONMENT" != "prod" ]; then
  echo -e "${RED}Error: Environment must be 'dev' or 'prod'${NC}"
  exit 1
fi

echo -e "${YELLOW}Generating frontend configuration for ${ENVIRONMENT} environment...${NC}"

# Navigate to backend infrastructure directory
cd "$BACKEND_INFRA_DIR"

# Select the correct workspace
if [ "$ENVIRONMENT" = "prod" ]; then
  echo "Selecting Terraform workspace: default (prod)"
  terraform workspace select default > /dev/null
else
  echo "Selecting Terraform workspace: $ENVIRONMENT"
  terraform workspace select "$ENVIRONMENT" > /dev/null
fi

# Check if Terraform state exists
if ! terraform state list > /dev/null 2>&1; then
  echo -e "${RED}Error: No Terraform state found. Please run 'terraform apply' first.${NC}"
  exit 1
fi

# Get outputs from Terraform
echo "Fetching Terraform outputs..."
USER_POOL_ID=$(terraform output -raw user_pool_id 2>/dev/null || echo "")
CLIENT_ID=$(terraform output -raw user_pool_client_id 2>/dev/null || echo "")
COGNITO_DOMAIN=$(terraform output -raw cognito_domain 2>/dev/null || echo "")
API_URL=$(terraform output -raw api_gateway_url 2>/dev/null || echo "")
REGION=$(terraform output -raw aws_region 2>/dev/null || echo "eu-central-1")

# Validate outputs
if [ -z "$USER_POOL_ID" ] || [ -z "$CLIENT_ID" ] || [ -z "$COGNITO_DOMAIN" ] || [ -z "$API_URL" ]; then
  echo -e "${RED}Error: Failed to retrieve all required Terraform outputs${NC}"
  echo "Make sure your Terraform infrastructure is deployed."
  exit 1
fi

# Determine the env file name
if [ "$ENVIRONMENT" = "prod" ]; then
  ENV_FILE=".env.production"
else
  ENV_FILE=".env.development"
fi

# Generate .env file
ENV_FILE_PATH="$FRONTEND_DIR/$ENV_FILE"

cat > "$ENV_FILE_PATH" << EOF
# Auto-generated from Terraform outputs on $(date)
# DO NOT COMMIT THIS FILE - Add to .gitignore
# To regenerate: ./scripts/generate-frontend-config.sh $ENVIRONMENT

VITE_AWS_REGION=$REGION
VITE_COGNITO_USER_POOL_ID=$USER_POOL_ID
VITE_COGNITO_CLIENT_ID=$CLIENT_ID
VITE_COGNITO_DOMAIN=$COGNITO_DOMAIN
VITE_REACT_APP_API_BASE_URL=$API_URL
EOF

echo -e "${GREEN}✓ Successfully generated $ENV_FILE${NC}"
echo ""
echo "Configuration details:"
echo "  Region:           $REGION"
echo "  User Pool ID:     $USER_POOL_ID"
echo "  Client ID:        $CLIENT_ID"
echo "  Cognito Domain:   $COGNITO_DOMAIN"
echo "  API URL:          $API_URL"
echo ""
echo -e "${YELLOW}File location: $ENV_FILE_PATH${NC}"
echo ""
echo "Next steps:"
echo "  1. Review the generated file"
echo "  2. Ensure $ENV_FILE is in your .gitignore"
echo "  3. Run your frontend with: npm run dev (for dev) or npm run build (for prod)"
