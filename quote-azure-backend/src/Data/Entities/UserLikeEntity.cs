using Azure;
using Azure.Data.Tables;

namespace QuoteAzureBackend.Data.Entities
{
    public class UserLikeEntity : ITableEntity
    {
        public string PartitionKey { get; set; } = string.Empty;
        public string RowKey { get; set; } = string.Empty;
        public DateTimeOffset? Timestamp { get; set; }
        public ETag ETag { get; set; }

        public string UserId { get; set; } = string.Empty;
        public int QuoteId { get; set; }
        public int Order { get; set; }
        public DateTime LikedAt { get; set; }

        public UserLikeEntity() { }

        public UserLikeEntity(string userId, int quoteId)
        {
            PartitionKey = userId;
            RowKey = $"{userId}_{quoteId}";
            UserId = userId;
            QuoteId = quoteId;
            LikedAt = DateTime.UtcNow;
        }
    }
}
