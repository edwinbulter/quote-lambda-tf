import React, { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react';
import apiService from '../api/authApi';

interface User {
  id: string;
  email: string;
  username: string;
  roles: string[];
}

interface AuthContextType {
  user: User | null;
  login: (username: string, password: string) => Promise<void>;
  register: (userData: any) => Promise<void>;
  logout: () => void;
  isLoading: boolean;
  isAuthenticated: boolean;
  isAdmin: boolean;
}

const OVHAuthContext = createContext<AuthContextType | undefined>(undefined);

export const useOVHAuth = () => {
  const context = useContext(OVHAuthContext);
  if (!context) {
    throw new Error('useOVHAuth must be used within OVHAuthProvider');
  }
  return context;
};

interface AuthProviderProps {
  children: ReactNode;
}

export const OVHAuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const logout = useCallback(() => {
    localStorage.removeItem('jwt_token');
    setUser(null);
  }, []);

  // Check if token is expired
  const isTokenExpired = useCallback((token: string): boolean => {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const currentTime = Date.now() / 1000;
      return payload.exp < currentTime;
    } catch (error) {
      console.error('Error checking token expiration:', error);
      return true; // Assume expired if we can't check
    }
  }, []);

  useEffect(() => {
    const token = localStorage.getItem('jwt_token');
    if (token) {
      // Check if token is expired
      if (isTokenExpired(token)) {
        console.log('🔒 Token expired during initial check');
        logout();
        setIsLoading(false);
        return;
      }

      // Decode JWT to get user info - OVH backend uses different claim names
      try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        setUser({
          id: payload.user_id,
          email: payload.email,
          username: payload.username,
          roles: Array.isArray(payload.roles) ? payload.roles : (payload.roles ? [payload.roles] : []),
        });
      } catch (error) {
        console.error('Invalid token:', error);
        localStorage.removeItem('jwt_token');
      }
    }
    setIsLoading(false);

    // Listen for token expiration events
    const handleTokenExpired = () => {
      console.log('🔒 Token expired, logging out user and showing sign-in');
      logout();
      // Show sign-in screen when token expires
      window.dispatchEvent(new CustomEvent('auth:show-signin'));
    };

    window.addEventListener('auth:logout', handleTokenExpired);

    return () => {
      window.removeEventListener('auth:logout', handleTokenExpired);
    };
  }, [logout, isTokenExpired]);

  const login = async (username: string, password: string) => {
    try {
      const response = await apiService.login(username, password);
      console.log('Login response:', response);
      
      // The response contains token and user info
      const token = response.token;
      if (!token) {
        console.error('No token in response:', response);
        throw new Error('No token received from server');
      }
      
      // Use user info from response if available, otherwise decode JWT
      if (response.user) {
        setUser({
          id: response.user.id,
          email: response.user.email,
          username: response.user.username,
          roles: response.user.roles || [],
        });
      } else {
        // Decode JWT to get user info - OVH backend claim structure
        try {
          const payload = JSON.parse(atob(token.split('.')[1]));
          console.log('Decoded token payload:', payload);
          
          setUser({
            id: payload.user_id,
            email: payload.email,
            username: payload.username,
            roles: Array.isArray(payload.roles) ? payload.roles : (payload.roles ? [payload.roles] : []),
          });
        } catch (decodeError) {
          console.error('Failed to decode token:', decodeError);
          throw new Error('Invalid token received from server');
        }
      }
    } catch (error: any) {
      console.error('Login error:', error);
      throw error;
    }
  };

  const register = async (userData: any) => {
    try {
      const response = await apiService.register(userData);
      console.log('Register response:', response);
      
      // Registration successful but no token - user should login separately
      // Just return success, don't set user or token
      return response;
    } catch (error: any) {
      console.error('Register error:', error);
      throw error;
    }
  };

  const isAuthenticated = !!user;
  const isAdmin = user?.roles?.includes('admin') || false;

  // Periodic token expiration check (every 5 minutes)
  useEffect(() => {
    if (!isAuthenticated) return;

    const checkTokenExpiration = () => {
      const token = localStorage.getItem('jwt_token');
      if (token && isTokenExpired(token)) {
        console.log('🔒 Token expired during periodic check');
        logout();
      }
    };

    const interval = setInterval(checkTokenExpiration, 5 * 60 * 1000); // 5 minutes

    return () => clearInterval(interval);
  }, [isAuthenticated, logout, isTokenExpired]);

  return (
    <OVHAuthContext.Provider value={{ 
      user, 
      login, 
      register, 
      logout, 
      isLoading, 
      isAuthenticated,
      isAdmin 
    }}>
      {children}
    </OVHAuthContext.Provider>
  );
};