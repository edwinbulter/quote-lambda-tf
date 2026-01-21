#!/bin/bash

# Setup script for using existing storage account for frontend files

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Setting up Frontend Infrastructure with Existing Storage Account${NC}"

# Get storage account name
read -p "Enter your existing storage account name for frontend files: " STORAGE_ACCOUNT

# Get resource group name
read -p "Enter the resource group name of the storage account: " RESOURCE_GROUP

# Verify the storage account exists
echo -e "${YELLOW}Verifying storage account...${NC}"
if ! az storage account show --name $STORAGE_ACCOUNT --resource-group $RESOURCE_GROUP >/dev/null 2>&1; then
    echo -e "${RED}Error: Storage account '$STORAGE_ACCOUNT' not found in resource group '$RESOURCE_GROUP'${NC}"
    exit 1
fi

echo -e "${GREEN}Storage account verified!${NC}"

# Create terraform.tfvars
echo -e "${YELLOW}Creating terraform.tfvars...${NC}"
cat > terraform.tfvars << EOF
# Use existing storage account for frontend files
use_existing_storage_account = true
frontend_storage_account_name = "$STORAGE_ACCOUNT"
frontend_resource_group_name = "$RESOURCE_GROUP"

# Resource Group (only used if creating new storage account)
resource_group_name = "quote-frontend-rg"
location            = "westeurope"

# Storage Account (only used if not using existing)
storage_account_name = "quotefrontend"

# Environment
environment = "dev"

# CDN Configuration
enable_cdn = false

# Custom Domain (optional)
custom_domain = {
  domain_name = ""
  ttl         = 3600
}

# Tags
tags = {
  Project     = "quote-azure"
  Component   = "frontend"
  Environment = "dev"
  Owner       = "code.bulter"
}
EOF

echo -e "${GREEN}terraform.tfvars created successfully!${NC}"

# Check if backend.tf exists
if [ ! -f "backend.tf" ]; then
    echo -e "${YELLOW}Creating backend.tf from template...${NC}"
    cp backend.tf.template backend.tf
    echo -e "${GREEN}Backend configuration set up for dedicated state storage.${NC}"
else
    echo -e "${GREEN}backend.tf already exists${NC}"
fi

echo
echo -e "${GREEN}Setup complete!${NC}"
echo
echo -e "${YELLOW}Next steps:${NC}"
echo "1. Ensure you've created the state storage: az group create --name quote-frontend-rg --location westeurope"
echo "2. Ensure you've created the state storage account: az storage account create --name quotefrontendstate --resource-group quote-frontend-rg --location westeurope --sku Standard_LRS --kind StorageV2"
echo "3. Ensure you've created the state container: az storage container create --name terraform-state-frontend --account-name quotefrontendstate --auth-mode login"
echo "4. Run: terraform init"
echo "5. Run: terraform plan"
echo "6. Run: terraform apply"
echo
echo -e "${YELLOW}Or use the deploy script: ./deploy.sh${NC}"
