namespace QuoteAzureBackend.Models
{
    public class UserView
    {
        public string UserId { get; set; } = string.Empty;
        public int QuoteId { get; set; }
        public DateTime ViewedAt { get; set; }
    }
}
