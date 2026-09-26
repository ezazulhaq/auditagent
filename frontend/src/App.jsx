import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { auditApi } from './api/auditApi';
import { AppHeader } from './components/AppHeader';
import { Modal } from './components/Modal';
import { ConfirmDialog } from './components/ConfirmDialog';
import { OnboardingTour } from './components/OnboardingTour';
import { SkeletonApp } from './components/SkeletonApp';
import { useToast } from './components/Toast';
import { LoginPage } from './features/auth/LoginPage';
import { ChatWidget } from './features/chat/ChatWidget';
import { useChatController } from './features/chat/useChatController';
import { useChatState } from './features/chat/useChatState';
import { DashboardView } from './features/dashboard/DashboardView';
import { FindingsView } from './features/findings/FindingsView';
import { FindingDrawer } from './features/remediation/FindingDrawer';
import { useRemediationController } from './features/remediation/useRemediationController';
import { ReportView } from './features/report/ReportView';
import { WorkspaceTabs } from './features/report/WorkspaceTabs';
import { ScanPanel } from './features/scan/ScanPanel';
import { useScanController } from './features/scan/useScanController';
import { useScanElapsedSeconds } from './features/scan/useScanElapsedSeconds';
import { useMemoryThread } from './features/session/useMemoryThread';
import { severityCounts } from './utils/auditSelectors';
import { useTheme } from './utils/useTheme';
import { useKeyboardShortcuts } from './utils/useKeyboardShortcuts';
import { DocsView } from './features/docs/DocsView';
import { TokenUsageLayout } from './features/observability/TokenUsageLayout';

const initialConfig = { repositoryId: '', branch: '', scannerName: 'semgrep', forceRescan: false };

export default function App({ api = auditApi }) {
  const { theme, toggleTheme } = useTheme();
  const [authSession, setAuthSession] = useState(null);
  const [authError, setAuthError] = useState('');
  const [config, setConfig] = useState(initialConfig);
  const [repositories, setRepositories] = useState([]);
  const [branches, setBranches] = useState([]);
  const [loadingRepositories, setLoadingRepositories] = useState(true);
  const [activeTab, setActiveTab] = useState('dashboard');
  const [restoredSession, setRestoredSession] = useState(null);
  const [scanHistory, setScanHistory] = useState([]);
  const selectedKeyRef = useRef('');
  const chat = useChatState();
  const toast = useToast();
  const [confirmAction, setConfirmAction] = useState(null);
  const [isTourOpen, setIsTourOpen] = useState(false);
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);
  const [isViewingDocs, setIsViewingDocs] = useState(false);
  const [isViewingTokenUsage, setIsViewingTokenUsage] = useState(false);
  const { addMessage, resetMessages, restoreMessages } = chat;

  useEffect(() => {
    const controller = new AbortController();
    api.authSession(controller.signal).then(setAuthSession).catch(error => {
      if (error.name !== 'AbortError') { setAuthError(error.message); setAuthSession({ configured: false, authenticated: false }); }
    });
    return () => controller.abort();
  }, [api]);

  const handleThreadRestore = useCallback(data => setRestoredSession(data), []);
  const session = useMemoryThread({ api, onRestore: handleThreadRestore });
  const { ensureThread, forgetThread, restoreThread, threadId } = session;
  const handleScanSuccess = useCallback(() => setActiveTab('report'), []);
  const scan = useScanController({ api, ensureThread, onMessage: addMessage, onSuccess: handleScanSuccess });
  const { cancel: cancelScan, replaceResult, result, startScan: runScan } = scan;
  const scanTime = useScanElapsedSeconds(scan.startedAt, scan.finishedAt);

  const handleFindings = useCallback((findings) => {
    if (result) replaceResult({ ...result, findings });
  }, [replaceResult, result]);
  const handleRemediationReport = useCallback((nextResult, silent = false) => {
    replaceResult(nextResult);
    if (!silent) setActiveTab('report');
  }, [replaceResult]);
  const remediation = useRemediationController({ api, toast, onMessage: addMessage, onReport: handleRemediationReport, onFindings: handleFindings, repositoryId: config.repositoryId, branch: config.branch });
  const { activeRun, analyze, approvalPreview, awaitingApproval, decide, discard, hydrate, isAnalyzing,
    isPublishing, loadApprovalPreview, publication, resetForScan, resume, retryPublish,
    selectedFinding, setSelectedFinding, graphSteps } = remediation;

  useEffect(() => {
    if (!authSession?.authenticated) return undefined;
    const controller = new AbortController();
    api.repositories(controller.signal).then(setRepositories).catch(error => {
      if (error.name !== 'AbortError') toast.error(`Unable to load installed repositories: ${error.message}`);
    }).finally(() => { if (!controller.signal.aborted) setLoadingRepositories(false); });
    return () => controller.abort();
  }, [api, authSession?.authenticated, toast]);

  useEffect(() => {
    if (!config.repositoryId) return undefined;
    const controller = new AbortController();
    api.branches(config.repositoryId, controller.signal).then(items => {
      setBranches(items);
      const repository = repositories.find(item => String(item.repositoryId) === String(config.repositoryId));
      const preferred = items.includes(repository?.defaultBranch) ? repository.defaultBranch : items[0] ?? '';
      setConfig(previous => previous.repositoryId === config.repositoryId ? { ...previous, branch: preferred } : previous);
    }).catch(error => { if (error.name !== 'AbortError') toast.error(`Unable to load branches: ${error.message}`); });
    return () => controller.abort();
  }, [api, cancelScan, config.repositoryId, replaceResult, repositories, resetForScan, toast]);

  useEffect(() => {
    if (!authSession?.authenticated || !config.repositoryId || !config.branch) return undefined;
    const key = `${config.repositoryId}:${config.branch}`;
    if (selectedKeyRef.current === key) return undefined;
    selectedKeyRef.current = key;
    const controller = new AbortController();
    restoreThread(config.repositoryId, config.branch, controller.signal).catch(error => {
      if (error.name !== 'AbortError') toast.error(`Memory restore failed: ${error.message}`);
    });
    api.getReport(config.repositoryId, config.branch, controller.signal).then(replaceResult).catch(error => {
      if (error.name !== 'AbortError' && !error.message.includes('404')) toast.error(`Report restore failed: ${error.message}`);
    });
    api.getReportHistory(config.repositoryId, config.branch, controller.signal).then(setScanHistory).catch(error => {
      if (error.name !== 'AbortError') console.error(`Scan history restore failed: ${error.message}`);
    });
    return () => controller.abort();
  }, [api, authSession?.authenticated, config.branch, config.repositoryId, replaceResult, restoreThread, toast]);

  const refreshReport = useCallback(() => {
    if (config.repositoryId && config.branch && remediation.refreshReport) {
      remediation.refreshReport(config.repositoryId, config.branch).catch(error => {
        if (!error?.message?.includes('404')) toast.error(`Report restore failed: ${error.message}`);
      });
    }
  }, [config.repositoryId, config.branch, remediation, toast]);

  useEffect(() => {
    if (scan.finishedAt && config.repositoryId && config.branch) {
      const controller = new AbortController();
      api.getReportHistory(config.repositoryId, config.branch, controller.signal).then(setScanHistory).catch(error => {
        if (error.name !== 'AbortError') console.error(`Scan history fetch failed: ${error.message}`);
      });
      return () => controller.abort();
    }
  }, [api, config.branch, config.repositoryId, scan.finishedAt]);

  useEffect(() => {
    if (!restoredSession) return;
    restoreMessages(restoredSession.messages);
    hydrate(restoredSession);
  }, [hydrate, restoreMessages, restoredSession]);

  useEffect(() => {
    if (activeRun?.status !== 'PR_OPEN' || !config.repositoryId || !config.branch) return undefined;
    const interval = setInterval(() => {
      restoreThread(config.repositoryId, config.branch).catch(error => {
        console.error(`Polling restore failed: ${error.message}`);
      });
    }, 60000);
    return () => clearInterval(interval);
  }, [activeRun?.status, config.branch, config.repositoryId, restoreThread]);

  const updateConfig = useCallback((name, value) => {
    if (name === 'repositoryId') {
      selectedKeyRef.current = '';
      cancelScan(); replaceResult(null); resetForScan(); setBranches([]); setScanHistory([]);
    }
    if (name === 'branch') {
      selectedKeyRef.current = '';
      setScanHistory([]);
    }
    setConfig(previous => ({ ...previous, [name]: value, ...(name === 'repositoryId' ? { branch: '' } : {}) }));
  }, [cancelScan, replaceResult, resetForScan]);
  const startScan = useCallback(async () => { resetForScan(); return runScan(config); }, [config, resetForScan, runScan]);
  const analyzeFinding = useCallback(finding => analyze(finding, config.repositoryId, config.branch, threadId), [analyze, config.branch, config.repositoryId, threadId]);
  const submitDecision = useCallback((decision, reason) => decide(decision, config.repositoryId, config.branch, reason), [config.branch, config.repositoryId, decide]);
  const retry = useCallback(() => retryPublish(config.repositoryId, config.branch), [config.branch, config.repositoryId, retryPublish]);

  const { submitChat, cancelStream } = useChatController({
    api, chat, repositoryId: config.repositoryId, branch: config.branch,
    threadId, findings: result?.findings ?? [], onScan: startScan, onAnalyze: analyzeFinding
  });

  const forgetConversation = useCallback(async () => {
    if (!threadId) return;
    setConfirmAction({
      title: 'Forget conversation',
      message: 'Forget this conversation and its unpublished run details?',
      confirmText: 'Forget',
      style: 'danger',
      action: async () => {
        try { await forgetThread(config.repositoryId, config.branch); resetMessages(); hydrate({}); toast.success('Conversation forgotten.'); }
        catch (error) { toast.error(`Unable to forget conversation: ${error.message}`); }
      }
    });
  }, [config.branch, config.repositoryId, forgetThread, hydrate, resetMessages, threadId, toast]);
  const forgetRepository = useCallback(async () => {
    if (!config.repositoryId || !config.branch) return;
    setConfirmAction({
      title: 'Forget repository memory',
      message: 'Forget approved remediation memory for this repository branch?',
      confirmText: 'Forget',
      style: 'danger',
      action: async () => {
        try { await api.forgetRepository(config.repositoryId, config.branch); toast.success('Approved repository remediation memory was forgotten.'); }
        catch (error) { toast.error(`Unable to forget repository memory: ${error.message}`); }
      }
    });
  }, [api, config.branch, config.repositoryId, toast]);
  const logout = useCallback(async () => {
    try { await api.logout(); } finally { setAuthSession(previous => ({ ...previous, authenticated: false, user: null })); }
  }, [api]);

  const findings = useMemo(() => result?.findings ?? [], [result]);
  const metadata = result?.metadata ?? null;
  const counts = useMemo(() => severityCounts(findings), [findings]);
  const selectedRepository = repositories.find(item => String(item.repositoryId) === String(config.repositoryId));

  const activeRunFinding = activeRun ? findings.find(f => f.id === activeRun.vulnerabilityId) : null;
  const activeFilePath = activeRunFinding?.filePath;
  const isActiveGroupFinding = (finding) => finding && activeFilePath && finding.filePath === activeFilePath && finding.status !== 'FIXED' && finding.status !== 'IGNORED';

  const isSelectedInActiveGroup = isActiveGroupFinding(selectedFinding);

  const approvalForDrawer = isSelectedInActiveGroup ? approvalPreview : null;
  const awaitingForDrawer = awaitingApproval && isSelectedInActiveGroup;
  const activeRunForDrawer = isSelectedInActiveGroup ? activeRun : null;
  const publicationForDrawer = isSelectedInActiveGroup ? publication : null;
  const graphStepsForDrawer = isSelectedInActiveGroup ? graphSteps : [];

  const handleBulkAction = useCallback((action, selectedIds) => {
    chat.setIsOpen(true);
    if (action === 'analyze') {
      chat.addMessage('system', `Bulk analyze initiated for ${selectedIds.length} findings. (Backend batch processing pending)`);
      const firstFinding = findings.find(f => f.id === selectedIds[0]);
      if (firstFinding) analyzeFinding(firstFinding);
    } else if (action === 'ignore') {
      chat.addMessage('system', `Bulk ignore requested for ${selectedIds.length} findings. (Backend batch processing pending)`);
    }
  }, [findings, analyzeFinding, chat]);

  const [activeModal, setActiveModal] = useState(null);

  const handleClose = useCallback(() => {
    if (confirmAction !== null) { setConfirmAction(null); return; }
    if (activeModal !== null) { setActiveModal(null); return; }
    if (isTourOpen) { setIsTourOpen(false); return; }
    if (selectedFinding !== null) { setSelectedFinding(null); return; }
    if (chat.isOpen) { chat.setIsOpen(false); return; }
  }, [confirmAction, activeModal, isTourOpen, selectedFinding, chat, setSelectedFinding]);

  useKeyboardShortcuts({
    onSearch: () => {
      const select = document.querySelector('select');
      if (select) select.focus();
    },
    onClose: handleClose,
    onTabChange: setActiveTab,
    onChatToggle: chat.toggle
  });

  if (!authSession) return <SkeletonApp />;
  if (!authSession.authenticated) return <LoginPage configured={authSession.configured} error={authError} onLogin={() => window.location.assign(api.loginUrl())} />;

  return <div className="app-shell flex min-h-dvh flex-col text-slate-200 lg:h-dvh lg:overflow-hidden relative">
    <AppHeader metadata={metadata} counts={counts} user={authSession.user} onLogout={logout} findings={findings} onShowObservability={() => setIsViewingTokenUsage(true)} onShowFindings={(title, modalFindings) => setActiveModal({ title, findings: modalFindings })} theme={theme} onToggleTheme={toggleTheme} onTourClick={() => setIsTourOpen(true)} onShowDocs={() => setIsViewingDocs(true)} onToggleSidebar={() => setIsSidebarOpen(!isSidebarOpen)} isSidebarOpen={isSidebarOpen} />
    <div className="relative mx-auto flex w-full max-w-450 flex-1 flex-col lg:min-h-0 lg:flex-row">
      {/* Mobile overlay */}
      {isSidebarOpen && (
        <div
          className="fixed inset-0 z-30 bg-slate-900/60 backdrop-blur-sm lg:hidden"
          onClick={() => setIsSidebarOpen(false)}
          aria-hidden="true"
        />
      )}
      <ScanPanel config={config} onConfigChange={updateConfig} onScan={startScan} scan={{ ...scan, scanTime }}
        repositories={repositories} branches={branches} loadingRepositories={loadingRepositories}
        installationUrl={authSession.installationUrl} isOpen={isSidebarOpen} onToggle={() => setIsSidebarOpen(!isSidebarOpen)} />
      <main className="workspace-surface min-w-0 flex-1 lg:flex lg:min-h-0 lg:flex-col">
        <WorkspaceTabs activeTab={activeTab} onChange={setActiveTab} findingCount={findings.length} />
        <div className="min-h-0 flex-1 overflow-auto scroll-smooth">
          {activeTab === 'dashboard' && <DashboardView metadata={metadata} findings={findings} counts={counts} scanHistory={scanHistory} repositoryLabel={result?.repository || selectedRepository?.fullName || ''} onSelect={setSelectedFinding} onShowFindings={(title, modalFindings) => setActiveModal({ title, findings: modalFindings })} />}
          {activeTab === 'findings' && <FindingsView findings={findings} activeRun={activeRun} onSelect={setSelectedFinding} onBulkAction={handleBulkAction} onRefresh={refreshReport} />}
          {activeTab === 'report' && <ReportView key={`${config.repositoryId}:${config.branch}`} api={api} repositoryId={config.repositoryId} branch={config.branch}
            reportAvailable={result?.reportAvailable ?? Boolean(result?.htmlReport)} projectName={metadata?.projectName || selectedRepository?.name} />}
        </div>
      </main>
    </div>
    {isViewingDocs && <DocsView api={api} theme={theme} onClose={() => setIsViewingDocs(false)} />}
    <FindingDrawer finding={selectedFinding} isAnalyzing={isAnalyzing} isPublishing={isPublishing}
      awaitingApproval={awaitingForDrawer} approvalPreview={approvalForDrawer} publication={publicationForDrawer}
      activeRun={activeRunForDrawer} graphSteps={graphStepsForDrawer} onClose={() => setSelectedFinding(null)} onAnalyze={() => analyzeFinding(selectedFinding)}
      onLoadPreview={loadApprovalPreview} onDecision={submitDecision} onRetryPublish={retry} theme={theme} />
    <ChatWidget chat={chat} onSubmit={submitChat} onCancel={cancelStream} activeRun={activeRun} publication={publication}
      onResume={() => publication?.approvedAt ? retry() : resume(threadId)} onDiscard={discard} onRetryPublish={retry}
      onForgetConversation={forgetConversation} onForgetRepository={forgetRepository} />
    {isViewingTokenUsage && <TokenUsageLayout api={api} theme={theme} onClose={() => setIsViewingTokenUsage(false)} />}
    <Modal isOpen={activeModal !== null} onClose={() => setActiveModal(null)} title={activeModal?.title || 'Findings'}>
      {activeModal && <FindingsView findings={activeModal.findings} activeRun={activeRun} onSelect={(finding) => { setActiveModal(null); setSelectedFinding(finding); }} onBulkAction={(action, ids) => { setActiveModal(null); handleBulkAction(action, ids); }} onRefresh={refreshReport} />}
    </Modal>
    <ConfirmDialog
      isOpen={confirmAction !== null}
      onClose={() => setConfirmAction(null)}
      onConfirm={confirmAction?.action || (() => { })}
      title={confirmAction?.title}
      message={confirmAction?.message}
      confirmText={confirmAction?.confirmText}
      confirmStyle={confirmAction?.style}
    />
    <OnboardingTour isOpen={isTourOpen} onClose={() => setIsTourOpen(false)} />
  </div>;
}
