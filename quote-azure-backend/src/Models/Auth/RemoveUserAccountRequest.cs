using System.ComponentModel.DataAnnotations;

namespace QuoteAzureBackend.Models.Auth
{
    public class RemoveUserAccountRequest
    {
        [Required(ErrorMessage = "Username is required")]
        public string Username { get; set; } = string.Empty;
    }
}
