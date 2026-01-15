using Azure;
using Azure.Data.Tables;

namespace QuoteAzureBackend.Data.Entities
{
    public class UserViewHistoryEntity : ITableEntity
    {
        public string PartitionKey { get; set; } = string.Empty;
        public string RowKey { get; set; } = string.Empty;
        public DateTimeOffset? Timestamp { get; set; }
        public ETag ETag { get; set; }

        public string UserId { get; set; } = string.Empty;
        public int QuoteId { get; set; }
        public DateTime ViewedAt { get; set; }

        public UserViewHistoryEntity() { }

        public UserViewHistoryEntity(string userId, int quoteId)
        {
            PartitionKey = userId;
            RowKey = $"{userId}_{quoteId}_{DateTime.UtcNow:yyyyMMddHHmmss}";
            UserId = userId;
            QuoteId = quoteId;
            ViewedAt = DateTime.UtcNow;
        }
    }
}
