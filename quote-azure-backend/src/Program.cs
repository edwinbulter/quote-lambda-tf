using Microsoft.Azure.Functions.Worker.Configuration;
using Microsoft.Azure.Functions.Worker;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using QuoteAzureBackend.Handlers;
using QuoteAzureBackend.Services;
using QuoteAzureBackend.Data;
using QuoteAzureBackend.Middleware;
using Microsoft.IdentityModel.Tokens;
using System.Text;

var host = new HostBuilder()
    .ConfigureFunctionsWorkerDefaults()
    .ConfigureServices(services => {
        services.AddApplicationInsightsTelemetryWorkerService();
        
        // Register HttpClient
        services.AddHttpClient();
        
        // Register repositories
        services.AddSingleton<IQuoteRepository, QuoteRepository>();
        services.AddSingleton<IUserActivityRepository, UserActivityRepository>();
        
        // Register services
        services.AddSingleton<IQuoteService, QuoteService>();
        services.AddSingleton<IZenQuotesService, ZenQuotesService>();
        services.AddSingleton<IUserActivityService, UserActivityService>();
        
        // Register authentication services
        services.AddSingleton<IAuthenticationService, AuthenticationService>();
        services.AddSingleton<JwtAuthenticationMiddleware>();
        
        // Add logging
        services.AddLogging();
    })
    .Build();

host.Run();
