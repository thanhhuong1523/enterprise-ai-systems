import React, { createContext, useContext, useState, useEffect } from 'react';
import { User } from '@/types';
import axios from 'axios';
import { setAccessToken, setHasSession } from '@/api/client';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  login: (accessToken: string, userInfo: User) => void;
  logout: () => void;
  loading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

let initializePromise: Promise<void> | null = null;

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const initializeAuth = () => {
      if (!initializePromise) {
        initializePromise = axios.post(
          `${API_BASE_URL}/api/v1/auth/refresh`,
          {},
          { withCredentials: true }
        )
        .then((response) => {
          if (response.data && response.data.success && response.data.data) {
            const { accessToken, userInfo: freshUserInfo } = response.data.data;
            setAccessToken(accessToken);
            setHasSession(true);
            setUser(freshUserInfo);
          }
        })
        .catch(() => {
          // Ignore refresh error on initialization (guest access)
        });
      }

      initializePromise.finally(() => {
        setLoading(false);
      });
    };
    initializeAuth();
  }, []);

  const login = (accessToken: string, userInfo: User) => {
    setAccessToken(accessToken);
    setHasSession(true);
    setUser(userInfo);
  };

  const logout = () => {
    setAccessToken(null);
    setHasSession(false);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, login, logout, loading }}>
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
