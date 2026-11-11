import { ApiProvider } from './ApiContext';
import { AuthProvider } from './AuthContext';
import { WorkspaceProvider } from './WorkspaceContext';
import { ModalProvider } from './ModalContext';
import { AuditProvider } from './AuditContext';

/**
 * AppProviders composes all core domain contexts in proper dependency hierarchy:
 * ApiProvider (DIP) -> AuthProvider (Auth/User) -> WorkspaceProvider (Repos/Branches)
 * -> ModalProvider (UI Modals) -> AuditProvider (Workflows, Scanning, Remediation).
 */
export function AppProviders({ api, children }) {
  return (
    <ApiProvider api={api}>
      <AuthProvider>
        <WorkspaceProvider>
          <ModalProvider>
            <AuditProvider>
              {children}
            </AuditProvider>
          </ModalProvider>
        </WorkspaceProvider>
      </AuthProvider>
    </ApiProvider>
  );
}
