

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

