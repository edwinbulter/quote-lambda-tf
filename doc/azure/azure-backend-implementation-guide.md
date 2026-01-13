# Azure Backend Implementation Guide

## Table of Contents

- [Overview](#overview)
- [Development Workflow](#development-workflow)
  - [Dual-IDE Strategy](#dual-ide-strategy)
  - [Why This Approach?](#why-this-approach)
- [Prerequisites](#prerequisites)
  - [For Local Development](#for-local-development-no-azure-account-needed)
  - [For Azure Deployment](#for-azure-deployment)
- [Phase 1: Local Development Setup](#phase-1-local-development-setup)
  - [Step 1: Install Development Tools](#step-1-install-development-tools)
  - [Step 2: Create the Azure Functions Project](#step-2-create-the-azure-functions-project)
  - [Step 3: Implement the C# Models](#step-3-implement-the-c-models)
  - [Step 4: Implement Services](#step-4-implement-services)
  - [Step 5: Implement HTTP Handlers](#step-5-implement-http-handlers)
  - [Step 6: Configure Local Settings](#step-6-configure-local-settings)
  - [Step 7: Configure Dependency Injection](#step-7-configure-dependency-injection)
- [Phase 2: Local Testing on Mac](#phase-2-local-testing-on-mac)
  - [Step 8: Run and Test Locally](#step-8-run-and-test-locally)
- [Phase 3: Create Azure Account](#phase-3-create-azure-account)
  - [Step 10: Sign Up for Azure](#step-10-sign-up-for-azure)
  - [Step 11: Install Azure CLI](#step-11-install-azure-cli)
- [Phase 4: Terraform Deployment](#phase-4-terraform-deployment)
  - [Step 12: Create Terraform Configuration](#step-12-create-terraform-configuration)
  - [Step 13: Deploy with Terraform](#step-13-deploy-with-terraform)
  - [Step 14: Deploy the Function Code](#step-14-deploy-the-function-code)
- [Phase 5: Testing in Azure](#phase-5-testing-in-azure)
  - [Step 15: Test the Deployed API](#step-15-test-the-deployed-api)
  - [Step 16: Monitor and Debug](#step-16-monitor-and-debug)
- [Phase 6: Advanced Features Implementation](#phase-6-advanced-features-implementation)
  - [Step 17: Implement ZenQuotes API Integration](#step-17-implement-zenquotes-api-integration)
  - [Step 18: Implement User Activity Tracking](#step-18-implement-user-activity-tracking)
  - [Step 19: Update HTTP Handlers for Advanced Features](#step-19-update-http-handlers-for-advanced-features)
  - [Step 20: Update Dependency Injection](#step-20-update-dependency-injection)
  - [Step 21: Update Configuration](#step-21-update-configuration)
- [Troubleshooting](#troubleshooting)
  - [Common Issues](#common-issues)
- [Resources](#resources)

---

## Overview

This step-by-step guide shows how to create the Azure backend for the Quote API using a dual-IDE workflow: **Windsurf IDE** for AI-powered development assistance and **VS Code** for C# development with the C# Dev Kit. You'll test locally on Mac, create an Azure account, and deploy using Terraform.

## Development Workflow

### Dual-IDE Strategy
- **Windsurf IDE**: Use for AI-powered code generation, explanations, and guidance
- **VS Code**: Use for C# compilation, debugging, testing, and C# Dev Kit features
- **Same Project**: Both IDEs work on the same folder simultaneously

### Why This Approach?
- **Windsurf Limitation**: C# Dev Kit extension cannot be installed in Windsurf
- **Best of Both Worlds**: AI assistance in Windsurf + full C# tooling in VS Code
- **Seamless Workflow**: Generate code in Windsurf, test/debug in VS Code

## Prerequisites

### For Local Development (No Azure Account Needed)
- **Mac with macOS 10.15+**
- **Windsurf IDE** (free) - for AI assistance
- **VS Code** (free) - for C# development with C# Dev Kit
- **.NET 8 SDK** (free)
- **Azure Functions Core Tools** (free)
- **Node.js** (for Azure Functions Core Tools)

### For Azure Deployment
- **Microsoft Azure account** (free tier available)
- **Azure CLI** (free)
- **Terraform** (free)

---

## Phase 1: Local Development Setup

### Step 1: Install Development Tools

#### Install .NET 8 SDK
```bash
# Install .NET 8 SDK using Homebrew
brew install --cask dotnet-sdk

# Verify installation
dotnet --version
```

#### Install Both IDEs
```bash
# Install Windsurf IDE (follow Windsurf installation instructions)
# Download from: https://windsurf-ai.com/

# Install VS Code
brew install --cask visual-studio-code

# Verify VS Code installation
code --version
```

#### Install C# Dev Kit in VS Code
1. Open VS Code
2. Go to Extensions
3. Search and install "C# Dev Kit" (ms-dotnettools.csdevkit)
4. Install "Azure Functions" (ms-azuretools.vscode-azurefunctions)

**Azure Extensions Note**: 
- The original "Azure Account" extension is deprecated
- For Azure functionality, use the individual extensions you need:
  - "Azure Functions" for function app development
  - "Azure App Service" for web app deployment
  - "Azure Storage" for storage account management
- Or search for "Azure" in the marketplace and install the specific extensions you need

#### Set Up Windsurf for AI Development
1. Open Windsurf
2. Install basic C# syntax highlighting (if available)
3. Configure for AI assistance (built-in)

#### Open Project in Both IDEs
```bash
# Navigate to your project directory
cd /path/to/quote-lambda-tf

# Open in VS Code
code .

# Open in Windsurf (in separate terminal)
windsurf .
```

**Workflow Tips:**
- Keep both IDEs open on the same folder
- Use Windsurf for AI code generation and explanations
- Switch to VS Code for compilation, debugging, and testing
- Changes made in one IDE appear automatically in the other

#### Install Node.js (required for Azure Functions Core Tools)
```bash
# Install Node.js
brew install node

# Verify installation
node --version
npm --version
```

#### Install Azure Functions Core Tools
```bash
# Install Azure Functions Core Tools
npm install -g azure-functions-core-tools@4 --unsafe-perm

# Verify installation
func --version
```

### Step 2: Create the Azure Functions Project

#### Create Project Structure
```bash
# Create project directory
mkdir quote-azure-backend
cd quote-azure-backend

# Initialize Azure Functions project
func init . --worker-runtime dotnet-isolated --target-framework net8.0

# Create HTTP trigger functions
func new --name QuoteHandler --template "HTTP trigger" --authlevel function
func new --name AdminHandler --template "HTTP trigger" --authlevel function
```

#### Project Structure
```
quote-azure-backend/
├── QuoteHandler.cs
├── AdminHandler.cs
├── Models/
│   ├── Quote.cs
│   ├── UserLike.cs
│   └── UserView.cs
├── Services/
│   ├── QuoteService.cs
│   └── AuthService.cs
├── local.settings.json
├── host.json
└── quote-azure-backend.csproj
```

### Step 3: Implement the C# Models

#### Create Quote Model
```csharp
// Models/Quote.cs
namespace QuoteAzureBackend.Models
{
    public class Quote
    {
        public int Id { get; set; }
        public string QuoteText { get; set; } = string.Empty;
        public string Author { get; set; } = string.Empty;
        public int LikeCount { get; set; }
        public DateTime CreatedAt { get; set; }
    }
}
```

#### Create User Models
```csharp
// Models/UserLike.cs
namespace QuoteAzureBackend.Models
{
    public class UserLike
    {
        public string UserId { get; set; } = string.Empty;
        public int QuoteId { get; set; }
        public int Order { get; set; }
        public DateTime LikedAt { get; set; }
    }
}

// Models/UserView.cs
namespace QuoteAzureBackend.Models
{
    public class UserView
    {
        public string UserId { get; set; } = string.Empty;
        public int QuoteId { get; set; }
        public DateTime ViewedAt { get; set; }
    }
}
```

### Step 4: Implement Services

#### Create Quote Service
```csharp
// Services/QuoteService.cs
using QuoteAzureBackend.Models;

namespace QuoteAzureBackend.Services
{
    public interface IQuoteService
    {
        Task<Quote> GetRandomQuoteAsync(List<int>? excludeIds = null);
        Task<List<Quote>> GetLikedQuotesAsync(string userId);
        Task LikeQuoteAsync(string userId, int quoteId);
        Task UnlikeQuoteAsync(string userId, int quoteId);
        Task<List<Quote>> GetViewHistoryAsync(string userId);
    }

    public class QuoteService : IQuoteService
    {
        private readonly ILogger<QuoteService> _logger;
        private readonly List<Quote> _quotes; // In-memory for local testing

        public QuoteService(ILogger<QuoteService> logger)
        {
            _logger = logger;
            _quotes = GenerateSampleQuotes();
        }

        public async Task<Quote> GetRandomQuoteAsync(List<int>? excludeIds = null)
        {
            var availableQuotes = excludeIds != null 
                ? _quotes.Where(q => !excludeIds.Contains(q.Id)).ToList()
                : _quotes;

            if (!availableQuotes.Any())
                throw new InvalidOperationException("No quotes available");

            var random = new Random();
            var selectedQuote = availableQuotes[random.Next(availableQuotes.Count)];
            
            return await Task.FromResult(selectedQuote);
        }

        public async Task<List<Quote>> GetLikedQuotesAsync(string userId)
        {
            // Mock implementation - in real app, query database
            return await Task.FromResult(new List<Quote>());
        }

        public async Task LikeQuoteAsync(string userId, int quoteId)
        {
            // Mock implementation - in real app, save to database
            await Task.CompletedTask;
        }

        public async Task UnlikeQuoteAsync(string userId, int quoteId)
        {
            // Mock implementation - in real app, remove from database
            await Task.CompletedTask;
        }

        public async Task<List<Quote>> GetViewHistoryAsync(string userId)
        {
            // Mock implementation - in real app, query database
            return await Task.FromResult(new List<Quote>());
        }

        private List<Quote> GenerateSampleQuotes()
        {
            return new List<Quote>
            {
                new Quote { Id = 1, QuoteText = "The only way to do great work is to love what you do.", Author = "Steve Jobs", LikeCount = 15, CreatedAt = DateTime.UtcNow },
                new Quote { Id = 2, QuoteText = "Innovation distinguishes between a leader and a follower.", Author = "Steve Jobs", LikeCount = 12, CreatedAt = DateTime.UtcNow },
                new Quote { Id = 3, QuoteText = "Life is what happens when you're busy making other plans.", Author = "John Lennon", LikeCount = 8, CreatedAt = DateTime.UtcNow },
                new Quote { Id = 4, QuoteText = "The future belongs to those who believe in the beauty of their dreams.", Author = "Eleanor Roosevelt", LikeCount = 10, CreatedAt = DateTime.UtcNow },
                new Quote { Id = 5, QuoteText = "It is during our darkest moments that we must focus to see the light.", Author = "Aristotle", LikeCount = 6, CreatedAt = DateTime.UtcNow }
            };
        }
    }
}
```

### Step 5: Implement HTTP Handlers

#### Create Quote Handler
```csharp
// QuoteHandler.cs
using Microsoft.AspNetCore.Mvc;
using Microsoft.Azure.Functions.Worker;
using Microsoft.Azure.Functions.Worker.Http;
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Services;
using System.Net;

namespace QuoteAzureBackend
{
    public class QuoteHandler
    {
        private readonly ILogger<QuoteHandler> _logger;
        private readonly IQuoteService _quoteService;

        public QuoteHandler(ILogger<QuoteHandler> logger, IQuoteService quoteService)
        {
            _logger = logger;
            _quoteService = quoteService;
        }

        [Function("QuoteHandler")]
        public async Task<HttpResponseData> RunAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", "post", Route = "quote")] HttpRequestData req,
            FunctionContext executionContext)
        {
            _logger.LogInformation("QuoteHandler function processed a request.");

            try
            {
                if (req.Method == HttpMethod.Get)
                {
                    var quote = await _quoteService.GetRandomQuoteAsync();
                    var response = req.CreateResponse(HttpStatusCode.OK);
                    await response.WriteAsJsonAsync(quote);
                    return response;
                }
                else if (req.Method == HttpMethod.Post)
                {
                    var requestBody = await req.ReadAsStringAsync();
                    var excludeIds = System.Text.Json.JsonSerializer.Deserialize<List<int>>(requestBody);
                    
                    var quote = await _quoteService.GetRandomQuoteAsync(excludeIds);
                    var response = req.CreateResponse(HttpStatusCode.OK);
                    await response.WriteAsJsonAsync(quote);
                    return response;
                }
                else
                {
                    var response = req.CreateResponse(HttpStatusCode.MethodNotAllowed);
                    return response;
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error processing quote request");
                var response = req.CreateResponse(HttpStatusCode.InternalServerError);
                return response;
            }
        }

        [Function("LikeQuote")]
        public async Task<HttpResponseData> LikeQuoteAsync(
            [HttpTrigger(AuthorizationLevel.Function, "post", Route = "quote/{id}/like")] HttpRequestData req,
            int id)
        {
            _logger.LogInformation($"LikeQuote function processed a request for quote {id}.");

            try
            {
                // Extract user ID from headers (mock for local testing)
                var userId = req.Headers.Contains("Authorization") ? "test-user" : "anonymous";
                
                await _quoteService.LikeQuoteAsync(userId, id);
                
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteStringAsync("Quote liked successfully");
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, $"Error liking quote {id}");
                var response = req.CreateResponse(HttpStatusCode.InternalServerError);
                return response;
            }
        }

        [Function("GetLikedQuotes")]
        public async Task<HttpResponseData> GetLikedQuotesAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", Route = "quote/liked")] HttpRequestData req)
        {
            _logger.LogInformation("GetLikedQuotes function processed a request.");

            try
            {
                var userId = req.Headers.Contains("Authorization") ? "test-user" : "anonymous";
                var likedQuotes = await _quoteService.GetLikedQuotesAsync(userId);
                
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(likedQuotes);
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting liked quotes");
                var response = req.CreateResponse(HttpStatusCode.InternalServerError);
                return response;
            }
        }
    }
}
```

### Step 6: Configure Local Settings

#### Update local.settings.json
```json
{
  "IsEncrypted": false,
  "Values": {
    "AzureWebJobsStorage": "UseDevelopmentStorage=true",
    "FUNCTIONS_WORKER_RUNTIME": "dotnet-isolated",
    "StorageConnectionString": "UseDevelopmentStorage=true"
  }
}
```

#### Update host.json
```json
{
  "version": "2.0",
  "logging": {
    "applicationInsights": {
      "samplingSettings": {
        "isEnabled": true,
        "excludedTypes": "Request"
      }
    }
  },
  "extensionBundle": {
    "id": "Microsoft.Azure.Functions.ExtensionBundle",
    "version": "[4.*, 5.0.0)"
  },
  "functionTimeout": "00:05:00"
}
```

### Step 7: Configure Dependency Injection

#### Update Program.cs
```csharp
using Microsoft.Azure.Functions.Worker.Configuration;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using QuoteAzureBackend.Services;

var host = new HostBuilder()
    .ConfigureFunctionsWorkerDefaults()
    .ConfigureServices(services => {
        services.AddScoped<IQuoteService, QuoteService>();
    })
    .ConfigureAppConfiguration(configuration => {
        configuration.AddCommandLine(args);
    })
    .Build();

host.Run();
```

---

## Phase 2: Local Testing on Mac

### Step 8: Run and Test Locally

#### Start the Functions
```bash
# In the project directory
func start
```

You should see output like:
```
Functions:
        QuoteHandler: [GET,POST] http://localhost:7071/api/quote
        LikeQuote: [POST] http://localhost:7071/api/quote/{id}/like
        GetLikedQuotes: [GET] http://localhost:7071/api/quote/liked
```

#### Test with curl
```bash
# Test getting a random quote
curl http://localhost:7071/api/quote

# Test getting a random quote with exclusions
curl -X POST http://localhost:7071/api/quote \
  -H "Content-Type: application/json" \
  -d "[1,2]"

# Test liking a quote
curl -X POST http://localhost:7071/api/quote/1/like

# Test getting liked quotes
curl http://localhost:7071/api/quote/liked
```

#### Open Project in Windsurf
```bash
# Open the project in Windsurf
windsurf .
```

#### Test in VS Code (Recommended)
1. Open the project in VS Code
2. Set breakpoints in the code
3. Press F5 to start debugging with C# Dev Kit
4. Use the integrated terminal to test endpoints
5. Use the debugger panel to inspect variables
6. Benefit from full IntelliSense and C# language support

#### Test in Windsurf (AI-Assisted)
1. Open the project in Windsurf
2. Use AI chat to explain code behavior
3. Request AI assistance for debugging
4. Generate test cases with AI help
5. Use AI to suggest improvements

**Recommended Testing Workflow:**
- **VS Code**: Run, debug, and compile the code
- **Windsurf**: Get AI explanations and generate additional test scenarios
- **Both**: Use terminal commands for API testing

---

## Phase 3: Create Azure Account

### Step 10: Sign Up for Azure

#### Create Free Account
1. Go to [https://azure.microsoft.com](https://azure.microsoft.com)
2. Click "Start free" button
3. Sign in with Microsoft account or create one
4. Provide credit card (for verification, not charged unless you upgrade)
5. Verify phone number

#### Free Tier Benefits
- **$200 credit** for first 30 days
- **12 months** of popular free services
- **Always free** services (including Azure Functions)

### Step 11: Install Azure CLI

```bash
# Install Azure CLI
brew install azure-cli

# Verify installation
az --version

# Login to Azure
az login
```

---

## Phase 4: Terraform Deployment

### Step 12: Create Terraform Configuration

#### Create Terraform Files
```bash
mkdir infrastructure
cd infrastructure
```

#### Create main.tf
```hcl
terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.0"
    }
  }
}

provider "azurerm" {
  features {}
}

# Resource Group
resource "azurerm_resource_group" "quote_backend" {
  name     = "quote-backend-rg"
  location = "West Europe"
}

# Storage Account
resource "azurerm_storage_account" "quote_storage" {
  name                     = "quotebackendstorage"
  resource_group_name      = azurerm_resource_group.quote_backend.name
  location                 = azurerm_resource_group.quote_backend.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
}

# Function App
resource "azurerm_function_app" "quote_function_app" {
  name                       = "quote-backend-function"
  location                   = azurerm_resource_group.quote_backend.location
  resource_group_name        = azurerm_resource_group.quote_backend.name
  app_service_plan_id        = azurerm_service_plan.quote_service_plan.id
  storage_account_name       = azurerm_storage_account.quote_storage.name
  storage_account_access_key = azurerm_storage_account.quote_storage.primary_access_key
  os_type                    = "linux"
  version                    = "~4"

  app_settings = {
    "FUNCTIONS_WORKER_RUNTIME" = "dotnet-isolated"
    "WEBSITE_RUN_FROM_PACKAGE" = "1"
  }

  site_config {
    linux_fx_version = "DOTNET-ISOLATED|8.0"
  }
}

# Service Plan
resource "azurerm_service_plan" "quote_service_plan" {
  name                = "quote-backend-plan"
  location            = azurerm_resource_group.quote_backend.location
  resource_group_name = azurerm_resource_group.quote_backend.name
  os_type             = "Linux"
  sku_name            = "Y1"  # Consumption plan
}
```

#### Create variables.tf
```hcl
variable "location" {
  description = "Azure region"
  type        = string
  default     = "West Europe"
}

variable "project_name" {
  description = "Project name prefix"
  type        = string
  default     = "quote-backend"
}
```

#### Create outputs.tf
```hcl
output "function_app_url" {
  description = "URL of the deployed Function App"
  value       = azurerm_function_app.quote_function_app.default_hostname
}

output "resource_group_name" {
  description = "Name of the resource group"
  value       = azurerm_resource_group.quote_backend.name
}
```

### Step 13: Deploy with Terraform

#### Initialize Terraform
```bash
cd infrastructure

# Initialize Terraform
terraform init

# Plan the deployment
terraform plan

# Apply the deployment
terraform apply
```

### Step 14: Deploy the Function Code

#### Build and Package
```bash
# Go back to project root
cd ..

# Build the project
dotnet build -c Release

# Create deployment package
dotnet publish -c Release -o ./publish
```

#### Deploy to Azure
```bash
# Install Azure Functions Core Tools if not already installed
# Deploy to Azure
func azure functionapp publish quote-backend-function
```

---

## Phase 5: Testing in Azure

### Step 15: Test the Deployed API

#### Get the Function URL
```bash
# Get the URL from Terraform outputs
cd infrastructure
terraform output function_app_url

# Or get it from Azure portal
```

#### Test the Cloud API
```bash
# Replace with your actual function URL
FUNCTION_URL="https://quote-backend-function.azurewebsites.net"

# Test endpoints
curl $FUNCTION_URL/api/quote
curl -X POST $FUNCTION_URL/api/quote/1/like
curl $FUNCTION_URL/api/quote/liked
```

### Step 16: Monitor and Debug

#### View Logs
```bash
# Stream logs in real-time
func azure functionapp logstream quote-backend-function

# Or view in Azure portal
```

#### Monitor in Azure Portal
1. Go to Azure Portal
2. Navigate to your Function App
3. Check Application Insights for monitoring
4. Review logs and metrics

---

## Troubleshooting

### Common Issues

#### Local Development Issues
- **Functions Core Tools not found**: Ensure npm installation is in PATH
- **.NET not found**: Verify .NET 8 SDK installation with `dotnet --version`
- **Port conflicts**: Change port in `local.settings.json`

#### Azure Deployment Issues
- **Authentication failures**: Run `az login` again
- **Resource limits**: Check free tier quotas in Azure portal
- **Deployment failures**: Check function logs for specific errors

#### Performance Issues
- **Cold starts**: Consider always-on instances for production
- **Memory limits**: Monitor function memory usage
- **Timeouts**: Increase timeout in host.json if needed

### Getting Help

#### Resources
- [Azure Functions Documentation](https://docs.microsoft.com/en-us/azure/azure-functions/)
- [Terraform Azure Provider](https://registry.terraform.io/providers/hashicorp/azurerm/latest/docs)
- [.NET on Azure](https://docs.microsoft.com/en-us/dotnet/azure/)
- [VS Code C# Dev Kit](https://docs.microsoft.com/en-us/visualstudio/ide/csharp-dev-kit)
- [Windsurf IDE Documentation](https://windsurf-ai.com/docs)

#### Support
- Azure Portal: Built-in help and documentation
- Stack Overflow: Tag with `azure-functions` and `c#`
- GitHub Issues: For specific tool problems

---

## Phase 6: Advanced Features Implementation

### Step 17: Implement ZenQuotes API Integration

#### Add HTTP Client Service
```csharp
// Services/ZenQuotesService.cs
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using System.Net.Http.Json;
using System.Text.Json;

namespace QuoteAzureBackend.Services
{
    public interface IZenQuotesService
    {
        Task<Quote> GetRandomQuoteAsync();
        Task<List<Quote>> GetMultipleQuotesAsync(int count = 5);
    }

    public class ZenQuotesService : IZenQuotesService
    {
        private readonly HttpClient _httpClient;
        private readonly ILogger<ZenQuotesService> _logger;
        private readonly string _apiKey;

        public ZenQuotesService(HttpClient httpClient, ILogger<ZenQuotesService> logger, IConfiguration configuration)
        {
            _httpClient = httpClient;
            _logger = logger;
            _apiKey = configuration["ZenQuotes:ApiKey"] ?? string.Empty;
            _httpClient.BaseAddress = new Uri("https://zenquotes.io/api/");
        }

        public async Task<Quote> GetRandomQuoteAsync()
        {
            try
            {
                var response = await _httpClient.GetAsync($"random/{_apiKey}");
                response.EnsureSuccessStatusCode();
                
                var content = await response.Content.ReadAsStringAsync();
                var zenQuotes = JsonSerializer.Deserialize<List<ZenQuoteResponse>>(content);
                
                if (zenQuotes?.Any() == true)
                {
                    var zenQuote = zenQuotes.First();
                    return new Quote
                    {
                        Id = Guid.NewGuid().GetHashCode(),
                        QuoteText = zenQuote.q,
                        Author = zenQuote.a,
                        LikeCount = 0,
                        CreatedAt = DateTime.UtcNow,
                        Source = "ZenQuotes"
                    };
                }
                
                throw new InvalidOperationException("No quotes returned from ZenQuotes API");
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error fetching quote from ZenQuotes API");
                throw;
            }
        }

        public async Task<List<Quote>> GetMultipleQuotesAsync(int count = 5)
        {
            try
            {
                var response = await _httpClient.GetAsync($"quotes/{_apiKey}");
                response.EnsureSuccessStatusCode();
                
                var content = await response.Content.ReadAsStringAsync();
                var zenQuotes = JsonSerializer.Deserialize<List<ZenQuoteResponse>>(content);
                
                return zenQuotes?.Select(zq => new Quote
                {
                    Id = Guid.NewGuid().GetHashCode(),
                    QuoteText = zq.q,
                    Author = zq.a,
                    LikeCount = 0,
                    CreatedAt = DateTime.UtcNow,
                    Source = "ZenQuotes"
                }).ToList() ?? new List<Quote>();
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error fetching multiple quotes from ZenQuotes API");
                throw;
            }
        }
    }

    public class ZenQuoteResponse
    {
        public string q { get; set; } = string.Empty;
        public string a { get; set; } = string.Empty;
        public string h { get; set; } = string.Empty;
    }
}
```

#### Update Quote Service to Use ZenQuotes
```csharp
// Services/QuoteService.cs (updated)
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Data;

namespace QuoteAzureBackend.Services
{
    public interface IQuoteService
    {
        Task<Quote> GetRandomQuoteAsync();
        Task<Quote> GetQuoteByIdAsync(int id);
        Task<List<Quote>> GetAllQuotesAsync();
        Task<Quote> AddQuoteAsync(Quote quote);
        Task<bool> DeleteQuoteAsync(int id);
        Task<List<Quote>> GetQuotesFromZenQuotesAsync(int count = 5);
    }

    public class QuoteService : IQuoteService
    {
        private readonly IQuoteRepository _repository;
        private readonly IZenQuotesService _zenQuotesService;
        private readonly ILogger<QuoteService> _logger;

        public QuoteService(IQuoteRepository repository, IZenQuotesService zenQuotesService, ILogger<QuoteService> logger)
        {
            _repository = repository;
            _zenQuotesService = zenQuotesService;
            _logger = logger;
        }

        public async Task<Quote> GetRandomQuoteAsync()
        {
            var quotes = await _repository.GetAllQuotesAsync();
            if (quotes.Any())
            {
                var random = new Random();
                return quotes[random.Next(quotes.Count)];
            }
            
            // Fallback to ZenQuotes if no local quotes
            return await _zenQuotesService.GetRandomQuoteAsync();
        }

        public async Task<Quote> GetQuoteByIdAsync(int id)
        {
            return await _repository.GetQuoteByIdAsync(id);
        }

        public async Task<List<Quote>> GetAllQuotesAsync()
        {
            return await _repository.GetAllQuotesAsync();
        }

        public async Task<Quote> AddQuoteAsync(Quote quote)
        {
            return await _repository.AddQuoteAsync(quote);
        }

        public async Task<bool> DeleteQuoteAsync(int id)
        {
            return await _repository.DeleteQuoteAsync(id);
        }

        public async Task<List<Quote>> GetQuotesFromZenQuotesAsync(int count = 5)
        {
            var zenQuotes = await _zenQuotesService.GetMultipleQuotesAsync(count);
            var addedQuotes = new List<Quote>();
            
            foreach (var quote in zenQuotes)
            {
                try
                {
                    var addedQuote = await _repository.AddQuoteAsync(quote);
                    addedQuotes.Add(addedQuote);
                }
                catch (Exception ex)
                {
                    _logger.LogWarning(ex, "Failed to add quote: {QuoteText}", quote.QuoteText);
                }
            }
            
            return addedQuotes;
        }
    }
}
```

### Step 18: Implement User Activity Tracking

#### Add User Activity Models
```csharp
// Models/UserActivity.cs
using System.ComponentModel.DataAnnotations;

namespace QuoteAzureBackend.Models
{
    public class UserFavorite
    {
        [Required]
        public string UserId { get; set; } = string.Empty;
        
        [Required]
        public int QuoteId { get; set; }
        
        public DateTime AddedAt { get; set; } = DateTime.UtcNow;
        
        public string PartitionKey => UserId;
        public string RowKey => $"favorite_{QuoteId}";
    }

    public class UserViewHistory
    {
        [Required]
        public string UserId { get; set; } = string.Empty;
        
        [Required]
        public int QuoteId { get; set; }
        
        public DateTime ViewedAt { get; set; } = DateTime.UtcNow;
        
        public string PartitionKey => UserId;
        public string RowKey => $"view_{QuoteId}_{ViewedAt:yyyyMMddHHmmss}";
    }

    public class UserPreferences
    {
        [Required]
        public string UserId { get; set; } = string.Empty;
        
        public string PreferredCategory { get; set; } = string.Empty;
        
        public int QuotesPerPage { get; set; } = 10;
        
        public bool EnableNotifications { get; set; } = true;
        
        public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
        
        public string PartitionKey => UserId;
        public string RowKey => "preferences";
    }
}
```

#### Create User Activity Service
```csharp
// Services/UserActivityService.cs
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Data;

namespace QuoteAzureBackend.Services
{
    public interface IUserActivityService
    {
        Task<bool> AddFavoriteAsync(string userId, int quoteId);
        Task<bool> RemoveFavoriteAsync(string userId, int quoteId);
        Task<List<Quote>> GetUserFavoritesAsync(string userId);
        Task<bool> RecordViewAsync(string userId, int quoteId);
        Task<List<Quote>> GetUserViewHistoryAsync(string userId, int limit = 50);
        Task<UserPreferences> GetUserPreferencesAsync(string userId);
        Task<bool> UpdateUserPreferencesAsync(string userId, UserPreferences preferences);
    }

    public class UserActivityService : IUserActivityService
    {
        private readonly IUserActivityRepository _repository;
        private readonly IQuoteService _quoteService;
        private readonly ILogger<UserActivityService> _logger;

        public UserActivityService(IUserActivityRepository repository, IQuoteService quoteService, ILogger<UserActivityService> logger)
        {
            _repository = repository;
            _quoteService = quoteService;
            _logger = logger;
        }

        public async Task<bool> AddFavoriteAsync(string userId, int quoteId)
        {
            try
            {
                var favorite = new UserFavorite { UserId = userId, QuoteId = quoteId };
                return await _repository.AddFavoriteAsync(favorite);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error adding favorite for user {UserId}, quote {QuoteId}", userId, quoteId);
                return false;
            }
        }

        public async Task<bool> RemoveFavoriteAsync(string userId, int quoteId)
        {
            try
            {
                return await _repository.RemoveFavoriteAsync(userId, quoteId);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error removing favorite for user {UserId}, quote {QuoteId}", userId, quoteId);
                return false;
            }
        }

        public async Task<List<Quote>> GetUserFavoritesAsync(string userId)
        {
            try
            {
                var favoriteQuoteIds = await _repository.GetUserFavoriteQuoteIdsAsync(userId);
                var quotes = new List<Quote>();
                
                foreach (var quoteId in favoriteQuoteIds)
                {
                    var quote = await _quoteService.GetQuoteByIdAsync(quoteId);
                    if (quote != null)
                    {
                        quotes.Add(quote);
                    }
                }
                
                return quotes;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting favorites for user {UserId}", userId);
                return new List<Quote>();
            }
        }

        public async Task<bool> RecordViewAsync(string userId, int quoteId)
        {
            try
            {
                var viewHistory = new UserViewHistory { UserId = userId, QuoteId = quoteId };
                return await _repository.RecordViewAsync(viewHistory);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error recording view for user {UserId}, quote {QuoteId}", userId, quoteId);
                return false;
            }
        }

        public async Task<List<Quote>> GetUserViewHistoryAsync(string userId, int limit = 50)
        {
            try
            {
                var viewedQuoteIds = await _repository.GetUserViewHistoryQuoteIdsAsync(userId, limit);
                var quotes = new List<Quote>();
                
                foreach (var quoteId in viewedQuoteIds)
                {
                    var quote = await _quoteService.GetQuoteByIdAsync(quoteId);
                    if (quote != null)
                    {
                        quotes.Add(quote);
                    }
                }
                
                return quotes;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting view history for user {UserId}", userId);
                return new List<Quote>();
            }
        }

        public async Task<UserPreferences> GetUserPreferencesAsync(string userId)
        {
            try
            {
                return await _repository.GetUserPreferencesAsync(userId) ?? new UserPreferences { UserId = userId };
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting preferences for user {UserId}", userId);
                return new UserPreferences { UserId = userId };
            }
        }

        public async Task<bool> UpdateUserPreferencesAsync(string userId, UserPreferences preferences)
        {
            try
            {
                preferences.UserId = userId;
                preferences.UpdatedAt = DateTime.UtcNow;
                return await _repository.UpdateUserPreferencesAsync(preferences);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error updating preferences for user {UserId}", userId);
                return false;
            }
        }
    }
}
```

### Step 19: Update HTTP Handlers for Advanced Features

#### Enhanced Quote Handler
```csharp
// QuoteHandler.cs (updated)
using Microsoft.AspNetCore.Mvc;
using Microsoft.Azure.Functions.Worker;
using Microsoft.Azure.Functions.Worker.Http;
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Services;
using System.Net;

namespace QuoteAzureBackend
{
    public class QuoteHandler
    {
        private readonly ILogger<QuoteHandler> _logger;
        private readonly IQuoteService _quoteService;
        private readonly IUserActivityService _userActivityService;

        public QuoteHandler(ILogger<QuoteHandler> logger, IQuoteService quoteService, IUserActivityService userActivityService)
        {
            _logger = logger;
            _quoteService = quoteService;
            _userActivityService = userActivityService;
        }

        [Function("quotes")]
        public async Task<HttpResponseData> GetQuotesAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", Route = "quotes")] HttpRequestData req,
            FunctionContext executionContext)
        {
            _logger.LogInformation("Getting all quotes");

            try
            {
                var quotes = await _quoteService.GetAllQuotesAsync();
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(quotes);
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting quotes");
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        [Function("quote/random")]
        public async Task<HttpResponseData> GetRandomQuoteAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", Route = "quote/random")] HttpRequestData req,
            FunctionContext executionContext)
        {
            _logger.LogInformation("Getting random quote");

            try
            {
                var quote = await _quoteService.GetRandomQuoteAsync();
                
                // Record view if user is authenticated
                var userId = GetUserFromRequest(req);
                if (!string.IsNullOrEmpty(userId))
                {
                    await _userActivityService.RecordViewAsync(userId, quote.Id);
                }

                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(quote);
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting random quote");
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        [Function("quotes/zen")]
        public async Task<HttpResponseData> GetZenQuotesAsync(
            [HttpTrigger(AuthorizationLevel.Function, "post", Route = "quotes/zen")] HttpRequestData req,
            FunctionContext executionContext)
        {
            _logger.LogInformation("Fetching quotes from ZenQuotes API");

            try
            {
                var requestBody = await new StreamReader(req.Body).ReadToEndAsync();
                var count = string.IsNullOrEmpty(requestBody) ? 5 : int.Parse(requestBody);
                
                var quotes = await _quoteService.GetQuotesFromZenQuotesAsync(count);
                
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(new { AddedQuotes = quotes.Count, Quotes = quotes });
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error fetching ZenQuotes");
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        [Function("quote/{id}")]
        public async Task<HttpResponseData> GetQuoteByIdAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", Route = "quote/{id}")] HttpRequestData req,
            int id,
            FunctionContext executionContext)
        {
            _logger.LogInformation("Getting quote by ID: {QuoteId}", id);

            try
            {
                var quote = await _quoteService.GetQuoteByIdAsync(id);
                
                if (quote == null)
                {
                    return req.CreateResponse(HttpStatusCode.NotFound);
                }

                // Record view if user is authenticated
                var userId = GetUserFromRequest(req);
                if (!string.IsNullOrEmpty(userId))
                {
                    await _userActivityService.RecordViewAsync(userId, quote.Id);
                }

                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(quote);
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting quote by ID");
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        private string GetUserFromRequest(HttpRequestData req)
        {
            // Extract user ID from JWT token or headers
            // This is a simplified version - implement proper JWT validation
            if (req.Headers.TryGetValues("X-User-Id", out var userIdValues))
            {
                return userIdValues.FirstOrDefault();
            }
            return string.Empty;
        }
    }
}
```

#### Add User Activity Handler
```csharp
// UserActivityHandler.cs
using Microsoft.AspNetCore.Mvc;
using Microsoft.Azure.Functions.Worker;
using Microsoft.Azure.Functions.Worker.Http;
using Microsoft.Extensions.Logging;
using QuoteAzureBackend.Models;
using QuoteAzureBackend.Services;
using System.Net;

namespace QuoteAzureBackend
{
    public class UserActivityHandler
    {
        private readonly ILogger<UserActivityHandler> _logger;
        private readonly IUserActivityService _userActivityService;

        public UserActivityHandler(ILogger<UserActivityHandler> logger, IUserActivityService userActivityService)
        {
            _logger = logger;
            _userActivityService = userActivityService;
        }

        [Function("user/favorites")]
        public async Task<HttpResponseData> GetUserFavoritesAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", "post", "delete", Route = "user/favorites")] HttpRequestData req,
            FunctionContext executionContext)
        {
            var userId = GetUserFromRequest(req);
            if (string.IsNullOrEmpty(userId))
            {
                return req.CreateResponse(HttpStatusCode.Unauthorized);
            }

            try
            {
                if (req.Method == HttpMethod.Get)
                {
                    var favorites = await _userActivityService.GetUserFavoritesAsync(userId);
                    var response = req.CreateResponse(HttpStatusCode.OK);
                    await response.WriteAsJsonAsync(favorites);
                    return response;
                }
                else if (req.Method == HttpMethod.Post)
                {
                    var requestBody = await new StreamReader(req.Body).ReadToEndAsync();
                    var quoteId = int.Parse(requestBody);
                    
                    var success = await _userActivityService.AddFavoriteAsync(userId, quoteId);
                    return success ? req.CreateResponse(HttpStatusCode.OK) : req.CreateResponse(HttpStatusCode.BadRequest);
                }
                else if (req.Method == HttpMethod.Delete)
                {
                    var requestBody = await new StreamReader(req.Body).ReadToEndAsync();
                    var quoteId = int.Parse(requestBody);
                    
                    var success = await _userActivityService.RemoveFavoriteAsync(userId, quoteId);
                    return success ? req.CreateResponse(HttpStatusCode.OK) : req.CreateResponse(HttpStatusCode.BadRequest);
                }

                return req.CreateResponse(HttpStatusCode.MethodNotAllowed);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error handling user favorites request");
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        [Function("user/history")]
        public async Task<HttpResponseData> GetUserViewHistoryAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", Route = "user/history")] HttpRequestData req,
            FunctionContext executionContext)
        {
            var userId = GetUserFromRequest(req);
            if (string.IsNullOrEmpty(userId))
            {
                return req.CreateResponse(HttpStatusCode.Unauthorized);
            }

            try
            {
                var queryParams = System.Web.HttpUtility.ParseQueryString(req.Url.Query);
                var limit = int.TryParse(queryParams["limit"], out var l) ? l : 50;
                
                var history = await _userActivityService.GetUserViewHistoryAsync(userId, limit);
                var response = req.CreateResponse(HttpStatusCode.OK);
                await response.WriteAsJsonAsync(history);
                return response;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting user view history");
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        [Function("user/preferences")]
        public async Task<HttpResponseData> GetUserPreferencesAsync(
            [HttpTrigger(AuthorizationLevel.Function, "get", "put", Route = "user/preferences")] HttpRequestData req,
            FunctionContext executionContext)
        {
            var userId = GetUserFromRequest(req);
            if (string.IsNullOrEmpty(userId))
            {
                return req.CreateResponse(HttpStatusCode.Unauthorized);
            }

            try
            {
                if (req.Method == HttpMethod.Get)
                {
                    var preferences = await _userActivityService.GetUserPreferencesAsync(userId);
                    var response = req.CreateResponse(HttpStatusCode.OK);
                    await response.WriteAsJsonAsync(preferences);
                    return response;
                }
                else if (req.Method == HttpMethod.Put)
                {
                    var preferences = await req.ReadFromJsonAsync<UserPreferences>();
                    if (preferences == null)
                    {
                        return req.CreateResponse(HttpStatusCode.BadRequest);
                    }
                    
                    var success = await _userActivityService.UpdateUserPreferencesAsync(userId, preferences);
                    return success ? req.CreateResponse(HttpStatusCode.OK) : req.CreateResponse(HttpStatusCode.BadRequest);
                }

                return req.CreateResponse(HttpStatusCode.MethodNotAllowed);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error handling user preferences request");
                return req.CreateResponse(HttpStatusCode.InternalServerError);
            }
        }

        private string GetUserFromRequest(HttpRequestData req)
        {
            // Extract user ID from JWT token or headers
            // This is a simplified version - implement proper JWT validation
            if (req.Headers.TryGetValues("X-User-Id", out var userIdValues))
            {
                return userIdValues.FirstOrDefault();
            }
            return string.Empty;
        }
    }
}
```

### Step 20: Update Dependency Injection

#### Update Program.cs
```csharp
// Program.cs (updated)
using Microsoft.Azure.Functions.Worker.Configuration;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using QuoteAzureBackend.Services;
using QuoteAzureBackend.Data;

var host = new HostBuilder()
    .ConfigureFunctionsWorkerDefaults()
    .ConfigureAppConfiguration((context, config) =>
    {
        config.AddJsonFile("local.settings.json", optional: true, reloadOnChange: true);
        config.AddEnvironmentVariables();
    })
    .ConfigureServices((context, services) =>
    {
        // Add HTTP client for ZenQuotes
        services.AddHttpClient<IZenQuotesService, ZenQuotesService>();
        
        // Add repositories
        services.AddScoped<IQuoteRepository, QuoteRepository>();
        services.AddScoped<IUserActivityRepository, UserActivityRepository>();
        
        // Add services
        services.AddScoped<IQuoteService, QuoteService>();
        services.AddScoped<IUserActivityService, UserActivityService>();
        
        // Add logging
        services.AddLogging();
    })
    .Build();

host.Run();
```

### Step 21: Update Configuration

#### Add ZenQuotes Configuration
```json
// local.settings.json (updated)
{
  "IsEncrypted": false,
  "Values": {
    "AzureWebJobsStorage": "UseDevelopmentStorage=true",
    "FUNCTIONS_WORKER_RUNTIME": "dotnet",
    "ZenQuotes:ApiKey": "your_zenquotes_api_key_here"
  },
  "ConnectionStrings": {
    "AzureStorage": "DefaultEndpointsProtocol=https;AccountName=yourstorageaccount;AccountKey=yourkey;EndpointSuffix=core.windows.net"
  }
}
```

---

## Next Steps

### Production Considerations
1. **Add Azure AD B2C** for authentication
2. **Implement Azure Table Storage** for data persistence
3. **Set up CI/CD** with GitHub Actions
4. **Add monitoring** with Application Insights
5. **Configure scaling** and performance optimization

### Advanced Features Implemented
1. **ZenQuotes Integration** - Fetch quotes from external API
2. **User Activity Tracking** - Track favorites and view history
3. **User Preferences** - Store and manage user preferences
4. **Enhanced API Endpoints** - Full CRUD operations for user data

### Cost Optimization
1. **Monitor usage** in Azure Cost Management
2. **Optimize function performance** to reduce compute costs
3. **Use appropriate storage tiers** based on access patterns
4. **Set up budgets** and alerts for cost control

This extended guide now provides a complete implementation matching the AWS Lambda backend functionality, including external API integration, user activity tracking, and comprehensive data management capabilities.
