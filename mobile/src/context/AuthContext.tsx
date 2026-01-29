import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import * as SecureStore from 'expo-secure-store';
import { ApiClient } from '../api/client';
import { AuthTokens } from '../types';

type AuthContextType = {
  tokens: AuthTokens | null;
  apiClient: ApiClient;
  login: (username: string, pin: string) => Promise<void>;
  logout: () => Promise<void>;
  isLoading: boolean;
};

const AUTH_STORAGE_KEY = 'somtranscriber_tokens';

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [tokens, setTokens] = useState<AuthTokens | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const load = async () => {
      const raw = await SecureStore.getItemAsync(AUTH_STORAGE_KEY);
      if (raw) {
        setTokens(JSON.parse(raw));
      }
      setIsLoading(false);
    };

    void load();
  }, []);

  const persistTokens = async (newTokens: AuthTokens | null) => {
    setTokens(newTokens);
    if (!newTokens) {
      await SecureStore.deleteItemAsync(AUTH_STORAGE_KEY);
      return;
    }
    await SecureStore.setItemAsync(AUTH_STORAGE_KEY, JSON.stringify(newTokens));
  };

  const apiClient = useMemo(
    () =>
      new ApiClient({
        getAccessToken: () => tokens?.accessToken ?? null,
        getRefreshToken: () => tokens?.refreshToken ?? null,
        onTokens: async (newTokens) => {
          await persistTokens(newTokens);
        },
        onAuthFailure: async () => {
          await persistTokens(null);
        }
      }),
    [tokens]
  );

  const login = async (username: string, pin: string) => {
    const response = await apiClient.login(username, pin);
    await persistTokens(response);
  };

  const logout = async () => {
    try {
      await apiClient.logout();
    } finally {
      await persistTokens(null);
    }
  };

  const value = { tokens, apiClient, login, logout, isLoading };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}
