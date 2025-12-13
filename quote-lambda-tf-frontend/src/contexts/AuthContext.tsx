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
import { Hub } from 'aws-amplify/utils';

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

        // Listen for auth events
        const hubListener = Hub.listen('auth', ({ payload }) => {
            console.log('Auth event:', payload);
            switch (payload.event) {
                case 'signedIn':
                    console.log('User signed in');
                    checkAuthState();
                    break;
                case 'signedOut':
                    console.log('User signed out');
                    setUser(null);
                    setIsAuthenticated(false);
                    setUserGroups([]);
                    break;
                case 'tokenRefresh':
                    console.log('Token refreshed');
                    checkAuthState();
                    break;
                case 'tokenRefresh_failure':
                    console.log('Token refresh failed');
                    setUser(null);
                    setIsAuthenticated(false);
                    setUserGroups([]);
                    break;
            }
        });

        return () => hubListener();
    }, []);

    const checkAuthState = async () => {
        try {
            console.log('Checking auth state...');
            const currentUser = await getCurrentUser();
            console.log('Current user:', currentUser);
            setUser(currentUser);
            setIsAuthenticated(true);

            // Extract groups from JWT token
            const session = await fetchAuthSession();
            console.log('Session:', session);
            const idToken = session.tokens?.idToken;
            const groups = (idToken?.payload['cognito:groups'] as string[]) || [];
            console.log('User groups:', groups);
            setUserGroups(groups);
        } catch (error: any) {
            // This is expected when user is not authenticated - not an error
            if (error.name === 'UserUnAuthenticatedException') {
                console.log('User not authenticated (expected on initial load)');
            } else {
                console.error('Auth state check error:', error);
            }
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