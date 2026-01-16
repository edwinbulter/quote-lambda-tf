using Microsoft.Azure.Functions.Worker.Configuration;
using Microsoft.Azure.Functions.Worker;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Configuration;
using QuoteAzureBackend.Handlers;
using QuoteAzureBackend.Services;
using QuoteAzureBackend.Data;
using QuoteAzureBackend.Middleware;
using Microsoft.IdentityModel.Tokens;
using Azure.Data.Tables;
using System.Text;

var host = new HostBuilder()
    .ConfigureFunctionsWorkerDefaults()
    .ConfigureServices(services => {
        services.AddApplicationInsightsTelemetryWorkerService();
        
        // Register HttpClient
        services.AddHttpClient();
        
        // Register Table Storage client
        services.AddSingleton(sp => {
            var configuration = sp.GetRequiredService<IConfiguration>();
            var connectionString = configuration["TableStorageConnectionString"];
            return new TableServiceClient(connectionString);
        });
        
        // Register repositories
        services.AddSingleton<IQuoteRepository, QuoteRepository>();
        services.AddSingleton<IUserActivityRepository, UserActivityRepository>();
        services.AddSingleton<IUserRoleRepository, UserRoleRepository>();
        
        // Register services
        services.AddSingleton<IQuoteService, QuoteService>();
        services.AddSingleton<IZenQuotesService, ZenQuotesService>();
        services.AddSingleton<IUserActivityService, UserActivityService>();
        services.AddSingleton<IQuoteManagementService, QuoteManagementService>();
        
        // Register authentication services
        services.AddSingleton<IAuthenticationService, AuthenticationService>();
        services.AddSingleton<JwtAuthenticationMiddleware>();
        
        // Register admin services
        services.AddSingleton<IAdminService, AdminService>();
        
        // Add logging
        services.AddLogging();
    })
    .Build();

host.Run();
