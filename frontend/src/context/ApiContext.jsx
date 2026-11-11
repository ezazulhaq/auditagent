import { createContext, useContext } from 'react';
import { auditApi } from '../api/auditApi';

export const ApiContext = createContext(auditApi);

/**
 * ApiProvider supplies the API client to the application tree.
 * Follows Dependency Inversion (DIP) and enables effortless mocking for Liskov Substitution (LSP).
 */
export function ApiProvider({ api = auditApi, children }) {
  return (
    <ApiContext.Provider value={api}>
      {children}
    </ApiContext.Provider>
  );
}

/**
 * Hook to consume the injected API client.
 */
export function useApi() {
  const context = useContext(ApiContext);
  if (!context) {
    return auditApi;
  }
  return context;
}
