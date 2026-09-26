import { useCallback, useEffect, useRef, useState } from 'react';

export function useRemediationController({ api, toast, onMessage, onReport, onFindings, repositoryId, branch }) {
  const [selectedFinding, setSelectedFinding] = useState(null);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [isPublishing, setIsPublishing] = useState(false);
  const [awaitingApproval, setAwaitingApproval] = useState(false);
  const [approvalPreview, setApprovalPreview] = useState(null);
  const [activeRun, setActiveRun] = useState(null);
  const [activeRunId, setActiveRunId] = useState(null);
  const [publication, setPublication] = useState(null);
  const [graphSteps, setGraphSteps] = useState([]);
  const abortRef = useRef(null);

  useEffect(() => () => abortRef.current?.abort(), []);


  const hydrate = useCallback((data) => {
    const run = data.activeRun ?? null;
    if (run) {
      if (!run.vulnerabilityId && run.vulnerability_id) run.vulnerabilityId = run.vulnerability_id;
      if (!run.runId && run.run_id) run.runId = run.run_id;
      if (!run.threadId && run.thread_id) run.threadId = run.thread_id;
    }
    setActiveRun(run);
    setActiveRunId(run?.runId ?? null);
    setPublication(data.publication ?? null);
    setSelectedFinding(data.activeFinding ?? null);
    setAwaitingApproval(run?.status === 'AWAITING_APPROVAL');
    setApprovalPreview(null);
  }, []);

  const resetForScan = useCallback(() => {
    setSelectedFinding(null); setAwaitingApproval(false); setApprovalPreview(null);
    setActiveRun(null); setActiveRunId(null); setPublication(null);
  }, []);

  const refreshReport = useCallback(async (repositoryId, branch, silent = false) => {
    const result = await api.getReport(repositoryId, branch);
    onReport(result, silent); onFindings(result.findings);
    setSelectedFinding(previous => result.findings.find(item => item.id === previous?.id) ?? previous);
    
    // Sync active run status if it resolved in the backend
    setActiveRun(prev => {
      if (!prev) return prev;
      const currentFinding = result.findings?.find(f => f.id === prev.vulnerabilityId);
      if (currentFinding && currentFinding.status === 'FIXED' && prev.status !== 'FIXED') {
        return { ...prev, status: 'FIXED' };
      }
      return prev;
    });

    return result;
  }, [api, onFindings, onReport]);

  const analyze = useCallback(async (finding, repositoryId, branch, threadId) => {
    if (!finding || !threadId) return;
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setIsAnalyzing(true); setSelectedFinding(finding); setApprovalPreview(null); setGraphSteps([]); setPublication(null);
    setActiveRun({ vulnerabilityId: finding.id, status: 'ACTIVE' });
    onMessage('system', `Starting isolated remediation for ${finding.id}.`);
    try {
      await api.analyze({ vulnId: finding.id, repositoryId: Number(repositoryId), branch, threadId }, event => {
        if (event.runId) {
          setActiveRunId(event.runId);
          setActiveRun(previous => ({
            ...(previous || {}), runId: event.runId,
            vulnerabilityId: finding.id, status: 'ACTIVE'
          }));
        }
        if (event.type === 'graph_step') {
          setGraphSteps(prev => [...prev, { node: event.node, status: event.status, iteration: event.iteration, duration_ms: event.duration_ms }]);
        } else if (event.type === 'graph_interrupt') {
          setApprovalPreview(event.preview); setAwaitingApproval(true);
          setActiveRun(previous => ({ ...(previous || {}), runId: event.runId ?? previous?.runId, status: 'AWAITING_APPROVAL' }));
        } else if (event.type === 'workspace_prepared') {
          onMessage('agent', `Isolated workspace prepared at ${event.baseSha?.slice(0, 12)}. No branch exists yet.`);
        } else if (event.type === 'status' && event.message) {
          if (event.message.startsWith('Remediation failed')) {
            toast?.error(event.message);
          } else {
            onMessage('agent', event.message);
          }
        } else if (event.type === 'approval_ready') {
          setApprovalPreview(event.preview); setAwaitingApproval(true);
          setActiveRun(previous => ({ ...(previous || {}), runId: event.runId, status: 'AWAITING_APPROVAL' }));
        } else if (event.type === 'analysis_complete') {
          if (event.message) onMessage('agent', event.message);
          setAwaitingApproval(Boolean(event.requires_approval));
          if (event.preview) setApprovalPreview(event.preview);
          setActiveRun(previous => ({
            ...(previous || {}), runId: event.runId ?? previous?.runId,
            status: event.requires_approval ? 'AWAITING_APPROVAL' : (previous?.status || 'FAILED')
          }));
        } else if (event.type === 'publish_progress' && event.message) {
          onMessage('system', event.message);
        } else if (event.type === 'pr_created') {
          setPublication(event);
          setActiveRun(previous => ({ ...(previous || {}), status: 'PR_OPEN' }));
          if (event.pullRequestNumber) onMessage('agent', `Pull request #${event.pullRequestNumber} created: ${event.pullRequestUrl}`);
        } else if (event.type === 'complete') {
          if (event.message) onMessage('agent', event.message);
          setActiveRun(previous => ({
            ...(previous || {}), runId: event.runId ?? previous?.runId,
            status: event.runStatus ?? previous?.status
          }));
          if (event.runStatus === 'PUBLISHED' || event.runStatus === 'PR_OPEN') {
            refreshReport(repositoryId, branch).catch(() => { });
          }
        }
      }, controller.signal);
    } catch (error) {
      if (error.name !== 'AbortError') {
        toast?.error(`Analysis failed: ${error.message}`);
        onMessage('agent', `Analysis failed: ${error.message}`);
      }
    } finally {
      setIsAnalyzing(false);
    }
  }, [api, onMessage, toast, refreshReport]);

  const loadApprovalPreview = useCallback(async () => {
    if (!activeRunId) return null;
    try {
      const preview = await api.approvalPreview(activeRunId);
      setApprovalPreview(preview);
      return preview;
    } catch (error) {
      toast?.error(error.message || 'Failed to load preview');
      return null;
    }
  }, [activeRunId, api, toast]);

  // Poll the report periodically if we have an open PR to catch webhook updates (e.g. merge)
  useEffect(() => {
    let intervalId;
    if (activeRun?.status === 'PR_OPEN' && repositoryId && branch) {
      intervalId = setInterval(async () => {
        try {
          if (activeRunId) {
            await refreshReport(repositoryId, branch, true);
          }
        } catch {
          // Ignore polling errors
        }
      }, 5000); // Check every 5 seconds
    }
    return () => { if (intervalId) clearInterval(intervalId); };
  }, [activeRun?.status, activeRunId, repositoryId, branch, refreshReport]);

  const decide = useCallback(async (decision, repositoryId, branch, reason = null) => {
    if (!activeRunId) {
      onMessage('agent', 'This approval is stale. Reload the conversation before responding.');
      return;
    }
    if (decision === 'APPROVE_AND_CREATE_PR' && !approvalPreview) {
      onMessage('agent', 'Load and review the current diff before approving publication.');
      return;
    }
    setIsPublishing(decision === 'APPROVE_AND_CREATE_PR');
    try {
      await api.decide(activeRunId, {
        vulnId: approvalPreview?.vulnerabilityId ?? activeRun?.vulnerabilityId,
        decision,
        approvalDigest: approvalPreview?.approvalDigest ?? null,
        reason,
      }, event => {
        if (event.type === 'publish_progress' && event.message) onMessage('system', event.message);
        if (event.type === 'publish_failed') {
          setActiveRun(previous => ({ ...(previous || {}), status: event.runStatus ?? 'PUBLISH_FAILED' }));
          setPublication(previous => ({ ...(previous || {}), publishError: event.message }));
          onMessage('agent', `Publication failed: ${event.message}`);
        }
        if (event.type === 'pr_created') {
          setPublication(event);
          setActiveRun(previous => ({ ...(previous || {}), status: 'PR_OPEN' }));
          onMessage('agent', `Pull request #${event.pullRequestNumber} created: ${event.pullRequestUrl}`);
        }
        if (event.type === 'complete' && event.message) onMessage('agent', event.message);
      });
      await refreshReport(repositoryId, branch);
      if (decision === 'REJECT') {
        setActiveRun(null); setActiveRunId(null); setPublication(null);
      }
      setAwaitingApproval(false); setApprovalPreview(null);
    } catch (error) {
      setAwaitingApproval(decision === 'APPROVE_AND_CREATE_PR');
      onMessage('agent', `Decision failed: ${error.message}`);
    } finally {
      setIsPublishing(false);
    }
  }, [activeRun, activeRunId, api, approvalPreview, onMessage, refreshReport]);

  const retryPublish = useCallback(async (repositoryId, branch) => {
    if (!activeRunId) return;
    setIsPublishing(true);
    try {
      await api.retryPublish(activeRunId, event => {
        if (event.message) onMessage(event.type === 'publish_failed' ? 'agent' : 'system', event.message);
        if (event.type === 'pr_created') {
          setPublication(event);
          setActiveRun(previous => ({ ...(previous || {}), status: 'PR_OPEN' }));
        }
      });
      await refreshReport(repositoryId, branch);
    } finally { setIsPublishing(false); }
  }, [activeRunId, api, onMessage, refreshReport]);

  const resume = useCallback(async (threadId) => {
    if (!activeRunId || !threadId) return;
    setIsAnalyzing(true);
    try {
      await api.resumeRun(activeRunId, threadId, event => {
        if (event.message) onMessage(event.type === 'status' ? 'agent' : 'system', event.message);
        if (event.type === 'analysis_complete' && event.requires_approval) {
          setAwaitingApproval(true);
          setActiveRun(previous => ({ ...(previous || {}), status: 'AWAITING_APPROVAL' }));
        } else if (event.type === 'publish_progress' && event.message) {
          onMessage('system', event.message);
        } else if (event.type === 'pr_created') {
          setPublication(event);
          setActiveRun(previous => ({ ...(previous || {}), status: 'PR_OPEN' }));
          if (event.pullRequestNumber) onMessage('agent', `Pull request #${event.pullRequestNumber} created: ${event.pullRequestUrl}`);
        } else if (event.type === 'complete') {
          if (event.message) onMessage('agent', event.message);
          setActiveRun(previous => ({
            ...(previous || {}), runId: event.runId ?? previous?.runId,
            status: event.runStatus ?? previous?.status
          }));
        }
      });
      if (activeRun?.status !== 'PUBLISH_FAILED') await loadApprovalPreview();
    } catch (error) { onMessage('agent', `Resume failed: ${error.message}`); }
    finally { setIsAnalyzing(false); }
  }, [activeRun, activeRunId, api, loadApprovalPreview, onMessage]);

  const discard = useCallback(async () => {
    if (!activeRunId) return;
    try {
      const data = await api.discardRun(activeRunId);
      onMessage('agent', data.rolledBack ? 'Run discarded; the isolated workspace was removed.' : 'Run discarded.');
      setAwaitingApproval(false); setApprovalPreview(null); setActiveRun(null); setActiveRunId(null);
    } catch (error) { onMessage('agent', `Discard failed: ${error.message}`); }
  }, [activeRunId, api, onMessage]);

  return {
    selectedFinding, setSelectedFinding, isAnalyzing, isPublishing, awaitingApproval,
    approvalPreview, loadApprovalPreview, activeRun, activeRunId, publication, graphSteps,
    hydrate, resetForScan, analyze, decide, retryPublish, resume, discard, refreshReport,
  };
}
