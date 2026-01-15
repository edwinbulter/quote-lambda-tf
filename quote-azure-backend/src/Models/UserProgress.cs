namespace QuoteAzureBackend.Models
{
    public class UserProgress
    {
        public string Username { get; set; } = string.Empty;
        public int LastQuoteId { get; set; }
        public DateTime UpdatedAt { get; set; }
    }
}
