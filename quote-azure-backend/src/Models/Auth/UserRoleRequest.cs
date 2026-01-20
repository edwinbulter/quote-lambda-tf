using System.ComponentModel.DataAnnotations;

namespace QuoteAzureBackend.Models.Auth
{
    public class UserRoleRequest
    {
        [Required(ErrorMessage = "Username is required")]
        public string Username { get; set; } = string.Empty;
        
        [Required(ErrorMessage = "Role is required")]
        public string Role { get; set; } = string.Empty;
    }
}
