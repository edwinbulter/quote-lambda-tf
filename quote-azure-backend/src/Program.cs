using Microsoft.Azure.Functions.Worker.Configuration;
using Microsoft.Azure.Functions.Worker;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using QuoteAzureBackend.Services;
using QuoteAzureBackend.Data;

var host = new HostBuilder()
    .ConfigureFunctionsWorkerDefaults()
    .ConfigureServices(services => {
        services.AddHttpClient();
        services.AddSingleton<IQuoteRepository, QuoteRepository>();
        services.AddSingleton<IUserActivityRepository, UserActivityRepository>();
        services.AddSingleton<IQuoteService, QuoteService>();
        services.AddSingleton<IZenQuotesService, ZenQuotesService>();
        
        // Add logging
        services.AddLogging();
    })
    .Build();

host.Run();
