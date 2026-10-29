import { useCallback, useEffect, useRef, useState, useMemo } from 'react';

export function useRemediationController({ api, toast, onMessage, onReport, onFindings, repositoryId, branch }) {
  const [selectedFinding, setSelectedFinding] = useState(null);

  // Maps of vulnId -> run data
  const [runs, setRuns] = useState({});
  const [publications, setPublications] = useState({});
  const [previews, setPreviews] = useState({});

  const [isAnalyzingMap, setIsAnalyzingMap] = useState({});
  const [isPublishingMap, setIsPublishingMap] = useState({});
  const [graphStepsMap, setGraphStepsMap] = useState({});
  const [batchState, setBatchState] = useState(null);

  const abortRefs = useRef({});
  const batchAbortRef = useRef(null);

  useEffect(() => () => {
    Object.values(abortRefs.current).forEach(c => c?.abort());
    if (batchAbortRef.current) batchAbortRef.current.abort();
  }, []);

  const hydrate = useCallback((data) => {
    const newRuns = {};
    const newPubs = {};
    const newPreviews = {};

    if (data.activeRuns) {
      data.activeRuns.forEach(run => {
        if (!run.vulnerabilityId && run.vulnerability_id) run.vulnerabilityId = run.vulnerability_id;
        if (!run.runId && run.run_id) run.runId = run.run_id;
        if (!run.vulnerabilityId && data.activeFinding) run.vulnerabilityId = data.activeFinding.id;
        newRuns[run.vulnerabilityId] = run;
      });
    } else if (data.activeRun) {
      const run = data.activeRun;
      if (!run.vulnerabilityId && run.vulnerability_id) run.vulnerabilityId = run.vulnerability_id;
      if (!run.runId && run.run_id) run.runId = run.run_id;
      if (!run.vulnerabilityId && data.activeFinding) run.vulnerabilityId = data.activeFinding.id;
      newRuns[run.vulnerabilityId] = run;
    }

    if (data.publications) {
      Object.entries(data.publications).forEach(([runId, pub]) => {
        const vulnId = Object.values(newRuns).find(r => r.runId === runId)?.vulnerabilityId;
        if (vulnId) newPubs[vulnId] = pub;
      });
    } else if (data.publication && data.activeRun) {
      newPubs[data.activeRun.vulnerabilityId] = data.publication;
    }

    setRuns(newRuns);
    setPublications(newPubs);
    setPreviews({});
    setSelectedFinding(data.activeFinding ?? null);
  }, []);

  const resetForScan = useCallback(() => {
    setSelectedFinding(null);
    setRuns({}); setPublications({}); setPreviews({});
    setIsAnalyzingMap({}); setIsPublishingMap({}); setGraphStepsMap({});
  }, []);

  const refreshReport = useCallback(async (repoId, br, silent = false) => {
    try {
      const result = await api.getReport(repoId, br);
      if (result) {
        if (onReport) onReport(result, silent);
      }
    } catch (e) {
      if (!silent) toast?.error("Failed to refresh scan results.");
    }
  }, [api, onReport, onFindings, toast]);

  const updateRun = useCallback((vulnId, updater) => {
    setRuns(prev => ({ ...prev, [vulnId]: typeof updater === 'function' ? updater(prev[vulnId]) : updater }));
  }, []);

  const notify = useCallback((type, message) => {
    if (!message) return;
    try {
      if (api?.saveNotification) {
        api.saveNotification(type, message).catch(err => {
          console.warn('Failed to save notification to server:', err);
          if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
            window.dispatchEvent(new CustomEvent('notification-added', { detail: { type, message } }));
          }
        });
      } else if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
        window.dispatchEvent(new CustomEvent('notification-added', { detail: { type, message } }));
      }
    } catch (e) {
      console.warn('Failed to dispatch notification:', e);
    }
  }, [api]);

  const analyze = useCallback(async (finding, repoId, br, threadId) => {
    const vulnId = finding?.id;
    const targetRepoId = repoId ?? repositoryId;
    const targetBranch = br ?? branch;
    if (!targetRepoId || !targetBranch) { toast?.error?.('No repository or branch selected.'); return; }
    if (!vulnId) { toast?.error?.('No vulnerability selected.'); return; }

    setSelectedFinding(finding);
    setIsAnalyzingMap(prev => ({ ...prev, [vulnId]: true }));
    setPreviews(prev => ({ ...prev, [vulnId]: null }));
    updateRun(vulnId, null);
    setPublications(prev => ({ ...prev, [vulnId]: null }));
    setGraphStepsMap(prev => ({ ...prev, [vulnId]: [] }));

    if (abortRefs.current[vulnId]) abortRefs.current[vulnId].abort();
    const controller = new AbortController();
    abortRefs.current[vulnId] = controller;

    try {
      const runId = crypto.randomUUID();
      updateRun(vulnId, { runId, status: 'ACTIVE', vulnerabilityId: vulnId });

      await api.analyze({ vulnId, repositoryId: Number(targetRepoId), branch: targetBranch, threadId }, event => {
        if (event.type === 'graph_step') {
          setGraphStepsMap(prev => {
            const steps = prev[vulnId] || [];
            if (!steps.find(s => s.node === event.node)) {
              return { ...prev, [vulnId]: [...steps, { node: event.node, status: event.status, time: Date.now() }] };
            }
            return prev;
          });
        } else if (event.type === 'workspace_prepared') {
          updateRun(vulnId, prev => ({ ...(prev || {}), status: 'ACTIVE', runId: event.runId }));
        } else if (event.type === 'status') {
          if (event.message?.startsWith('Remediation failed') || event.status === 'FAILED') {
            toast?.error?.(event.message);
            notify('error', event.message || `Analysis failed for ${vulnId}.`);
          } else {
            onMessage('agent', event.message);
          }
        } else if (event.type === 'approval_ready') {
          setPreviews(prev => ({ ...prev, [vulnId]: event.preview }));
          updateRun(vulnId, prev => ({ ...(prev || {}), runId: event.runId, status: 'AWAITING_APPROVAL' }));
        } else if (event.type === 'analysis_complete') {
          if (event.message) onMessage('agent', event.message);
          if (event.preview) setPreviews(prev => ({ ...prev, [vulnId]: event.preview }));
          updateRun(vulnId, prev => ({
            ...(prev || {}), runId: event.runId ?? prev?.runId,
            status: event.requires_approval ? 'AWAITING_APPROVAL' : (prev?.status || 'FAILED')
          }));
          if (event.requires_approval) {
            notify('success', `Analysis completed for ${vulnId}: Remediation verified and awaiting approval.`);
          } else if (event.status === 'FAILED') {
            notify('error', event.message || `Analysis failed for ${vulnId}.`);
          }
        } else if (event.type === 'publish_progress' && event.message) {
          onMessage('system', event.message);
        } else if (event.type === 'pr_created') {
          setPublications(prev => ({ ...prev, [vulnId]: event }));
          updateRun(vulnId, prev => ({ ...(prev || {}), status: 'PR_OPEN' }));
          if (event.pullRequestNumber) {
            onMessage('agent', `Pull request #${event.pullRequestNumber} created: ${event.pullRequestUrl}`);
            notify('success', `Pull request #${event.pullRequestNumber} created for ${vulnId}.`);
          }
        } else if (event.type === 'complete') {
          if (event.message) onMessage('agent', event.message);
          updateRun(vulnId, prev => ({
            ...(prev || {}), runId: event.runId ?? prev?.runId,
            status: event.runStatus ?? prev?.status
          }));
          if (event.runStatus === 'PUBLISHED' || event.runStatus === 'PR_OPEN') {
            refreshReport(targetRepoId, targetBranch).catch(() => { });
          }
        }
      }, controller.signal);
    } catch (error) {
      if (error.name !== 'AbortError') {
        toast?.error?.(`Analysis failed: ${error.message}`);
        onMessage('agent', `Analysis failed: ${error.message}`);
        notify('error', `Analysis failed for ${vulnId}: ${error.message}`);
      }
    } finally {
      setIsAnalyzingMap(prev => ({ ...prev, [vulnId]: false }));
    }
  }, [api, onMessage, toast, refreshReport, repositoryId, branch, updateRun, notify]);

  const cancelBatch = useCallback(() => {
    if (batchAbortRef.current) {
      batchAbortRef.current.abort();
      batchAbortRef.current = null;
    }
    setBatchState(null);
    setIsAnalyzingMap({});
  }, []);

  const analyzeBatch = useCallback(async (findingsToAnalyze, repoId, br, threadId) => {
    const targetRepoId = repoId ?? repositoryId;
    const targetBranch = br ?? branch;
    if (!targetRepoId || !targetBranch) { toast?.error?.('No repository or branch selected.'); return; }
    if (!findingsToAnalyze || findingsToAnalyze.length === 0) { toast?.error?.('No vulnerabilities selected.'); return; }

    const vulnIds = findingsToAnalyze.map(f => typeof f === 'string' ? f : f?.id).filter(Boolean);
    if (vulnIds.length === 0) return;

    setIsAnalyzingMap(prev => {
      const next = { ...prev };
      vulnIds.forEach(id => { next[id] = true; });
      return next;
    });
    setPreviews(prev => {
      const next = { ...prev };
      vulnIds.forEach(id => { delete next[id]; });
      return next;
    });
    setRuns(prev => {
      const next = { ...prev };
      vulnIds.forEach(id => { next[id] = { status: 'QUEUED', vulnerabilityId: id }; });
      return next;
    });
    setGraphStepsMap(prev => {
      const next = { ...prev };
      vulnIds.forEach(id => { next[id] = []; });
      return next;
    });

    if (batchAbortRef.current) batchAbortRef.current.abort();
    const controller = new AbortController();
    batchAbortRef.current = controller;

    setBatchState({
      active: true,
      total: vulnIds.length,
      completed: 0,
      failed: 0,
      cancel: () => controller.abort()
    });

    try {
      await api.analyzeBatch({
        repositoryId: Number(targetRepoId),
        branch: targetBranch,
        threadId,
        vulnIds
      }, event => {
        const eventVulnId = event.vulnId;

        if (event.type === 'batch_start') {
          setBatchState(prev => ({ ...(prev || {}), active: true, total: event.total ?? vulnIds.length }));
        } else if (event.type === 'batch_progress') {
          setBatchState(prev => ({
            ...(prev || {}),
            active: true,
            total: event.total ?? prev?.total ?? vulnIds.length,
            completed: event.completed ?? prev?.completed ?? 0,
            failed: event.failed ?? prev?.failed ?? 0,
          }));
        } else if (event.type === 'batch_complete') {
          setBatchState(null);
          refreshReport(targetRepoId, targetBranch, true).catch(() => {});
          const total = event.total ?? vulnIds.length;
          const completed = event.completed ?? 0;
          const failed = event.failed ?? 0;
          const type = failed === 0 ? 'success' : (completed > 0 ? 'warning' : 'error');
          notify(type, `Batch analysis completed: ${completed} of ${total} vulnerabilities analyzed${failed > 0 ? ` (${failed} failed)` : ''}.`);
        }

        if (eventVulnId) {
          if (event.type === 'graph_step') {
            setGraphStepsMap(prev => {
              const steps = prev[eventVulnId] || [];
              if (!steps.find(s => s.node === event.node)) {
                return { ...prev, [eventVulnId]: [...steps, { node: event.node, status: event.status, time: Date.now() }] };
              }
              return prev;
            });
          } else if (event.type === 'workspace_prepared') {
            updateRun(eventVulnId, prev => ({ ...(prev || {}), status: 'ACTIVE', runId: event.runId }));
          } else if (event.type === 'approval_ready') {
            setPreviews(prev => ({ ...prev, [eventVulnId]: event.preview }));
            updateRun(eventVulnId, prev => ({ ...(prev || {}), runId: event.runId, status: 'AWAITING_APPROVAL' }));
          } else if (event.type === 'analysis_complete') {
            if (event.preview) setPreviews(prev => ({ ...prev, [eventVulnId]: event.preview }));
            updateRun(eventVulnId, prev => ({
              ...(prev || {}), runId: event.runId ?? prev?.runId,
              status: event.requires_approval ? 'AWAITING_APPROVAL' : (prev?.status || 'FAILED')
            }));
            setIsAnalyzingMap(prev => ({ ...prev, [eventVulnId]: false }));
            if (event.requires_approval) {
              notify('success', `Analysis completed for ${eventVulnId}: Remediation verified.`);
            } else if (event.status === 'FAILED') {
              notify('error', event.message || `Analysis failed for ${eventVulnId}.`);
            }
          } else if (event.type === 'status') {
            if (event.status === 'FAILED' || event.message?.startsWith('Remediation failed')) {
              updateRun(eventVulnId, prev => ({ ...(prev || {}), status: 'FAILED' }));
              setIsAnalyzingMap(prev => ({ ...prev, [eventVulnId]: false }));
              notify('error', event.message || `Analysis failed for ${eventVulnId}.`);
            }
          }
        }
      }, controller.signal);
    } catch (error) {
      if (error.name !== 'AbortError') {
        toast?.error?.(`Batch analysis failed: ${error.message}`);
        onMessage?.('agent', `Batch analysis failed: ${error.message}`);
        notify('error', `Batch analysis failed: ${error.message}`);
      }
    } finally {
      setBatchState(null);
      setIsAnalyzingMap(prev => {
        const next = { ...prev };
        vulnIds.forEach(id => { next[id] = false; });
        return next;
      });
      refreshReport(targetRepoId, targetBranch, true).catch(() => {});
    }
  }, [api, onMessage, toast, refreshReport, repositoryId, branch, updateRun, notify]);

  const loadApprovalPreview = useCallback(async (vulnId) => {
    const runId = runs[vulnId]?.runId;
    if (!runId) return null;
    try {
      const preview = await api.approvalPreview(runId);
      setPreviews(prev => ({ ...prev, [vulnId]: preview }));
      return preview;
    } catch (error) {
      toast?.error(error.message || 'Failed to load preview');
      return null;
    }
  }, [runs, api, toast]);

  // Poll the report periodically if ANY run is PR_OPEN
  useEffect(() => {
    let intervalId;
    const hasOpenPr = Object.values(runs).some(r => r?.status === 'PR_OPEN');
    if (hasOpenPr && repositoryId && branch) {
      intervalId = setInterval(async () => {
        try {
          await refreshReport(repositoryId, branch, true);
        } catch { }
      }, 5000);
    }
    return () => { if (intervalId) clearInterval(intervalId); };
  }, [runs, repositoryId, branch, refreshReport]);

  const decide = useCallback(async (vulnId, decision, repoId, br, reason = null) => {
    const run = runs[vulnId];
    const preview = previews[vulnId];
    if (!run?.runId) {
      onMessage('agent', 'This approval is stale. Reload the conversation before responding.');
      return;
    }
    if (decision === 'APPROVE_AND_CREATE_PR' && !preview) {
      onMessage('agent', 'Load and review the current diff before approving publication.');
      return;
    }
    setIsPublishingMap(prev => ({ ...prev, [vulnId]: decision === 'APPROVE_AND_CREATE_PR' }));
    try {
      await api.decide(run.runId, {
        vulnId: preview?.vulnerabilityId ?? run.vulnerabilityId,
        decision,
        approvalDigest: preview?.approvalDigest ?? null,
        reason,
      }, event => {
        if (event.type === 'publish_progress' && event.message) onMessage('system', event.message);
        if (event.type === 'publish_failed') {
          updateRun(vulnId, prev => ({ ...(prev || {}), status: event.runStatus ?? 'PUBLISH_FAILED' }));
          setPublications(prev => ({ ...prev, [vulnId]: { ...prev[vulnId], publishError: event.message } }));
          onMessage('agent', `Publication failed: ${event.message}`);
          notify('error', `Publication failed for ${vulnId}: ${event.message}`);
        }
        if (event.type === 'pr_created') {
          setPublications(prev => ({ ...prev, [vulnId]: event }));
          updateRun(vulnId, prev => ({ ...(prev || {}), status: 'PR_OPEN' }));
          onMessage('agent', `Pull request #${event.pullRequestNumber} created: ${event.pullRequestUrl}`);
          notify('success', `Pull request #${event.pullRequestNumber} created for ${vulnId}.`);
        }
        if (event.type === 'complete' && event.message) onMessage('agent', event.message);
      });
      await refreshReport(repoId, br);
      if (decision === 'REJECT') {
        updateRun(vulnId, null);
        setPublications(prev => { const n = { ...prev }; delete n[vulnId]; return n; });
      }
      setPreviews(prev => { const n = { ...prev }; delete n[vulnId]; return n; });
    } catch (error) {
      onMessage('agent', `Decision failed: ${error.message}`);
      notify('error', `Decision failed for ${vulnId}: ${error.message}`);
    } finally {
      setIsPublishingMap(prev => ({ ...prev, [vulnId]: false }));
    }
  }, [runs, previews, api, onMessage, refreshReport, updateRun, notify]);

  const retryPublish = useCallback(async (vulnId, repoId, br) => {
    const runId = runs[vulnId]?.runId;
    if (!runId) return;
    setIsPublishingMap(prev => ({ ...prev, [vulnId]: true }));
    try {
      await api.retryPublish(runId, event => {
        if (event.message) onMessage(event.type === 'publish_failed' ? 'agent' : 'system', event.message);
        if (event.type === 'publish_failed') {
          notify('error', `Retry publication failed for ${vulnId}: ${event.message}`);
        }
        if (event.type === 'pr_created') {
          setPublications(prev => ({ ...prev, [vulnId]: event }));
          updateRun(vulnId, prev => ({ ...(prev || {}), status: 'PR_OPEN' }));
          notify('success', `Pull request #${event.pullRequestNumber} created for ${vulnId}.`);
        }
      });
      await refreshReport(repoId, br);
    } finally { setIsPublishingMap(prev => ({ ...prev, [vulnId]: false })); }
  }, [runs, api, onMessage, refreshReport, updateRun, notify]);

  const resume = useCallback(async (vulnId, threadId) => {
    const runId = runs[vulnId]?.runId;
    if (!runId || !threadId) return;
    setIsAnalyzingMap(prev => ({ ...prev, [vulnId]: true }));
    try {
      await api.resumeRun(runId, threadId, event => {
        if (event.message) onMessage(event.type === 'status' ? 'agent' : 'system', event.message);
        if (event.type === 'analysis_complete' && event.requires_approval) {
          updateRun(vulnId, prev => ({ ...(prev || {}), status: 'AWAITING_APPROVAL' }));
        } else if (event.type === 'publish_progress' && event.message) {
          onMessage('system', event.message);
        } else if (event.type === 'pr_created') {
          setPublications(prev => ({ ...prev, [vulnId]: event }));
          updateRun(vulnId, prev => ({ ...(prev || {}), status: 'PR_OPEN' }));
          if (event.pullRequestNumber) {
            onMessage('agent', `Pull request #${event.pullRequestNumber} created: ${event.pullRequestUrl}`);
            notify('success', `Pull request #${event.pullRequestNumber} created for ${vulnId}.`);
          }
        } else if (event.type === 'complete') {
          if (event.message) onMessage('agent', event.message);
          updateRun(vulnId, prev => ({
            ...(prev || {}), runId: event.runId ?? prev?.runId,
            status: event.runStatus ?? prev?.status
          }));
        }
      });
      if (runs[vulnId]?.status !== 'PUBLISH_FAILED') await loadApprovalPreview(vulnId);
    } catch (error) {
      onMessage('agent', `Resume failed: ${error.message}`);
      notify('error', `Resume failed for ${vulnId}: ${error.message}`);
    }
    finally { setIsAnalyzingMap(prev => ({ ...prev, [vulnId]: false })); }
  }, [runs, api, loadApprovalPreview, onMessage, updateRun, notify]);

  const discard = useCallback(async (vulnId) => {
    const runId = runs[vulnId]?.runId;
    if (!runId) return;
    try {
      const data = await api.discardRun(runId);
      onMessage('agent', data.rolledBack ? 'Run discarded; the isolated workspace was removed.' : 'Run discarded.');
      setPreviews(prev => { const n = { ...prev }; delete n[vulnId]; return n; });
      updateRun(vulnId, null);
    } catch (error) { onMessage('agent', `Discard failed: ${error.message}`); }
  }, [runs, api, onMessage, updateRun]);

  // Provide derived values for the currently selected finding to keep App.jsx happy
  const activeRun = selectedFinding ? runs[selectedFinding.id] : null;
  const activeRunId = activeRun?.runId ?? null;
  const isAnalyzing = selectedFinding ? isAnalyzingMap[selectedFinding.id] : false;
  const isPublishing = selectedFinding ? isPublishingMap[selectedFinding.id] : false;
  const awaitingApproval = activeRun?.status === 'AWAITING_APPROVAL';
  const approvalPreview = selectedFinding ? previews[selectedFinding.id] : null;
  const publication = selectedFinding ? publications[selectedFinding.id] : null;
  const graphSteps = selectedFinding ? graphStepsMap[selectedFinding.id] : [];

  return {
    selectedFinding, setSelectedFinding, isAnalyzing, isPublishing, awaitingApproval,
    approvalPreview, loadApprovalPreview: () => selectedFinding && loadApprovalPreview(selectedFinding.id),
    activeRun, activeRunId, publication, graphSteps, runs, isAnalyzingMap,
    hydrate, resetForScan, analyze, analyzeBatch, cancelBatch, batchState,
    decide: (decision, repoId, br, reason) => decide(selectedFinding?.id, decision, repoId, br, reason),
    retryPublish: (repoId, br) => retryPublish(selectedFinding?.id, repoId, br),
    resume: (threadId) => resume(selectedFinding?.id, threadId),
    discard: () => discard(selectedFinding?.id),
    refreshReport,
  };
}
