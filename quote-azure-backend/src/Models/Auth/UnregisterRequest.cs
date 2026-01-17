using System.ComponentModel.DataAnnotations;

namespace QuoteAzureBackend.Models.Auth
{
    public class UnregisterRequest
    {
        [Required(ErrorMessage = "Password is required")]
        public string Password { get; set; } = string.Empty;
    }
}
