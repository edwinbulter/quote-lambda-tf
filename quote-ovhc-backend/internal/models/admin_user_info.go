package models

type AdminUserInfo struct {
	Username             string   `json:"username"`
	Email                string   `json:"email"`
	Roles                []string `json:"roles"`
	Enabled              bool     `json:"enabled"`
	UserStatus           string   `json:"userStatus"`
	UserCreateDate       string   `json:"userCreateDate"`
	UserLastModifiedDate string   `json:"userLastModifiedDate"`
}

func NewAdminUserInfo(user *User) *AdminUserInfo {
	status := "INACTIVE"
	if user.IsActive {
		status = "ACTIVE"
	}

	// Convert roles to uppercase to match Azure implementation
	roles := make([]string, len(user.Roles))
	for i, role := range user.Roles {
		roles[i] = toUpper(role)
	}

	return &AdminUserInfo{
		Username:             user.Username,
		Email:                user.Email,
		Roles:                roles,
		Enabled:              user.IsActive,
		UserStatus:           status,
		UserCreateDate:       user.CreatedAt.UTC().Format("2006-01-02T15:04:05Z"),
		UserLastModifiedDate: user.UpdatedAt.UTC().Format("2006-01-02T15:04:05Z"),
	}
}

func toUpper(s string) string {
	if len(s) == 0 {
		return s
	}
	// Simple uppercase conversion for ASCII letters
	result := make([]byte, len(s))
	for i, b := range []byte(s) {
		if b >= 'a' && b <= 'z' {
			result[i] = b - 32
		} else {
			result[i] = b
		}
	}
	return string(result)
}
