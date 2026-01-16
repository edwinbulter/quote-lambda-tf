namespace QuoteAzureBackend.Models.Admin
{
    public class QuoteAddResponse
    {
        public int QuotesAdded { get; set; }
        public int TotalQuotes { get; set; }
        public string Message { get; set; } = string.Empty;
    }
}
