# Quote Backend on OVHcloud - Third Implementation Guide

## Table of Contents

- [Overview](#overview)
- [Learning Objectives](#learning-objectives)
- [OVHcloud Architecture Overview](#ovhcloud-architecture-overview)
  - [Recommended Technology Stack](#recommended-technology-stack)
- [Actual Cost Comparison](#actual-cost-comparison)
  - [Real Monthly Costs (EUR/USD)](#real-monthly-costs-eurusd)
  - [Detailed Cost Breakdown](#detailed-cost-breakdown)
  - [Cost Analysis](#cost-analysis)
- [Infrastructure Setup with Terraform](#infrastructure-setup-with-terraform)
  - [Terraform Support](#terraform-support)
  - [Required Terraform Providers](#required-terraform-providers)
- [Platform as a Service: OVHcloud Web Apps](#platform-as-a-service-ovhcloud-web-apps)
  - [OVHcloud Web Apps Solution](#ovhcloud-web-apps-solution)
  - [Why Web Apps Instead of FaaS?](#why-web-apps-instead-of-faas)
  - [OVHcloud Web Apps Cost Structure](#ovhcloud-web-apps-cost-structure)
  - [Comparison with AWS/Azure](#comparison-with-awsazure)
  - [Cost Analysis](#cost-analysis-1)
  - [Complete API Functionality](#complete-api-functionality)
- [Database: MongoDB Discovery (FREE)](#database-mongodb-discovery-free)
  - [Why MongoDB Instead of PostgreSQL?](#why-mongodb-instead-of-postgresql)
  - [MongoDB Discovery Plan Benefits](#mongodb-discovery-plan-benefits)
  - [Plan Limitations](#plan-limitations)
  - [Alternative Database Options](#alternative-database-options)
- [Language Choice: Go (Golang)](#language-choice-go-golang)
  - [Why Go for This Implementation?](#why-go-for-this-implementation)
  - [Example Go Handler](#example-go-handler)
- [API Gateway: Built-in Routing](#api-gateway-built-in-routing)
  - [Web Apps Built-in Routing](#web-apps-built-in-routing)
  - [Routing Features](#routing-features)
  - [Configuration Example](#configuration-example)
  - [Benefits vs Traditional API Gateway](#benefits-vs-traditional-api-gateway)
- [Authentication: Custom JWT (Same as Azure)](#authentication-custom-jwt-same-as-azure)
  - [Why Custom JWT Instead of Keycloak?](#why-custom-jwt-instead-of-keycloak)
  - [Go JWT Implementation](#go-jwt-implementation)
- [Step-by-Step Implementation Guide](#step-by-step-implementation-guide)
  - [1. Sign Up for OVHcloud](#1-sign-up-for-ovhcloud)
  - [2. Set Up Project](#2-set-up-project)
  - [3. Deploy Infrastructure](#3-deploy-infrastructure)
  - [4. Deploy Go Application](#4-deploy-go-application)
  - [5. Deploy as Web App](#5-deploy-as-web-app)
- [Project Structure](#project-structure)
- [Implementation Checklist](#implementation-checklist)
- [Conclusion](#conclusion)
  - [Multi-Cloud Experience](#multi-cloud-experience)
  - [Real-World Cost Analysis](#real-world-cost-analysis)
  - [Technology Versatility](#technology-versatility)
  - [Authentication Consistency](#authentication-consistency)
  - [Infrastructure Patterns](#infrastructure-patterns)

## Overview

This document describes how to implement the quote backend on OVHcloud as a third implementation for learning purposes, alongside the existing AWS Lambda (Java) and Azure Functions (C#) versions. This implementation focuses on using OVHcloud's services with Terraform for infrastructure management and Go for the backend logic.

## Learning Objectives

This third implementation demonstrates:
- **Multi-cloud architecture**: Understanding different cloud providers
- **Cost optimization**: Choosing the most economical solutions
- **Technology comparison**: Contrasting serverless offerings across clouds
- **Infrastructure as Code**: Terraform across different providers
- **Language versatility**: Go vs Java vs C# for backend development
- **Open-source alternatives**: Using community tools over proprietary solutions

## OVHcloud Architecture Overview

### Recommended Technology Stack

| AWS Service | Azure Service | OVHcloud Alternative | Learning Focus | Cost Efficiency |
|-------------|---------------|---------------------|---------------|-----------------|
| AWS Lambda | Azure Functions | OVHcloud Web Apps | PaaS comparison | ★★★★★ |
| API Gateway | API Management | Web Apps Built-in Routing | API patterns | ★★★★★ |
| DynamoDB | Storage Tables | MongoDB Discovery | Database alternatives | ★★★★★ |
| Cognito | Custom JWT | Custom JWT (same approach) | Auth consistency | ★★★★★ |
| CloudWatch | App Insights | Basic monitoring | Monitoring tools | ★★★★☆ |

## Actual Cost Comparison

### Real Monthly Costs (EUR/USD)

| Component | AWS (Java) | Azure (C#) | OVHcloud (Go) | Learning Value |
|-----------|------------|------------|---------------|----------------|
| Compute | ~$0.50/month | $0-2/month | €6/month | PaaS comparison |
| API Gateway | $0.012/month | $2-5/month | €0 (built-in) | API patterns |
| Database | ~$0.01/month | $0-1/month | **FREE** (MongoDB) | Database alternatives |
| Storage | <$0.01/month | $0-1/month | €0.50/month | Storage solutions |
| Registry | Free tier | Free tier | €0 (Docker Hub) | Container management |
| Monitoring | Free tier | $0-1/month | Free tier | Monitoring tools |
| **Total** | **~$0.52/month** | **$2-9/month** | **~€6.50/month** | **Cost vs Features** |

### Detailed Cost Breakdown

#### **AWS Implementation (Java)**
- **Lambda**: ~$0.002 + duration costs (~$0.50 for 10K requests)
- **API Gateway**: $0.012 (10K requests)
- **DynamoDB**: ~$0.01 (within free tier)
- **S3**: <$0.01 (within free tier)
- **CloudFront**: Free (within free tier)
- **Total**: ~$0.52/month for low traffic

#### **Azure Implementation (C#)**
- **Azure Functions**: $0-2 (Consumption Plan, within free tier)
- **API Management**: $2-5 (Consumption tier, no free tier)
- **Storage Tables**: $0-1 (within free tier)
- **Application Insights**: $0-1 (within free tier)
- **Log Analytics**: $0-1 (within free tier)
- **Frontend Storage**: $0-1 (shared storage account)
- **Total**: $2-9/month depending on traffic

#### **OVHcloud Implementation (Go)**
- **Web Apps**: €6/month (PaaS hosting)
- **Database**: **FREE** (MongoDB Discovery plan)
- **Storage**: €0.50 (application storage)
- **API Gateway**: €0 (built-in routing)
- **Registry**: €0 (use Docker Hub)
- **Monitoring**: Free tier
- **Total**: **~€6.50/month** (PaaS setup)

### Cost Analysis

#### **Free Tier Benefits**
- **AWS**: Extremely generous free tier, perfect for learning projects
- **Azure**: Moderate free tier, some services not covered
- **OVHcloud**: No free tier, but transparent pricing

#### **Cost Scaling**
- **AWS**: Costs scale very slowly due to extensive free tier
- **Azure**: Moderate scaling, API Management drives costs
- **OVHcloud**: Predictable costs, no sudden spikes

#### **Value Proposition**
- **AWS**: Best for learning and experimentation (lowest cost)
- **Azure**: Good balance of features and cost for production
- **OVHcloud**: Predictable costs for budget planning, no vendor lock-in

## Infrastructure Setup with Terraform

### Terraform Support

✅ **Yes, Terraform is fully supported** for OVHcloud infrastructure management.

**Getting Started:**
- [OVHcloud Terraform Provider](https://registry.terraform.io/providers/ovh/ovh/latest/docs)
- [Terraform on OVHcloud Documentation](https://docs.ovh.com/gb/en/public-cloud/terraform/)

### Required Terraform Providers

```hcl
terraform {
  required_providers {
    ovh = {
      source  = "ovh/ovh"
      version = "~> 0.42.0"
    }
  }
}

provider "ovh" {
  endpoint = "ovh-eu"
  application_key    = var.ovh_application_key
  application_secret = var.ovh_application_secret
  consumer_key       = var.ovh_consumer_key
}
```

## Platform as a Service: OVHcloud Web Apps

### OVHcloud Web Apps Solution

OVHcloud offers **Web Apps** (Platform as a Service) that provides the perfect balance between simplicity and functionality for learning projects. This approach delivers the complete API functionality without Kubernetes complexity.

### Why Web Apps Instead of FaaS?

✅ **Complete API Support**: All endpoints, authentication, database integration  
✅ **Simple Deployment**: Direct Git/Docker deployment  
✅ **Built-in Routing**: No separate API gateway needed  
✅ **Cost Effective**: €6/month vs $13+ for Kubernetes  
✅ **Same Learning Value**: Cloud deployment, database, auth patterns  
✅ **No Vendor Lock-in**: Standard container deployment  

### OVHcloud Web Apps Cost Structure

Based on OVHcloud's actual pricing:

| Component | OVHcloud Price | Cost Calculation | Monthly Estimate |
|-----------|---------------|------------------|------------------|
| **Web Apps** | €6/month | Standard PaaS instance | €6.00 |
| **Database** | **FREE** | MongoDB Discovery plan | **€0** |
| **Storage** | €0.01/GB | 50GB for application | €0.50 |
| **Bandwidth** | FREE | Unlimited traffic | €0 |
| **Monitoring** | FREE | Basic metrics | €0 |
| **Total** | | | **~€6.50/month** |

### Comparison with AWS/Azure

| Feature | AWS Lambda | Azure Functions | OVHcloud Web Apps |
|---------|------------|----------------|------------------|
| **Pricing Model** | Per invocation + duration | Per invocation + duration | Fixed monthly |
| **Free Tier** | 1M requests/month | 1M requests/month | None |
| **Deployment** | Zip upload | Package deployment | Git/Docker |
| **Scaling** | Automatic | Automatic | Manual/Configurable |
| **Languages** | 15+ languages | 10+ languages | Any container |
| **Vendor Lock-in** | High | Medium | Low (standard) |
| **Monthly Cost** | ~$0.50 | $2-5 | ~€6.50 |

### Cost Analysis

#### **When OVHcloud Web Apps Makes Sense:**
- **Complete API needed** (all endpoints, auth, database)
- **Simple deployment** preferred (no Kubernetes)
- **Budget planning** (predictable costs)
- **Open source preference** (standard containers)
- **Learning PaaS patterns** (vs serverless)

#### **When AWS/Azure Are Better:**
- **True serverless** required (pay-per-invocation)
- **Free tier dependency** (learning/experimentation)
- **Complex auto-scaling** needs
- **Managed service preference**

### Complete API Functionality

With Web Apps, you can implement the **entire quote backend**:

#### **✅ All API Endpoints**
```go
// Authentication endpoints
router.POST("/api/auth/register", registerUser)
router.POST("/api/auth/login", loginUser)

// Quote management endpoints  
router.GET("/api/quotes", getQuotes)
router.POST("/api/quotes", createQuote)
router.PUT("/api/quotes/:id", updateQuote)
router.DELETE("/api/quotes/:id", deleteQuote)

// Admin endpoints
router.POST("/api/manage/quotes/fetch", fetchFromZen)
router.GET("/api/admin/stats", getStats)
```

#### **✅ JWT Authentication**
```go
// Same JWT approach as Azure implementation
func generateJWTToken(userID, email, username string, roles []string) (string, error) {
    // JWT token generation logic
}
```

#### **✅ Database Integration**
```go
// MongoDB integration
func connectToMongoDB() *mongo.Client {
    // Connect to OVHcloud MongoDB Discovery
}
```

#### **✅ External API Integration**
```go
// ZEN quotes fetching
func fetchFromZen() ([]Quote, error) {
    // HTTP client to fetch quotes from external API
}
```

## Database: MongoDB Discovery (FREE)

### Why MongoDB Instead of PostgreSQL?

For a learning implementation, **MongoDB Discovery plan** is the perfect choice:

#### **MongoDB Discovery Plan Benefits:**
✅ **100% FREE** - No cost for learning projects  
✅ **NoSQL Approach** - Matches DynamoDB/Azure Storage Tables patterns  
✅ **Document-based** - Perfect for quotes, users, likes data  
✅ **Managed Service** - No database administration needed  
✅ **JSON Native** - Natural fit for Go/Node.js applications  

#### **Plan Limitations:**
- **1 service per project** (perfect for learning)
- **Limited resources** (sufficient for development)
- **No SLA** (acceptable for learning)

### Alternative Database Options

| Option | Cost | Use Case |
|--------|------|----------|
| **MongoDB Discovery** | **FREE** | ⭐ Learning projects |
| **PostgreSQL Essential** | ~$36/month | Production needs |
| **Self-hosted on node** | $0 | Advanced users |
| **External (Supabase)** | Free tier | Hybrid approach |

**Example Terraform Configuration:**
```hcl
resource "ovh_cloud_project_database" "quote_db" {
  service_name = var.project_id
  engine       = "postgresql"
  version      = "15"
  flavor       = "db1-7"
  region       = "GRA"
  name         = "quote-backend-db"
  
  network {
    id   = ovh_cloud_project_network.private_network.id
    subnet_id = ovh_cloud_project_network_subnet.private_subnet.id
  }
}
```

## Authentication: Custom JWT (Same as Azure)

### Why Custom JWT Instead of Keycloak?

You're absolutely right! Since the Azure implementation already uses **Custom JWT** (not Azure AD), we should maintain the same approach:
- **Consistency** with existing implementations
- **Lower complexity** - no additional infrastructure
- **Cost effective** - completely free
- **Learning value** - JWT implementation patterns

### Go JWT Implementation

```go
package auth

import (
    "time"
    "github.com/golang-jwt/jwt/v5"
)

type Claims struct {
    UserID   string   `json:"nameid"`
    Email    string   `json:"email"`
    Username string   `json:"unique_name"`
    Roles    []string `json:"role"`
    jwt.RegisteredClaims
}

func GenerateToken(userID, email, username string, roles []string) (string, error) {
    claims := Claims{
        UserID:   userID,
        Email:    email,
        Username: username,
        Roles:    roles,
        RegisteredClaims: jwt.RegisteredClaims{
            ExpiresAt: jwt.NewNumericDate(time.Now().Add(24 * time.Hour)),
            IssuedAt:  jwt.NewNumericDate(time.Now()),
        },
    }
    
    token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
    return token.SignedString([]byte(secret))
}
```

## Language Choice: Go (Golang)

### Why Go for This Implementation?

- **Performance**: Excellent for containerized applications
- **Memory**: Low memory footprint (critical for cost)
- **Concurrency**: Perfect for API handling
- **Deployment**: Single binary deployment
- **Learning**: Different paradigm from Java/C#

### Example Go Handler

```go
package handlers

import (
    "encoding/json"
    "net/http"
    "database/sql"
    _ "github.com/lib/pq"
)

type QuoteHandler struct {
    db *sql.DB
}

func (h *QuoteHandler) GetRandomQuote(w http.ResponseWriter, r *http.Request) {
    quote, err := h.getRandomQuoteFromDB()
    if err != nil {
        http.Error(w, err.Error(), http.StatusInternalServerError)
        return
    }
    
    w.Header().Set("Content-Type", "application/json")
    json.NewEncoder(w).Encode(quote)
}

func (h *QuoteHandler) getRandomQuoteFromDB() (*Quote, error) {
    query := `SELECT id, text, author FROM quotes ORDER BY RANDOM() LIMIT 1`
    var quote Quote
    err := h.db.QueryRow(query).Scan(&quote.ID, &quote.Text, &quote.Author)
    return &quote, err
}
```

## API Gateway: Built-in Routing

### Web Apps Built-in Routing

OVHcloud Web Apps includes **built-in routing** capabilities, eliminating the need for a separate API gateway:

#### **Routing Features:**
✅ **Custom domains** supported  
✅ **SSL/TLS certificates** automatic  
✅ **Path-based routing**  
✅ **Load balancing** included  
✅ **CORS configuration**  

#### **Configuration Example:**
```go
// No separate gateway needed - Web Apps handles routing
func main() {
    router := gin.Default()
    
    // API routes automatically accessible
    router.GET("/api/quotes", getQuotes)
    router.POST("/api/auth/login", loginUser)
    
    // Web Apps handles incoming requests
    router.Run(":8080")
}
```

#### **Benefits vs Traditional API Gateway:**
- **Simpler setup**: No additional configuration
- **Cost effective**: Included in Web Apps pricing
- **Lower complexity**: Fewer moving parts
- **Adequate for learning**: Sufficient for API projects

## Step-by-Step Implementation Guide

### 1. Sign Up for OVHcloud

1. Visit [OVHcloud Public Cloud](https://www.ovhcloud.com/en/public-cloud/)
2. Create an account
3. Generate API keys: [API Documentation](https://docs.ovh.com/gb/en/api/first-steps/)

### 2. Set Up Project

```bash
# Install OVHcloud CLI
curl https://eu.api.ovh.com/install.sh | bash

# Configure CLI
ovhcli --endpoint ovh-eu

# Create new project
ovhai project create
```

### 3. Deploy Infrastructure

```bash
# Initialize Terraform
terraform init

# Plan deployment
terraform plan

# Apply changes
terraform apply
```

### 4. Deploy Go Application

```dockerfile
FROM golang:1.21-alpine AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN go build -o quote-backend

FROM alpine:latest
RUN apk --no-cache add ca-certificates
WORKDIR /root/
COPY --from=builder /app/quote-backend .
EXPOSE 8080
CMD ["./quote-backend"]
```

### 5. Deploy as OpenFaaS Function

```yaml
# quote-function.yml
apiVersion: openfaas.com/v1
kind: Function
metadata:
  name: quote-backend
  namespace: openfaas-fn
spec:
  image: registry.ovh.com/quote-backend:latest
  environment:
    DB_HOST: postgresql.example.com
    DB_NAME: quotedb
    JWT_SECRET: your-secret-key
  limits:
    memory: "256Mi"
    cpu: "200m"
  requests:
    memory: "128Mi"
    cpu: "100m"
```

## Project Structure

```
quote-ovhc-backend/
├── src/
│   ├── main.go                    # Application entry point
│   ├── handlers/                  # API route handlers
│   │   ├── quotes.go             # Quote management
│   │   ├── auth.go               # Authentication
│   │   └── admin.go              # Admin operations
│   ├── services/                  # Business logic
│   │   ├── quote_service.go       # Quote operations
│   │   ├── auth_service.go       # Authentication logic
│   │   └── admin_service.go      # Admin functions
│   ├── models/                    # Data models
│   │   ├── quote.go              # Quote entity
│   │   ├── user.go               # User entity
│   │   └── auth.go               # Auth models
│   ├── database/                  # Database layer
│   │   ├── mongodb/              # MongoDB integration
│   │   │   ├── client.go         # MongoDB client
│   │   │   └── repositories.go   # Data access
│   ├── middleware/                # HTTP middleware
│   │   ├── auth.go               # JWT validation
│   │   └── cors.go               # CORS handling
│   └── config/                    # Configuration
│       └── config.go             # Environment config
├── infrastructure/                 # Terraform configuration
│   ├── main.tf                   # OVHcloud resources
│   ├── variables.tf              # Input variables
│   ├── outputs.tf                # Output values
│   ├── webapp.tf                 # Web Apps setup
│   ├── database.tf               # MongoDB setup
│   └── storage.tf                # Storage configuration
├── docker/                        # Container configuration
│   ├── Dockerfile                # Application container
│   └── docker-compose.yml        # Local development
├── scripts/                       # Deployment scripts
│   ├── deploy.sh                 # Deployment automation
│   └── setup.sh                  # Initial setup
├── tests/                         # Test files
│   ├── integration/              # Integration tests
│   └── unit/                     # Unit tests
├── docs/                          # Documentation
│   ├── api.md                    # API documentation
│   └── deployment.md             # Deployment guide
└── README.md                      # Project documentation
```

## Implementation Checklist

- [ ] Create OVHcloud account and project
- [ ] Set up Terraform provider
- [ ] Deploy Web Apps infrastructure
- [ ] Set up MongoDB database
- [ ] Implement Go application with JWT auth
- [ ] Containerize and deploy to Web Apps
- [ ] Configure built-in routing and SSL
- [ ] Set up monitoring and logging
- [ ] Test all API endpoints
- [ ] Performance testing
- [ ] Security audit
- [ ] Compare with AWS and Azure implementations

## Conclusion

Implementing the quote backend on OVHcloud as a third implementation provides excellent learning opportunities:

### **Multi-Cloud Experience**
- Compare serverless offerings across AWS, Azure, and OVHcloud
- Understand different approaches to API gateways
- Experience various database solutions (DynamoDB, Storage Tables, PostgreSQL)

### **Real-World Cost Analysis**
- AWS: Extremely cheap with free tiers, good for learning
- Azure: Moderate costs, enterprise features
- OVHcloud: Predictable pricing, no hidden costs

### **Technology Versatility**
- **Java (AWS)**: Enterprise-grade, mature ecosystem
- **C# (Azure)**: Modern .NET, excellent tooling
- **Go (OVHcloud)**: Performance-focused, simple deployment

### **Authentication Consistency**
All three implementations can use **Custom JWT** for consistency:
- AWS: Cognito with JWT tokens
- Azure: Custom JWT implementation
- OVHcloud: Custom JWT implementation (same approach as Azure)

### **Infrastructure Patterns**
- **AWS**: Fully managed serverless services
- **Azure**: Hybrid managed/custom approach
- **OVHcloud**: PaaS-based open-source stack

This third implementation demonstrates that different cloud providers require different approaches, and the "best" solution depends on requirements, budget, and learning objectives. The OVHcloud version provides valuable experience with PaaS deployment, predictable pricing, and open-source technologies.
