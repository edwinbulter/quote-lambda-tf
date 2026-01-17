using System.ComponentModel.DataAnnotations;

namespace QuoteAzureBackend.Models.Auth
{
    public class UpdateRoleRequest
    {
        [Required(ErrorMessage = "User ID is required")]
        public string UserId { get; set; } = string.Empty;
        
        [Required(ErrorMessage = "Role is required")]
        public string NewRole { get; set; } = string.Empty;
    }
}
