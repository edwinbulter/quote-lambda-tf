using Microsoft.Azure.Functions.Worker;
using Microsoft.Azure.Functions.Worker.Http;
using Microsoft.Extensions.Logging;
using System.Net;

namespace QuoteAzureBackend;

public class AdminHandler
{
    private readonly ILogger<AdminHandler> _logger;

    public AdminHandler(ILogger<AdminHandler> logger)
    {
        _logger = logger;
    }

    [Function("AdminHealth")]
    public HttpResponseData Run([HttpTrigger(AuthorizationLevel.Function, "get", "post", Route = "system/status")] HttpRequestData req)
    {
        _logger.LogInformation("C# HTTP trigger function processed a request.");
        var response = req.CreateResponse(HttpStatusCode.OK);
        response.WriteString("Welcome to Azure Functions!");
        return response;
    }
}
