import {
  createContext,
  useContext,
  useState,
  useCallback,
  useMemo,
  useEffect,
  startTransition
} from 'react';
import { useApi } from './ApiContext';
import { useAuth } from './AuthContext';
import { useWorkspace } from './WorkspaceContext';
import { useModals } from './ModalContext';
import { useToast } from '../components/Toast';
import { useChatState } from '../features/chat/useChatState';
import { useChatController } from '../features/chat/useChatController';
import { useMemoryThread } from '../features/session/useMemoryThread';
import { useScanController } from '../features/scan/useScanController';
import { useRemediationController } from '../features/remediation/useRemediationController';
import { severityCounts } from '../utils/auditSelectors';

export const AuditContext = createContext(null);

/**
 * AuditProvider orchestrates scanning, remediation, persistent memory threads, and findings state.
 * Implements Single Responsibility Principle (SRP) by decoupling workflow logic from presentation components.
 */
export function AuditProvider({ children }) {
  const api = useApi();
  const { isAuthenticated } = useAuth();
  const { config, repositoryKeyRef } = useWorkspace();
  const { showConfirm, openProfile } = useModals();
  const toast = useToast();

  const [activeTab, setActiveTab] = useState('dashboard');
  const [issues, setIssues] = useState([]);
  const [loadingIssues, setLoadingIssues] = useState(false);
  const [scanHistory, setScanHistory] = useState([]);
  const [restoredSession, setRestoredSession] = useState(null);

  const chat = useChatState();
  const { addMessage, resetMessages, restoreMessages } = chat;

  const handleThreadRestore = useCallback((data) => {
    setRestoredSession(data);
  }, []);

  const session = useMemoryThread({ api, onRestore: handleThreadRestore });
  const { ensureThread, forgetThread, restoreThread, threadId } = session;

  const handleScanSuccess = useCallback(() => {
    setActiveTab('report');
  }, []);

  const scan = useScanController({
    api,
    ensureThread,
    onMessage: addMessage,
    onSuccess: handleScanSuccess,
  });

  const { cancel: cancelScan, replaceResult, result, startScan: runScan, startUploadScan: runUploadScan } = scan;
  const isUploadScan = result?.source === 'upload';

  const handleFindings = useCallback((findings) => {
    if (result) replaceResult({ ...result, findings });
  }, [replaceResult, result]);

  const handleRemediationReport = useCallback((nextResult, silent = false) => {
    replaceResult(nextResult);
    if (!silent) setActiveTab('report');
  }, [replaceResult]);

  const remediation = useRemediationController({
    api,
    toast,
    onMessage: addMessage,
    onReport: handleRemediationReport,
    onFindings: handleFindings,
    repositoryId: config.repositoryId,
    branch: config.branch,
    threadId,
    onOpenProfileSettings: openProfile,
  });

  const {
    activeRun, runs, analyze, approvalPreview, awaitingApproval, decide, discard, hydrate, isAnalyzing,
    isPublishing, isLoadingPreview, isLoadingRun, loadApprovalPreview, publication, resetForScan, resume, retryPublish,
    selectedFinding, setSelectedFinding, graphSteps,
    analyzeBatch, cancelBatch, batchState, isAnalyzingMap
  } = remediation;

  // Restore thread, report, and scan history when repository:branch changes
  useEffect(() => {
    if (!isAuthenticated || !config.repositoryId || !config.branch) return undefined;
    const key = `${config.repositoryId}:${config.branch}`;
    if (repositoryKeyRef.current === key) return undefined;
    repositoryKeyRef.current = key;

    const controller = new AbortController();
    restoreThread(config.repositoryId, config.branch, controller.signal).catch(error => {
      if (error.name !== 'AbortError') toast.error(`Memory restore failed: ${error.message}`);
    });

    api.getReport(config.repositoryId, config.branch, controller.signal)
      .then(replaceResult)
      .catch(error => {
        if (error.name === 'AbortError') return;
        if (!error.message.includes('404')) {
          toast.error(`Report restore failed: ${error.message}`);
        }
      });

    api.getReportHistory(config.repositoryId, config.branch, controller.signal)
      .then(setScanHistory)
      .catch(error => {
        if (error.name !== 'AbortError') console.error(`Scan history restore failed: ${error.message}`);
      });

    return () => controller.abort();
  }, [api, isAuthenticated, config.repositoryId, config.branch, repositoryKeyRef, replaceResult, restoreThread, toast]);

  // Load issues when repository changes
  const loadIssues = useCallback((signal) => {
    if (!config.repositoryId || typeof api?.issues !== 'function') {
      return Promise.resolve();
    }
    return api.issues(config.repositoryId, signal)
      .then(items => {
        setIssues(Array.isArray(items) ? items : []);
      })
      .catch(error => {
        if (error.name !== 'AbortError') {
          console.error(`Issues load failed: ${error.message}`);
        }
      })
      .finally(() => setLoadingIssues(false));
  }, [api, config.repositoryId]);

  useEffect(() => {
    if (!config.repositoryId) {
      return undefined;
    }
    const controller = new AbortController();
    loadIssues(controller.signal);
    return () => {
      controller.abort();
      setIssues([]);
    };
  }, [config.repositoryId, loadIssues]);

  // Refresh scan history after a completed scan
  useEffect(() => {
    if (scan.finishedAt && config.repositoryId && config.branch) {
      const controller = new AbortController();
      api.getReportHistory(config.repositoryId, config.branch, controller.signal)
        .then(setScanHistory)
        .catch(error => {
          if (error.name !== 'AbortError') console.error(`Scan history fetch failed: ${error.message}`);
        });
      return () => controller.abort();
    }
  }, [api, config.branch, config.repositoryId, scan.finishedAt]);

  // Hydrate chat messages and state upon thread restore
  useEffect(() => {
    if (!restoredSession) return;
    restoreMessages(restoredSession.messages);
    hydrate(restoredSession);
  }, [hydrate, restoreMessages, restoredSession]);

  const refreshReport = useCallback(() => {
    if (config.repositoryId && config.branch && remediation.refreshReport) {
      if (threadId && api.getThread) {
        api.getThread(threadId).then(threadData => {
          if (threadData) hydrate(threadData, { isPoll: true });
        }).catch(() => {});
      }
      remediation.refreshReport(config.repositoryId, config.branch, true).catch(error => {
        if (!error?.message?.includes('404')) toast.error(`Report restore failed: ${error.message}`);
      });
    }
  }, [config.repositoryId, config.branch, remediation, toast, threadId, api, hydrate]);

  const startScan = useCallback(async () => {
    resetForScan();
    return runScan(config);
  }, [config, resetForScan, runScan]);

  const startUploadScan = useCallback(async (file, projectName) => {
    resetForScan();
    return runUploadScan({ file, projectName, scannerName: config.scannerName });
  }, [config.scannerName, resetForScan, runUploadScan]);

  const analyzeFinding = useCallback((finding) => {
    return analyze(finding, config.repositoryId, config.branch, threadId);
  }, [analyze, config.branch, config.repositoryId, threadId]);

  const submitDecision = useCallback((decision, reason) => {
    return decide(decision, config.repositoryId, config.branch, reason);
  }, [config.branch, config.repositoryId, decide]);

  const retry = useCallback(() => {
    return retryPublish(config.repositoryId, config.branch);
  }, [config.branch, config.repositoryId, retryPublish]);

  const findings = useMemo(() => result?.findings ?? [], [result]);
  const metadata = result?.metadata ?? null;
  const counts = useMemo(() => severityCounts(findings), [findings]);

  const { submitChat, cancelStream } = useChatController({
    api,
    chat,
    repositoryId: config.repositoryId,
    branch: config.branch,
    threadId,
    findings,
    onScan: startScan,
    onAnalyze: analyzeFinding,
  });

  const forgetConversation = useCallback(async () => {
    if (!threadId) return;
    showConfirm({
      title: 'Forget conversation',
      message: 'Forget this conversation and its unpublished run details?',
      confirmText: 'Forget',
      style: 'danger',
      action: async () => {
        try {
          await forgetThread(config.repositoryId, config.branch);
          resetMessages();
          hydrate({});
          toast.success('Conversation forgotten.');
        } catch (error) {
          toast.error(`Unable to forget conversation: ${error.message}`);
        }
      }
    });
  }, [config.branch, config.repositoryId, forgetThread, hydrate, resetMessages, showConfirm, threadId, toast]);

  const forgetRepository = useCallback(async () => {
    if (!config.repositoryId || !config.branch) return;
    showConfirm({
      title: 'Forget repository memory',
      message: 'Forget approved remediation memory for this repository branch?',
      confirmText: 'Forget',
      style: 'danger',
      action: async () => {
        try {
          await api.forgetRepository(config.repositoryId, config.branch);
          toast.success('Approved repository remediation memory was forgotten.');
        } catch (error) {
          toast.error(`Unable to forget repository memory: ${error.message}`);
        }
      }
    });
  }, [api, config.branch, config.repositoryId, showConfirm, toast]);

  const clearScanHistory = useCallback(async () => {
    if (!config.repositoryId || !config.branch) return;
    showConfirm({
      title: 'Clear scan history',
      message: 'Permanently delete all scan reports, findings, and history for this repository branch?',
      confirmText: 'Clear History',
      style: 'danger',
      action: async () => {
        try {
          await api.clearScanHistory(config.repositoryId, config.branch);
          toast.success('Scan history cleared successfully.');
          scan.replaceResult(null);
          setScanHistory([]);
        } catch (error) {
          toast.error(`Failed to clear scan history: ${error.message}`);
        }
      }
    });
  }, [api, config.branch, config.repositoryId, showConfirm, toast, scan]);

  const handleBulkAction = useCallback((action, selectedIds) => {
    chat.setIsOpen(true);
    if (action === 'analyze') {
      const selectedFindings = findings.filter(f => selectedIds.includes(f.id));
      chat.addMessage('system', `Bulk analyze started for ${selectedFindings.length} vulnerabilities.`);
      analyzeBatch(selectedFindings, config.repositoryId, config.branch, threadId);
    } else if (action === 'ignore') {
      chat.addMessage('system', `Bulk ignore requested for ${selectedIds.length} findings. (Backend batch processing pending)`);
    }
  }, [findings, analyzeBatch, config.repositoryId, config.branch, threadId, chat]);

  const handleTabChange = useCallback((tab) => {
    startTransition(() => {
      setActiveTab(tab);
    });
  }, []);

  const handleSelectFinding = useCallback((finding) => {
    startTransition(() => {
      setSelectedFinding(finding);
    });
  }, [setSelectedFinding]);

  const handleCloseFindingDrawer = useCallback(() => {
    setSelectedFinding(null);
  }, [setSelectedFinding]);

  const value = useMemo(() => ({
    activeTab,
    setActiveTab,
    handleTabChange,
    scan,
    result,
    isUploadScan,
    findings,
    metadata,
    counts,
    scanHistory,
    setScanHistory,
    issues,
    loadingIssues,
    loadIssues,
    chat,
    submitChat,
    cancelStream,
    threadId,
    remediation,
    activeRun,
    runs,
    selectedFinding,
    handleSelectFinding,
    handleCloseFindingDrawer,
    isAnalyzing,
    isPublishing,
    isLoadingPreview,
    isLoadingRun,
    awaitingApproval,
    approvalPreview,
    loadApprovalPreview,
    publication,
    graphSteps,
    batchState,
    isAnalyzingMap,
    analyzeBatch,
    cancelBatch,
    startScan,
    startUploadScan,
    analyzeFinding,
    submitDecision,
    retry,
    resume,
    discard,
    refreshReport,
    handleBulkAction,
    forgetConversation,
    forgetRepository,
    clearScanHistory,
    cancelScan,
    replaceResult,
    resetForScan,
  }), [
    activeTab,
    handleTabChange,
    scan,
    result,
    isUploadScan,
    findings,
    metadata,
    counts,
    scanHistory,
    issues,
    loadingIssues,
    loadIssues,
    chat,
    submitChat,
    cancelStream,
    threadId,
    remediation,
    activeRun,
    runs,
    selectedFinding,
    handleSelectFinding,
    handleCloseFindingDrawer,
    isAnalyzing,
    isPublishing,
    isLoadingPreview,
    isLoadingRun,
    awaitingApproval,
    approvalPreview,
    loadApprovalPreview,
    publication,
    graphSteps,
    batchState,
    isAnalyzingMap,
    analyzeBatch,
    cancelBatch,
    startScan,
    startUploadScan,
    analyzeFinding,
    submitDecision,
    retry,
    resume,
    discard,
    refreshReport,
    handleBulkAction,
    forgetConversation,
    forgetRepository,
    clearScanHistory,
    cancelScan,
    replaceResult,
    resetForScan,
  ]);

  return (
    <AuditContext.Provider value={value}>
      {children}
    </AuditContext.Provider>
  );
}

/**
 * Hook to consume audit and scan state.
 */
export function useAudit() {
  const context = useContext(AuditContext);
  if (!context) {
    throw new Error('useAudit must be used within an AuditProvider');
  }
  return context;
}
