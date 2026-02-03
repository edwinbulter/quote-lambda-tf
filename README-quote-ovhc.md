# Quote OVHcloud - OVHcloud Deployment

A full-stack quote management application deployed on OVHcloud with AWS S3 storage. This deployment showcases OVHcloud's virtual machine capabilities with Go backend, React frontend, and AWS S3 integration, demonstrating best practices for cloud-native architecture, infrastructure as code, and CI/CD automation on OVHcloud.

## Table of Contents

- [🌟 Live Demo](#-live-demo)
- [📋 Overview](#-overview)
- [🏗️ OVHcloud Architecture](#️-ovhcloud-architecture)
- [📦 Repository Structure](#-repository-structure)
  - [Frontend - React Web Application](#frontend---react-web-application)
  - [Backend - Go REST API](#backend---go-rest-api)
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

Access the OVHcloud-deployed application at:

**OVHcloud Production Environment:**
> **https://quote-ovhc.kabulter.click/**

**API Endpoint:**
> **https://quote-ovhc-backend.mooo.com/api/v1**

Not all features can be used if you're not signed in.  
If you don't want to register and test the restricted features, you can use these user/password combinations:  
- user-1 with password Hello-user-1
- user-2 with password Hello-user-2
- user-3 with password Hello-user-3

And to see what you can do as an admin:
- admin-1 with password Hello-admin-1

## 📋 Overview

This OVHcloud deployment allows users to:
- Browse inspirational quotes from [ZenQuotes API](https://zenquotes.io/)
- Get random quotes with smart filtering
- Like their favorite quotes
- View popular quotes sorted by likes
- Track reading progress and manage personal quote collections

The OVHcloud deployment showcases:
- **Virtual Machine Architecture** - Go backend running on OVHcloud VM
- **Infrastructure as Code** - Complete Terraform configurations for AWS S3 and CloudFront
- **Modern Frontend** - React with TypeScript and Vite
- **CI/CD Automation** - GitHub Actions with AWS IAM role authentication
- **Hybrid Cloud Design** - OVHcloud compute with OVHcloud Object Storage and AWS CloudFront CDN
- **Data Persistence**: SQLite database with OVHcloud Object Storage backup and restore capabilities

## 🏗️ OVHcloud Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   AWS CloudFront CDN                        │
│              (quote-ovhc.kabulter.click    )                │
│                   Global Content Delivery                   │
└────────────────────────┬────────────────────────────────────┘
                         │
                         │ HTTPS Requests
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                      AWS S3 Storage                         │
│                (quote-ovhc-frontend bucket)                 │
│              • Static Website Hosting                       │
│              • Global Asset Distribution                    │
└────────────────────────┬────────────────────────────────────┘
                         │
                         │ API Calls
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   OVHcloud VM Instance                      │
│            (quote-ovhc-backend.mooo.com)                    │
│              • Go REST API Server                           │
│              • SQLite Database                              │
│              • OVHcloud Object Storage Backup               │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                 OVHcloud Object Storage                     │
│                  (Backend Data Container)                   │
│              • Database Backups                             │
│              • Persistent Storage                           │
│              • S3-Compatible API                            │
└─────────────────────────────────────────────────────────────┘
```

## 📦 Repository Structure

This OVHcloud deployment contains two main modules:

### [Frontend](./quote-ovhc-frontend/) - React Web Application

A modern, responsive web application built with:
- **Framework**: React 18 with TypeScript
- **Build Tool**: Vite for fast development and optimized builds
- **Styling**: TailwindCSS for responsive design
- **Testing**: Vitest for unit testing
- **State Management**: React Query for server state
- **Authentication**: JWT token-based authentication
- **Hosting**: AWS S3 Static Website with CloudFront CDN
- **Deployment**: GitHub Actions with AWS IAM role authentication

### [Backend](./quote-ovhc-backend/) - Go REST API

A high-performance REST API built with:
- **Language**: Go 1.21+
- **Runtime**: Standalone binary on OVHcloud VM
- **Database**: SQLite with OVHcloud Object Storage backup/restore
- **Authentication**: Custom JWT-based authentication with email/password registration
- **Authorization**: Role-based access control (USER/ADMIN roles) with JWT tokens
- **Infrastructure**: Terraform for AWS S3 (frontend) and CloudFront management
- **External API**: ZenQuotes.io for quote data
- **Data Persistence**: Hybrid SQLite + OVHcloud Object Storage
- **Deployment**: Manual deployment to OVHcloud VM

## 🚀 Quick Start

### Prerequisites

- **OVHcloud Account** with VM instance and Object Storage
- **AWS Account** for S3 (frontend) and CloudFront
- **Terraform** >= 1.0.0
- **Go 1.21+** (for backend)
- **Node.js 18+** (for frontend)
- **AWS CLI** configured with credentials

### Deploy the Complete Stack

#### 1. Configure AWS Credentials

```bash
# Configure AWS CLI for S3 and CloudFront
aws configure
# Enter your AWS Access Key ID, Secret Access Key, region (eu-central-1), and output format
```

#### 2. Deploy Frontend Infrastructure

```bash
cd quote-ovhc-frontend/infrastructure
terraform init
terraform apply
```

#### 3. Deploy Frontend Application

Use the GitHub Actions workflow or deploy manually:

```bash
cd quote-ovhc-frontend
npm install
npm run build
aws s3 sync dist/ s3://quote-ovhc-frontend --delete
```

#### 4. Deploy Backend to OVHcloud VM

```bash
# Build the Go backend
cd quote-ovhc-backend
make build

# Deploy to OVHcloud VM (example)
scp ./quote-backend user@your-vm-ip:/home/user/
ssh user@your-vm-ip
sudo systemctl start quote-backend
```

#### 5. Configure Backend Environment

Create environment file on the VM:

```bash
# /etc/quote-backend/environment
OVH_ACCESS_KEY_ID=your_ovh_access_key
OVH_SECRET_ACCESS_KEY=your_ovh_secret_key
OVH_REGION=gra
OVH_ENDPOINT=https://s3.gra.cloud.ovh.net
S3_BUCKET_NAME=quote-ovhc-backend-data
JWT_SECRET=your_jwt_secret
PORT=8080
```

## 📚 Documentation

### Backend Documentation
- [Backend README](./quote-ovhc-backend/README.md) - Complete API documentation
- [Infrastructure Setup](./quote-ovhc-backend/infrastructure/) - OVHcloud Object Storage configuration
- [API Testing](./quote-ovhc-backend/test-endpoints.md) - HTTP request examples

### Frontend Documentation
- [Development Setup](./quote-ovhc-frontend/) - Local development and build process
- [Infrastructure](./quote-ovhc-frontend/infrastructure/) - Terraform configuration

## 🔐 Authentication & Authorization

The OVHcloud deployment uses a **custom JWT-based authentication system** for user authentication and **role-based authorization** for protecting API endpoints.

### User Authentication

Users can authenticate via:

**Email + Password Registration**
- Users register with email, choose a custom username, and set a password
- Password is securely hashed using bcrypt
- Users are automatically assigned the `USER` role
- JWT access tokens (24-hour expiry) and refresh tokens (7-day expiry) are issued

### Authorization

- **Public endpoints** (`GET /quote/public`, `GET /health`) - No authentication required
- **Protected endpoints** (`POST /quote/{id}/like`, `GET /quote/liked`) - Requires `USER` role
- **Admin endpoints** (`GET /manage/users`, `POST /manage/quotes/fetch`) - Requires `ADMIN` role
- **Authorization** is enforced in the Go backend by validating JWT tokens and checking user roles

### Key Features

- ✅ Secure password hashing (bcrypt)
- ✅ JWT tokens with 24-hour expiration (refreshable for 7 days)
- ✅ Role-based access control (USER, ADMIN roles)
- ✅ User action logging
- ✅ CORS configured for CloudFront domain
- ✅ Custom JWT service with configurable signing keys
- ✅ SQLite database with S3 backup/restore

## 🔐 GitHub Actions Setup

The frontend uses GitHub Actions for automated deployments with AWS IAM role authentication.

### Required GitHub Secrets

Add these secrets to your repository:

| Secret Name | Value | Description |
|-------------|-------|-------------|
| `AWS_ROLE_ARN` | IAM Role ARN | AWS IAM role for S3/CloudFront access |
| `AWS_REGION` | `eu-central-1` | AWS region for resources |

### Workflows

- **[deploy-ovhc-frontend.yml](./.github/workflows/deploy-ovhc-frontend.yml)** - Builds and deploys the frontend to AWS S3 and CloudFront

## 🎯 Learning Goals

This OVHcloud deployment demonstrates:

1. **Hybrid Cloud Architecture**
   - Go backend on OVHcloud virtual machines
   - AWS S3 for scalable storage and backup
   - CloudFront CDN for global content delivery

2. **Infrastructure as Code**
   - Managing AWS resources with Terraform
   - Remote state management with S3 backend
   - Modular infrastructure design

3. **Modern Backend Development**
   - Go REST API with SQLite database
   - JWT authentication and authorization
   - S3 integration for data persistence

4. **Modern Frontend Development**
   - React with TypeScript and Vite
   - Responsive design with TailwindCSS
   - Unit testing with Vitest

5. **DevOps Best Practices**
   - CI/CD with GitHub Actions
   - AWS IAM role authentication
   - Automated testing and deployment

6. **Data Persistence Patterns**
   - SQLite for local performance
   - OVHcloud Object Storage for backup and restore
   - Hybrid storage strategy

## 💰 Cost Estimate

This application uses both OVHcloud and AWS services. Here's the complete cost breakdown:

### **OVHcloud Infrastructure (Backend)**

| Service | Configuration | Estimated Monthly Cost |
|---------|---------------|----------------------|
| **VM Instance** | d2-2 (2 vCPU, 4GB RAM) | €7.30 (~$8) |
| **Object Storage** | OVHcloud Object Storage (S3-compatible, <1 GiB) | €0.007 (~$0.01) |
| **Data Transfer** | Public bandwidth (included with VM) | €0 (included) |
| **Local SSD** | 25GB SSD (included with VM) | €0 (included) |

### **AWS Infrastructure (Frontend & Storage)**

| Service | Configuration | Free Tier Coverage | Estimated Monthly Cost |
|---------|---------------|-------------------|----------------------|
| **S3 Storage** | Static assets only | 5GB storage + 100K transactions free | $2-5 for typical usage |
| **CloudFront CDN** | Global content delivery | 1TB data transfer free | $0-5 for moderate traffic |
| **Terraform State** | S3 backend storage | Included in S3 free tier | $0 |

### **Total Estimated Monthly Costs**

| Usage Level | OVHcloud | AWS | **Total** |
|-------------|----------|-----|-----------|
| **Low Traffic** (<10K API calls/month) | €7.31 (~$8.01) | $2-5 | **$10-13** |
| **Moderate Traffic** (50K API calls/month) | €7.31 (~$8.01) | $5-10 | **$13-18** |
| **High Traffic** (100K+ API calls/month) | €7.31 (~$8.01) | $10-15 | **$18-23** |

### **Cost Optimization Benefits**

✅ **Exceptional VM Pricing**: OVHcloud d2-2 instance at only €0.01/hour (~$8/month) makes this extremely cost-effective  
✅ **Ultra-Low Storage Cost**: OVHcloud Object Storage at €0.007/GiB/month (<$0.01 for <1 GiB usage)  
✅ **Hybrid Architecture**: Leverages OVHcloud's competitive VM and Object Storage pricing with AWS's mature CDN services  
✅ **Free Tier Utilization**: Most AWS services stay within Free Tier limits for low-traffic usage  
✅ **Pay-per-use Model**: Only pay for actual storage consumption and data transfer  
✅ **CDN Optimization**: CloudFront reduces VM load and improves global performance  
✅ **Efficient Backup Strategy**: OVHcloud Object Storage provides cost-effective, durable backup storage  

### **Cost Management Tips**

1. **Monitor VM Usage**: Right-size your OVHcloud VM instance based on actual load
2. **Optimize Object Storage**: Use lifecycle policies for old database backups
3. **CDN Caching**: Configure appropriate CloudFront cache headers
4. **Data Transfer**: Monitor and optimize data transfer costs between OVHcloud and AWS
5. **Backup Strategy**: Implement appropriate backup retention policies

### **Infrastructure Summary**

**OVHcloud Resources:**
- 1 Virtual Machine (Go backend)
- 1 Object Storage container (backend data backups)
- Local SSD storage
- Data transfer allowance

**AWS Resources:**
- 1 S3 bucket (frontend static assets)
- 1 CloudFront distribution
- Terraform state management

**This hybrid approach provides an optimal balance of performance, cost, and scalability, leveraging the strengths of both cloud providers.**

## 🤝 Contributing

This is a learning project, but suggestions and improvements are welcome!

## 📄 License

This project is open source and available for educational purposes.

## 🔗 Links

- **Live Application (OVHcloud)**: https://quote-ovhc.kabulter.click/
- **API Endpoint**: https://quote-ovhc-backend.mooo.com/api/v1
- **OVHcloud Control Panel**: https://www.ovh.com/manager/
- **AWS Console**: https://console.aws.amazon.com/
- **ZenQuotes API**: https://zenquotes.io/

---

Built with ❤️ to learn hybrid cloud architecture and modern web development
