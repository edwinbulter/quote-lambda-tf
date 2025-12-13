# Cognito User Pool
resource "aws_cognito_user_pool" "quote_app" {
  name = "${var.project_name}-user-pool-${local.environment}"
  
  # Password policy
  password_policy {
    minimum_length                   = 8
    require_lowercase               = true
    require_numbers                 = true
    require_symbols                 = true
    require_uppercase               = true
    temporary_password_validity_days = 7
  }

  # User attributes
  schema {
    name                = "email"
    attribute_data_type = "String"
    required           = true
    mutable           = true
  }

  schema {
    name                = "custom:roles"
    attribute_data_type = "String"
    mutable            = true
    string_attribute_constraints {
      min_length = 0
      max_length = 2048
    }
  }

  # Email configuration
  auto_verified_attributes = ["email"]
  
  # MFA configuration (optional)
  mfa_configuration = "OFF"

  # Verification and account recovery
  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }

  # Email settings
  email_configuration {
    email_sending_account = "COGNITO_DEFAULT"
  }

  tags = {
    Environment = local.environment
    Project     = var.project_name
  }
}

# User Pool Client
resource "aws_cognito_user_pool_client" "web_client" {
  name = "${var.project_name}-web-client-${local.environment}"
  
  user_pool_id = aws_cognito_user_pool.quote_app.id
  
  # Supported identity providers
  supported_identity_providers = ["COGNITO", "Google"]
  
  # Authentication flows
  explicit_auth_flows = [
    "ALLOW_USER_SRP_AUTH",        # Required for Amplify v6 (secure password auth)
    "ALLOW_USER_PASSWORD_AUTH",   # Alternative auth flow
    "ALLOW_REFRESH_TOKEN_AUTH"    # Required for token refresh
  ]
  
  # OAuth settings
  allowed_oauth_flows = ["code"]
  allowed_oauth_scopes = ["email", "openid", "profile"]
  allowed_oauth_flows_user_pool_client = true
  
  # Callback URLs (update with your frontend URLs)
  callback_urls = ["http://localhost:5173/"]
  logout_urls   = ["http://localhost:5173/logout"]
  
  # Token configuration using duration format
  id_token_validity      = 1  # 1 hour (valid range: 1-24 hours)
  access_token_validity  = 1  # 1 hour (valid range: 1-24 hours)
  refresh_token_validity = 720 # 30 days (valid range: 1-8760 hours)
  
  # Security
  prevent_user_existence_errors = "ENABLED"
  enable_token_revocation = true
}

# User Pool Domain
resource "aws_cognito_user_pool_domain" "main" {
  domain       = "${var.project_name}-${local.environment}"
  user_pool_id = aws_cognito_user_pool.quote_app.id
}

# Google Identity Provider
resource "aws_cognito_identity_provider" "google" {
  user_pool_id  = aws_cognito_user_pool.quote_app.id
  provider_name = "Google"
  provider_type = "Google"

  provider_details = {
    client_id     = var.google_oauth_client_id
    client_secret = var.google_oauth_client_secret
    authorize_scopes = "email openid profile"
  }

  attribute_mapping = {
    email    = "email"
    name     = "name"
    username = "email"
  }
}

# Cognito User Groups for role-based access control
resource "aws_cognito_user_group" "users" {
  name         = "USER"
  user_pool_id = aws_cognito_user_pool.quote_app.id
  description  = "Standard users who can like quotes"
  precedence   = 10
}

resource "aws_cognito_user_group" "admins" {
  name         = "ADMIN"
  user_pool_id = aws_cognito_user_pool.quote_app.id
  description  = "Administrators with full access"
  precedence   = 1
}

# Outputs
output "user_pool_id" {
  value = aws_cognito_user_pool.quote_app.id
}

output "user_pool_arn" {
  value = aws_cognito_user_pool.quote_app.arn
}

output "user_pool_client_id" {
  value = aws_cognito_user_pool_client.web_client.id
}

output "user_pool_endpoint" {
  value = "${aws_cognito_user_pool.quote_app.endpoint}"
}

output "cognito_domain" {
  value = "${aws_cognito_user_pool_domain.main.domain}.auth.${var.aws_region}.amazoncognito.com"
}

# IAM Role for Cognito to access other AWS services (if needed)
resource "aws_iam_role" "cognito_authenticated_role" {
  name = "${var.project_name}-cognito-authenticated-role-${local.environment}"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Federated = "cognito-identity.amazonaws.com"
        }
        Action = "sts:AssumeRoleWithWebIdentity"
        Condition = {
          StringEquals = {
            "cognito-identity.amazonaws.com:aud" = aws_cognito_identity_pool.main.id
          }
          "ForAnyValue:StringLike" = {
            "cognito-identity.amazonaws.com:amr" = "authenticated"
          }
        }
      }
    ]
  })
}

# Identity Pool (optional, for AWS service access from frontend)
resource "aws_cognito_identity_pool" "main" {
  identity_pool_name               = "${var.project_name}-identity-pool-${local.environment}"
  allow_unauthenticated_identities = false
  
  cognito_identity_providers {
    client_id               = aws_cognito_user_pool_client.web_client.id
    provider_name           = aws_cognito_user_pool.quote_app.endpoint
    server_side_token_check = false
  }
}

# Attach policies to the authenticated role (example: S3 access)
resource "aws_iam_role_policy_attachment" "cognito_authenticated_policy" {
  role       = aws_iam_role.cognito_authenticated_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess"  # Example policy
}

# Output the identity pool ID
output "identity_pool_id" {
  value = aws_cognito_identity_pool.main.id
}
