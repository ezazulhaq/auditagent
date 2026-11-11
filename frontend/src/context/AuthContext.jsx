import { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import { useApi } from './ApiContext';
import { useToast } from '../components/Toast';

export const AuthContext = createContext(null);

/**
 * AuthProvider manages authentication session, security credentials, and LLM onboarding state.
 * Adheres to Single Responsibility Principle (SRP).
 */
export function AuthProvider({ children }) {
  const api = useApi();
  const toast = useToast();
  const [authSession, setAuthSession] = useState(null);
  const [authError, setAuthError] = useState('');

  const refreshSession = useCallback((signal) => {
    return api.authSession(signal)
      .then(session => {
        setAuthSession(session);
        setAuthError('');
        return session;
      })
      .catch(error => {
        if (error.name !== 'AbortError') {
          setAuthError(error.message);
          setAuthSession({ configured: false, authenticated: false });
        }
        throw error;
      });
  }, [api]);

  useEffect(() => {
    const controller = new AbortController();
    refreshSession(controller.signal).catch(() => {});
    return () => controller.abort();
  }, [refreshSession]);

  const needsLlmOnboarding = Boolean(
    authSession?.authenticated && authSession?.userKeysRequired && !authSession?.llmConfigured
  );

  const handleLlmOnboardingComplete = useCallback(() => {
    setAuthSession(prev => prev ? ({ ...prev, llmConfigured: true }) : prev);
    toast.success('AI credentials configured successfully!');
  }, [toast]);

  const logout = useCallback(async () => {
    try {
      await api.logout();
    } finally {
      setAuthSession(previous => ({ ...previous, authenticated: false, user: null }));
    }
  }, [api]);

  const loginUrl = useCallback(() => {
    return typeof api.loginUrl === 'function' ? api.loginUrl() : '/api/auth/github/login';
  }, [api]);

  const value = useMemo(() => ({
    authSession,
    setAuthSession,
    authError,
    isAuthenticated: Boolean(authSession?.authenticated),
    isConfigured: Boolean(authSession?.configured),
    user: authSession?.user ?? null,
    installationUrl: authSession?.installationUrl,
    needsLlmOnboarding,
    handleLlmOnboardingComplete,
    logout,
    loginUrl,
    refreshSession,
  }), [
    authSession,
    authError,
    needsLlmOnboarding,
    handleLlmOnboardingComplete,
    logout,
    loginUrl,
    refreshSession,
  ]);

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}

/**
 * Hook to consume authentication and session state.
 */
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
