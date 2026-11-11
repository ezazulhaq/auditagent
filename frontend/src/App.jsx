import { auditApi } from './api/auditApi';
import { AppProviders } from './context/AppProviders';
import { useApi } from './context/ApiContext';
import { useAuth } from './context/AuthContext';
import { useWorkspace } from './context/WorkspaceContext';
import { useAudit } from './context/AuditContext';
import { useModals } from './context/ModalContext';
import { useTheme } from './utils/useTheme';
import { useKeyboardShortcuts } from './utils/useKeyboardShortcuts';
import { SkeletonApp } from './components/SkeletonApp';
import { LoginPage } from './features/auth/LoginPage';
import { AppHeader } from './components/AppHeader';
import { ScanPanel } from './features/scan/ScanPanel';
import { WorkspaceContainer } from './features/navigation/WorkspaceContainer';
import { ModalManager } from './components/ModalManager';

/**
 * AppShell renders the main workspace layout, header, navigation, and sidebar.
 * Strictly adheres to Single Responsibility Principle (SRP) by delegating business logic
 * and workflows to domain providers (Auth, Workspace, Audit, Modals).
 */
function AppShell() {
  const { theme, toggleTheme } = useTheme();
  const api = useApi();
  const auth = useAuth();
  const workspace = useWorkspace();
  const audit = useAudit();
  const modals = useModals();

  const { authSession, authError, logout, loginUrl } = auth;
  const { config, updateConfig, repositories, branches, loadingRepositories } = workspace;
  const {
    metadata, counts, findings, startScan, startUploadScan, scan,
    handleTabChange, selectedFinding, handleCloseFindingDrawer, chat
  } = audit;
  const {
    isSidebarOpen, toggleSidebar, closeSidebar, openObservability,
    openFindingsModal, openTour, openDocs, openProfile, handleTopLevelClose
  } = modals;

  useKeyboardShortcuts({
    onSearch: () => {
      const select = document.querySelector('select');
      if (select) select.focus();
    },
    onClose: () => handleTopLevelClose({
      isFindingDrawerOpen: Boolean(selectedFinding),
      onCloseFindingDrawer: handleCloseFindingDrawer,
      isChatOpen: Boolean(chat.isOpen),
      onCloseChat: () => chat.setIsOpen(false),
    }),
    onTabChange: handleTabChange,
    onChatToggle: chat.toggle
  });

  if (!authSession) {
    return <SkeletonApp />;
  }

  if (!authSession.authenticated) {
    return (
      <LoginPage
        configured={authSession.configured}
        error={authError}
        onLogin={() => window.location.assign(loginUrl())}
      />
    );
  }

  return (
    <div className="app-shell flex min-h-dvh flex-col text-slate-200 lg:h-dvh lg:overflow-hidden relative">
      <AppHeader
        api={api}
        metadata={metadata}
        counts={counts}
        user={authSession.user}
        onLogout={logout}
        findings={findings}
        onShowObservability={openObservability}
        onShowFindings={openFindingsModal}
        theme={theme}
        onToggleTheme={toggleTheme}
        onTourClick={openTour}
        onShowDocs={openDocs}
        onToggleSidebar={toggleSidebar}
        isSidebarOpen={isSidebarOpen}
        onOpenProfileSettings={openProfile}
      />
      <div className="relative mx-auto flex w-full max-w-450 flex-1 flex-col lg:min-h-0 lg:flex-row">
        {/* Mobile overlay */}
        {isSidebarOpen && (
          <div
            className="fixed inset-0 z-30 bg-slate-900/60 backdrop-blur-sm lg:hidden"
            onClick={closeSidebar}
            aria-hidden="true"
          />
        )}
        <ScanPanel
          config={config}
          onConfigChange={updateConfig}
          onScan={startScan}
          onUploadScan={startUploadScan}
          scan={scan}
          repositories={repositories}
          branches={branches}
          loadingRepositories={loadingRepositories}
          installationUrl={authSession.installationUrl}
          isOpen={isSidebarOpen}
          onToggle={toggleSidebar}
        />
        <WorkspaceContainer />
      </div>
      <ModalManager theme={theme} />
    </div>
  );
}

/**
 * Root App component injecting domain providers (DIP) and preserving backward-compatible props.
 */
export default function App({ api = auditApi }) {
  return (
    <AppProviders api={api}>
      <AppShell />
    </AppProviders>
  );
}
