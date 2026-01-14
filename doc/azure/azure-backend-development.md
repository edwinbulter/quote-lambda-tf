# Azure Backend Development Guide

## Table of Contents

- [Overview](#overview)
- [Development Environment Setup](#development-environment-setup)
  - [Prerequisites](#prerequisites)
  - [Opening the Project](#opening-the-project)
- [Local Development Workflow](#local-development-workflow)
  - [1. Configuration Management](#1-configuration-management)
  - [2. Running the Application](#2-running-the-application)
  - [3. Testing the API](#3-testing-the-api)
  - [4. Debugging](#4-debugging)
  - [5. Code Development](#5-code-development)
  - [6. Project Structure](#6-project-structure)
  - [7. Common Development Issues](#7-common-development-issues)
  - [8. Best Practices](#8-best-practices)
  - [9. Testing Strategy](#9-testing-strategy)
  - [10. Deployment Preparation](#10-deployment-preparation)
- [Troubleshooting Quick Reference](#troubleshooting-quick-reference)
- [Next Steps](#next-steps)

## Overview

This guide covers the day-to-day development workflow for the Azure Functions Quote API backend once the initial setup is complete. It assumes you have already followed the implementation guide and have a working codebase.

## Development Environment Setup

### Prerequisites
- **VS Code** with C# Dev Kit installed
- **Windsurf IDE** for AI assistance (optional but recommended)
- **.NET 8 SDK** installed
- **Azure Functions Core Tools** installed
- **Node.js** installed

### Opening the Project

#### VS Code (Primary Development)
```bash
cd /path/to/quote-lambda-tf/quote-azure-backend
code .
```

#### Windsurf IDE (AI Assistance)
```bash
# In separate terminal
cd /path/to/quote-lambda-tf/quote-azure-backend
windsurf .
```

## Local Development Workflow

### 1. Configuration Management

#### ⚠️ IMPORTANT: local.settings.json Security

**NEVER commit `local.settings.json` to version control!**

This file contains:
- Connection strings
- API keys and secrets
- Local development settings
- Storage account credentials

**Why it's gitignored:**
- Security: Prevents secrets from being exposed in git history
- Environment-specific: Each developer needs their own local configuration
- Best Practice: Microsoft's official recommendation for Azure Functions

#### Creating Your Local Configuration

If `local.settings.json` doesn't exist locally, create it:

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

**For Azure Storage (Optional):**
Replace `UseDevelopmentStorage=true` with actual connection string when testing against Azure storage:
```json
"StorageConnectionString": "DefaultEndpointsProtocol=https;AccountName=youraccount;AccountKey=yourkey;EndpointSuffix=core.windows.net"
```

### 2. Running the Application

#### Start Azure Functions Runtime
```bash
# In VS Code terminal or any terminal
func start
```

**Expected Output:**
```
Functions:
        QuoteHandler: [GET,POST] http://localhost:7071/api/quote
        LikeQuote: [POST] http://localhost:7071/api/quote/{id}/like
        GetLikedQuotes: [GET] http://localhost:7071/api/quote/liked
```

#### Development Server URLs
- **Functions Runtime**: http://localhost:7071
- **API Endpoints**: http://localhost:7071/api/

### 3. Testing the API

#### Using curl Commands
```bash
# Get a random quote
curl http://localhost:7071/api/quote

# Get random quote with exclusions
curl -X POST http://localhost:7071/api/quote \
  -H "Content-Type: application/json" \
  -d "[1,2]"

# Like a quote
curl -X POST http://localhost:7071/api/quote/1/like

# Get liked quotes
curl http://localhost:7071/api/quote/liked
```

#### Using VS Code REST Client
Create `test.http` file:
```http
### Get random quote
GET http://localhost:7071/api/quote

### Get random quote with exclusions
POST http://localhost:7071/api/quote
Content-Type: application/json

[1,2,3]

### Like quote
POST http://localhost:7071/api/quote/1/like

### Get liked quotes
GET http://localhost:7071/api/quote/liked
```

### 4. Debugging

#### VS Code Debugging (Recommended)
1. Set breakpoints in your code
2. Press F5 or go to Run → Start Debugging
3. Select "Azure Functions" launch configuration
4. Use debugger panel to inspect variables
5. View call stack and watch expressions

#### Windsurf AI-Assisted Debugging
1. Use AI chat to explain error messages
2. Request AI assistance for troubleshooting
3. Generate additional test scenarios
4. Get code improvement suggestions

#### Log Monitoring
```bash
# View real-time logs
func start

# Or check logs in VS Code terminal
```

### 5. Code Development

#### Making Changes
1. **Edit in Windsurf**: Use AI for code generation and explanations
2. **Test in VS Code**: Compile, debug, and run tests
3. **Auto-reload**: Functions runtime automatically reloads on file changes

#### Common Development Tasks

**Adding New Functions:**
```bash
func new --name NewHandler --template "HTTP trigger" --authlevel function
```

**Adding New Models:**
1. Create new `.cs` file in `Models/` folder
2. Follow existing naming convention (PascalCase)
3. Add appropriate properties

**Adding New Services:**
1. Create interface in `Services/` folder
2. Create implementation class
3. Register in `Program.cs` dependency injection

**Updating Dependencies:**
```bash
# Add NuGet package
dotnet add package PackageName

# Restore packages
dotnet restore
```

### 6. Project Structure

```
quote-azure-backend/
├── Models/                    # Data models
│   ├── Quote.cs
│   ├── UserLike.cs
│   └── UserView.cs
├── Services/                  # Business logic
│   ├── QuoteService.cs
│   └── AuthService.cs
├── QuoteHandler.cs           # HTTP trigger functions
├── AdminHandler.cs           # Admin functions
├── Program.cs                # Dependency injection setup
├── host.json                 # Functions configuration
├── local.settings.json       # ⚠️ Local settings (NEVER commit)
├── quote-azure-backend.csproj # Project file
└── Properties/
    └── launchSettings.json   # Debug configuration
```

### 7. Common Development Issues

#### Port Conflicts
If port 7071 is in use:
```bash
# Kill processes using the port
lsof -ti:7071 | xargs kill -9

# Or change port in local.settings.json
```

#### Build Errors
```bash
# Clean and rebuild
dotnet clean
dotnet build

# Check for missing packages
dotnet restore
```

#### Functions Runtime Issues
```bash
# Restart Functions runtime
# Stop with Ctrl+C, then:
func start
```

#### Dependency Injection Issues
- Ensure services are registered in `Program.cs`
- Check interface implementations match signatures
- Verify namespace references

### 8. Best Practices

#### Code Organization
- Keep models simple (POCOs)
- Separate business logic into services
- Use dependency injection for testability
- Follow C# naming conventions

#### Error Handling
- Use try-catch blocks in function handlers
- Log errors with appropriate context
- Return appropriate HTTP status codes
- Validate input parameters

#### Performance
- Use async/await for I/O operations
- Avoid blocking calls in functions
- Consider cold start optimization
- Monitor function execution time

#### Security
- Never hardcode secrets in code
- Use environment variables for configuration
- Validate all user inputs
- Implement proper authentication

### 9. Testing Strategy

#### Unit Testing
```bash
# Create test project
dotnet new xunit -n QuoteBackend.Tests

# Add reference to main project
dotnet add QuoteBackend.Tests reference quote-azure-backend.csproj
```

#### Integration Testing
- Test HTTP endpoints directly
- Mock external dependencies
- Test error scenarios
- Validate response formats

#### Manual Testing
- Use curl or Postman for API testing
- Test all HTTP methods and status codes
- Verify error handling
- Check logging output

### 10. Deployment Preparation

#### Before Deploying
1. Test all functionality locally
2. Update any hardcoded values
3. Review error handling
4. Check performance
5. Verify logging works

#### Configuration for Production
- Remove any mock data/services
- Update connection strings for Azure resources
- Configure proper authentication
- Set up monitoring and logging

## Troubleshooting Quick Reference

| Issue | Solution |
|-------|----------|
| Functions won't start | Check .NET SDK version, run `dotnet --version` |
| Build errors | Run `dotnet clean && dotnet restore && dotnet build` |
| Port 7071 in use | Kill process or change port |
| Missing dependencies | Run `dotnet restore` |
| Configuration errors | Check local.settings.json format |
| Debugging not working | Verify VS Code C# Dev Kit installation |

## Next Steps

When development is complete:
1. Test thoroughly in local environment
2. Update Azure configuration
3. Deploy using Terraform
4. Monitor in Azure Portal
5. Set up CI/CD pipeline

---

**Remember**: The dual-IDE workflow (VS Code + Windsurf) gives you the best of both worlds - full C# development capabilities in VS Code and AI-powered assistance in Windsurf.
