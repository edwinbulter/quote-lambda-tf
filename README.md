# Quote Lambda TF

A full-stack serverless quote management application built with modern cloud-native technologies. This monorepo contains both the frontend React application and the backend Java Lambda API, demonstrating best practices for serverless architecture, infrastructure as code, and CI/CD automation.

## Table of Contents

- [🌟 Live Demo](#-live-demo)
- [📋 Overview](#-overview)
- [🏗️ Architecture](#️-architecture)
- [📦 Repository Structure](#-repository-structure)
  - [Frontend - React Web Application](#frontend---react-web-application)
  - [Backend - Java Lambda API](#backend---java-lambda-api)
- [🚀 Quick Start](#-quick-start)
  - [Prerequisites](#prerequisites)
  - [Deploy the Complete Stack](#deploy-the-complete-stack)
- [🔧 Technology Stack](#-technology-stack)
  - [Frontend](#frontend)
  - [Backend](#backend)
  - [Infrastructure](#infrastructure)
  - [DevOps](#devops)
- [📚 Documentation](#-documentation)
  - [Backend Documentation](#backend-documentation)
  - [Frontend Documentation](#frontend-documentation)
  - [Shared Documentation](#shared-documentation)
- [🔐 Authentication & Authorization](#-authentication--authorization)
- [🔐 GitHub Actions Setup](#-github-actions-setup)
  - [Required GitHub Secret](#required-github-secret)
  - [Workflows](#workflows)
- [🎯 Learning Goals](#-learning-goals)
- [💰 Cost Estimate](#-cost-estimate)
- [🤝 Contributing](#-contributing)
- [📄 License](#-license)
- [🔗 Links](#-links)

## 🌟 Live Demo

Access the live application at:

**Production Environment:**
> **https://d5ly3miadik75.cloudfront.net/**

**Development Environment:**
> **https://d1fzgis91zws1k.cloudfront.net/**

Not all features can be used if you're not signed in.  
If you don't want to register and test the restricted features, you can use these user/password combinations:  
- user-1 with password Hello-user-1
- user-2 with password Hello-user-2
- user-3 with password Hello-user-3

## 📋 Overview

This application allows users to:
- Browse inspirational quotes from [ZenQuotes API](https://zenquotes.io/)
- Get random quotes with smart filtering
- Like their favorite quotes
- View popular quotes sorted by likes

The project showcases:
- **Serverless Architecture** - AWS Lambda with SnapStart optimization
- **Infrastructure as Code** - Complete Terraform configurations
- **Modern Frontend** - React with TypeScript and Vite
- **CI/CD Automation** - GitHub Actions with OIDC authentication
- **Cloud-Native Design** - API Gateway, DynamoDB, S3, CloudFront

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        CloudFront CDN                        │
│                  (d5ly3miadik75.cloudfront.net)             │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    S3 Static Website                         │
│              (quote-lambda-tf-frontend)                      │
│                   React + TypeScript                         │
└────────────────────────┬────────────────────────────────────┘
                         │
                         │ HTTPS API Calls
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                      API Gateway                             │
│         (blgydc5rjk.execute-api.eu-central-1...)            │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    AWS Lambda (Java 21)                      │
│                  with SnapStart enabled                      │
│              (quote-lambda-tf-backend)                       │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                       DynamoDB                               │
│                 (quote-lambda-tf-quotes)                     │
└─────────────────────────────────────────────────────────────┘
```

## 📦 Repository Structure

This monorepo contains two main modules:

### [Frontend](./quote-lambda-tf-frontend/README.md) - React Web Application

A modern, responsive web application built with:
- **React 18** with TypeScript
- **Vite** for fast development and optimized builds
- **TailwindCSS** for styling
- **Playwright** for end-to-end testing
- **Deployed on**: AWS S3 + CloudFront CDN

**[📖 Frontend Documentation →](./quote-lambda-tf-frontend/README.md)**

### [Backend](./quote-lambda-tf-backend/README.md) - Java Lambda API

A serverless REST API built with:
- **Java 21** (Amazon Corretto)
- **AWS Lambda** with SnapStart (~200ms cold starts)
- **API Gateway** for HTTP endpoints
- **DynamoDB** for data persistence
- **Terraform** for infrastructure management

**[📖 Backend Documentation →](./quote-lambda-tf-backend/README.md)**

## 🚀 Quick Start

### Prerequisites

- **AWS CLI** configured with credentials
- **Terraform** >= 1.0.0
- **Java 21** (for backend)
- **Node.js 18+** (for frontend)
- **Maven 3.x** (for backend)

### Deploy the Complete Stack

#### 1. Set Up AWS OIDC for GitHub Actions

```bash
# Get your AWS account ID
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

# Run the setup script (replace with your GitHub username)
.github/setup-aws-oidc.sh $AWS_ACCOUNT_ID YOUR_GITHUB_USERNAME
```

This creates an IAM role with permissions for both Lambda and S3/CloudFront deployments.

#### 2. Deploy Backend Infrastructure

```bash
cd quote-lambda-tf-backend/infrastructure
terraform init
terraform apply
```

#### 3. Deploy Backend Lambda Function

Use GitHub Actions or deploy manually:

```bash
cd quote-lambda-tf-backend
mvn clean package
aws lambda update-function-code \
  --function-name quote-lambda-tf-backend \
  --zip-file fileb://target/quote-lambda-tf-backend-1.0-SNAPSHOT.jar \
  --region eu-central-1
```

#### 4. Deploy Frontend Infrastructure

```bash
cd quote-lambda-tf-frontend/infrastructure
terraform init
terraform apply
```

#### 5. Deploy Frontend Application

```bash
cd quote-lambda-tf-frontend
npm install
npm run build
aws s3 sync dist/ s3://quote-lambda-tf-frontend --delete
```

## 🔧 Technology Stack

### Frontend
- **Framework**: React 18 with TypeScript
- **Build Tool**: Vite
- **Styling**: TailwindCSS
- **Testing**: Playwright
- **Hosting**: AWS S3 + CloudFront

### Backend
- **Language**: Java 21 (Amazon Corretto)
- **Runtime**: AWS Lambda with SnapStart
- **API**: API Gateway (HTTP API)
- **Database**: DynamoDB
- **External API**: ZenQuotes.io

### Infrastructure
- **IaC**: Terraform
- **State Management**: S3 + DynamoDB locking
- **CI/CD**: GitHub Actions
- **Authentication**: AWS OIDC (no long-lived credentials)

### DevOps
- **Version Control**: Git + GitHub
- **Automation**: GitHub Actions workflows
- **Monitoring**: CloudWatch Logs
- **Security**: IAM roles with least-privilege policies

## 📚 Documentation

### Backend Documentation
- [Infrastructure Setup](./quote-lambda-tf-backend/doc/infrastructure.md) - Terraform configuration and deployment
- [GitHub Workflows](./quote-lambda-tf-backend/doc/github-workflows.md) - CI/CD pipeline setup
- [SnapStart Setup](./quote-lambda-tf-backend/doc/snapstart-setup.md) - Lambda cold start optimization
- [API Testing](./quote-lambda-tf-backend/doc/test-api.http) - HTTP request examples

### Frontend Documentation
- [Infrastructure Setup](./quote-lambda-tf-frontend/doc/infrastructure.md) - S3 and CloudFront deployment
- [GitHub Workflows](./quote-lambda-tf-frontend/doc/github-workflows.md) - Deployment automation

### Shared Documentation
- [Terraform State Architecture](doc/terraform-state-architecture.md) - Complete state management architecture
- [Multi-Environment Setup](doc/multi-environment-setup.md) - Dev/Prod environment configuration
- [AWS OIDC Setup Script](./.github/setup-aws-oidc.sh) - Automated IAM role configuration
- [Authentication & Authorization Architecture](doc/authentication/authentication-authorization-setup.md) - How users authenticate and access is authorized

## 🔐 Authentication & Authorization

The application uses **AWS Cognito** for user authentication and **JWT-based authorization** for protecting API endpoints.

### User Authentication

Users can authenticate in two ways:

1. **Email + Password Registration**
   - Users register with email, choose a custom username, and set a password
   - Email is verified via confirmation code
   - Users are automatically assigned the `USER` role

2. **Google OAuth Sign-In**
   - Users can sign in with their Google account
   - On first login, users choose a custom username
   - Users are automatically assigned the `USER` role

### Authorization

- **Public endpoints** (`GET /quote`, `GET /quote/liked`) - No authentication required
- **Protected endpoints** (`POST /quote/{id}/like`, `DELETE /quote/{id}/like`) - Requires `USER` role
- **Authorization** is enforced in the Lambda function by validating JWT tokens and checking user roles

### Key Features

- ✅ Secure password hashing (bcrypt)
- ✅ JWT tokens with 1-hour expiration (refreshable for 30 days)
- ✅ Role-based access control (USER, ADMIN groups)
- ✅ User action logging to CloudWatch
- ✅ CORS configured for secure cross-origin requests

For detailed architecture and implementation details, see: **[Authentication & Authorization Architecture](doc/authentication/authentication-authorization-setup.md)**

## 🔐 GitHub Actions Setup

Both frontend and backend use GitHub Actions for automated deployments with OIDC authentication.

### Required GitHub Secret

Add this secret to your repository:

| Secret Name | Value | Description |
|-------------|-------|-------------|
| `AWS_ROLE_ARN` | `arn:aws:iam::ACCOUNT_ID:role/GitHubActionsLambdaDeployRole` | IAM role for deployments |

### Workflows

- **[deploy-lambda.yml](./.github/workflows/deploy-lambda.yml)** - Builds and deploys the backend Lambda function
- **[deploy-frontend.yml](./.github/workflows/deploy-frontend.yml)** - Builds and deploys the frontend to S3
- **[playwright.yml](./.github/workflows/playwright.yml)** - Runs end-to-end tests

## 🎯 Learning Goals

This project demonstrates:

1. **Serverless Architecture**
   - Building REST APIs with AWS Lambda
   - Optimizing cold starts with SnapStart
   - API Gateway configuration and throttling

2. **Infrastructure as Code**
   - Managing AWS resources with Terraform
   - Remote state management with S3 and DynamoDB
   - Modular infrastructure design

3. **Modern Frontend Development**
   - React with TypeScript and Vite
   - Responsive design with TailwindCSS
   - End-to-end testing with Playwright

4. **DevOps Best Practices**
   - CI/CD with GitHub Actions
   - OIDC authentication (no long-lived credentials)
   - Automated testing and deployment

5. **Cloud-Native Patterns**
   - Static website hosting with S3 and CloudFront
   - NoSQL data modeling with DynamoDB
   - RESTful API design

## 💰 Cost Estimate

This application runs on AWS Free Tier eligible services:

- **Lambda**: ~1M free requests/month
- **API Gateway**: ~1M free requests/month (first 12 months)
- **DynamoDB**: 25GB storage + 25 RCU/WCU free
- **S3**: 5GB storage + 20K GET requests free
- **CloudFront**: 1TB data transfer + 10M requests free (first 12 months)

**Estimated monthly cost**: $0-5 for low traffic

## 🤝 Contributing

This is a learning project, but suggestions and improvements are welcome!

## 📄 License

This project is open source and available for educational purposes.

## 🔗 Links

- **Live Application (Production)**: https://d5ly3miadik75.cloudfront.net/
- **Live Application (Development)**: https://d1fzgis91zws1k.cloudfront.net/
- **API Endpoint**: https://blgydc5rjk.execute-api.eu-central-1.amazonaws.com
- **ZenQuotes API**: https://zenquotes.io/

---

Built with ❤️ to learn serverless architecture and modern web development
