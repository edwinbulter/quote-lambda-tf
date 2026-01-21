## Publish

```shell
cd ~/IdeaProjects/quote-lambda-tf/quote-azure-backend/src && func azure functionapp publish quote-backend-function
```

## Restart function app
```shell
az webapp restart --resource-group quote-backend-rg --name quote-backend-function
```

## Logging

View Live tail logging:
```shell
az webapp log tail --resource-group quote-backend-rg --name quote-backend-function
```

View logging in Azure portal:  
1. Navigate to Application Insights → quote-backend-ai
2. Click on Logs (under Monitoring)
3. Create a query

    **Example queries:**
    1. For specific logging:
    ```shell
    traces | where message contains "Getting all quotes"
    ```
    2. All logging:
    ```shell
    traces
    ```

Search for a trace in Azure portal:
1. Navigate to Application Insights → quote-backend-ai
2. Click on Search (under Investigate)
3. Search for "Getting all quotes"
4. Click on a search result and you can expand the trace

## View azure table storage

### In Azure Portal
Open the Storage account  
Open Storage Browser>Tables

### By Azure CLI
1. Get your-connection-string by copying the entire output of this command:
   ```shell
   az storage account show-connection-string \
     --resource-group quote-backend-rg \
     --name $(az storage account list --resource-group quote-backend-rg --query "[?contains(name, 'qbtst')].name" --output tsv) \
     --query "connectionString" \
     --output tsv
   ```
2. List tables
   ```shell
   az storage table list --account-name "qbtst*" --connection-string "your-connection-string"
   ```
3. Query table data
   ```shell
   az storage entity query --table-name quotes --account-name "qbtst*" --connection-string "your-connection-string"
   ```
   
### By Microsoft Azure Storage Explorer
Download it from https://azure.microsoft.com/en-us/products/storage/storage-explorer/  
Copy it in ~/Applications  
Request the connection string (see CLI command above)  
Use the connection option in Microsoft Azure Storage Explorer where you can connect with the connection-string  

## Setup Azure infrastructure
See README.md in the infrastructure folder


## Authentication with Azure AD

Azure AD (Windows Azure Active Directory):  
- supports only admin-managed users

Azure AD B2C:
- self-service registration
- Social login (Google, Facebook, etc.)
- Upgrade to Premium P1
- Cost: ~$6/user/month


## Implementation plan

Since Azure AD is broken:

- Start with JWT implementation
- Remove Azure AD completely
- Get a working system as soon as possible
- Add API Gateway once JWT is working so rest calls can be done without the master_key


