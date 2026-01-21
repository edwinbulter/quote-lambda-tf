#!/bin/bash

# Destroy Frontend Infrastructure
# WARNING: This will delete all resources!

set -e

RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

echo -e "${RED}WARNING: This will delete ALL frontend infrastructure!${NC}"
echo -e "${YELLOW}This includes:${NC}"
echo -e "  - Resource Group"
echo -e "  - Storage Account"
echo -e "  - CDN Profile"
echo -e "  - All uploaded files"
echo

read -p "Are you sure you want to continue? (yes/no): " -r
if [[ ! $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
    echo "Aborted."
    exit 1
fi

echo
echo -e "${YELLOW}Destroying infrastructure...${NC}"

# Initialize Terraform (if not already done)
if [ ! -d ".terraform" ]; then
    terraform init
fi

# Destroy all resources
terraform destroy

echo -e "${GREEN}Infrastructure destroyed successfully.${NC}"
