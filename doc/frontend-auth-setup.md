# Frontend Authentication Setup Guide

## Overview

This document provides step-by-step instructions for integrating AWS Cognito authentication into the React frontend application. The backend authentication system is already complete and tested.

**Prerequisites:**
- ✅ Backend authentication completed (see [`backend-auth-setup.md`](./backend-auth-setup.md))
- ✅ Cognito User Pool created and configured
- ✅ USER and ADMIN groups created
- ✅ API Gateway configured with Authorization header support

**Estimated Time:** 4-6 hours for implementation + testing

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Phase 1: AWS Amplify Setup](#phase-1-aws-amplify-setup)
3. [Phase 2: Authentication UI](#phase-2-authentication-ui)
4. [Phase 3: Protected Routes](#phase-3-protected-routes)
5. [Phase 4: API Integration](#phase-4-api-integration)
6. [Phase 5: Role-Based UI](#phase-5-role-based-ui)
7. [Testing](#testing)
8. [Troubleshooting](#troubleshooting)

---

## Architecture Overview

### Authentication Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    Frontend Application                      │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │  1. User clicks "Sign In" or "Sign Up"            │    │
│  └────────────────────────────────────────────────────┘    │
│                           │                                  │
│                           ▼                                  │
│  ┌────────────────────────────────────────────────────┐    │
│  │  2. AWS Amplify Auth.signIn() / Auth.signUp()     │    │
│  └────────────────────────────────────────────────────┘    │
└──────────────────────┬───────────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────────────┐
│                    AWS Cognito User Pool                      │
│  - Validates credentials                                     │
│  - Returns JWT tokens (ID, Access, Refresh)                 │
│  - Includes cognito:groups claim with USER/ADMIN roles      │
└──────────────────────┬───────────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────────────┐
│                    Frontend Application                       │
│  ┌────────────────────────────────────────────────────┐     │
│  │  3. Store tokens in memory (Amplify handles this) │     │
│  └────────────────────────────────────────────────────┘     │
│                           │                                   │
│                           ▼                                   │
│  ┌────────────────────────────────────────────────────┐     │
│  │  4. Make API calls with Authorization header       │     │
│  │     Authorization: <JWT ID Token>                  │     │
│  └────────────────────────────────────────────────────┘     │
└───────────────────────────────────────────────────────────────┘
```

### Key Concepts

1. **AWS Amplify** - Simplifies Cognito integration
2. **JWT Tokens** - ID token contains user info and groups
3. **Authorization Header** - Backend validates JWT and checks groups
4. **Role-Based UI** - Show/hide features based on user's groups

---

## Phase 1: AWS Amplify Setup

### Step 1: Install Dependencies

```bash
cd quote-lambda-tf-frontend

# Install AWS Amplify
npm install aws-amplify @aws-amplify/ui-react

# Install additional dependencies for auth UI
npm install @aws-amplify/ui-react-core
```

### Step 2: Verify AWS Configuration

The Amplify configuration file `src/config/aws-exports.ts` was already created during the backend setup (see [`backend-auth-setup.md`](./backend-auth-setup.md)). Verify it exists and has the correct structure:

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
                },
            },
        },
    },
};

export default awsConfig;
```

**Note:** This uses the Amplify v6 configuration format with proper TypeScript typing (`ResourcesConfig`).

### Step 3: Verify Environment Variables

Your `.env.development` file should already have these values from the backend setup:

```bash
# Cognito Configuration
VITE_AWS_REGION=eu-central-1
VITE_COGNITO_USER_POOL_ID=eu-central-1_XrKxJWy5u
VITE_COGNITO_CLIENT_ID=7lkohh6t96igkm9q16rdchansh
VITE_COGNITO_DOMAIN=quote-lambda-tf-backend-dev.auth.eu-central-1.amazoncognito.com

# API Configuration
VITE_REACT_APP_API_BASE_URL=https://sy5vvqbh93.execute-api.eu-central-1.amazonaws.com/
```

### Step 4: Verify Amplify Initialization

The `src/main.tsx` file was already configured during the backend setup to initialize Amplify:

```typescript
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { Amplify } from 'aws-amplify';
import awsConfig from './config/aws-exports';

Amplify.configure(awsConfig);

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
```

**Note:** The configuration is imported from `./config/aws-exports` and explicitly configured using `Amplify.configure(awsConfig)` before rendering the app. This ensures authentication is ready before any components mount.

---

## Phase 2: Authentication UI

### Step 1: Create Authentication Context

Create `src/contexts/AuthContext.tsx`:

**Note:** This uses AWS Amplify v6 API. The authentication functions are imported from `'aws-amplify/auth'` instead of the old `'aws-amplify'` package.

```typescript
import React, { createContext, useContext, useEffect, useState } from 'react';
import { 
  signIn as amplifySignIn,
  signUp as amplifySignUp,
  signOut as amplifySignOut,
  confirmSignUp as amplifyConfirmSignUp,
  getCurrentUser,
  fetchAuthSession,
  AuthUser
} from 'aws-amplify/auth';

interface AuthContextType {
    user: AuthUser | null;
    isAuthenticated: boolean;
    isLoading: boolean;
    userGroups: string[];
    signIn: (username: string, password: string) => Promise<void>;
    signUp: (username: string, password: string, email: string) => Promise<void>;
    signOut: () => Promise<void>;
    confirmSignUp: (username: string, code: string) => Promise<void>;
    hasRole: (role: string) => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [user, setUser] = useState<AuthUser | null>(null);
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const [userGroups, setUserGroups] = useState<string[]>([]);

    // Check if user is already authenticated on mount
    useEffect(() => {
        checkAuthState();
    }, []);

    const checkAuthState = async () => {
        try {
            const currentUser = await getCurrentUser();
            setUser(currentUser);
            setIsAuthenticated(true);

            // Extract groups from JWT token
            const session = await fetchAuthSession();
            const idToken = session.tokens?.idToken;
            const groups = (idToken?.payload['cognito:groups'] as string[]) || [];
            setUserGroups(groups);
        } catch (error) {
            setUser(null);
            setIsAuthenticated(false);
            setUserGroups([]);
        } finally {
            setIsLoading(false);
        }
    };

    const signIn = async (username: string, password: string) => {
        try {
            await amplifySignIn({ username, password });
            await checkAuthState(); // Refresh user state and groups
        } catch (error) {
            console.error('Sign in error:', error);
            throw error;
        }
    };

    const signUp = async (username: string, password: string, email: string) => {
        try {
            await amplifySignUp({
                username,
                password,
                options: {
                    userAttributes: {
                        email
                    }
                }
            });
        } catch (error) {
            console.error('Sign up error:', error);
            throw error;
        }
    };

    const confirmSignUp = async (username: string, code: string) => {
        try {
            await amplifyConfirmSignUp({ username, confirmationCode: code });
        } catch (error) {
            console.error('Confirm sign up error:', error);
            throw error;
        }
    };

    const signOut = async () => {
        try {
            await amplifySignOut();
            setUser(null);
            setIsAuthenticated(false);
            setUserGroups([]);
        } catch (error) {
            console.error('Sign out error:', error);
            throw error;
        }
    };

    const hasRole = (role: string): boolean => {
        return userGroups.includes(role);
    };

    return (
        <AuthContext.Provider
            value={{
                user,
                isAuthenticated,
                isLoading,
                userGroups,
                signIn,
                signUp,
                signOut,
                confirmSignUp,
                hasRole
            }}
        >
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => {
    const context = useContext(AuthContext);
    if (context === undefined) {
        throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
};
```

### Step 2: Create Login Component

Create `src/components/Login.tsx`:

```typescript
import React, { useState } from 'react';
import { useAuth } from '../contexts/AuthContext';

export const Login: React.FC = () => {
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
      <button onClick={() => setIsSignUp(!isSignUp)}>
        {isSignUp ? 'Already have an account? Sign In' : "Don't have an account? Sign Up"}
      </button>
    </div>
  );
};
```

### Step 3: Update App Component

Your existing `src/App.tsx` contains the quote application logic. To add authentication, you need to wrap it with the `AuthProvider` and add login/logout UI. Here are two approaches:

#### Option A: Wrap in main.tsx (Recommended)

Update `src/main.tsx` to wrap the entire app with `AuthProvider`:

```typescript
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { Amplify } from 'aws-amplify';
import awsConfig from './config/aws-exports';
import { AuthProvider } from './contexts/AuthContext';

Amplify.configure(awsConfig);

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider>
      <App />
    </AuthProvider>
  </StrictMode>,
)
```

Then add authentication UI to your existing `App.tsx` by adding a header with login status:

```typescript
import './App.scss';
import React, { useEffect, useRef, useState } from "react";
import quoteApi from "./api/quoteApi";
import FavouritesComponent, { FavouritesComponentHandle } from "./components/FavouritesComponent.tsx";
import { Quote } from "./types/Quote";
import { useAuth } from './contexts/AuthContext';
import { Login } from './components/Login';

const App: React.FC = () => {
    const { isAuthenticated, isLoading, signOut, user } = useAuth();
    const [quote, setQuote] = useState<Quote | null>(null);
    const [receivedQuotes, setReceivedQuotes] = useState<Quote[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [liking, setLiking] = useState<boolean>(false);
    const indexRef = useRef<number>(0);
    const favouritesRef = useRef<FavouritesComponentHandle>(null);

    // Show loading while checking auth state
    if (isLoading) {
        return <div className="app"><div className="quoteView"><p>Loading...</p></div></div>;
    }

    // Show login if not authenticated
    if (!isAuthenticated) {
        return <Login />;
    }

    // Rest of your existing App.tsx code...
    // (fetchFirstQuote, newQuote, like, previous, next, etc.)

    return (
        <div className="app">
            {/* Add auth header */}
            <div className="auth-header">
                <span>Welcome, {user?.username}</span>
                <button onClick={signOut}>Sign Out</button>
            </div>
            
            {/* Your existing quote UI */}
            <div className="quoteView">
                <p>"{loading ? "Loading..." : quote?.quoteText || ""}"</p>
                <p className="author">{loading ? "" : quote?.author || ""}</p>
            </div>
            
            {/* Your existing button bar and favorites */}
            <div className="buttonBar">
                {/* ... existing buttons ... */}
            </div>
            <FavouritesComponent ref={favouritesRef}/>
        </div>
    );
};

export default App;
```

#### Option B: Create Separate AppContent Component

Keep your existing `App.tsx` unchanged and create a new wrapper:

Create `src/AppWithAuth.tsx`:

```typescript
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { Login } from './components/Login';
import App from './App'; // Your existing quote app

function AppContent() {
  const { isAuthenticated, isLoading, signOut, user } = useAuth();

  if (isLoading) {
    return <div>Loading...</div>;
  }

  if (!isAuthenticated) {
    return <Login />;
  }

  return (
    <div>
      <header style={{ padding: '1rem', background: '#f0f0f0' }}>
        <span>Welcome, {user?.username}</span>
        <button onClick={signOut} style={{ marginLeft: '1rem' }}>Sign Out</button>
      </header>
      <App />
    </div>
  );
}

function AppWithAuth() {
  return (
    <AuthProvider>
      <AppContent />
    </AuthProvider>
  );
}

export default AppWithAuth;
```

Then update `src/main.tsx` to use `AppWithAuth` instead of `App`:

```typescript
import AppWithAuth from './AppWithAuth.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppWithAuth />
  </StrictMode>,
)
```

**Note:** With Amplify v6, the user object has a `username` property instead of `getUsername()` method.

---

## Phase 3: Protected Routes

### Optional: Add React Router

If you want separate pages for login, quotes, profile, etc.:

```bash
npm install react-router-dom
```

Create `src/components/ProtectedRoute.tsx`:

```typescript
import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

interface ProtectedRouteProps {
  children: React.ReactNode;
  requiredRole?: string;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ 
  children, 
  requiredRole 
}) => {
  const { isAuthenticated, isLoading, hasRole } = useAuth();

  if (isLoading) {
    return <div>Loading...</div>;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (requiredRole && !hasRole(requiredRole)) {
    return <div>Access Denied: You don't have the required role.</div>;
  }

  return <>{children}</>;
};
```

---

## Phase 4: API Integration

### Step 1: Create API Client with Auth

Create `src/api/client.ts`:

**Note:** This uses AWS Amplify v6 API with `fetchAuthSession` from `'aws-amplify/auth'`.

```typescript
import { fetchAuthSession } from 'aws-amplify/auth';

const API_BASE_URL = import.meta.env.VITE_REACT_APP_API_BASE_URL;

export class ApiClient {
  private async getAuthHeader(): Promise<{ Authorization: string } | {}> {
    try {
      const session = await fetchAuthSession();
      const idToken = session.tokens?.idToken?.toString();
      return idToken ? { Authorization: idToken } : {};
    } catch (error) {
      console.error('Failed to get auth token:', error);
      return {};
    }
  }

  async getQuote(excludeIds: number[] = []): Promise<any> {
    const response = await fetch(`${API_BASE_URL}/quote`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(excludeIds)
    });
    
    if (!response.ok) {
      throw new Error('Failed to fetch quote');
    }
    
    return response.json();
  }

  async likeQuote(quoteId: number): Promise<any> {
    const authHeader = await this.getAuthHeader();
    
    const response = await fetch(`${API_BASE_URL}/quote/${quoteId}/like`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...authHeader
      }
    });
    
    if (!response.ok) {
      if (response.status === 403) {
        throw new Error('You need USER role to like quotes');
      }
      throw new Error('Failed to like quote');
    }
    
    return response.json();
  }

  async getLikedQuotes(): Promise<any> {
    const response = await fetch(`${API_BASE_URL}/quote/liked`);
    
    if (!response.ok) {
      throw new Error('Failed to fetch liked quotes');
    }
    
    return response.json();
  }
}

export const apiClient = new ApiClient();
```

### Step 2: Update Quote Component

Update your quote component to use the API client:

```typescript
import { useState } from 'react';
import { apiClient } from '../api/client';
import { useAuth } from '../contexts/AuthContext';

export const QuoteComponent = () => {
  const [quote, setQuote] = useState<any>(null);
  const [error, setError] = useState('');
  const { hasRole, isAuthenticated } = useAuth();

  const handleLike = async () => {
    if (!quote) return;
    
    try {
      const likedQuote = await apiClient.likeQuote(quote.id);
      setQuote(likedQuote);
      setError('');
    } catch (err: any) {
      setError(err.message);
    }
  };

  const handleNewQuote = async () => {
    try {
      const newQuote = await apiClient.getQuote();
      setQuote(newQuote);
      setError('');
    } catch (err: any) {
      setError(err.message);
    }
  };

  // Check if user can like (must be authenticated and have USER role)
  const canLike = isAuthenticated && hasRole('USER');

  return (
    <div>
      {quote && (
        <div>
          <p>{quote.quoteText}</p>
          <p>- {quote.author}</p>
          <p>Likes: {quote.likes}</p>
        </div>
      )}
      
      <button onClick={handleNewQuote}>New Quote</button>
      
      <button 
        onClick={handleLike} 
        disabled={!canLike}
        title={!canLike ? 'Sign in with USER role to like quotes' : ''}
      >
        Like
      </button>
      
      {error && <p className="error">{error}</p>}
      {!canLike && <p>Sign in to like quotes</p>}
    </div>
  );
};
```

---

## Phase 5: Role-Based UI

### Show/Hide Features Based on Roles

```typescript
import { useAuth } from '../contexts/AuthContext';

export const AdminPanel = () => {
  const { hasRole } = useAuth();

  if (!hasRole('ADMIN')) {
    return null; // Don't show admin panel to non-admins
  }

  return (
    <div className="admin-panel">
      <h2>Admin Panel</h2>
      {/* Admin-only features */}
    </div>
  );
};
```

### Conditional Rendering Example

```typescript
const { isAuthenticated, hasRole, userGroups } = useAuth();

return (
  <div>
    {/* Show to everyone */}
    <QuoteDisplay />
    
    {/* Show only to authenticated users */}
    {isAuthenticated && (
      <button>Save Favorite</button>
    )}
    
    {/* Show only to users with USER role */}
    {hasRole('USER') && (
      <button>Like Quote</button>
    )}
    
    {/* Show only to admins */}
    {hasRole('ADMIN') && (
      <AdminPanel />
    )}
    
    {/* Debug: Show user's roles */}
    <p>Your roles: {userGroups.join(', ')}</p>
  </div>
);
```

---

## Testing

### Manual Testing Checklist

1. **Sign Up Flow**
   - [ ] Sign up with email/password
   - [ ] Receive confirmation email
   - [ ] Confirm account with code
   - [ ] Sign in after confirmation

2. **Sign In Flow**
   - [ ] Sign in with email/password
   - [ ] Tokens stored correctly
   - [ ] User info displayed

3. **Authorization**
   - [ ] Like button disabled when not authenticated
   - [ ] Like button disabled when user has no USER role
   - [ ] Like button enabled when user has USER role
   - [ ] Like request includes Authorization header
   - [ ] Backend validates token and returns success

4. **Sign Out**
   - [ ] Sign out clears tokens
   - [ ] Redirects to login
   - [ ] Protected features hidden

5. **Token Refresh**
   - [ ] Tokens refresh automatically before expiration
   - [ ] User stays logged in across page refreshes

### Add User to USER Group

After signing up, manually add the user to the USER group:

```bash
aws cognito-idp admin-add-user-to-group \
    --user-pool-id eu-central-1_XrKxJWy5u \
    --username user@example.com \
    --group-name USER
```

Then sign out and sign in again to get the updated token with groups.

### Browser DevTools Testing

1. Open DevTools → Network tab
2. Sign in and make a like request
3. Check the request headers include: `Authorization: eyJraWQ...`
4. Check the response is 200 OK (not 403 Forbidden)

---

## Troubleshooting

### Common Issues

**1. "User is not authenticated"**
- Check if Amplify is configured correctly in `aws-config.ts`
- Verify environment variables are loaded
- Check browser console for errors

**2. "403 Forbidden" when liking**
- Ensure user is added to USER group in Cognito
- Sign out and sign in again to get updated token
- Check Authorization header is being sent
- Verify token includes `cognito:groups` claim

**3. "CORS error"**
- Verify API Gateway CORS includes `authorization` header
- Check `allow_headers` in `api_gateway.tf`

**4. Token expired**
- Amplify should auto-refresh tokens
- If not, implement manual refresh:
```typescript
import { fetchAuthSession } from 'aws-amplify/auth';

const session = await fetchAuthSession({ forceRefresh: true });
// This forces a token refresh
```

**5. Confirmation code not received**
- Check spam folder
- Verify email in Cognito console
- Resend code:
```typescript
import { resendSignUpCode } from 'aws-amplify/auth';

await resendSignUpCode({ username });
```

### Debug Tips

**Check Current User (Amplify v6):**
```typescript
import { getCurrentUser, fetchAuthSession } from 'aws-amplify/auth';

const user = await getCurrentUser();
console.log('User:', user);

const session = await fetchAuthSession();
const idToken = session.tokens?.idToken;
console.log('ID Token payload:', idToken?.payload);
console.log('Groups:', idToken?.payload['cognito:groups']);
```

**Decode JWT Token:**
```typescript
const token = session.tokens?.idToken?.toString();
if (token) {
  const base64Url = token.split('.')[1];
  const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
  const payload = JSON.parse(window.atob(base64));
  console.log('Token payload:', payload);
}
```

---

## Next Steps

1. **Implement GitHub OAuth Sign-In**
   - Add "Sign in with GitHub" button
   - Use Amplify v6 syntax:
   ```typescript
   import { signInWithRedirect } from 'aws-amplify/auth';
   
   await signInWithRedirect({ provider: 'Google' });
   ```

2. **Add Profile Page**
   - Display user information
   - Show user's roles
   - Allow password change

3. **Implement Post-Confirmation Trigger**
   - Automatically assign USER role on registration
   - No manual group assignment needed

4. **Add Admin Console**
   - User management interface
   - Role assignment UI
   - Audit logs

5. **Enhanced Security**
   - Implement MFA
   - Add password reset flow
   - Session timeout handling

---

## Resources

- [AWS Amplify Auth Documentation](https://docs.amplify.aws/lib/auth/getting-started/q/platform/js/)
- [Cognito User Pools](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-identity-pools.html)
- [JWT.io - Token Decoder](https://jwt.io/)
- [React Context API](https://react.dev/reference/react/useContext)
