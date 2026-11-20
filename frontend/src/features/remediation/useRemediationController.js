import { useCallback, useEffect, useRef } from 'react';
import {
  initialRemediationState,
  RemediationActionTypes,
  useRemediationReducer,
} from './useRemediationReducer';

/**
 * useRemediationController orchestrates vulnerability remediation workflows,
 * 16-stage LangGraph4j graph updates, patch approval/rejection decisions,
 * and real-time push event subscriptions.
 * Refactored in Phase 6 to use a centralized domain reducer and zero-polling repository SSE streams.
 */
export function useRemediationController({
  api,
  toast,
  onMessage,
  onReport,
  repositoryId,
  branch,
  onOpenProfileSettings,
  threadId,
}) {
  const [state, dispatch, selectors] = useRemediationReducer(initialRemediationState);

  const abortRefs = useRef({});
  const batchAbortRef = useRef(null);
  const loadingRunVulnsRef = useRef(new Set());

  // Clean up any ongoing network streams on unmount
  useEffect(() => () => {
    Object.values(abortRefs.current).forEach(c => c?.abort());
    if (batchAbortRef.current) batchAbortRef.current.abort();
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

  const setSelectedFinding = useCallback((finding) => {
    dispatch({ type: RemediationActionTypes.SELECT_FINDING, finding });
  }, [dispatch]);

  const hydrate = useCallback((data, { isPoll = false } = {}) => {
    if (!data) return;
    dispatch({ type: RemediationActionTypes.HYDRATE, data, isPoll });
  }, [dispatch]);

  const resetForScan = useCallback(() => {
    dispatch({ type: RemediationActionTypes.SCAN_RESET });
    loadingRunVulnsRef.current.clear();
  }, [dispatch]);

  const refreshReport = useCallback(async (repoId, br, silent = false) => {
    try {
      const result = await api.getReport(repoId, br);
      if (result) {
        if (Array.isArray(result.findings)) {
          // Check for any PRs that transitioned to FIXED
          result.findings.forEach(f => {
            if (f.status === 'FIXED') {
              const entity = state.entities[f.id];
              if (entity?.run?.status === 'PR_OPEN') {
                notify('success', `Pull request merged: ${f.id} (${f.vulnType}) is now fixed.`);
                onMessage?.('agent', `Pull request merged on GitHub. Finding ${f.id} (${f.vulnType}) has been marked as fixed.`);
              }
            }
          });
          dispatch({ type: RemediationActionTypes.SYNC_FINDINGS, findings: result.findings });
        }
        if (onReport) onReport(result, silent);
      }
    } catch {
      if (!silent) toast?.error?.('Failed to refresh scan results.');
    }
  }, [api, onReport, toast, notify, onMessage, state.entities, dispatch]);

  const analyze = useCallback(async (finding, repoId, br, threadIdParam) => {
    const vulnId = finding?.id;
    const targetRepoId = repoId ?? repositoryId;
    const targetBranch = br ?? branch;
    const targetThreadId = threadIdParam ?? threadId;

    if (!targetRepoId || !targetBranch) { toast?.error?.('No repository or branch selected.'); return; }
    if (!vulnId) { toast?.error?.('No vulnerability selected.'); return; }

    loadingRunVulnsRef.current.delete(vulnId);
    if (abortRefs.current[vulnId]) abortRefs.current[vulnId].abort();
    const controller = new AbortController();
    abortRefs.current[vulnId] = controller;

    const runId = crypto.randomUUID();
    dispatch({ type: RemediationActionTypes.ANALYSIS_STARTED, vulnId, runId, finding });

    try {
      await api.analyze({
        vulnId,
        repositoryId: Number(targetRepoId),
        branch: targetBranch,
        threadId: targetThreadId,
      }, event => {
        if (event.type === 'queued') {
          onMessage?.('system', event.message || 'Remediation task queued.');
        } else if (event.type === 'graph_step') {
          dispatch({
            type: RemediationActionTypes.GRAPH_STEP_RECEIVED,
            vulnId,
            step: { node: event.node, status: event.status, time: Date.now() },
          });
        } else if (event.type === 'workspace_prepared') {
          dispatch({ type: RemediationActionTypes.WORKSPACE_PREPARED, vulnId, runId: event.runId });
        } else if (event.type === 'status') {
          if (event.message?.startsWith('Remediation failed') || event.status === 'FAILED') {
            toast?.error?.(event.message);
            notify('error', event.message || `Analysis failed for ${vulnId}.`);
            dispatch({ type: RemediationActionTypes.ANALYSIS_FAILED, vulnId, error: event.message });
            refreshReport(targetRepoId, targetBranch, true).catch(() => {});
            if (event.requiresLlmConfig && onOpenProfileSettings) {
              onOpenProfileSettings();
            }
          } else {
            onMessage?.('agent', event.message);
          }
        } else if (event.type === 'error') {
          const msg = event.message || `Analysis failed for ${vulnId}.`;
          toast?.error?.(msg);
          notify('error', msg);
          onMessage?.('agent', msg);
          dispatch({ type: RemediationActionTypes.ANALYSIS_FAILED, vulnId, error: msg });
          refreshReport(targetRepoId, targetBranch, true).catch(() => {});
          if (event.requiresLlmConfig && onOpenProfileSettings) {
            onOpenProfileSettings();
          }
        } else if (event.type === 'approval_ready') {
          dispatch({ type: RemediationActionTypes.APPROVAL_READY, vulnId, preview: event.preview, runId: event.runId });
          refreshReport(targetRepoId, targetBranch, true).catch(() => {});
        } else if (event.type === 'analysis_complete') {
          if (event.message) onMessage?.('agent', event.message);
          dispatch({
            type: RemediationActionTypes.ANALYSIS_COMPLETE,
            vulnId,
            preview: event.preview,
            runId: event.runId,
            requires_approval: event.requires_approval,
            status: event.status,
          });
          refreshReport(targetRepoId, targetBranch, true).catch(() => {});
          if (event.requires_approval) {
            notify('success', `Analysis completed for ${vulnId}: Remediation verified and awaiting approval.`);
          } else if (event.status === 'FAILED') {
            notify('error', event.message || `Analysis failed for ${vulnId}.`);
          }
        } else if (event.type === 'publish_progress' && event.message) {
          onMessage?.('system', event.message);
        } else if (event.type === 'pr_created') {
          dispatch({ type: RemediationActionTypes.PR_CREATED, vulnId, publication: event });
          if (event.pullRequestNumber) {
            onMessage?.('agent', `Pull request #${event.pullRequestNumber} created: ${event.pullRequestUrl}`);
            notify('success', `Pull request #${event.pullRequestNumber} created for ${vulnId}.`);
          }
        } else if (event.type === 'complete') {
          if (event.message) onMessage?.('agent', event.message);
          dispatch({
            type: RemediationActionTypes.ANALYSIS_COMPLETE,
            vulnId,
            runId: event.runId,
            status: event.runStatus,
          });
          if (event.runStatus === 'PUBLISHED' || event.runStatus === 'PR_OPEN') {
            refreshReport(targetRepoId, targetBranch).catch(() => {});
          }
        }
      }, controller.signal);
    } catch (error) {
      if (error.name !== 'AbortError') {
        const msg = `Analysis failed: ${error.message}`;
        toast?.error?.(msg);
        onMessage?.('agent', msg);
        notify('error', `Analysis failed for ${vulnId}: ${error.message}`);
        dispatch({ type: RemediationActionTypes.ANALYSIS_FAILED, vulnId, error: error.message });
        refreshReport(targetRepoId, targetBranch, true).catch(() => {});
        if (error.message?.includes('Profile & LLM Settings') && onOpenProfileSettings) {
          onOpenProfileSettings();
        }
      }
    } finally {
      dispatch({ type: RemediationActionTypes.SET_ANALYZING, vulnId, isAnalyzing: false });
    }
  }, [api, onMessage, toast, refreshReport, repositoryId, branch, threadId, notify, onOpenProfileSettings, dispatch]);

  const cancelBatch = useCallback(() => {
    if (batchAbortRef.current) {
      batchAbortRef.current.abort();
      batchAbortRef.current = null;
    }
    dispatch({ type: RemediationActionTypes.BATCH_CANCEL });
  }, [dispatch]);

  const analyzeBatch = useCallback(async (findingsToAnalyze, repoId, br, threadIdParam) => {
    const targetRepoId = repoId ?? repositoryId;
    const targetBranch = br ?? branch;
    const targetThreadId = threadIdParam ?? threadId;
    if (!targetRepoId || !targetBranch) { toast?.error?.('No repository or branch selected.'); return; }
    if (!findingsToAnalyze || findingsToAnalyze.length === 0) { toast?.error?.('No vulnerabilities selected.'); return; }

    const vulnIds = findingsToAnalyze.map(f => typeof f === 'string' ? f : f?.id).filter(Boolean);
    if (vulnIds.length === 0) return;

    if (batchAbortRef.current) batchAbortRef.current.abort();
    const controller = new AbortController();
    batchAbortRef.current = controller;

    dispatch({
      type: RemediationActionTypes.BATCH_STARTED,
      vulnIds,
      cancel: () => controller.abort(),
    });

    try {
      await api.analyzeBatch({
        repositoryId: Number(targetRepoId),
        branch: targetBranch,
        threadId: targetThreadId,
        vulnIds,
      }, event => {
        const eventVulnId = event.vulnId;

        if (event.type === 'batch_start') {
          dispatch({ type: RemediationActionTypes.BATCH_PROGRESS, total: event.total ?? vulnIds.length });
        } else if (event.type === 'batch_progress') {
          dispatch({
            type: RemediationActionTypes.BATCH_PROGRESS,
            total: event.total,
            completed: event.completed,
            failed: event.failed,
          });
        } else if (event.type === 'batch_complete') {
          dispatch({ type: RemediationActionTypes.BATCH_COMPLETE });
          refreshReport(targetRepoId, targetBranch, true).catch(() => {});
          const total = event.total ?? vulnIds.length;
          const completed = event.completed ?? 0;
          const failed = event.failed ?? 0;
          const type = failed === 0 ? 'success' : (completed > 0 ? 'warning' : 'error');
          notify(type, `Batch analysis completed: ${completed} of ${total} vulnerabilities analyzed${failed > 0 ? ` (${failed} failed)` : ''}.`);
        }

        if (eventVulnId) {
          if (event.type === 'graph_step') {
            dispatch({
              type: RemediationActionTypes.GRAPH_STEP_RECEIVED,
              vulnId: eventVulnId,
              step: { node: event.node, status: event.status, time: Date.now() },
            });
          } else if (event.type === 'workspace_prepared') {
            dispatch({ type: RemediationActionTypes.WORKSPACE_PREPARED, vulnId: eventVulnId, runId: event.runId });
          } else if (event.type === 'approval_ready') {
            dispatch({ type: RemediationActionTypes.APPROVAL_READY, vulnId: eventVulnId, preview: event.preview, runId: event.runId });
          } else if (event.type === 'analysis_complete') {
            dispatch({
              type: RemediationActionTypes.ANALYSIS_COMPLETE,
              vulnId: eventVulnId,
              preview: event.preview,
              runId: event.runId,
              requires_approval: event.requires_approval,
              status: event.status,
            });
            if (event.requires_approval) {
              notify('success', `Analysis completed for ${eventVulnId}: Remediation verified.`);
            } else if (event.status === 'FAILED') {
              notify('error', event.message || `Analysis failed for ${eventVulnId}.`);
            }
          } else if (event.type === 'status' || event.type === 'error') {
            if (event.status === 'FAILED' || event.message?.startsWith('Remediation failed') || event.type === 'error') {
              dispatch({ type: RemediationActionTypes.ANALYSIS_FAILED, vulnId: eventVulnId, error: event.message });
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
      dispatch({ type: RemediationActionTypes.BATCH_COMPLETE });
      refreshReport(targetRepoId, targetBranch, true).catch(() => {});
    }
  }, [api, onMessage, toast, refreshReport, repositoryId, branch, threadId, notify, dispatch]);

  // Fallback run lookup when an active finding has an unresolved run state
  useEffect(() => {
    const finding = state.selectedFinding;
    if (!finding?.id || !api?.getActiveRunForVulnerability) return undefined;
    const vulnId = finding.id;
    const entity = state.entities[vulnId];
    if (entity?.run || entity?.hasCheckedRun || loadingRunVulnsRef.current.has(vulnId)) return undefined;

    if (['AWAITING_APPROVAL', 'PR_OPEN', 'PUBLISH_FAILED', 'PUBLISHING', 'ACTIVE'].includes(finding.status)) {
      let isCurrent = true;
      loadingRunVulnsRef.current.add(vulnId);

      api.getActiveRunForVulnerability(vulnId)
        .then(data => {
          if (!isCurrent) return;
          dispatch({
            type: RemediationActionTypes.RUN_LOADED,
            vulnId,
            run: data?.run,
            publication: data?.publication,
          });
        })
        .catch(err => {
          console.warn(`Failed to fetch active run for finding ${vulnId}:`, err);
          if (isCurrent) {
            dispatch({
              type: RemediationActionTypes.RUN_LOADED,
              vulnId,
              run: null,
              publication: null,
            });
          }
        })
        .finally(() => {
          loadingRunVulnsRef.current.delete(vulnId);
        });

      return () => { isCurrent = false; };
    }
    return undefined;
  }, [state.selectedFinding?.id, state.selectedFinding?.status, state.entities, api, dispatch]);

  const loadApprovalPreview = useCallback(async (vulnId) => {
    let runId = state.entities[vulnId]?.run?.runId;
    if (!runId && api?.getActiveRunForVulnerability) {
      try {
        const data = await api.getActiveRunForVulnerability(vulnId);
        if (data?.run?.runId) {
          runId = data.run.runId;
          dispatch({
            type: RemediationActionTypes.RUN_LOADED,
            vulnId,
            run: data.run,
            publication: data.publication,
          });
        }
      } catch (err) {
        console.warn('Failed fallback run lookup in loadApprovalPreview:', err);
      }
    }
    if (!runId) return null;
    dispatch({ type: RemediationActionTypes.PREVIEW_LOADING, vulnId, isLoading: true });
    try {
      const preview = await api.approvalPreview(runId);
      dispatch({ type: RemediationActionTypes.PREVIEW_LOADED, vulnId, preview });
      return preview;
    } catch (error) {
      toast?.error?.(error.message || 'Failed to load preview');
      return null;
    } finally {
      dispatch({ type: RemediationActionTypes.PREVIEW_LOADING, vulnId, isLoading: false });
    }
  }, [state.entities, api, toast, dispatch]);

  // Real-time server push: subscribe to repository SSE events (PR merges, closures, status changes)
  // Completely eliminates the 5-second polling loop during healthy SSE connection.
  useEffect(() => {
    if (!repositoryId || !api?.subscribeRepositoryEvents) return undefined;

    const controller = new AbortController();
    let sseActive = false;
    let fallbackTimer = null;

    const startStream = async () => {
      try {
        await api.subscribeRepositoryEvents(repositoryId, branch, (event) => {
          sseActive = true;
          if (event.type === 'pr_lifecycle' && event.vulnId) {
            dispatch({
              type: RemediationActionTypes.PR_STATUS_UPDATED,
              vulnId: event.vulnId,
              merged: event.merged,
              pullRequestState: event.pullRequestState,
            });
            if (event.merged) {
              notify('success', `Pull request merged: ${event.vulnId} is now fixed.`);
              onMessage?.('agent', `Pull request merged on GitHub. Finding ${event.vulnId} has been marked as fixed.`);
            }
            refreshReport(repositoryId, branch, true).catch(() => {});
          } else if (event.type === 'report_updated' || event.type === 'scan_complete') {
            refreshReport(repositoryId, branch, true).catch(() => {});
          }
        }, controller.signal);
      } catch (err) {
        if (err.name !== 'AbortError') {
          sseActive = false;
        }
      }
    };

    startStream();

    // Resilient adaptive fallback: only backoff polls if SSE connection is disconnected
    // and there is an open PR, pausing when document is hidden.
    let backoffMs = 15000;
    const scheduleFallback = () => {
      if (controller.signal.aborted) return;
      fallbackTimer = setTimeout(async () => {
        if (!sseActive && selectors.hasOpenPr && document.visibilityState !== 'hidden') {
          try {
            if (threadId && api?.getThread) {
              try {
                const threadData = await api.getThread(threadId);
                if (threadData) hydrate(threadData, { isPoll: true });
              } catch {}
            }
            await refreshReport(repositoryId, branch, true);
            backoffMs = Math.min(backoffMs * 1.5, 60000);
          } catch {}
        }
        scheduleFallback();
      }, backoffMs);
    };

    scheduleFallback();

    return () => {
      controller.abort();
      if (fallbackTimer) clearTimeout(fallbackTimer);
    };
  }, [repositoryId, branch, api, refreshReport, threadId, hydrate, notify, onMessage, selectors.hasOpenPr, dispatch]);

  const decide = useCallback(async (vulnId, decision, repoId, br, reason = null) => {
    const entity = state.entities[vulnId];
    const run = entity?.run;
    const preview = entity?.preview;

    if (!run?.runId) {
      onMessage?.('agent', 'This approval is stale. Reload the conversation before responding.');
      return;
    }
    if (decision === 'APPROVE_AND_CREATE_PR' && !preview) {
      onMessage?.('agent', 'Load and review the current diff before approving publication.');
      return;
    }

    dispatch({
      type: RemediationActionTypes.DECISION_STARTED,
      vulnId,
      isPublishing: decision === 'APPROVE_AND_CREATE_PR',
    });

    try {
      await api.decide(run.runId, {
        vulnId: preview?.vulnerabilityId ?? run.vulnerabilityId,
        decision,
        approvalDigest: preview?.approvalDigest ?? null,
        reason,
      }, event => {
        if (event.type === 'publish_progress' && event.message) onMessage?.('system', event.message);
        if (event.type === 'publish_failed') {
          dispatch({
            type: RemediationActionTypes.DECISION_FAILED,
            vulnId,
            error: event.message,
            runStatus: event.runStatus,
          });
          onMessage?.('agent', `Publication failed: ${event.message}`);
          notify('error', `Publication failed for ${vulnId}: ${event.message}`);
        }
        if (event.type === 'pr_created') {
          dispatch({ type: RemediationActionTypes.PR_CREATED, vulnId, publication: event });
          onMessage?.('agent', `Pull request #${event.pullRequestNumber} created: ${event.pullRequestUrl}`);
          notify('success', `Pull request #${event.pullRequestNumber} created for ${vulnId}.`);
        }
        if (event.type === 'complete' && event.message) onMessage?.('agent', event.message);
      });
      await refreshReport(repoId, br);
      if (decision === 'REJECT') {
        dispatch({ type: RemediationActionTypes.DISCARD_RUN, vulnId });
      }
    } catch (error) {
      onMessage?.('agent', `Decision failed: ${error.message}`);
      notify('error', `Decision failed for ${vulnId}: ${error.message}`);
      dispatch({ type: RemediationActionTypes.DECISION_FAILED, vulnId, error: error.message });
    } finally {
      dispatch({ type: RemediationActionTypes.DECISION_STARTED, vulnId, isPublishing: false });
    }
  }, [state.entities, api, onMessage, refreshReport, notify, dispatch]);

  const retryPublish = useCallback(async (vulnId, repoId, br) => {
    const runId = state.entities[vulnId]?.run?.runId;
    if (!runId) return;
    dispatch({ type: RemediationActionTypes.DECISION_STARTED, vulnId, isPublishing: true });
    try {
      await api.retryPublish(runId, event => {
        if (event.message) onMessage?.(event.type === 'publish_failed' ? 'agent' : 'system', event.message);
        if (event.type === 'publish_failed') {
          notify('error', `Retry publication failed for ${vulnId}: ${event.message}`);
        }
        if (event.type === 'pr_created') {
          dispatch({ type: RemediationActionTypes.PR_CREATED, vulnId, publication: event });
          notify('success', `Pull request #${event.pullRequestNumber} created for ${vulnId}.`);
        }
      });
      await refreshReport(repoId, br);
    } finally {
      dispatch({ type: RemediationActionTypes.DECISION_STARTED, vulnId, isPublishing: false });
    }
  }, [state.entities, api, onMessage, refreshReport, notify, dispatch]);

  const resume = useCallback(async (vulnId, threadIdParam) => {
    const runId = state.entities[vulnId]?.run?.runId;
    if (!runId || !threadIdParam) return;
    dispatch({ type: RemediationActionTypes.SET_ANALYZING, vulnId, isAnalyzing: true });
    try {
      await api.resumeRun(runId, threadIdParam, event => {
        if (event.message) onMessage?.(event.type === 'status' ? 'agent' : 'system', event.message);
        if (event.type === 'analysis_complete' && event.requires_approval) {
          dispatch({
            type: RemediationActionTypes.ANALYSIS_COMPLETE,
            vulnId,
            requires_approval: true,
            runId,
          });
        } else if (event.type === 'publish_progress' && event.message) {
          onMessage?.('system', event.message);
        } else if (event.type === 'pr_created') {
          dispatch({ type: RemediationActionTypes.PR_CREATED, vulnId, publication: event });
          if (event.pullRequestNumber) {
            onMessage?.('agent', `Pull request #${event.pullRequestNumber} created: ${event.pullRequestUrl}`);
            notify('success', `Pull request #${event.pullRequestNumber} created for ${vulnId}.`);
          }
        } else if (event.type === 'complete') {
          if (event.message) onMessage?.('agent', event.message);
          dispatch({
            type: RemediationActionTypes.ANALYSIS_COMPLETE,
            vulnId,
            runId: event.runId ?? runId,
            status: event.runStatus,
          });
        }
      });
      if (state.entities[vulnId]?.status !== 'PUBLISH_FAILED') await loadApprovalPreview(vulnId);
    } catch (error) {
      onMessage?.('agent', `Resume failed: ${error.message}`);
      notify('error', `Resume failed for ${vulnId}: ${error.message}`);
    } finally {
      dispatch({ type: RemediationActionTypes.SET_ANALYZING, vulnId, isAnalyzing: false });
    }
  }, [state.entities, api, loadApprovalPreview, onMessage, notify, dispatch]);

  const discard = useCallback(async (vulnId) => {
    const runId = state.entities[vulnId]?.run?.runId;
    if (!runId) return;
    try {
      const data = await api.discardRun(runId);
      onMessage?.('agent', data?.rolledBack ? 'Run discarded; the isolated workspace was removed.' : 'Run discarded.');
      dispatch({ type: RemediationActionTypes.DISCARD_RUN, vulnId });
    } catch (error) {
      onMessage?.('agent', `Discard failed: ${error.message}`);
    }
  }, [state.entities, api, onMessage, dispatch]);

  return {
    selectedFinding: state.selectedFinding,
    setSelectedFinding,
    isAnalyzing: selectors.isAnalyzing,
    isPublishing: selectors.isPublishing,
    isLoadingPreview: selectors.isLoadingPreview,
    isLoadingRun: selectors.isLoadingRun,
    awaitingApproval: selectors.awaitingApproval,
    approvalPreview: selectors.approvalPreview,
    loadApprovalPreview: () => state.selectedFinding && loadApprovalPreview(state.selectedFinding.id),
    activeRun: selectors.activeRun,
    activeRunId: selectors.activeRunId,
    publication: selectors.publication,
    graphSteps: selectors.graphSteps,
    runs: selectors.runsMap,
    isAnalyzingMap: selectors.isAnalyzingMap,
    hydrate,
    resetForScan,
    analyze,
    analyzeBatch,
    cancelBatch,
    batchState: state.batch,
    decide: (decision, repoId, br, reason) => decide(state.selectedFinding?.id, decision, repoId, br, reason),
    retryPublish: (repoId, br) => retryPublish(state.selectedFinding?.id, repoId, br),
    resume: (tId) => resume(state.selectedFinding?.id, tId),
    discard: () => discard(state.selectedFinding?.id),
    refreshReport,
  };
}
