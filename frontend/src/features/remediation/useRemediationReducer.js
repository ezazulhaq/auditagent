import { useReducer, useMemo } from 'react';
import {
  createRemediationEntity,
  normalizeGraphSteps,
  normalizeHydrateData,
  normalizePreview,
  normalizePublication,
  normalizeRun,
} from './remediationNormalizer';

export const RemediationActionTypes = {
  SELECT_FINDING: 'SELECT_FINDING',
  UPDATE_SELECTED_FINDING: 'UPDATE_SELECTED_FINDING',
  SCAN_RESET: 'SCAN_RESET',
  HYDRATE: 'HYDRATE',
  ANALYSIS_STARTED: 'ANALYSIS_STARTED',
  WORKSPACE_PREPARED: 'WORKSPACE_PREPARED',
  GRAPH_STEP_RECEIVED: 'GRAPH_STEP_RECEIVED',
  APPROVAL_READY: 'APPROVAL_READY',
  ANALYSIS_COMPLETE: 'ANALYSIS_COMPLETE',
  ANALYSIS_FAILED: 'ANALYSIS_FAILED',
  SET_ANALYZING: 'SET_ANALYZING',
  DECISION_STARTED: 'DECISION_STARTED',
  DECISION_FAILED: 'DECISION_FAILED',
  PR_CREATED: 'PR_CREATED',
  RUN_CHECK_STARTED: 'RUN_CHECK_STARTED',
  RUN_LOADED: 'RUN_LOADED',
  PREVIEW_LOADING: 'PREVIEW_LOADING',
  PREVIEW_LOADED: 'PREVIEW_LOADED',
  BATCH_STARTED: 'BATCH_STARTED',
  BATCH_PROGRESS: 'BATCH_PROGRESS',
  BATCH_COMPLETE: 'BATCH_COMPLETE',
  BATCH_CANCEL: 'BATCH_CANCEL',
  PR_STATUS_UPDATED: 'PR_STATUS_UPDATED',
  SYNC_FINDINGS: 'SYNC_FINDINGS',
  DISCARD_RUN: 'DISCARD_RUN',
};

export const initialRemediationState = {
  selectedFinding: null,
  entities: {},
  batch: null,
};

export function remediationReducer(state, action) {
  switch (action.type) {
    case RemediationActionTypes.SELECT_FINDING: {
      const finding = action.finding;
      if (!finding) {
        return { ...state, selectedFinding: null };
      }
      const existing = state.entities[finding.id];
      const entity = existing || createRemediationEntity(finding.id, { status: finding.status });
      return {
        ...state,
        selectedFinding: finding,
        entities: existing ? state.entities : { ...state.entities, [finding.id]: entity },
      };
    }

    case RemediationActionTypes.UPDATE_SELECTED_FINDING: {
      if (!state.selectedFinding) return state;
      return {
        ...state,
        selectedFinding: { ...state.selectedFinding, ...action.finding },
      };
    }

    case RemediationActionTypes.SCAN_RESET:
      return {
        selectedFinding: null,
        entities: {},
        batch: null,
      };

    case RemediationActionTypes.HYDRATE: {
      const { entities, activeFinding } = normalizeHydrateData(action.data, state.entities);
      let nextSelected = state.selectedFinding;

      if (action.isPoll) {
        if (state.selectedFinding && activeFinding && state.selectedFinding.id === activeFinding.id) {
          nextSelected = { ...state.selectedFinding, ...activeFinding };
        }
      } else {
        if (state.selectedFinding) {
          if (activeFinding && state.selectedFinding.id === activeFinding.id) {
            nextSelected = { ...state.selectedFinding, ...activeFinding };
          }
        } else {
          nextSelected = activeFinding ?? null;
        }
      }

      return {
        ...state,
        entities,
        selectedFinding: nextSelected,
      };
    }

    case RemediationActionTypes.ANALYSIS_STARTED: {
      const vulnId = action.vulnId;
      const prev = state.entities[vulnId] || createRemediationEntity(vulnId);
      const updatedEntity = {
        ...prev,
        vulnId,
        status: 'ACTIVE',
        run: { runId: action.runId, status: 'ACTIVE', vulnerabilityId: vulnId },
        preview: null,
        publication: null,
        graphSteps: [],
        isAnalyzing: true,
        hasCheckedRun: true,
        error: null,
      };
      return {
        ...state,
        selectedFinding: action.finding || state.selectedFinding,
        entities: { ...state.entities, [vulnId]: updatedEntity },
      };
    }

    case RemediationActionTypes.WORKSPACE_PREPARED: {
      const vulnId = action.vulnId;
      const prev = state.entities[vulnId] || createRemediationEntity(vulnId);
      return {
        ...state,
        entities: {
          ...state.entities,
          [vulnId]: {
            ...prev,
            status: 'ACTIVE',
            run: { ...(prev.run || {}), status: 'ACTIVE', runId: action.runId },
          },
        },
      };
    }

    case RemediationActionTypes.GRAPH_STEP_RECEIVED: {
      const vulnId = action.vulnId;
      if (!vulnId) return state;
      const prev = state.entities[vulnId] || createRemediationEntity(vulnId);
      const existingSteps = prev.graphSteps || [];
      if (existingSteps.some(s => s.node === action.step?.node)) {
        return state;
      }
      return {
        ...state,
        entities: {
          ...state.entities,
          [vulnId]: {
            ...prev,
            graphSteps: normalizeGraphSteps([...existingSteps, action.step]),
          },
        },
      };
    }

    case RemediationActionTypes.APPROVAL_READY: {
      const vulnId = action.vulnId;
      const prev = state.entities[vulnId] || createRemediationEntity(vulnId);
      return {
        ...state,
        entities: {
          ...state.entities,
          [vulnId]: {
            ...prev,
            status: 'AWAITING_APPROVAL',
            preview: normalizePreview(action.preview, vulnId),
            run: {
              ...(prev.run || {}),
              runId: action.runId ?? prev.run?.runId,
              status: 'AWAITING_APPROVAL',
            },
          },
        },
      };
    }

    case RemediationActionTypes.ANALYSIS_COMPLETE: {
      const vulnId = action.vulnId;
      const prev = state.entities[vulnId] || createRemediationEntity(vulnId);
      const isAwaiting = Boolean(action.requires_approval);
      const nextStatus = isAwaiting ? 'AWAITING_APPROVAL' : (action.status || prev.status);
      return {
        ...state,
        entities: {
          ...state.entities,
          [vulnId]: {
            ...prev,
            status: nextStatus,
            isAnalyzing: false,
            preview: action.preview ? normalizePreview(action.preview, vulnId) : prev.preview,
            run: {
              ...(prev.run || {}),
              runId: action.runId ?? prev.run?.runId,
              status: nextStatus,
            },
          },
        },
      };
    }

    case RemediationActionTypes.ANALYSIS_FAILED: {
      const vulnId = action.vulnId;
      const prev = state.entities[vulnId] || createRemediationEntity(vulnId);
      return {
        ...state,
        entities: {
          ...state.entities,
          [vulnId]: {
            ...prev,
            status: 'FAILED',
            isAnalyzing: false,
            error: action.error || null,
            run: { ...(prev.run || {}), status: 'FAILED' },
          },
        },
      };
    }

    case RemediationActionTypes.SET_ANALYZING: {
      const vulnId = action.vulnId;
      const prev = state.entities[vulnId] || createRemediationEntity(vulnId);
      return {
        ...state,
        entities: {
          ...state.entities,
          [vulnId]: { ...prev, isAnalyzing: Boolean(action.isAnalyzing) },
        },
      };
    }

    case RemediationActionTypes.DECISION_STARTED: {
      const vulnId = action.vulnId;
      const prev = state.entities[vulnId] || createRemediationEntity(vulnId);
      return {
        ...state,
        entities: {
          ...state.entities,
          [vulnId]: { ...prev, isPublishing: action.isPublishing !== false },
        },
      };
    }

    case RemediationActionTypes.DECISION_FAILED: {
      const vulnId = action.vulnId;
      const prev = state.entities[vulnId] || createRemediationEntity(vulnId);
      const nextStatus = action.runStatus || 'PUBLISH_FAILED';
      return {
        ...state,
        entities: {
          ...state.entities,
          [vulnId]: {
            ...prev,
            isPublishing: false,
            status: nextStatus,
            error: action.error,
            run: { ...(prev.run || {}), status: nextStatus },
            publication: { ...(prev.publication || {}), publishError: action.error },
          },
        },
      };
    }

    case RemediationActionTypes.PR_CREATED: {
      const vulnId = action.vulnId;
      const prev = state.entities[vulnId] || createRemediationEntity(vulnId);
      return {
        ...state,
        entities: {
          ...state.entities,
          [vulnId]: {
            ...prev,
            isPublishing: false,
            status: 'PR_OPEN',
            run: { ...(prev.run || {}), status: 'PR_OPEN' },
            publication: normalizePublication(action.publication, vulnId),
          },
        },
      };
    }

    case RemediationActionTypes.RUN_CHECK_STARTED: {
      const vulnId = action.vulnId;
      const prev = state.entities[vulnId] || createRemediationEntity(vulnId);
      return {
        ...state,
        entities: {
          ...state.entities,
          [vulnId]: { ...prev, isLoadingRun: true },
        },
      };
    }

    case RemediationActionTypes.RUN_LOADED: {
      const vulnId = action.vulnId;
      const prev = state.entities[vulnId] || createRemediationEntity(vulnId);
      const run = action.run ? normalizeRun(action.run, vulnId) : null;
      const publication = action.publication ? normalizePublication(action.publication, vulnId) : (action.run ? prev.publication : null);
      return {
        ...state,
        entities: {
          ...state.entities,
          [vulnId]: {
            ...prev,
            isLoadingRun: false,
            hasCheckedRun: true,
            run,
            publication,
            status: run?.status || prev.status,
          },
        },
      };
    }

    case RemediationActionTypes.PREVIEW_LOADING: {
      const vulnId = action.vulnId;
      const prev = state.entities[vulnId] || createRemediationEntity(vulnId);
      return {
        ...state,
        entities: {
          ...state.entities,
          [vulnId]: { ...prev, isLoadingPreview: Boolean(action.isLoading) },
        },
      };
    }

    case RemediationActionTypes.PREVIEW_LOADED: {
      const vulnId = action.vulnId;
      const prev = state.entities[vulnId] || createRemediationEntity(vulnId);
      return {
        ...state,
        entities: {
          ...state.entities,
          [vulnId]: {
            ...prev,
            isLoadingPreview: false,
            preview: normalizePreview(action.preview, vulnId),
          },
        },
      };
    }

    case RemediationActionTypes.BATCH_STARTED: {
      const vulnIds = action.vulnIds || [];
      const nextEntities = { ...state.entities };
      vulnIds.forEach(id => {
        const prev = nextEntities[id] || createRemediationEntity(id);
        nextEntities[id] = {
          ...prev,
          status: 'QUEUED',
          isAnalyzing: true,
          hasCheckedRun: false,
          run: { status: 'QUEUED', vulnerabilityId: id },
          preview: null,
          graphSteps: [],
        };
      });
      return {
        ...state,
        entities: nextEntities,
        batch: {
          active: true,
          total: vulnIds.length,
          completed: 0,
          failed: 0,
          vulnIds,
          cancel: action.cancel,
        },
      };
    }

    case RemediationActionTypes.BATCH_PROGRESS: {
      if (!state.batch) return state;
      return {
        ...state,
        batch: {
          ...state.batch,
          total: action.total ?? state.batch.total,
          completed: action.completed ?? state.batch.completed,
          failed: action.failed ?? state.batch.failed,
        },
      };
    }

    case RemediationActionTypes.BATCH_COMPLETE:
    case RemediationActionTypes.BATCH_CANCEL: {
      const nextEntities = { ...state.entities };
      Object.keys(nextEntities).forEach(id => {
        if (nextEntities[id].isAnalyzing) {
          nextEntities[id] = { ...nextEntities[id], isAnalyzing: false };
        }
      });
      return {
        ...state,
        entities: nextEntities,
        batch: null,
      };
    }

    case RemediationActionTypes.PR_STATUS_UPDATED: {
      const vulnId = action.vulnId;
      const prev = state.entities[vulnId];
      if (!prev) return state;

      const isMerged = Boolean(action.merged);
      const nextStatus = isMerged ? 'FIXED' : 'DETECTED';

      const nextSelected = state.selectedFinding && state.selectedFinding.id === vulnId
        ? { ...state.selectedFinding, status: nextStatus }
        : state.selectedFinding;

      return {
        ...state,
        selectedFinding: nextSelected,
        entities: {
          ...state.entities,
          [vulnId]: {
            ...prev,
            status: nextStatus,
            run: prev.run ? { ...prev.run, status: isMerged ? 'PR_MERGED' : 'PR_CLOSED' } : null,
            publication: prev.publication
              ? { ...prev.publication, pullRequestState: isMerged ? 'MERGED' : 'CLOSED' }
              : null,
          },
        },
      };
    }

    case RemediationActionTypes.SYNC_FINDINGS: {
      const findings = action.findings;
      if (!Array.isArray(findings)) return state;

      const nextEntities = { ...state.entities };
      let changed = false;

      findings.forEach(f => {
        if (f.status === 'FIXED' || f.status === 'IGNORED') {
          if (nextEntities[f.id]) {
            delete nextEntities[f.id];
            changed = true;
          }
        }
      });

      let nextSelected = state.selectedFinding;
      if (state.selectedFinding) {
        const updated = findings.find(f => f.id === state.selectedFinding.id);
        if (updated) {
          nextSelected = { ...state.selectedFinding, ...updated };
        }
      }

      return {
        ...state,
        entities: changed ? nextEntities : state.entities,
        selectedFinding: nextSelected,
      };
    }

    case RemediationActionTypes.DISCARD_RUN: {
      const vulnId = action.vulnId;
      const prev = state.entities[vulnId];
      if (!prev) return state;
      return {
        ...state,
        entities: {
          ...state.entities,
          [vulnId]: {
            ...prev,
            status: 'DETECTED',
            run: null,
            preview: null,
            publication: null,
            graphSteps: [],
          },
        },
      };
    }

    default:
      return state;
  }
}

// Selectors
export function selectEntity(state, vulnId) {
  if (!vulnId) return null;
  return state.entities[vulnId] || null;
}

export function selectActiveEntity(state) {
  return state.selectedFinding?.id ? selectEntity(state, state.selectedFinding.id) : null;
}

export function selectActiveRun(state) {
  return selectActiveEntity(state)?.run || null;
}

export function selectActiveRunId(state) {
  return selectActiveRun(state)?.runId || null;
}

export function selectIsAnalyzing(state, vulnId = null) {
  if (vulnId) return Boolean(state.entities[vulnId]?.isAnalyzing);
  return Boolean(selectActiveEntity(state)?.isAnalyzing);
}

export function selectIsPublishing(state, vulnId = null) {
  if (vulnId) return Boolean(state.entities[vulnId]?.isPublishing);
  return Boolean(selectActiveEntity(state)?.isPublishing);
}

export function selectIsLoadingPreview(state, vulnId = null) {
  if (vulnId) return Boolean(state.entities[vulnId]?.isLoadingPreview);
  return Boolean(selectActiveEntity(state)?.isLoadingPreview);
}

export function selectIsLoadingRun(state, vulnId = null) {
  const target = vulnId ? selectEntity(state, vulnId) : selectActiveEntity(state);
  if (!target) return false;
  const isCheckingRun = state.selectedFinding?.status === 'AWAITING_APPROVAL' &&
    !target.run && !target.hasCheckedRun;
  return Boolean(target.isLoadingRun || isCheckingRun);
}

export function selectAwaitingApproval(state, vulnId = null) {
  const finding = state.selectedFinding;
  const entity = vulnId ? selectEntity(state, vulnId) : selectActiveEntity(state);
  const run = entity?.run;
  const isLoading = selectIsLoadingRun(state, vulnId);

  return Boolean(
    run?.status === 'AWAITING_APPROVAL' ||
    (Boolean(run?.runId) && finding?.status === 'AWAITING_APPROVAL') ||
    (finding?.status === 'AWAITING_APPROVAL' && isLoading)
  );
}

export function selectApprovalPreview(state, vulnId = null) {
  if (vulnId) return selectEntity(state, vulnId)?.preview || null;
  return selectActiveEntity(state)?.preview || null;
}

export function selectPublication(state, vulnId = null) {
  if (vulnId) return selectEntity(state, vulnId)?.publication || null;
  return selectActiveEntity(state)?.publication || null;
}

export function selectGraphSteps(state, vulnId = null) {
  if (vulnId) return selectEntity(state, vulnId)?.graphSteps || [];
  return selectActiveEntity(state)?.graphSteps || [];
}

export function selectHasOpenPr(state) {
  return Object.values(state.entities).some(e => e.run?.status === 'PR_OPEN' || e.status === 'PR_OPEN');
}

export function selectRunsMap(state) {
  const map = {};
  Object.entries(state.entities).forEach(([id, entity]) => {
    if (entity.run) map[id] = entity.run;
  });
  return map;
}

export function selectIsAnalyzingMap(state) {
  const map = {};
  Object.entries(state.entities).forEach(([id, entity]) => {
    map[id] = Boolean(entity.isAnalyzing);
  });
  return map;
}

export function useRemediationReducer(initial = initialRemediationState) {
  const [state, dispatch] = useReducer(remediationReducer, initial);

  const selectors = useMemo(() => ({
    activeEntity: selectActiveEntity(state),
    activeRun: selectActiveRun(state),
    activeRunId: selectActiveRunId(state),
    isAnalyzing: selectIsAnalyzing(state),
    isPublishing: selectIsPublishing(state),
    isLoadingPreview: selectIsLoadingPreview(state),
    isLoadingRun: selectIsLoadingRun(state),
    awaitingApproval: selectAwaitingApproval(state),
    approvalPreview: selectApprovalPreview(state),
    publication: selectPublication(state),
    graphSteps: selectGraphSteps(state),
    hasOpenPr: selectHasOpenPr(state),
    runsMap: selectRunsMap(state),
    isAnalyzingMap: selectIsAnalyzingMap(state),
  }), [state]);

  return [state, dispatch, selectors];
}
