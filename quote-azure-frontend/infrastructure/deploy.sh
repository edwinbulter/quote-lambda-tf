# Frontend Deployment Script

#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Starting Frontend Deployment...${NC}"

# Check if dist directory exists
if [ ! -d "../dist" ]; then
    echo -e "${YELLOW}Building frontend first...${NC}"
    cd ..
    npm run build
    cd infrastructure
fi

# Initialize Terraform
echo -e "${YELLOW}Initializing Terraform...${NC}"
terraform init

# Create backend.tf from template (if it doesn't exist)
if [ ! -f "backend.tf" ]; then
    echo -e "${YELLOW}Creating backend.tf from template...${NC}"
    cp backend.tf.template backend.tf
    echo -e "${RED}Please update the backend.tf file with your storage account details!${NC}"
    echo -e "${YELLOW}After updating, run this script again.${NC}"
    exit 1
fi

# Plan the deployment
echo -e "${YELLOW}Planning deployment...${NC}"
terraform plan -out=tfplan

# Apply the deployment
echo -e "${YELLOW}Applying deployment...${NC}"
terraform apply tfplan

# Get outputs
echo -e "${GREEN}Deployment completed!${NC}"
echo -e "${YELLOW}Static Website URL:${NC}"
terraform output -raw static_website_url

echo -e "${YELLOW}CDN Endpoint URL:${NC}"
terraform output -raw cdn_endpoint_url

# Clean up
rm -f tfplan

echo -e "${GREEN}Frontend deployment successful!${NC}"
