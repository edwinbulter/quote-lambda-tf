namespace QuoteAzureBackend.Models.Admin
{
    public class QuoteWithLikeCount
    {
        public int Id { get; set; }
        public string QuoteText { get; set; } = string.Empty;
        public string Author { get; set; } = string.Empty;
        public int LikeCount { get; set; }
        public DateTime CreatedAt { get; set; }
    }
}
