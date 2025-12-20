# Quote Backend

A serverless REST API backend for the [Quote frontend](../quote-lambda-tf-frontend/README.md), built with Java 21 and deployed on AWS Lambda. This project demonstrates modern serverless architecture patterns using AWS services and infrastructure as code.

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
  - [Deploy Lambda Function](#deploy-lambda-function)
- [Project Structure](#project-structure)
- [Goals](#goals)

## Overview

This AWS Lambda backend:
- Fetches inspirational quotes from [ZenQuotes API](https://zenquotes.io/) and stores them in DynamoDB
- Provides a REST API for retrieving random quotes and managing liked quotes
- Is created to learn about building serverless APIs with Java, AWS Lambda, API Gateway, and Terraform
- Is exposed through API Gateway and consumed by the Quote Web App frontend at:
  > https://d5ly3miadik75.cloudfront.net/

The code for the Quote Web App can be found at:
> https://github.com/edwinbulter/quote-lambda-tf/tree/main/quote-lambda-tf-frontend

## Features

- **Random Quote Retrieval**: Get random quotes with optional exclusion filters
- **Quote Persistence**: Automatic caching of quotes in DynamoDB
- **User Authentication**: AWS Cognito integration with email/password and Google OAuth
- **Like System**: Track and retrieve liked quotes with custom ordering
- **Favourite Management**: Reorder liked quotes with automatic sequential ordering
- **Sequential Quote Navigation**: Track user's progress through quotes with sequential viewing and previous/next navigation
- **Role-Based Access Control**: USER and ADMIN roles for authorization
- **Fast Cold Starts**: Lambda SnapStart enabled (~200ms cold start vs 3-6s)
- **Automated Deployments**: GitHub Actions CI/CD pipeline with OIDC authentication
- **Infrastructure as Code**: Complete Terraform configuration for reproducible deployments
- **CORS Support**: Configured for cross-origin requests from the frontend

## Tech Stack

### Core Technologies
- **Java 21** - Modern Java with Amazon Corretto runtime
- **AWS Lambda** - Serverless compute with SnapStart optimization
- **API Gateway** - HTTP API endpoints with throttling and CORS
- **DynamoDB** - NoSQL database with on-demand scaling
- **Terraform** - Infrastructure as Code with remote state management

### Dependencies
- **AWS SDK for Java 2.x** - DynamoDB client (v2.29.9)
- **AWS Lambda Java Core** - Lambda runtime interface (v1.2.1)
- **AWS Lambda Java Events** - API Gateway event handling (v3.12.0)
- **Apache HttpClient** - HTTP requests to ZenQuotes API (v4.5.13)
- **Gson** - JSON serialization/deserialization (v2.10.1)
- **Logback** - Logging framework (v1.5.12)

### Build & Testing
- **Maven** - Dependency management and build automation
- **Maven Shade Plugin** - Creates fat JAR for Lambda deployment
- **JUnit 5** - Unit testing framework (v5.11.3)
- **Mockito** - Mocking framework for tests (v4.5.1)

### DevOps
- **GitHub Actions** - CI/CD automation
- **AWS OIDC** - Secure authentication without long-lived credentials
- **Remote State** - S3 backend with DynamoDB locking

## API Endpoints

### Public Endpoints (No Authentication Required)

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|--------------|
| `GET` | `/quote` | Get a random quote (unauthenticated users don't record views) | None |
| `POST` | `/quote` | Get a random quote excluding specific IDs | Array of quote IDs to exclude |
| `GET` | `/quote/liked` | Get all liked quotes (public view, sorted by order) | None |

### Authenticated Endpoints (Requires USER Role)

| Method | Endpoint | Description | Auth | Request Body |
|--------|----------|-------------|------|--------------|
| `GET` | `/quote` | Get a random quote and record view (excludes previously viewed quotes) | Bearer Token | None |
| `POST` | `/quote/{id}/like` | Like a quote (adds to end of favourites list) | Bearer Token | None |
| `DELETE` | `/quote/{id}/unlike` | Unlike a quote (remove from favourites) | Bearer Token | None |
| `GET` | `/quote/liked` | Get user's liked quotes sorted by custom order | Bearer Token | None |
| `PUT` | `/quote/{id}/reorder` | Reorder a liked quote to new position | Bearer Token | `{"order": <integer>}` |
| `GET` | `/quote/history` | Get user's viewed quotes (quotes 1 to lastQuoteId) | Bearer Token | None |

### Admin Endpoints (Requires ADMIN Role)

| Method | Endpoint | Description | Auth | Request Body |
|--------|----------|-------------|------|--------------|
| `GET` | `/api/v1/admin/users` | List all users with their attributes | Bearer Token | None |
| `POST` | `/api/v1/admin/users/{username}/groups/{groupName}` | Add user to a group (USER/ADMIN) | Bearer Token | None |
| `DELETE` | `/api/v1/admin/users/{username}/groups/{groupName}` | Remove user from a group | Bearer Token | None |
| `DELETE` | `/api/v1/admin/users/{username}` | Delete user and all their data | Bearer Token | None |
| `GET` | `/api/v1/admin/quotes` | List all quotes with pagination, search, and sorting | Bearer Token | Query Parameters |
| `POST` | `/api/v1/admin/quotes/fetch` | Fetch and add new quotes from ZEN API | Bearer Token | None |
| `GET` | `/api/v1/admin/likes/total` | Get total number of likes across all quotes | Bearer Token | None |

**API Base URL**: `https://sy5vvqbh93.execute-api.eu-central-1.amazonaws.com`

### Admin Quotes Endpoint - Query Parameters

The `/api/v1/admin/quotes` endpoint supports the following query parameters for pagination, search, and sorting:

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
curl -X GET "https://sy5vvqbh93.execute-api.eu-central-1.amazonaws.com/api/v1/admin/quotes?page=1&pageSize=25&quoteText=inspiration&sortBy=likeCount&sortOrder=desc" \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>"
```

**Response Format**:
```json
{
  "quotes": [
    {
      "id": 1,
      "quoteText": "The only way to do great work is to love what you do.",
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

- **Authentication**: AWS Cognito with JWT tokens (Bearer token in Authorization header)
- **Authorization**: Role-based access control (USER, ADMIN roles)
- **Supported Auth Methods**:
  - Email/Password sign-up and sign-in
  - Google OAuth integration
  - Automatic role assignment on user creation

### Request/Response Examples

#### User Endpoints

**Like a Quote**:
```bash
curl -X POST https://sy5vvqbh93.execute-api.eu-central-1.amazonaws.com/quote/79/like \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json"
```

**Reorder a Liked Quote**:
```bash
curl -X PUT https://sy5vvqbh93.execute-api.eu-central-1.amazonaws.com/quote/79/reorder \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"order": 2}'
```

**Get Liked Quotes** (sorted by order):
```bash
curl -X GET https://sy5vvqbh93.execute-api.eu-central-1.amazonaws.com/quote/liked \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Get View History**:
```bash
curl -X GET https://sy5vvqbh93.execute-api.eu-central-1.amazonaws.com/quote/history \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

#### Admin Endpoints

**List All Users**:
```bash
curl -X GET https://sy5vvqbh93.execute-api.eu-central-1.amazonaws.com/api/v1/admin/users \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>"
```

**Add User to Admin Group**:
```bash
curl -X POST https://sy5vvqbh93.execute-api.eu-central-1.amazonaws.com/api/v1/admin/users/john.doe/groups/ADMIN \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>"
```

**Remove User from Group**:
```bash
curl -X DELETE https://sy5vvqbh93.execute-api.eu-central-1.amazonaws.com/api/v1/admin/users/john.doe/groups/ADMIN \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>"
```

**Delete User**:
```bash
curl -X DELETE https://sy5vvqbh93.execute-api.eu-central-1.amazonaws.com/api/v1/admin/users/john.doe \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>"
```

**List Quotes with Pagination and Search**:
```bash
curl -X GET "https://sy5vvqbh93.execute-api.eu-central-1.amazonaws.com/api/v1/admin/quotes?page=1&pageSize=25&quoteText=success&sortBy=likeCount&sortOrder=desc" \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>"
```

**Fetch New Quotes from ZEN API**:
```bash
curl -X POST https://sy5vvqbh93.execute-api.eu-central-1.amazonaws.com/api/v1/admin/quotes/fetch \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>" \
  -H "Content-Type: application/json"
```

**Get Total Likes**:
```bash
curl -X GET https://sy5vvqbh93.execute-api.eu-central-1.amazonaws.com/api/v1/admin/likes/total \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>"
```

**Response Format**:
```json
{
  "totalLikes": 150
}
```

## Documentation

Detailed documentation is available in the [`doc/`](./doc) folder:

### Core Documentation

- **[infrastructure.md](./doc/infrastructure.md)** - Complete guide to the Terraform infrastructure setup, including:
  - AWS architecture overview
  - Terraform state management with S3 and DynamoDB
  - Deployment instructions
  - API Gateway throttling configuration
  - Cost estimates and monitoring

- **[github-workflows.md](./doc/github-workflows.md)** - GitHub Actions CI/CD pipeline documentation:
  - Automated build, test, and deployment workflow
  - AWS OIDC setup instructions (quick and manual)
  - Workflow triggers and monitoring
  - Troubleshooting guide

- **[snapstart-setup.md](./doc/snapstart-setup.md)** - Lambda SnapStart configuration guide:
  - Cold start optimization (3-6s → ~200ms)
  - Deployment steps and verification
  - How SnapStart works
  - Cost analysis and monitoring

### Authentication & Features Documentation

- **[authentication-authorization-setup.md](../doc/authentication-authorization-setup.md)** - Complete authentication setup guide:
  - Cognito User Pool configuration
  - Email/Password authentication flow
  - Google OAuth integration
  - Role-based access control (RBAC)
  - JWT token handling

- **[backend-auth-setup.md](./doc/backend-auth-setup.md)** - Backend authentication implementation:
  - JWT token validation
  - Role extraction from Cognito tokens
  - Authorization checks in Lambda handlers
  - Error handling and security best practices

- **[test-api.http](./doc/test-api.http)** - HTTP request examples for testing the API endpoints:
  - Authentication flow examples
  - Like/Unlike operations
  - Reorder favourites workflow
  - View history retrieval
  - Error case testing

## Quick Start

### Prerequisites
- Java 21 (Amazon Corretto recommended)
- Maven 3.x
- AWS CLI configured with credentials
- Terraform >= 1.0.0

### Build the Project

```bash
mvn clean package
```

### Deploy Infrastructure

```bash
cd infrastructure
terraform init
terraform apply
```

### Deploy Lambda Function

Use GitHub Actions workflow or deploy manually:

```bash
aws lambda update-function-code \
  --function-name quotes-lambda-java \
  --zip-file fileb://target/quote-lambda-java-1.0-SNAPSHOT.jar \
  --region eu-central-1
```

## Project Structure

```
quote-lambda-tf-backend/
├── src/
│   ├── main/java/ebulter/quote/lambda/
│   │   ├── QuoteHandler.java           # Main Lambda handler
│   │   ├── service/
│   │   │   └── QuoteService.java       # Business logic
│   │   ├── repository/
│   │   │   ├── QuoteRepository.java    # Quote data access
│   │   │   ├── UserLikeRepository.java # Like/favourite management
│   │   │   └── UserProgressRepository.java # Sequential progress tracking
│   │   ├── model/
│   │   │   ├── Quote.java             # Quote entity
│   │   │   ├── UserLike.java          # User like with order
│   │   │   └── UserProgress.java      # User progress tracking with lastQuoteId
│   │   ├── client/
│   │   │   └── ZenClient.java         # ZenQuotes API client
│   │   └── util/
│   │       └── QuoteUtil.java         # Utility functions
│   └── test/java/                     # Unit tests
│       └── QuoteHandlerTest.java      # Handler tests (nested test classes)
├── infrastructure/                    # Terraform configuration
│   ├── backend.tf                     # S3 remote state
│   ├── lambda.tf                      # Lambda function with SnapStart
│   ├── dynamodb_quotes.tf             # Quotes table
│   ├── dynamodb_user_likes.tf         # User likes table with order field
│   ├── dynamodb_user_progress.tf     # User progress table with sequential tracking
│   ├── api_gateway.tf                 # API Gateway setup
│   ├── cognito.tf                     # Cognito User Pool
│   ├── iam.tf                         # IAM roles and policies
│   └── ...
├── .github/
│   ├── workflows/                     # GitHub Actions workflows
│   │   ├── deploy-lambda.yml
│   │   └── playwright.yml
│   └── setup-aws-oidc.sh              # OIDC setup script
├── doc/                               # Documentation
│   ├── infrastructure.md
│   ├── github-workflows.md
│   ├── snapstart-setup.md
│   ├── backend-auth-setup.md
│   ├── json-logging.md
│   └── test-api.http
└── pom.xml                            # Maven configuration
```

## Goals

This project serves as a learning platform for:
- Building serverless REST APIs with Java and AWS Lambda
- Implementing user authentication with AWS Cognito
- Implementing role-based access control (RBAC) in Lambda handlers
- Validating JWT tokens and extracting claims
- Designing data models with custom ordering (favourites management)
- Implementing infrastructure as code with Terraform
- Setting up CI/CD pipelines with GitHub Actions
- Optimizing Lambda cold starts with SnapStart
- Managing AWS resources with least-privilege IAM roles
- Integrating external APIs and caching data in DynamoDB
- Building user-centric features (sequential navigation, custom ordering)
- Writing comprehensive unit tests with nested test classes
