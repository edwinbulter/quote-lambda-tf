# Quote Azure - Microsoft Cloud Deployment

A full-stack serverless quote management application deployed on Microsoft Azure. This deployment showcases Azure's serverless capabilities with Azure Functions, API Management, and Azure Storage, demonstrating best practices for cloud-native architecture, infrastructure as code, and CI/CD automation on Azure.

## Table of Contents

- [🌟 Live Demo](#-live-demo)
- [📋 Overview](#-overview)
- [🏗️ Azure Architecture](#️-azure-architecture)
- [📦 Repository Structure](#-repository-structure)
  - [Frontend - React Web Application](#frontend---react-web-application)
  - [Backend - C# Azure Functions](#backend---c-azure-functions)
- [🚀 Quick Start](#-quick-start)
  - [Prerequisites](#prerequisites)
  - [Deploy the Complete Stack](#deploy-the-complete-stack)
- [📚 Documentation](#-documentation)
  - [Backend Documentation](#backend-documentation)
  - [Frontend Documentation](#frontend-documentation)
- [🔐 Authentication & Authorization](#-authentication--authorization)
- [🔐 GitHub Actions Setup](#github-actions-setup)
  - [Required GitHub Secrets](#required-github-secrets)
  - [Workflows](#workflows)
- [🎯 Learning Goals](#-learning-goals)
- [💰 Cost Estimate](#-cost-estimate)
- [🤝 Contributing](#-contributing)
- [📄 License](#-license)
- [🔗 Links](#-links)

## 🌟 Live Demo

Access the Azure-deployed application at:

**Azure Production Environment:**
> **https://quotefrontend.z6.web.core.windows.net/**

**API Endpoint:**
> **https://quote-api-gateway.azure-api.net/quote**

Not all features can be used if you're not signed in.  
If you don't want to register and test the restricted features, you can use these user/password combinations:  
- user-1 with password Hello-user-1
- user-2 with password Hello-user-2
- user-3 with password Hello-user-3

And to see what you can do as an admin:
- admin-1 with password Hello-admin-1

## 📋 Overview

This Azure deployment allows users to:
- Browse inspirational quotes from [ZenQuotes API](https://zenquotes.io/)
- Get random quotes with smart filtering
- Like their favorite quotes
- View popular quotes sorted by likes

The Azure deployment showcases:
- **Serverless Architecture** - Azure Functions with Java runtime
- **Infrastructure as Code** - Complete Terraform configurations for Azure
- **Modern Frontend** - React with TypeScript and Vite
- **CI/CD Automation** - GitHub Actions with Azure Service Principal authentication
- **Cloud-Native Design** - API Management, Azure Storage, Azure Functions

## 🏗️ Azure Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Azure Storage Static Web                 │
│              (quotefrontend.z6.web.core.windows.net)        │
│                   React + TypeScript                         │
└────────────────────────┬────────────────────────────────────┘
                         │
                         │ HTTPS API Calls
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   Azure API Management                     │
│         (quote-api-gateway.azure-api.net)                   │
│              • CORS Configuration                          │
│              • API Key Management                          │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                Azure Functions (C#/.NET 8)                  │
│              (quote-backend-function)                       │
│              • Serverless Compute                           │
│              • Auto-scaling                                 │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   Azure Storage Tables                      │
│                 (Storage Account Tables)                     │
│              • Quotes Table                                 │
│              • User Progress Table                          │
│              • User Likes Table                             │
└─────────────────────────────────────────────────────────────┘
```

## 📦 Repository Structure

This Azure deployment contains two main modules:

### [Frontend](./quote-azure-frontend/README.md) - React Web Application

A modern, responsive web application built with:
- **Framework**: React 18 with TypeScript
- **Build Tool**: Vite for fast development and optimized builds
- **Styling**: TailwindCSS for responsive design
- **Testing**: Vitest for unit testing
- **State Management**: React Query for server state
- **Authentication**: Azure AD integration for user login
- **Hosting**: Azure Storage Static Website
- **Deployment**: GitHub Actions with Azure Service Principal

**[📖 Frontend Documentation →](./quote-azure-frontend/README.md)**

### [Backend](./quote-azure-backend/README.md) - C# Azure Functions

A serverless REST API built with:
- **Language**: C# (.NET 8)
- **Runtime**: Azure Functions (Premium Plan)
- **API**: Azure API Management for HTTP endpoints
- **Database**: Azure Storage Tables for data persistence
- **Authentication**: Custom JWT-based authentication with email/password registration
- **Authorization**: Role-based access control (USER/ADMIN roles) with JWT tokens
- **Infrastructure**: Terraform for infrastructure management
- **External API**: ZenQuotes.io for quote data
- **Monitoring**: Azure Monitor and Application Insights
- **Deployment**: GitHub Actions with Azure Service Principal

**[📖 Backend Documentation →](./quote-azure-backend/README.md)**

## 🚀 Quick Start

### Prerequisites

- **Azure CLI** configured with credentials
- **Terraform** >= 1.0.0
- **.NET 8** (for backend)
- **Node.js 18+** (for frontend)
- **Azure Functions Core Tools** (for local development)

### Deploy the Complete Stack

#### 1. Create Azure Service Principal for GitHub Actions

```bash
# Create a service principal
az ad sp create-for-rbac --name "github-azure-deploy" --role "Contributor" --scopes "/subscriptions/<subscription-id>"

# Note down these values for GitHub secrets:
# - appId (client ID)
# - password (client secret)
# - tenant
# - subscription ID
```

#### 2. Deploy Backend Infrastructure

```bash
cd quote-azure-backend/infrastructure
terraform init
terraform apply
```

#### 3. Deploy Backend Azure Functions

Use GitHub Actions or deploy manually:

```bash
cd quote-azure-backend
dotnet build --configuration Release
func azure functionapp publish quote-backend-function
```

#### 4. Deploy Frontend Infrastructure

The frontend uses Azure Storage Static Website, which is configured in the backend Terraform.

#### 5. Deploy Frontend Application

Use the GitHub Actions workflow or deploy manually:

```bash
cd quote-azure-frontend
npm install
npm run build
az storage blob upload-batch --destination '$web' --source ./dist --account-name quotefrontend
```

## 📚 Documentation

### Backend Documentation
- [Infrastructure Setup](./quote-azure-backend/doc/infrastructure.md) - Terraform configuration and deployment
- [GitHub Workflows](./quote-azure-backend/doc/github-workflows.md) - CI/CD pipeline setup
- [API Testing](./quote-azure-backend/test-jwt-auth.http) - HTTP request examples

### Frontend Documentation
- [Development Setup](./quote-azure-frontend/README.md) - Local development and build process

## 🔐 Authentication & Authorization

The Azure deployment uses a **custom JWT-based authentication system** for user authentication and **role-based authorization** for protecting API endpoints.

### User Authentication

Users can authenticate via:

**Email + Password Registration**
- Users register with email, choose a custom username, and set a password
- Password is securely hashed using bcrypt
- Users are automatically assigned the `USER` role
- JWT access tokens (24-hour expiry) and refresh tokens (7-day expiry) are issued

### Authorization

- **Public endpoints** (`GET /quote`, `GET /quote/liked`) - No authentication required
- **Protected endpoints** (`POST /quote/{id}/like`, `DELETE /quote/{id}/like`) - Requires `USER` role
- **Authorization** is enforced in the Azure Function by validating JWT tokens and checking user roles

### Key Features

- ✅ Secure password hashing (bcrypt)
- ✅ JWT tokens with 24-hour expiration (refreshable for 7 days)
- ✅ Role-based access control (USER, ADMIN groups)
- ✅ User action logging to Azure Monitor
- ✅ CORS configured for secure cross-origin requests
- ✅ Custom JWT service with configurable signing keys

## 🔐 GitHub Actions Setup

Both frontend and backend use GitHub Actions for automated deployments with Azure Service Principal authentication.

### Required GitHub Secrets

Add these secrets to your repository:

| Secret Name | Value | Description |
|-------------|-------|-------------|
| `AZURE_CLIENT_ID` | Service Principal App ID | Azure AD application ID |
| `AZURE_CLIENT_SECRET` | Service Principal Password | Client secret for authentication |
| `AZURE_TENANT_ID` | Tenant ID | Azure AD tenant ID |
| `AZURE_SUBSCRIPTION_ID` | Subscription ID | Azure subscription ID |
| `STORAGE_ACCOUNT_NAME` | `quotefrontend` | Frontend storage account |
| `RESOURCE_GROUP_NAME` | `quote-frontend-rg` | Resource group name |

### Workflows

- **[deploy-azure-backend.yml](./.github/workflows/deploy-azure-backend.yml)** - Builds and deploys the backend Azure Functions
- **[deploy-azure-frontend.yml](./.github/workflows/deploy-azure-frontend.yml)** - Builds and deploys the frontend to Azure Storage

## 🎯 Learning Goals

This Azure deployment demonstrates:

1. **Serverless Architecture on Azure**
   - Building REST APIs with Azure Functions
   - Optimizing performance with Premium Plan
   - API Management configuration and policies

2. **Infrastructure as Code for Azure**
   - Managing Azure resources with Terraform
   - Remote state management with Azure Storage
   - Modular infrastructure design

3. **Modern Frontend Development**
   - React with TypeScript and Vite
   - Responsive design with TailwindCSS
   - Unit testing with Vitest

4. **DevOps Best Practices on Azure**
   - CI/CD with GitHub Actions
   - Service Principal authentication
   - Automated testing and deployment

5. **Azure Cloud-Native Patterns**
   - Static website hosting with Azure Storage
   - NoSQL data modeling with Azure Storage Tables
   - RESTful API design with API Management

## 💰 Cost Estimate

This application runs on Azure Free Tier eligible services:

- **Azure Functions**: 1M executions free (Consumption Plan) / Always free on Premium with credit
- **API Management**: Consumption tier (~$2-5/month) - Pay-per-use pricing ✅ **Now configured**
- **Azure Storage**: 5GB storage + 100K transactions free
- **Azure Monitor**: Free tier included

**Estimated monthly cost**: $0-5 for low traffic (API Management Consumption tier with pay-per-use pricing)

**Cost Benefits**:
- ✅ API Management Consumption tier is significantly cheaper than Developer tier
- Most services are within Azure Free Tier limits for low-traffic applications
- Pay-per-use model means you only pay for actual usage

**Note**: API Management has been successfully switched from Developer tier (~$50/month) to Consumption tier (~$2-5/month). The API configuration may need to be manually recreated in the Azure Portal due to management endpoint issues during the migration.

## 🤝 Contributing

This is a learning project, but suggestions and improvements are welcome!

## 📄 License

This project is open source and available for educational purposes.

## 🔗 Links

- **Live Application (Azure)**: https://quotefrontend.z6.web.core.windows.net/
- **API Endpoint**: https://quote-api-gateway.azure-api.net/quote
- **Azure Portal**: https://portal.azure.com
- **ZenQuotes API**: https://zenquotes.io/

---

Built with ❤️ to learn Azure serverless architecture and modern web development
