namespace QuoteAzureBackend.Models
{
    public class UserLike
    {
        public string UserId { get; set; } = string.Empty;
        public int QuoteId { get; set; }
        public int Order { get; set; }
        public DateTime LikedAt { get; set; }
    }
}
