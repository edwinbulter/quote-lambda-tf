# Quote Lambda Java

A serverless REST API backend for a quote management application, built with Java 21 and deployed on AWS Lambda. This project demonstrates modern serverless architecture patterns using AWS services and infrastructure as code.

## Overview

This AWS Lambda backend:
- Fetches inspirational quotes from [ZenQuotes API](https://zenquotes.io/) and stores them in DynamoDB
- Provides a REST API for retrieving random quotes and managing liked quotes
- Is created to learn about building serverless APIs with Java, AWS Lambda, API Gateway, and Terraform
- Is exposed through API Gateway and consumed by the Quote Web App frontend at:
  > https://d2bs2se2x8mgcb.cloudfront.net/

The code for the Quote Web App can be found at:
> https://github.com/edwinbulter/quote-lambda-frontend

## Features

- **Random Quote Retrieval**: Get random quotes with optional exclusion filters
- **Quote Persistence**: Automatic caching of quotes in DynamoDB
- **Like System**: Track and retrieve liked quotes sorted by popularity
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

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/quote` | Get a random quote |
| `POST` | `/quote` | Get a random quote excluding specific IDs (body: array of IDs) |
| `PATCH` | `/quote/{id}/like` | Like a specific quote |
| `GET` | `/quote/liked` | Get all liked quotes sorted by likes |

**API Base URL**: `https://22n07ybi7e.execute-api.eu-central-1.amazonaws.com`

## Documentation

Detailed documentation is available in the [`doc/`](./doc) folder:

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

- **[test-api.http](./doc/test-api.http)** - HTTP request examples for testing the API endpoints

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
quote-lambda-java/
├── src/main/java/           # Java source code
│   └── ebulter/quote/lambda/
│       └── QuoteHandler.java
├── infrastructure/          # Terraform configuration
│   ├── backend.tf          # S3 remote state
│   ├── lambda.tf           # Lambda function with SnapStart
│   ├── dynamodb.tf         # DynamoDB table
│   ├── api_gateway.tf      # API Gateway setup
│   └── ...
├── .github/
│   ├── workflows/          # GitHub Actions workflows
│   └── setup-aws-oidc.sh   # OIDC setup script
├── doc/                    # Documentation
└── pom.xml                 # Maven configuration
```

## Goals

This project serves as a learning platform for:
- Building serverless REST APIs with Java and AWS Lambda
- Implementing infrastructure as code with Terraform
- Setting up CI/CD pipelines with GitHub Actions
- Optimizing Lambda cold starts with SnapStart
- Managing AWS resources with least-privilege IAM roles
- Integrating external APIs and caching data in DynamoDB
