using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.DependencyInjection;
using QuoteAzureBackend.Services;

var host = new HostBuilder()
    .ConfigureFunctionsWorkerDefaults()
    .ConfigureServices(services => {
        services.AddScoped<IQuoteService, QuoteService>();
        services.AddScoped<IAuthService, AuthService>();
    })
    .Build();

host.Run();
