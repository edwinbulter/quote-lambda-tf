# Quote Azure Backend

A serverless REST API backend for the [Quote Azure frontend](../quote-azure-frontend/README.md), built with C# .NET 8 and deployed on Azure Functions. This project demonstrates modern serverless architecture patterns using Azure services and infrastructure as code.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Tech Stack](#tech-stack)
  - [Core Technologies](#core-technologies)
  - [Dependencies](#dependencies)
  - [Build & Testing](#build--testing)
  - [DevOps](#devops)
- [API Endpoints](#api-endpoints)
- [Documentation](#documentation)
- [Quick Start](#quick-start)
  - [Prerequisites](#prerequisites)
  - [Build the Project](#build-the-project)
  - [Deploy Infrastructure](#deploy-infrastructure)
  - [Deploy Function App](#deploy-function-app)
- [Project Structure](#project-structure)
- [Goals](#goals)

## Overview

This Azure Functions backend:
- Fetches inspirational quotes from [ZenQuotes API](https://zenquotes.io/) and stores them in Azure Storage Tables
- Provides a REST API for retrieving random quotes and managing liked quotes
- Is created to learn about building serverless APIs with C#, Azure Functions, API Management, and Terraform
- Is exposed through Azure API Management and consumed by the Quote Azure Web App frontend at:
  > https://quotefrontend.z6.web.core.windows.net/

The code for the Quote Azure Web App can be found at:
> https://github.com/edwinbulter/quote-lambda-tf/tree/main/quote-azure-frontend

## Features

- **Random Quote Retrieval**: Get random quotes with optional exclusion filters
- **Quote Persistence**: Automatic caching of quotes in Azure Storage Tables
- **User Authentication**: Custom JWT-based authentication with email/password registration
- **Like System**: Track and retrieve liked quotes with custom ordering
- **Favourite Management**: Reorder liked quotes with automatic sequential ordering
- **View History**: Track user's viewed quotes with automatic exclusion from future requests
- **Role-Based Access Control**: USER and ADMIN roles for authorization
- **Fast Performance**: Azure Functions Premium Plan with consistent performance
- **Automated Deployments**: GitHub Actions CI/CD pipeline with Azure Service Principal authentication
- **Infrastructure as Code**: Complete Terraform configuration for reproducible deployments
- **CORS Support**: Configured for cross-origin requests from the frontend

## Tech Stack

### Core Technologies
- **C# .NET 8** - Modern .NET with isolated worker model
- **Azure Functions** - Serverless compute with Premium Plan
- **API Management** - HTTP API endpoints with throttling and CORS
- **Azure Storage Tables** - NoSQL database with on-demand scaling
- **Terraform** - Infrastructure as Code with remote state management

### Dependencies
- **Microsoft.Azure.Functions.Worker** - Azure Functions runtime (v1.13.0)
- **Microsoft.Azure.Functions.Worker.Sdk** - Functions SDK (v1.17.0)
- **Microsoft.Azure.Functions.Worker.Extensions.Http** - HTTP trigger support (v3.2.0)
- **Azure.Data.Tables** - Azure Storage Tables client (v12.8.0)
- **Microsoft.IdentityModel.Tokens** - JWT token handling (v7.1.2)
- **System.IdentityModel.Tokens.Jwt** - JWT token validation (v7.1.2)
- **Microsoft.AspNetCore.Identity** - Password hashing (v2.2.0)

### Build & Testing
- **.NET CLI** - Build and package management
- **Azure Functions Core Tools** - Local development and testing
- **xUnit** - Unit testing framework (v2.6.1)
- **Moq** - Mocking framework for tests (v4.20.69)

### DevOps
- **GitHub Actions** - CI/CD automation
- **Azure Service Principal** - Secure authentication without long-lived credentials
- **Remote State** - Azure Storage backend with state locking

## API Endpoints

### Public Endpoints (No Authentication Required)

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|--------------|
| `GET` | `/api/quote` | Get a random quote (unauthenticated users don't record views) | None |
| `POST` | `/api/quote` | Get a random quote excluding specific IDs | Array of quote IDs to exclude |
| `GET` | `/api/quote/liked` | Get all liked quotes (public view, sorted by order) | None |

### Authentication Endpoints

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|--------------|
| `POST` | `/api/auth/register` | Register new user account | `{email, username, password, confirmPassword}` |
| `POST` | `/api/auth/login` | Login user and receive JWT token | `{loginIdentifier, password}` |
| `POST` | `/api/auth/change-password` | Change user password | `{oldPassword, newPassword}` |
| `POST` | `/api/auth/unregister` | Delete user account | `{password}` |

### Authenticated Endpoints (Requires USER Role)

| Method | Endpoint | Description | Auth | Request Body |
|--------|----------|-------------|------|--------------|
| `GET` | `/api/quote` | Get a random quote and record view (excludes previously viewed quotes) | Bearer Token | None |
| `POST` | `/api/quote/{id}/like` | Like a quote (adds to end of favourites list) | Bearer Token | None |
| `DELETE` | `/api/quote/{id}/unlike` | Unlike a quote (remove from favourites) | Bearer Token | None |
| `GET` | `/api/quote/liked` | Get user's liked quotes sorted by custom order | Bearer Token | None |
| `PUT` | `/api/quote/{id}/reorder` | Reorder a liked quote to new position | Bearer Token | `{"order": <integer>}` |
| `GET` | `/api/quote/viewed` | Get user's view history in chronological order | Bearer Token | None |
| `GET` | `/api/quote/progress` | Get user's reading progress statistics | Bearer Token | None |

### Admin Endpoints (Requires ADMIN Role)

| Method | Endpoint | Description | Auth | Request Body |
|--------|----------|-------------|------|--------------|
| `GET` | `/api/manage/users` | List all users with their attributes | Bearer Token | None |
| `GET` | `/api/manage/users/{userId}` | Get specific user details | Bearer Token | None |
| `POST` | `/api/manage/users/role` | Add or remove user role | Bearer Token | `{userId, role, remove?: boolean}` |
| `DELETE` | `/api/manage/users/account` | Remove user account and all their data | Bearer Token | `{password}` |
| `GET` | `/api/manage/quotes` | List all quotes with pagination, search, and sorting | Bearer Token | Query Parameters |
| `POST` | `/api/manage/quotes/fetch` | Fetch and add new quotes from ZEN API | Bearer Token | `{text, author}` |
| `PUT` | `/api/manage/quotes/{id}` | Update existing quote | Bearer Token | `{text, author}` |
| `DELETE` | `/api/manage/quotes/{id}` | Delete quote | Bearer Token | None |
| `GET` | `/api/manage/stats` | Get application statistics | Bearer Token | None |
| `GET` | `/api/system/status` | Health check endpoint | Bearer Token | None |

**API Base URL**: `https://quote-api-gateway.azure-api.net/quote`

### Admin Quotes Endpoint - Query Parameters

The `/api/manage/quotes` endpoint supports the following query parameters for pagination, search, and sorting:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | integer | `1` | Page number (1-based) |
| `pageSize` | integer | `50` | Number of quotes per page (max 250) |
| `quoteText` | string | `null` | Filter quotes by text content (case-insensitive contains) |
| `author` | string | `null` | Filter quotes by author name (case-insensitive contains) |
| `sortBy` | string | `id` | Sort field: `id`, `quoteText`, `author`, `likeCount` |
| `sortOrder` | string | `asc` | Sort order: `asc`, `desc` (likeCount only supports `desc`) |

**Example Request**:
```bash
curl -X GET "https://quote-api-gateway.azure-api.net/quote/manage/quotes?page=1&pageSize=25&quoteText=inspiration&sortBy=likeCount&sortOrder=desc" \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>"
```

**Response Format**:
```json
{
  "quotes": [
    {
      "id": 1,
      "text": "The only way to do great work is to love what you do.",
      "author": "Steve Jobs",
      "likeCount": 15
    }
  ],
  "totalCount": 150,
  "page": 1,
  "pageSize": 25,
  "totalPages": 6
}
```

### Authentication & Authorization

- **Authentication**: Custom JWT-based authentication with Bearer tokens
- **Authorization**: Role-based access control (USER, ADMIN roles)
- **Supported Auth Methods**:
  - Email/Password registration and login
  - Automatic role assignment on user creation
  - JWT token validation with custom claims

### Request/Response Examples

#### Authentication Endpoints

**Register New User**:
```bash
curl -X POST https://quote-api-gateway.azure-api.net/quote/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "username": "testuser",
    "password": "SecurePassword123!",
    "confirmPassword": "SecurePassword123!"
  }'
```

**Login User**:
```bash
curl -X POST https://quote-api-gateway.azure-api.net/quote/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "loginIdentifier": "testuser",
    "password": "SecurePassword123!"
  }'
```

#### User Endpoints

**Like a Quote**:
```bash
curl -X POST https://quote-api-gateway.azure-api.net/quote/quote/79/like \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json"
```

**Reorder a Liked Quote**:
```bash
curl -X PUT https://quote-api-gateway.azure-api.net/quote/quote/79/reorder \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"order": 2}'
```

**Get Liked Quotes** (sorted by order):
```bash
curl -X GET https://quote-api-gateway.azure-api.net/quote/quote/liked \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Get View History**:
```bash
curl -X GET https://quote-api-gateway.azure-api.net/quote/quote/viewed \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

#### Admin Endpoints

**List All Users**:
```bash
curl -X GET https://quote-api-gateway.azure-api.net/quote/manage/users \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>"
```

**Add User to Admin Role**:
```bash
curl -X POST https://quote-api-gateway.azure-api.net/quote/manage/users/role \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-id-here",
    "role": "ADMIN"
  }'
```

**Remove User from Admin Role**:
```bash
curl -X POST https://quote-api-gateway.azure-api.net/quote/manage/users/role \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-id-here",
    "role": "ADMIN",
    "remove": true
  }'
```

**Delete User**:
```bash
curl -X DELETE https://quote-api-gateway.azure-api.net/quote/manage/users/account \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"password": "UserPassword123!"}'
```

**List Quotes with Pagination and Search**:
```bash
curl -X GET "https://quote-api-gateway.azure-api.net/quote/manage/quotes?page=1&pageSize=25&quoteText=success&sortBy=likeCount&sortOrder=desc" \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>"
```

**Add New Quote**:
```bash
curl -X POST https://quote-api-gateway.azure-api.net/quote/manage/quotes/fetch \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Success is not final, failure is not fatal: it is the courage to continue that counts.",
    "author": "Winston Churchill"
  }'
```

**Get Application Statistics**:
```bash
curl -X GET https://quote-api-gateway.azure-api.net/quote/manage/stats \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>"
```

## Documentation

Detailed documentation is available in the [`infrastructure/`](./infrastructure) folder:

### Core Documentation

- **[infrastructure/README.md](./infrastructure/README.md)** - Complete guide to the Terraform infrastructure setup, including:
  - Azure architecture overview
  - Terraform state management with Azure Storage
  - Deployment instructions
  - API Management configuration
  - Cost estimates and monitoring

### Authentication & Features Documentation

- **[JWT Service Implementation](./src/Services/JwtService.cs)** - Custom JWT token generation and validation:
  - Token creation with user claims
  - Token validation and extraction
  - Role-based authorization
  - Security best practices

- **[Authentication Handler](./src/Handlers/AuthHandler.cs)** - User authentication endpoints:
  - User registration with password hashing
  - Login with JWT token generation
  - Password change functionality
  - Account deletion

## Quick Start

### Prerequisites
- .NET 8 SDK
- Azure Functions Core Tools v4
- Azure CLI configured with credentials
- Terraform >= 1.0.0

### Build the Project

```bash
dotnet clean
dotnet build
dotnet publish -c Release -o ./publish
```

### Deploy Infrastructure

```bash
cd infrastructure
terraform init
terraform apply
```

### Deploy Function App

Use GitHub Actions workflow or deploy manually:

```bash
cd src
func azure functionapp publish quote-backend-function
```

## Project Structure

```
quote-azure-backend/
├── src/
│   ├── Handlers/
│   │   ├── AuthHandler.cs              # Authentication endpoints (register, login, etc.)
│   │   ├── QuoteHandler.cs             # Quote management endpoints
│   │   └── AdminHandler.cs             # Admin management endpoints
│   ├── Services/
│   │   ├── IJwtService.cs              # JWT service interface
│   │   ├── JwtService.cs               # JWT token generation and validation
│   │   ├── IQuoteService.cs            # Quote service interface
│   │   ├── QuoteService.cs             # Quote business logic
│   │   ├── IZenQuotesService.cs        # ZenQuotes API interface
│   │   ├── ZenQuotesService.cs         # External API client
│   │   ├── IQuoteManagementService.cs  # Quote management interface
│   │   ├── QuoteManagementService.cs   # Quote CRUD operations
│   │   ├── IAdminService.cs            # Admin service interface
│   │   ├── AdminService.cs             # Admin operations
│   │   └── IUserService.cs             # User service interface
│   ├── Data/
│   │   ├── Repositories/
│   │   │   ├── IQuoteRepository.cs     # Quote data access interface
│   │   │   ├── QuoteRepository.cs      # Quote data access implementation
│   │   │   ├── IUserRepository.cs      # User data access interface
│   │   │   ├── UserRepository.cs       # User data access implementation
│   │   │   ├── IUserActivityRepository.cs # User activity interface
│   │   │   ├── UserActivityRepository.cs # User activity tracking
│   │   │   ├── IUserRoleRepository.cs  # User role interface
│   │   │   └── UserRoleRepository.cs   # User role management
│   │   └── Entities/
│   │       ├── Quote.cs                # Quote entity
│   │       ├── User.cs                 # User entity
│   │       ├── UserActivity.cs         # User activity tracking
│   │       └── UserRole.cs              # User role entity
│   ├── Models/
│   │   ├── Auth/
│   │   │   ├── RegisterRequest.cs      # Registration request model
│   │   │   ├── LoginRequest.cs         # Login request model
│   │   │   └── ChangePasswordRequest.cs # Password change model
│   │   ├── Admin/
│   │   │   ├── UserResponse.cs          # User response model
│   │   │   └── StatsResponse.cs        # Statistics response model
│   │   └── Quote/
│   │       ├── QuoteRequest.cs         # Quote request model
│   │       └── QuoteResponse.cs        # Quote response model
│   ├── Middleware/
│   │   └── JwtAuthenticationMiddleware.cs # JWT authentication middleware
│   ├── Program.cs                      # Function app startup and DI configuration
│   └── local.settings.sample.json      # Local development settings
├── infrastructure/                     # Terraform configuration
│   ├── main.tf                        # Main Azure resources
│   ├── variables.tf                   # Terraform variables
│   ├── outputs.tf                     # Terraform outputs
│   ├── api-gateway.tf                 # API Management setup
│   ├── storage.tf                     # Azure Storage configuration
│   ├── function-app.tf                # Azure Functions configuration
│   ├── monitor.tf                     # Application Insights
│   └── backend.tf                     # Remote state configuration
├── .github/
│   └── workflows/
│       └── deploy-azure-backend.yml   # GitHub Actions CI/CD pipeline
├── tests/                            # Unit tests
│   ├── Services/
│   ├── Handlers/
│   └── Data/
├── host.json                         # Azure Functions host configuration
├── local.settings.json               # Local development settings
└── README.md                         # This file
```

## Goals

This project serves as a learning platform for:
- Building serverless REST APIs with C# and Azure Functions
- Implementing custom JWT-based authentication systems
- Implementing role-based access control (RBAC) in Function handlers
- Validating JWT tokens and extracting custom claims
- Designing data models with Azure Storage Tables
- Implementing infrastructure as code with Terraform
- Setting up CI/CD pipelines with GitHub Actions
- Managing Azure resources with least-privilege IAM roles
- Integrating external APIs and caching data in Azure Storage
- Building user-centric features (view history, custom ordering)
- Writing comprehensive unit tests with xUnit and Moq
- Understanding Azure API Management for API gateway functionality
