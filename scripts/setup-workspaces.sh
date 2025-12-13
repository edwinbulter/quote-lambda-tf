#!/bin/bash

# Terraform Workspaces Setup Script
# This script helps you set up development and production environments using Terraform workspaces

set -e  # Exit on error

echo "========================================="
echo "Terraform Workspaces Setup"
echo "========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to print colored output
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "$1"
}

# Check if we're in the right directory
if [ ! -d "quote-lambda-tf-backend" ] || [ ! -d "quote-lambda-tf-frontend" ]; then
    print_error "Please run this script from the root of the quote-lambda-tf repository"
    exit 1
fi

print_info "This script will:"
print_info "1. Reinitialize Terraform backends with workspace support"
print_info "2. Verify your production environment (default workspace)"
print_info "3. Create a development workspace"
print_info "4. Deploy development infrastructure"
print_info ""
read -p "Continue? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    print_warning "Setup cancelled"
    exit 0
fi

# Step 1: Backend - Reinitialize with workspace support
print_info ""
print_info "========================================="
print_info "Step 1: Backend - Reinitialize Terraform"
print_info "========================================="
cd quote-lambda-tf-backend/infrastructure/

print_info "Reinitializing Terraform backend..."
terraform init -reconfigure

print_success "Backend reinitialized"

# Check current workspace
CURRENT_WORKSPACE=$(terraform workspace show)
print_info "Current workspace: $CURRENT_WORKSPACE"

if [ "$CURRENT_WORKSPACE" != "default" ]; then
    print_warning "Switching to default workspace..."
    terraform workspace select default
fi

# Step 2: Verify production environment
print_info ""
print_info "========================================="
print_info "Step 2: Verify Production Environment"
print_info "========================================="
print_info "Running terraform plan for production..."
terraform plan -var="environment=prod"

read -p "Does the plan look correct? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    print_error "Please review the plan and fix any issues before continuing"
    exit 1
fi

# Step 3: Create development workspace
print_info ""
print_info "========================================="
print_info "Step 3: Create Development Workspace"
print_info "========================================="

if terraform workspace list | grep -q "dev"; then
    print_warning "Development workspace already exists"
    terraform workspace select dev
else
    print_info "Creating development workspace..."
    terraform workspace new dev
    print_success "Development workspace created"
fi

# Step 4: Deploy development backend
print_info ""
print_info "========================================="
print_info "Step 4: Deploy Development Backend"
print_info "========================================="
print_info "Planning development environment..."
terraform plan -var="environment=dev"

read -p "Deploy development backend infrastructure? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    terraform apply -var="environment=dev"
    print_success "Development backend deployed"
    
    # Get API Gateway URL
    API_URL=$(terraform output -raw api_gateway_url 2>/dev/null || echo "")
    if [ -n "$API_URL" ]; then
        print_success "Development API Gateway URL: $API_URL"
        echo "$API_URL" > ../../dev-api-url.txt
        print_info "Saved to dev-api-url.txt"
    fi
else
    print_warning "Skipped development backend deployment"
fi

# Step 5: Frontend - Reinitialize with workspace support
print_info ""
print_info "========================================="
print_info "Step 5: Frontend - Reinitialize Terraform"
print_info "========================================="
cd ../../quote-lambda-tf-frontend/infrastructure/

print_info "Reinitializing Terraform backend..."
terraform init -reconfigure

print_success "Frontend backend reinitialized"

# Check current workspace
CURRENT_WORKSPACE=$(terraform workspace show)
print_info "Current workspace: $CURRENT_WORKSPACE"

if [ "$CURRENT_WORKSPACE" != "default" ]; then
    print_warning "Switching to default workspace..."
    terraform workspace select default
fi

# Step 6: Verify production frontend
print_info ""
print_info "========================================="
print_info "Step 6: Verify Production Frontend"
print_info "========================================="
print_info "Running terraform plan for production..."
terraform plan -var="environment=prod"

read -p "Does the plan look correct? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    print_error "Please review the plan and fix any issues before continuing"
    exit 1
fi

# Step 7: Create development workspace for frontend
print_info ""
print_info "========================================="
print_info "Step 7: Create Development Frontend Workspace"
print_info "========================================="

if terraform workspace list | grep -q "dev"; then
    print_warning "Development workspace already exists"
    terraform workspace select dev
else
    print_info "Creating development workspace..."
    terraform workspace new dev
    print_success "Development workspace created"
fi

# Step 8: Deploy development frontend
print_info ""
print_info "========================================="
print_info "Step 8: Deploy Development Frontend"
print_info "========================================="
print_info "Planning development environment..."
terraform plan -var="environment=dev"

read -p "Deploy development frontend infrastructure? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    terraform apply -var="environment=dev"
    print_success "Development frontend deployed"
    
    # Get CloudFront URL
    CF_URL=$(terraform output -raw cloudfront_url 2>/dev/null || echo "")
    if [ -n "$CF_URL" ]; then
        print_success "Development CloudFront URL: https://$CF_URL"
        echo "https://$CF_URL" > ../../dev-cloudfront-url.txt
        print_info "Saved to dev-cloudfront-url.txt"
    fi
else
    print_warning "Skipped development frontend deployment"
fi

# Summary
print_info ""
print_info "========================================="
print_info "Setup Complete!"
print_info "========================================="
print_success "Terraform workspaces configured successfully"
print_info ""
print_info "Next steps:"
print_info "1. Build and deploy Lambda code to development:"
print_info "   cd quote-lambda-tf-backend"
print_info "   mvn clean package"
print_info "   aws lambda update-function-code --function-name quote-lambda-tf-backend-dev --zip-file fileb://target/quote-lambda-tf-backend-1.0-SNAPSHOT.jar --region eu-central-1"
print_info ""
print_info "2. Update frontend .env.development with dev API URL"
print_info "   (URL saved in dev-api-url.txt)"
print_info ""
print_info "3. Build and deploy frontend to development:"
print_info "   cd quote-lambda-tf-frontend"
print_info "   npm run build"
print_info "   aws s3 sync dist/ s3://quote-lambda-tf-frontend-dev --delete"
print_info ""
print_info "4. Test your development environment:"
print_info "   (URL saved in dev-cloudfront-url.txt)"
print_info ""
print_info "Workspace commands:"
print_info "  terraform workspace list    # List all workspaces"
print_info "  terraform workspace show    # Show current workspace"
print_info "  terraform workspace select dev     # Switch to dev"
print_info "  terraform workspace select default # Switch to prod"
print_info ""
print_success "Happy developing!"

cd ../..
