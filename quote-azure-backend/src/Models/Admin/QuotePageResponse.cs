namespace QuoteAzureBackend.Models.Admin
{
    public class QuotePageResponse
    {
        public List<QuoteWithLikeCount> Quotes { get; set; } = new List<QuoteWithLikeCount>();
        public int TotalCount { get; set; }
        public int Page { get; set; }
        public int PageSize { get; set; }
        public int TotalPages { get; set; }
    }
}
