using System.ComponentModel.DataAnnotations;

namespace QuoteAzureBackend.Models
{
    public class UserFavorite
    {
        [Required]
        public string UserId { get; set; } = string.Empty;
        
        [Required]
        public int QuoteId { get; set; }
        
        public DateTime AddedAt { get; set; } = DateTime.UtcNow;
        
        public string PartitionKey => UserId;
        public string RowKey => $"favorite_{QuoteId}";
    }

    public class UserViewHistory
    {
        [Required]
        public string UserId { get; set; } = string.Empty;
        
        [Required]
        public int QuoteId { get; set; }
        
        public DateTime ViewedAt { get; set; } = DateTime.UtcNow;
        
        public string PartitionKey => UserId;
        public string RowKey => $"view_{QuoteId}_{ViewedAt:yyyyMMddHHmmss}";
    }

    public class UserPreferences
    {
        [Required]
        public string UserId { get; set; } = string.Empty;
        
        public string PreferredCategory { get; set; } = string.Empty;
        
        public int QuotesPerPage { get; set; } = 10;
        
        public bool EnableNotifications { get; set; } = true;
        
        public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
        
        public string PartitionKey => UserId;
        public string RowKey => "preferences";
    }
}
