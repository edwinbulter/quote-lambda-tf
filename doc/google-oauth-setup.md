# Google OAuth Setup Guide

This guide walks you through adding Google OAuth authentication to the Quote App using AWS Cognito.

## Overview

Google OAuth is natively supported by AWS Cognito and works seamlessly with Amplify. This setup allows users to sign in using their Google accounts.

**Estimated Time:** 1-2 hours

---

## Step 1: Create Google OAuth Credentials

### 1.1 Go to Google Cloud Console

1. Navigate to [Google Cloud Console](https://console.cloud.google.com/)
2. Sign in with your Google account
3. Create a new project or select an existing one:
   - Click the project dropdown at the top
   - Click "NEW PROJECT"
   - Enter project name: `Quote App` (or similar)
   - Click "CREATE"

### 1.2 Enable Google+ API

1. In the left sidebar, go to **APIs & Services → Library**
2. Search for "Google+ API"
3. Click on it and press **ENABLE**

### 1.3 Create OAuth 2.0 Credentials

1. Go to **APIs & Services → Credentials**
2. Click **+ CREATE CREDENTIALS** → **OAuth client ID**
3. If prompted, click **CONFIGURE CONSENT SCREEN** first:
   - Choose **External** user type
   - Click **CREATE**
   - Fill in the form:
     - **App name:** Quote App
     - **User support email:** your-email@example.com
     - **Developer contact:** your-email@example.com
   - Click **SAVE AND CONTINUE**
   - Skip optional scopes, click **SAVE AND CONTINUE**
   - Review and click **BACK TO DASHBOARD**

4. Now create the OAuth client ID:
   - Go back to **Credentials**
   - Click **+ CREATE CREDENTIALS** → **OAuth client ID**
   - **Application type:** Web application
   - **Name:** Quote App OAuth Client
   - **Authorized JavaScript origins:** Add:
     ```
     http://localhost:5173
     https://your-production-domain.com
     ```
   - **Authorized redirect URIs:** Add:
     ```
     http://localhost:5173/
     https://your-production-domain.com/
     ```
   - Click **CREATE**

5. **Save your credentials:**
   - Copy the **Client ID** (looks like: `123456789-abc123def456.apps.googleusercontent.com`)
   - Copy the **Client Secret** (keep this secret!)
   - Click **OK**

---

## Step 2: Configure Terraform

### 2.1 Add Google OAuth Variables

Edit `quote-lambda-tf-backend/infrastructure/variables.tf` and add:

```hcl
# Google OAuth Configuration
variable "google_oauth_client_id" {
  type        = string
  description = "Google OAuth Client ID for Cognito authentication"
  sensitive   = true
}

variable "google_oauth_client_secret" {
  type        = string
  description = "Google OAuth Client Secret for Cognito authentication"
  sensitive   = true
}
```

### 2.2 Add Google Identity Provider to Cognito

Edit `quote-lambda-tf-backend/infrastructure/cognito.tf` and add this resource after the User Pool Domain:

```hcl
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
    username = "sub"
  }
}
```

### 2.3 Update App Client to Support Google

Edit the `aws_cognito_user_pool_client` resource in `cognito.tf`:

**Change this line:**
```hcl
supported_identity_providers = ["COGNITO"]
```

**To this:**
```hcl
supported_identity_providers = ["COGNITO", "Google"]
```

### 2.4 Add Google Credentials to dev.tfvars

Edit `quote-lambda-tf-backend/infrastructure/dev.tfvars` and add:

```hcl
# Google OAuth Configuration
google_oauth_client_id     = "YOUR_GOOGLE_CLIENT_ID_HERE"
google_oauth_client_secret = "YOUR_GOOGLE_CLIENT_SECRET_HERE"
```

Replace with your actual credentials from Step 1.

### 2.5 Apply Terraform

```bash
cd quote-lambda-tf-backend/infrastructure
terraform workspace select dev
terraform apply -var-file="dev.tfvars"
```

Review the changes and type `yes` to confirm.

---

## Step 3: Update Frontend Configuration

### 3.1 Update Amplify Config

Edit `quote-lambda-tf-frontend/src/config/aws-exports.ts`:

```typescript
import { ResourcesConfig } from 'aws-amplify';

const awsConfig: ResourcesConfig = {
    Auth: {
        Cognito: {
            userPoolId: import.meta.env.VITE_COGNITO_USER_POOL_ID,
            userPoolClientId: import.meta.env.VITE_COGNITO_CLIENT_ID,
            loginWith: {
                oauth: {
                    domain: import.meta.env.VITE_COGNITO_DOMAIN,
                    scopes: ['email', 'openid', 'profile'],
                    redirectSignIn: [window.location.origin + '/'],
                    redirectSignOut: [window.location.origin + '/logout'],
                    responseType: 'code',
                    providers: [{ custom: 'Google' }],
                },
            },
        },
    },
};

export default awsConfig;
```

### 3.2 Update Login Component

Edit `quote-lambda-tf-frontend/src/components/Login.tsx` and add the Google sign-in button:

```typescript
import React, { useState } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { signInWithRedirect } from 'aws-amplify/auth';
import './Login.scss';

interface LoginProps {
    onCancel?: () => void;
}

export const Login: React.FC<LoginProps> = ({ onCancel }) => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [isSignUp, setIsSignUp] = useState(false);
    const [needsConfirmation, setNeedsConfirmation] = useState(false);
    const [confirmationCode, setConfirmationCode] = useState('');

    const { signIn, signUp, confirmSignUp } = useAuth();

    const handleSignIn = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');

        try {
            await signIn(email, password);
        } catch (err: any) {
            setError(err.message || 'Failed to sign in');
        }
    };

    const handleSignUp = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');

        try {
            await signUp(email, password, email);
            setNeedsConfirmation(true);
        } catch (err: any) {
            setError(err.message || 'Failed to sign up');
        }
    };

    const handleConfirmSignUp = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');

        try {
            await confirmSignUp(email, confirmationCode);
            setNeedsConfirmation(false);
            setIsSignUp(false);
            alert('Account confirmed! You can now sign in.');
        } catch (err: any) {
            setError(err.message || 'Failed to confirm account');
        }
    };

    const handleGoogleSignIn = async () => {
        try {
            console.log('Initiating Google OAuth sign-in...');
            await signInWithRedirect({
                provider: { custom: 'Google' }
            });
        } catch (err: any) {
            console.error('Google sign-in error:', err);
            setError(err.message || 'Failed to sign in with Google');
        }
    };

    if (needsConfirmation) {
        return (
            <div className="auth-container">
                <h2>Confirm Your Account</h2>
                <p>Please enter the confirmation code sent to your email.</p>
                <form onSubmit={handleConfirmSignUp}>
                    <input
                        type="text"
                        placeholder="Confirmation Code"
                        value={confirmationCode}
                        onChange={(e) => setConfirmationCode(e.target.value)}
                        required
                    />
                    {error && <p className="error">{error}</p>}
                    <button type="submit">Confirm</button>
                </form>
                {onCancel && (
                    <button className="cancel-button" onClick={onCancel}>
                        Cancel
                    </button>
                )}
            </div>
        );
    }

    return (
        <div className="auth-container">
            <h2>{isSignUp ? 'Sign Up' : 'Sign In'}</h2>
            <form onSubmit={isSignUp ? handleSignUp : handleSignIn}>
                <input
                    type="email"
                    placeholder="Email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                />
                <input
                    type="password"
                    placeholder="Password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                />
                {error && <p className="error">{error}</p>}
                <button type="submit">
                    {isSignUp ? 'Sign Up' : 'Sign In'}
                </button>
            </form>
            
            {!isSignUp && (
                <>
                    <div className="divider">
                        <span>OR</span>
                    </div>
                    <button className="google-button" onClick={handleGoogleSignIn}>
                        <svg className="google-icon" viewBox="0 0 24 24" width="20" height="20">
                            <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                            <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                            <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
                            <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
                        </svg>
                        Sign in with Google
                    </button>
                </>
            )}
            
            <button onClick={() => setIsSignUp(!isSignUp)}>
                {isSignUp ? 'Already have an account? Sign In' : "Don't have an account? Sign Up"}
            </button>
            {onCancel && (
                <button className="cancel-button" onClick={onCancel}>
                    Cancel
                </button>
            )}
        </div>
    );
};
```

### 3.3 Add Google Button Styling

Add this to `quote-lambda-tf-frontend/src/components/Login.scss`:

```scss
.google-button {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    width: 100%;
    padding: 12px;
    margin: 10px 0;
    background-color: #ffffff;
    border: 1px solid #dadce0;
    border-radius: 4px;
    font-size: 16px;
    font-weight: 500;
    color: #3c4043;
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover {
        background-color: #f8f9fa;
        border-color: #d2d3d4;
        box-shadow: 0 1px 1px rgba(0, 0, 0, 0.1);
    }

    &:active {
        background-color: #f1f3f4;
    }

    .google-icon {
        width: 20px;
        height: 20px;
    }
}

.divider {
    display: flex;
    align-items: center;
    margin: 20px 0;
    
    &::before,
    &::after {
        content: '';
        flex: 1;
        height: 1px;
        background-color: #ccc;
    }

    span {
        padding: 0 10px;
        color: #666;
        font-size: 14px;
    }
}
```

---

## Step 4: Add Cognito Callback URL to Google OAuth App

**IMPORTANT:** This step is required for Google OAuth to work.

1. **Get your Cognito domain:**
   ```bash
   cd quote-lambda-tf-backend/infrastructure
   terraform output cognito_domain
   ```
   This will output: `quote-lambda-tf-backend-dev.auth.eu-central-1.amazoncognito.com`

2. **Go to Google Cloud Console:**
   - Navigate to [console.cloud.google.com](https://console.cloud.google.com/)
   - Select your project

3. **Go to APIs & Services → Credentials**

4. **Click on your OAuth 2.0 Client ID** to edit it

5. **Under "Authorized redirect URIs", add:**
   ```
   https://quote-lambda-tf-backend-dev.auth.eu-central-1.amazoncognito.com/oauth2/idpresponse
   ```

6. **Your complete redirect URIs should now be:**
   ```
   http://localhost:5173/
   https://quote-lambda-tf-backend-dev.auth.eu-central-1.amazoncognito.com/oauth2/idpresponse
   https://your-production-domain.com/ (if applicable)
   ```

7. **Click "SAVE"**

**Important:** The redirect URI format is critical:
- ✅ **Correct:** `https://quote-lambda-tf-backend-dev.auth.eu-central-1.amazoncognito.com/oauth2/idpresponse`
- ❌ **Wrong:** `https://quote-lambda-tf-backend-dev.auth.eu-central-1.amazoncognito.com/oauth2/idpresponse/` (trailing slash)
- ❌ **Wrong:** `http://` (must be `https://`)

---

## Step 5: Verify Cognito Configuration

1. Go to **AWS Console → Cognito → User Pools**
2. Select your user pool (`quote-lambda-tf-backend-user-pool-dev`)
3. Go to **Sign-in experience → Federated identity provider sign-in**
4. Verify **Google** is listed with:
   - Client ID: Your Google Client ID
   - Client Secret: Your Google Client Secret
   - Scopes: `email openid profile`

---

## Step 6: Verify Attribute Mapping

The Google Identity Provider attribute mapping should be configured to map Google's claims to Cognito user attributes.

Verify the `attribute_mapping` in the Google Identity Provider resource in `cognito.tf`:

```hcl
  attribute_mapping = {
    email    = "email"
    name     = "name"
    username = "sub"
  }
```

**Note:** AWS Cognito requires `username` to map to `sub` (the unique Google user ID). The username will appear as `Google_<google-user-id>` in Cognito, but the user's email will be available as the email attribute.

Then apply Terraform:

```bash
cd quote-lambda-tf-backend/infrastructure
terraform workspace select dev
terraform apply -var-file="dev.tfvars"
```

---

## Step 7: Test Google Sign-In

1. **Clear browser cache:**
   - Press F12 → Application tab → Clear storage
   - Or use incognito mode

2. **Restart the frontend dev server:**
   ```bash
   cd quote-lambda-tf-frontend
   npm run dev
   ```

3. **Open the app:**
   ```
   http://localhost:5173
   ```

4. **Click "Sign In"**

5. **Click "Sign in with Google"**

6. **Expected flow:**
   - Redirects to Google login page
   - Sign in with your Google account
   - Authorize the app
   - Redirected back to your app
   - User signed in and created in Cognito

7. **Verify in Cognito:**
   - Go to **AWS Console → Cognito → User Pools → Users**
   - You should see a new user created
   - Username will be something like `Google_109747799293641433374` (Google user ID)
   - Email attribute should be your Google email

8. **Verify in your app:**
   - Click "View Profile"
   - You should see:
     - **Username:** Google_<google-user-id>
     - **User ID:** UUID
     - **Roles:** (empty - no automatic group assignment)

---

## Troubleshooting

### Error: "redirect_uri_mismatch" - Error 400

**Full error:**
```
You can't sign in to this app because it doesn't comply with Google's OAuth 2.0 policy.
If you're the app developer, register the redirect URI in the Google Cloud Console.
Request details: redirect_uri=https://quote-lambda-tf-backend-dev.auth.eu-central-1.amazoncognito.com/oauth2/idpresponse
```

**Solution:**
1. Get your Cognito domain:
   ```bash
   cd quote-lambda-tf-backend/infrastructure
   terraform output cognito_domain
   ```

2. Go to **Google Cloud Console → APIs & Services → Credentials**

3. Click on your OAuth 2.0 Client ID to edit it

4. Under "Authorized redirect URIs", add:
   ```
   https://quote-lambda-tf-backend-dev.auth.eu-central-1.amazoncognito.com/oauth2/idpresponse
   ```

5. Click **SAVE**

6. Clear browser cache and try again

### Error: "Invalid client_id or client_secret"

**Solution:**
- Verify credentials in `dev.tfvars` match Google Cloud Console
- Regenerate credentials if needed
- Run `terraform apply -var-file="dev.tfvars"` again

### User not created in Cognito

**Solution:**
- Check browser console for errors
- Verify Cognito domain in `.env.development` is correct
- Check that Google provider is enabled in App Client settings

### Redirect loop or blank page

**Solution:**
- Clear browser cache and cookies
- Restart dev server
- Verify `.env.development` has correct Cognito domain

### Error: "Invalid attribute mapping. email cannot be mapped to username"

**Cause:** AWS Cognito doesn't allow mapping `email` to `username` for Google OAuth.

**Solution:**
- Verify attribute mapping in `cognito.tf` has `username = "sub"` (not `username = "email"`)
- The username will be the Google user ID (e.g., `Google_109747799293641433374`)
- The email is stored separately as the email attribute
- Apply Terraform: `terraform apply -var-file="dev.tfvars"`

---

## Manual User Group Assignment

Users are **not automatically assigned to groups** when they sign in with Google or register with email. To assign a user to the `USER` group:

### Via AWS Console

1. Go to **AWS Console → Cognito → User Pools**
2. Select your user pool
3. Go to **Users** tab
4. Click on the user
5. Scroll to **Group memberships**
6. Click **Add user to groups**
7. Select `USER` group
8. Click **Add**

### Via AWS CLI

```bash
aws cognito-idp admin-add-user-to-group \
  --user-pool-id eu-central-1_XrKxJWy5u \
  --username your-email@gmail.com \
  --group-name USER \
  --region eu-central-1
```

### Via Terraform

If you want to automatically assign users to groups, you can add a post-confirmation or post-authentication Lambda trigger. See the [Google OAuth with AWS Cognito - Incompatibility Issue](./github-oauth-setup.md) document for an example Lambda implementation.

---

## Production Setup

For production, repeat the same steps but:

1. **Create separate Google OAuth credentials** with production domain
2. **Update `prod.tfvars`** with production credentials
3. **Update callback URLs** in Google Cloud Console to production domain
4. **Deploy frontend** to production domain
5. **Apply Terraform** with production workspace:
   ```bash
   terraform workspace select default
   terraform apply -var-file="prod.tfvars"
   ```

---

## Security Best Practices

1. **Never commit credentials** to version control
   - `dev.tfvars` and `prod.tfvars` are in `.gitignore`

2. **Use separate credentials** for each environment
   - Development: localhost
   - Production: your domain

3. **Rotate secrets regularly**
   - Regenerate Google Client Secret periodically
   - Update Terraform variables and apply

4. **Restrict OAuth scopes**
   - Only request: `email`, `openid`, `profile`
   - Don't request unnecessary permissions

---

## Additional Resources

- [Google OAuth 2.0 Documentation](https://developers.google.com/identity/protocols/oauth2)
- [AWS Cognito Social Sign-In](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-social-idp.html)
- [Amplify Social Sign-In](https://docs.amplify.aws/react/build-a-backend/auth/add-social-provider/)
- [Google Cloud Console](https://console.cloud.google.com/)
