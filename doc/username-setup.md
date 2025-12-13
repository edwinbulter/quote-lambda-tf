# Username Setup for Email and Google OAuth Users

## Overview

Users can now choose custom usernames in two scenarios:

1. **Email Registration** - Users enter a username during sign-up
2. **Google OAuth** - Users are prompted to choose a username after their first sign-in

---

## Email Registration Flow

### User Experience

1. User clicks "Don't have an account? Sign Up"
2. Sign-up form shows:
   - **Username** field (new)
   - **Email** field
   - **Password** field
3. User enters all three fields
4. User receives confirmation code via email
5. User confirms email
6. Account is created with the custom username

### Technical Implementation

**Frontend Changes:**
- `Login.tsx`: Added username input field for sign-up form
- `AuthContext.tsx`: Username is passed to `signUp()` function

**Backend (Cognito):**
- Username is stored as the primary identifier in Cognito
- Email is stored separately as a user attribute

---

## Google OAuth Username Setup

### User Experience

1. User clicks "Sign in with Google"
2. User authorizes the app on Google
3. User is redirected back to the app
4. **Modal appears:** "Choose Your Username"
5. User enters a custom username
6. Username is saved to their profile
7. User is fully authenticated and can use the app

### Technical Implementation

**How It Works:**

1. **After Google sign-in**, `AuthContext.checkAuthState()` checks:
   - Is the username a Google ID? (starts with `Google_`)
   - Does the user have a `preferred_username` set?

2. **If both conditions are true:**
   - `needsUsernameSetup` is set to `true`
   - Login component shows the username modal

3. **User enters username:**
   - `updateUserAttributes()` saves it as `preferred_username`
   - Modal closes
   - User continues to the app

**Frontend Changes:**
- `Login.tsx`: Added username modal and `handleSetGoogleUsername()` function
- `AuthContext.tsx`: Added `needsUsernameSetup` state and detection logic

**Backend (Cognito):**
- Username remains the Google ID (e.g., `Google_109747799293641433374`)
- Custom username is stored in the `preferred_username` attribute
- Your app can display `preferred_username` instead of the technical username

---

## Displaying Usernames in Your App

### Option 1: Display preferred_username

If you want to show the custom username users chose:

```typescript
// In your profile component
const preferredUsername = user.attributes?.preferred_username;
const displayName = preferredUsername || user.username;

<p>Username: {displayName}</p>
```

### Option 2: Display email for Google users

For Google users, you can also display their email:

```typescript
const email = user.attributes?.email;
const displayName = email || user.username;

<p>Username: {displayName}</p>
```

---

## Database Considerations

### Cognito User Attributes

For each user, Cognito stores:

| Attribute | Email Users | Google Users |
|-----------|-------------|--------------|
| `username` | Custom username | `Google_<id>` |
| `email` | User's email | Google email |
| `preferred_username` | (not set) | Custom username |

### Accessing User Data

```typescript
// From AuthContext
const { user } = useAuth();

// User attributes
user.username              // Primary identifier
user.attributes?.email    // Email address
user.attributes?.preferred_username  // Custom username (Google users)
user.attributes?.name     // Full name (if available)
```

---

## Validation

### Username Requirements

Currently, usernames are validated by Cognito with these rules:
- Must be unique within the user pool
- Can contain alphanumeric characters, underscores, and hyphens
- Case-insensitive (e.g., "john" and "John" are the same)

### Custom Validation (Optional)

If you want to add custom validation, update `handleSetGoogleUsername()` in `Login.tsx`:

```typescript
const handleSetGoogleUsername = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // Custom validation
    if (googleUsername.length < 3) {
        setError('Username must be at least 3 characters');
        return;
    }

    if (!/^[a-zA-Z0-9_-]+$/.test(googleUsername)) {
        setError('Username can only contain letters, numbers, underscores, and hyphens');
        return;
    }

    // ... rest of the function
};
```

---

## Troubleshooting

### Username Modal Not Appearing for Google Users

**Cause:** The user already has a `preferred_username` set.

**Solution:**
- Modal only appears for new Google users without a custom username
- Existing users can update their username via a settings page (not yet implemented)

### Username Already Taken

**Error:** "An account with the given username already exists."

**Solution:**
- Cognito enforces username uniqueness
- User must choose a different username
- Consider adding a "Check availability" button (optional enhancement)

### Username Not Saving

**Cause:** `updateUserAttributes()` failed due to permissions.

**Solution:**
- Check browser console for error details
- Verify user is authenticated
- Check CloudWatch logs for Lambda errors (if applicable)

---

## Future Enhancements

Potential improvements:

1. **Username availability check** - Check if username is taken before submission
2. **Username update** - Allow users to change their username in settings
3. **Username validation** - Add custom validation rules
4. **Username suggestions** - Suggest usernames based on email or name
5. **Social username sync** - Sync username from Google profile name

---

## Summary

- ✅ Email users enter username during registration
- ✅ Google users choose username after first sign-in
- ✅ Custom usernames stored in `preferred_username` attribute
- ✅ Usernames are unique and case-insensitive
- ✅ App can display custom usernames or technical usernames
