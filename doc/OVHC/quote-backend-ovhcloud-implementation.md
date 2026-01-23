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
| Database | ~$0.01/month | $0-1/month | **~€0.01/month** (S3 storage) | Database alternatives |
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
- **Database**: **~€0.01/month** (In-memory + S3 storage)
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
| **Database** | **~€0.01/month** | In-memory + S3 storage | **€0.01** |
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
// In-memory database with S3 persistence
func initializeQuoteStore() *QuoteStore {
    store := &QuoteStore{
        quotes: make(map[string]Quote),
        users:  make(map[string]User),
        likes:  make(map[string][]string),
    }
    
    // Load from S3 on startup
    store.LoadFromS3()
    
    return store
}
```

#### **✅ External API Integration**
```go
// ZEN quotes fetching
func fetchFromZen() ([]Quote, error) {
    // HTTP client to fetch quotes from external API
}
```

## Database: In-Memory with S3 Persistence

### Why In-Memory Instead of MongoDB?

For a cost-optimized learning implementation, **in-memory database with S3 persistence** is the perfect choice:

#### **In-Memory + S3 Benefits:**
✅ **Ultra-low cost** - Only storage costs (~€0.01/month)  
✅ **Simple Architecture** - No database service management  
✅ **Fast Performance** - In-memory operations  
✅ **Persistent Storage** - S3-compatible Object Storage  
✅ **Scalable Pattern** - Similar to serverless approaches  

#### **Cost Comparison:**
| Option | Cost | Use Case |
|--------|------|----------|
| **In-Memory + S3** | **~€0.01/month** | ⭐ Learning projects |
| **MongoDB Discovery** | FREE | Complex setup |
| **PostgreSQL Essential** | ~€36/month | Production needs |
| **Self-hosted on node** | $0 | Advanced users |

### Architecture Overview

#### **Data Flow:**
1. **In-Memory Store**: Fast access for quotes, users, likes
2. **S3 Persistence**: Automatic backup to Object Storage
3. **JSON Format**: Simple, human-readable storage
4. **Automatic Sync**: Save on every change

#### **Implementation Pattern:**
```go
// In-memory data store
type QuoteStore struct {
    quotes map[string]Quote
    users  map[string]User
    likes  map[string][]string
    mutex  sync.RWMutex
}

// S3 persistence
func (s *QuoteStore) SaveToS3() error {
    data, _ := json.Marshal(s)
    // Upload to OVHcloud Object Storage
}
```

### S3-Compatible Storage Setup

#### **OVHcloud Object Storage:**
- **Endpoint**: `https://s3.gra.cloud.ovh.net`
- **Region**: `GRA`
- **Container**: `quotes-data`
- **Cost**: ~€0.01/GB/month

#### **Go S3 Integration:**
```go
// Use AWS SDK with OVHcloud endpoint
cfg, _ := config.LoadDefaultConfig(context.TODO(),
    config.WithRegion("GRA"),
    config.WithEndpointResolver(aws.EndpointResolverWithOptionsFunc(
        func(service, region string, options ...interface{}) (aws.Endpoint, error) {
            return aws.Endpoint{
                URL: "https://s3.gra.cloud.ovh.net",
            }, nil
        })))
```

### Data Persistence Strategy

#### **Automatic Backup:**
- **On startup**: Load from S3 if exists
- **On changes**: Save to S3 immediately
- **Periodic backup**: Every 5 minutes
- **Graceful shutdown**: Save before exit

#### **File Structure:**
```json
{
  "quotes": {
    "quote-123": {
      "id": "quote-123",
      "text": "Be yourself; everyone else is already taken.",
      "author": "Oscar Wilde",
      "created_at": "2024-01-01T00:00:00Z"
    }
  },
  "users": {
    "user-456": {
      "id": "user-456",
      "username": "john_doe",
      "created_at": "2024-01-01T00:00:00Z"
    }
  },
  "likes": {
    "quote-123": ["user-456", "user-789"]
  }
}
```

### Benefits for Learning

#### **✅ Educational Value:**
- **Memory management**: Learn about in-memory data structures
- **Persistence patterns**: Understand backup strategies
- **S3 integration**: Work with cloud storage APIs
- **Concurrency**: Implement thread-safe operations

#### **✅ Production-Ready Patterns:**
- **Caching layers**: Similar to Redis/Memcached
- **Eventual consistency**: Like distributed systems
- **Backup strategies**: Essential for production
- **API design**: Clean separation of concerns

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

### 1. Create OVHcloud Account and API Credentials

#### **1.1 Sign Up for OVHcloud**
1. Visit [OVHcloud Public Cloud](https://www.ovhcloud.com/en/public-cloud/)
2. Create an account

#### **1.2 Generate API Credentials**
1. **Open Control panel**: https://auth.eu.ovhcloud.com/signin/
2. **Click on your profile button**: opens https://manager.eu.ovhcloud.com/#/account/useraccount/dashboard
3. **In left panel click on** **>** 'Identity, Security & Operations'
4. **In the submenu click on** 'API keys'
5. **Now you see a table with all API keys**
6. **Click on 'Create API key'**: opens https://auth.eu.ovhcloud.com/api/createToken
7. **Fill in the form**:
   - **Application Name**: `quote-backend`
   - **Description**: `Quote Backend`
   - **Validity**: unlimited
   - **Rights**: 
     ```text
     GET /me
     POST /domain/*
  
     GET /cloud/*
     POST /cloud/*
     PUT /cloud/*
     PATCH /cloud/*
     DELETE /cloud/*
  
     GET /hosting/web/*
     POST /hosting/web/*
     PUT /hosting/web/*
     PATCH /hosting/web/*
     DELETE /hosting/web/*
  
     GET /database/*
     POST /database/*
     PUT /database/*
     PATCH /database/*
     DELETE /database/*
     ```
   - **Save the Application Key, Application Secret, and Consumer Key**

**Important**: These credentials are required for both CLI usage and Terraform automation. OVHcloud does not support creating API applications/credentials via Terraform - this must be done manually through the web interface first.

### 2. Set Up CLI and Project

#### **2.1 Install OVHcloud CLI**
```bash
# Install OVHcloud CLI (macOS)
brew install --cask ovh/tap/ovhcloud-cli

# Note: If macOS shows "malware" warning, run:
# sudo xattr -rd com.apple.quarantine /opt/homebrew/Caskroom/ovhcloud-cli/*/ovhcloud
```

#### **2.2 Authenticate CLI**
```bash
# Authenticate with OVHcloud CLI (using credentials from step 1)
ovhcloud login
# When prompted, enter:
# - Application Key: [paste from step 1]
# - Application Secret: [paste from step 1]  
# - Consumer Key: [paste from step 1]
```

#### **2.3 Verify and Create Project**
```bash
# Verify authentication works
ovhcloud cloud project list

# Create new project (if you don't have one)
ovhcloud cloud project create
```

### 3. Create Infrastructure Code

**Note**: Before running Terraform, you need to create the actual infrastructure files. See the **Project Structure** section below for the complete Terraform configuration.

**Terraform Provider Setup** (after you have credentials):

Create `providers.tf`:
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

Create `variables.tf`:
```hcl
variable "ovh_application_key" {
  description = "OVHcloud Application Key"
  type        = string
  sensitive   = true
}

variable "ovh_application_secret" {
  description = "OVHcloud Application Secret"
  type        = string
  sensitive   = true
}

variable "ovh_consumer_key" {
  description = "OVHcloud Consumer Key"
  type        = string
  sensitive   = true
}

variable "project_id" {
  description = "OVHcloud Project ID"
  type        = string
}
```

Then run:
```bash
# Create terraform.tfvars file 
# Edit terraform.tfvars with your actual credentials

# Initialize and apply
terraform init
terraform apply
```

**Important Note**: Terraform has successfully deployed the VM instance using OpenStack, but **OVHcloud Object Storage requires manual setup**. You'll need to create the Object Storage manually using the OVHcloud Manager, while the VM is fully automated via Terraform.

**✅ Terraform Deployment Complete:**
- Project ID: `69f598c73ece43c293f49860d94adac0`
- Cost: $0.00/month (Terraform infrastructure)
- Ready for manual setup

**Manual Object Storage Setup Steps:**
1. Go to [OVHcloud Manager](https://www.ovh.com/auth/)
2. Navigate to **Public Cloud** → **Your Project** (`69f598c73ece43c293f49860d94adac0`)
3. Click **"Object Storage"** in the left menu
4. Click **"Create Object Storage"**
5. Configure:
   - **Name**: `quote-storage` (this becomes your container name)
   - **Region**: Gravelines (GRA)
   - **Type**: Public
   - **Description**: `Object storage for quote backend data persistence`
   - **Add User**: Click "Add User" → Create new user `quote-app-user` with "Object Storage Operator" role → Generate S3 credentials (Access Key + Secret Key)
6. Click **"Create"**
7. **Save S3 Credentials**: The Access Key and Secret Key generated in step 5 are now available in the Users tab - save these for your Go application
8. **Note the S3 Endpoint**: `https://s3.gra.cloud.ovh.net`
9. **Verify Container**: You should see container `quote-storage` in the "My Containers" tab - this is where your quotes.json will be stored

**✅ Next Step**: Now proceed to section 4 to deploy the VM instance manually, since OpenStack authentication is complex.

### 4. Manual VM Instance Creation

**Note**: Due to OpenStack authentication complexity with OVHcloud, we'll create the VM manually in the OVHcloud Manager. This is actually simpler and gives you full control.

#### **4.1 Navigate to Instance Creation**

1. Go to [OVHcloud Manager](https://www.ovh.com/auth/)
2. Navigate to **Public Cloud** → **Your Project** (`69f598c73ece43c293f49860d94adac0`)
3. Look for **"COMPUTE > Instances"** in the left menu
4. Click **"Create instance"** or **"Add instance"**

#### **4.2 Configure VM Instance**

**Basic Configuration:**
- **Name**: `quote-backend-vm`
- **Region**: **Gravelines (GRA)** (to match Object Storage)
- **Flavor**: **D2-2** (Discovery tab - €5.49/month, cheapest option)

**Image Selection:**
- **Image**: **Ubuntu 22.04 LTS** (stable, long-term support)
- **Avoid**: Ubuntu 25.04 (development version, less stable)

**Network Configuration:**
- **Network Mode**: **Public** (direct internet access)
- **Public IP**: Yes (assigned automatically)

**SSH Access:**
- **SSH Key**: **Import your SSH key**
- **Key Name**: `quote-app-key`
- **Public Key**: Paste entire contents of `~/.ssh/id_rsa.pub`

#### **4.3 SSH Key Setup**

**If you need to create SSH key:**
```bash
# Create SSH key pair
ssh-keygen -t rsa -b 4096 -C "quote-backend@ovhcloud" -f ~/.ssh/id_rsa -N ""

# Copy public key
cat ~/.ssh/id_rsa.pub
# Copy the entire output (one line starting with ssh-rsa)
```

**Add SSH Key in OVHcloud:**
1. In instance creation, click **"Import SSH key"**
2. **Key name**: `quote-app-key`
3. **Public key**: Paste the entire contents of `~/.ssh/id_rsa.pub`
4. **Save** the key

#### **4.4 Finalize and Create**

**Review Configuration:**
- **Name**: `quote-backend-vm`
- **Flavor**: D2-2 (Discovery)
- **Image**: Ubuntu 22.04 LTS
- **Region**: GRA
- **Network**: Public
- **SSH Key**: quote-app-key

**Create Instance:**
1. Click **"Create instance"**
2. Wait for provisioning (2-5 minutes)
3. Note the **public IP address** when ready

#### **4.5 Connect to Your VM**

**Get VM IP Address:**
- Find your VM in the Instances list
- Copy the **Public IP address**

**Connect via SSH:**
```bash
# Connect to your VM
ssh root@YOUR_VM_IP_ADDRESS

# First time setup
apt update && apt upgrade -y
```

#### **4.6 VM Setup for Go Application**

**Install Go:**
```bash
# Install Go 1.21
wget https://go.dev/dl/go1.21.0.linux-amd64.tar.gz
tar -C /usr/local -xzf go1.21.0.linux-amd64.tar.gz
echo 'export PATH=$PATH:/usr/local/go/bin' >> ~/.bashrc
source ~/.bashrc

# Verify installation
go version
```

**Create Application Directory:**
```bash
mkdir /opt/quote-backend
cd /opt/quote-backend
```

**Next Steps:**
1. **Set up Object Storage** (section 3)
2. **Deploy Go application** (section 6)
3. **Test API endpoints**

### 5. Go Application Implementation

#### **5.1 In-Memory Database with OVHcloud Object Storage (S3-Compatible)**
```go
package main

import (
    "context"
    "encoding/json"
    "fmt"
    "log"
    "net/http"
    "time"
    "github.com/aws/aws-sdk-go-v2/config"
    "github.com/aws/aws-sdk-go-v2/service/s3"
)

type Quote struct {
    ID     string `json:"id"`
    Text   string `json:"text"`
    Author string `json:"author"`
}

var quotesDB = make(map[string]Quote)
var s3Client *s3.Client // AWS SDK works with OVHcloud S3-compatible endpoint

func main() {
    // Initialize S3-compatible client for OVHcloud Object Storage
    cfg, err := config.LoadDefaultConfig(context.TODO(),
        config.WithRegion("GRA"),
        config.WithEndpoint("https://s3.gra.cloud.ovh.net"),
    )
    if err != nil {
        log.Fatal(err)
    }
    s3Client = s3.NewFromConfig(cfg)
    
    // Load existing quotes from OVHcloud Object Storage
    loadQuotesFromStorage()
    
    // Setup HTTP routes
    router := http.NewServeMux()
    router.HandleFunc("/api/quotes", getQuotes)
    router.HandleFunc("/api/quotes", createQuote).Methods("POST")
    
    // Start server
    log.Fatal(http.ListenAndServe(":8080", router))
}

func loadQuotesFromStorage() {
    // Load quotes.json from OVHcloud Object Storage bucket
    // Parse JSON and populate quotesDB
}

func saveQuotesToStorage() {
    // Serialize quotesDB to JSON
    // Upload to OVHcloud Object Storage bucket as quotes.json
}
```

#### **5.2 Complete Implementation Example**
```go
func getQuotes(w http.ResponseWriter, r *http.Request) {
    w.Header().Set("Content-Type", "application/json")
    json.NewEncoder(w).Encode(quotesDB)
}

func createQuote(w http.ResponseWriter, r *http.Request) {
    var quote Quote
    json.NewDecoder(r.Body).Decode(&quote)
    
    // Generate unique ID
    quote.ID = generateID()
    quotesDB[quote.ID] = quote
    
    // Save to OVHcloud Object Storage
    saveQuotesToStorage()
    
    w.Header().Set("Content-Type", "application/json")
    json.NewEncoder(w).Encode(quote)
}

func generateID() string {
    return fmt.Sprintf("quote-%d", time.Now().Unix())
}
```

### 6. Testing and Deployment

#### **6.1 Local Testing**
```bash
# Set environment variables for local testing
export S3_ENDPOINT=https://s3.gra.cloud.ovh.net
export S3_REGION=GRA
export S3_BUCKET=quotes-data
export S3_ACCESS_KEY=your-access-key
export S3_SECRET_KEY=your-secret-key

# Run locally
go run main.go
```

#### **6.2 Production Deployment**
1. Build the application: `go build -o quote-backend`
2. Upload to OVHcloud Web App
3. Monitor logs in OVHcloud Manager
4. Test API endpoints

### 7. Cost Summary - CHEAPEST POSSIBLE SETUP

#### **💰 Monthly Costs (Updated):**
- **Terraform Infrastructure**: $0.00
- **Object Storage**: ~$0.01/GB/month
- **Discovery VM Instance**: ~€5.49/month (D2-2 flavor)
- **Total**: **~€5.50/month**

#### **💡 Cost Optimization Applied:**
- **✅ Discovery instance** (D2-2): Cheapest VM option (~€5.49/month)
- **✅ Minimal security groups**: Only essential ports (8080, 22)
- **✅ No HTTP/HTTPS**: App runs on port 8080 only
- **✅ Shared resources**: Discovery instances use shared infrastructure
- **✅ GRA region**: 1-AZ region (cheaper than 3-AZ)

#### **📊 Updated Cost Comparison:**
- **AWS Lambda**: $2-5/month (~€1.80-4.50/month)
- **Azure Functions**: $5-9/month (~€4.50-8.10/month)
- **OVHcloud Discovery VM**: ~€5.50/month
- **OVHcloud Total**: **~€5.50/month**

#### **🏆 Cost Analysis:**
OVHcloud Discovery VM setup is **slightly higher than AWS Lambda**:
- **Lower bound**: €5.50/month vs AWS €1.80/month
- **Upper bound**: €5.50/month vs AWS €4.50/month
- **Still reasonable** for learning and VM experience

#### **⚠️ Trade-offs for Lowest Cost:**
- **Discovery instances**: Shared resources, 99.95% SLA (vs 99.99%)
- **Lower performance**: Shared CPU/RAM vs dedicated
- **No resizing**: Limited scaling options
- **Perfect for**: Learning, development, test environments

### 8. When OVHcloud NOW Makes Sense

#### **✅ GOOD Use Cases (with Discovery instances):**
- **Learning projects** (perfect for this use case)
- **Development environments** (cost-effective)
- **Test/sandbox** environments
- **Low-traffic applications**
- **European data residency** requirements
- **Budget-conscious projects**

#### **❌ Still Not Recommended For:**
- **High-traffic production** (use dedicated instances)
- **Performance-critical apps** (Discovery has shared resources)
- **Enterprise applications** (need 99.99% SLA)
- **Auto-scaling needs** (Discovery can't resize)

### 9. Alternative Recommendations (Updated)

#### **🏆 For Learning Serverless:**
- **AWS Lambda**: $2-5/month (still best for serverless concepts)
- **Azure Functions**: $5-9/month
- **OVHcloud Discovery VM**: ~$2-3/month (great for VM learning)

#### **🌍 For European Hosting:**
- **OVHcloud Discovery**: ~$2-3/month (now competitive!)
- **Scaleway**: Has serverless functions
- **Hetzner**: Cheap VMs (~$4-6/month)

### 10. Conclusion - OPTIMIZED

#### **💡 Optimized Assessment:**
The OVHcloud Discovery implementation provides:
- **Competitive pricing** (~$2-3/month)
- **VM management** learning experience
- **S3-compatible storage** integration
- **European hosting** at competitive rates

#### **🎯 Learning Value:**
- **Cost optimization** techniques
- **VM management** and deployment
- **S3 integration** patterns
- **Infrastructure as code** with Terraform
- **Budget-conscious** cloud architecture

#### **📈 Final Recommendation:**
For **learning serverless concepts**, **AWS Lambda** is still best.
For **learning VM management** on a budget, **OVHcloud Discovery** is now excellent and competitive!

**OVHcloud Discovery instances make this setup cost-competitive while providing valuable VM management experience!**

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
