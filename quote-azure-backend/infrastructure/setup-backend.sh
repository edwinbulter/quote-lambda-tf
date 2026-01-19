#!/bin/bash

# Setup script for Terraform Backend Storage
# This script creates a container in the existing storage account for Terraform backend storage

set -e

# Variables
RESOURCE_GROUP_NAME="quote-backend-rg"
CONTAINER_NAME="terraform-state"

echo "Setting up Terraform backend storage..."

# Check if user is logged in
if ! az account show > /dev/null 2>&1; then
    echo "Please log in to Azure first: az login"
    exit 1
fi

# Get current tenant ID
TENANT_ID=$(az account show --query tenantId -o tsv)
echo "Using tenant ID: $TENANT_ID"

# Find the storage account with table services
echo "Finding storage account with table services..."
STORAGE_ACCOUNT_NAME=$(az storage account list --resource-group $RESOURCE_GROUP_NAME --query "[?supportsTable==true].name" -o tsv)

if [ -z "$STORAGE_ACCOUNT_NAME" ]; then
    echo "Error: No storage account with table services found in resource group $RESOURCE_GROUP_NAME"
    echo "Available storage accounts:"
    az storage account list --resource-group $RESOURCE_GROUP_NAME --query "[].name" -o tsv
    exit 1
fi

echo "Using storage account: $STORAGE_ACCOUNT_NAME"

# Check if storage account exists
echo "Checking storage account: $STORAGE_ACCOUNT_NAME"
if ! az storage account show --name $STORAGE_ACCOUNT_NAME --resource-group $RESOURCE_GROUP_NAME > /dev/null 2>&1; then
    echo "Error: Storage account $STORAGE_ACCOUNT_NAME not found in resource group $RESOURCE_GROUP_NAME"
    exit 1
fi

# Get storage account key
STORAGE_KEY=$(az storage account keys list \
    --resource-group $RESOURCE_GROUP_NAME \
    --account-name $STORAGE_ACCOUNT_NAME \
    --query "[0].value" -o tsv)

# Create blob container
echo "Creating blob container: $CONTAINER_NAME"
az storage container create \
    --name $CONTAINER_NAME \
    --account-name $STORAGE_ACCOUNT_NAME \
    --account-key $STORAGE_KEY \
    --public-access off

# Enable versioning for state backup (if not already enabled)
echo "Checking/enabling blob versioning..."
VERSIONING_ENABLED=$(az storage account blob-service-properties show \
    --account-name $STORAGE_ACCOUNT_NAME \
    --resource-group $RESOURCE_GROUP_NAME \
    --query "defaultServiceVersion" -o tsv)

if [ -z "$VERSIONING_ENABLED" ]; then
    echo "Enabling blob versioning..."
    az storage account blob-service-properties update \
        --account-name $STORAGE_ACCOUNT_NAME \
        --resource-group $RESOURCE_GROUP_NAME \
        --enable-versioning true
fi

# Enable soft delete for recovery (if not already enabled)
echo "Checking/enabling soft delete..."
az storage account blob-service-properties update \
    --account-name $STORAGE_ACCOUNT_NAME \
    --resource-group $RESOURCE_GROUP_NAME \
    --enable-delete-retention true \
    --delete-retention-days 30

echo ""
echo "Backend setup complete!"
echo ""
echo "Backend configuration:"
echo "  Resource Group: $RESOURCE_GROUP_NAME"
echo "  Storage Account: $STORAGE_ACCOUNT_NAME (existing)"
echo "  Container: $CONTAINER_NAME"
echo "  Tenant ID: $TENANT_ID"
echo ""
echo "Next steps:"
echo "1. Update backend.tf with your tenant ID: $TENANT_ID"
echo "2. Run: terraform init -migrate-state"
echo ""
echo "For team access, grant permissions with:"
echo "az role assignment create --assignee <USER_OR_SP_ID> --role 'Storage Blob Data Contributor' --scope \$(az storage account show -n $STORAGE_ACCOUNT_NAME -g $RESOURCE_GROUP_NAME --query id -o tsv)"
