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

The Live Demo is deactivated because OVH Cloud was sending mails about skyhigh bill predictions, which they admitted was wrong the first time. Today (2026-08-01) I became another high prediction, and as it is just a testing environment for me personlly I will unsubscribe from OVH Cloud.

```text
Dear customer,

As requested, we have sent you this email to warn you that by the end of the month you are likely to go over the threshold that you specified (€10.00).

As of 2026-06-23 at 12:47 you have used a total of €5.62 since the beginning of this month.
At the current rate, we estimate that in total you will use €4,043.52* in the current month.

------

Dear customer,

As requested, we have sent you this email to warn you that by the end of the month you are likely to go over the threshold that you specified (€10.00).

As of 2026-08-01 at 01:37 you have used a total of €0.27 since the beginning of this month.
At the current rate, we estimate that in total you will use €201.18* in the current month.

We therefore suggest that you adjust your alert or adjust your resources for this project in your control panel.
Please note that you can switch to "monthly billing" at any moment to reduce the cost in the medium term.

If you do not make any changes, you will receive another alert in 1 hour.

*estimate as a guideline only, excludes additional bandwidth and any resources added after this email.


The OVHcloud Team
```

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

This application uses multiple Azure services across both backend and frontend. Here's the complete cost breakdown:

### **Backend Infrastructure (quote-azure-backend)**

| Service | Configuration | Free Tier Coverage | Estimated Monthly Cost |
|---------|---------------|-------------------|----------------------|
| **Azure Functions** | Consumption Plan (Y1 SKU) | 1M executions free + 400k GB-sec | $0-2 for typical usage |
| **API Management** | Consumption tier (Consumption_0) | No free tier | $2-5 based on API calls |
| **Azure Storage Tables** | 4 tables (quotes, userlikes, userprogress, userroles) | 5GB storage + 100K transactions free | $0-1 for low traffic |
| **Application Insights** | Basic logging | 5GB data free | $0-1 for moderate usage |
| **Log Analytics Workspace** | PerGB2018 SKU | 5GB data free | $0-1 for moderate usage |
| **App Service Plan** | Consumption (Y1) | Included with Functions | $0 (billed with Functions) |

### **Frontend Infrastructure (quote-azure-frontend)**

| Service | Configuration | Free Tier Coverage | Estimated Monthly Cost |
|---------|---------------|-------------------|----------------------|
| **Azure Storage Account** | StorageV2 LRS (quotefrontend) | 5GB storage + 100K transactions free | $0-1 for static assets |
| **Static Website Hosting** | $web container | Completely free | $0 |
| **Terraform State Storage** | Same storage account, separate container | Included in storage free tier | $0 |

### **Total Estimated Monthly Costs**

| Usage Level | Backend | Frontend | **Total** |
|-------------|---------|----------|-----------|
| **Low Traffic** (<10K API calls/month) | $2-4 | $0-1 | **$2-5** |
| **Moderate Traffic** (50K API calls/month) | $5-8 | $0-1 | **$5-9** |
| **High Traffic** (100K+ API calls/month) | $8-15 | $1-2 | **$9-17** |

### **Cost Optimization Benefits**

✅ **API Management Consumption Tier**: Switched from Developer tier (~$50/month) to Consumption tier (~$2-5/month)  
✅ **Shared Storage**: Frontend uses existing storage account, no additional storage costs  
✅ **Free Tier Utilization**: Most services stay within Azure Free Tier limits for low-traffic usage  
✅ **Pay-per-use Model**: Only pay for actual API calls and storage consumption  
✅ **No CDN Costs**: Removed CDN configuration to eliminate additional expenses  

### **Cost Management Tips**

1. **Monitor API Usage**: API Management Consumption tier charges per million requests
2. **Optimize Storage**: Regular cleanup of unused data in Storage Tables
3. **Review Logs**: Configure appropriate retention periods in Log Analytics
4. **Function Optimization**: Keep function execution times minimal to reduce consumption costs
5. **Static Asset Caching**: Leverage browser caching to reduce storage transactions

### **Infrastructure Summary**

**Total Azure Resources Created:**
- 1 Resource Group (quote-backend-rg)
- 1 Azure Function App (Consumption Plan)
- 1 API Management instance (Consumption tier)
- 1 Application Insights instance
- 1 Log Analytics Workspace
- 4 Azure Storage Tables
- 1 Storage Account for frontend (shared)
- 1 Static Website hosting
- Terraform state management

**Most services are within Azure Free Tier limits for typical learning project usage, making this an extremely cost-effective deployment for development and small-scale production.**

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
