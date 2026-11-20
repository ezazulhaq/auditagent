import { describe, expect, it } from 'vitest';
import {
  initialRemediationState,
  remediationReducer,
  RemediationActionTypes,
  selectActiveEntity,
  selectActiveRun,
  selectAwaitingApproval,
  selectGraphSteps,
  selectHasOpenPr,
  selectIsAnalyzing,
  selectIsPublishing,
  selectRunsMap,
} from './useRemediationReducer';

describe('useRemediationReducer', () => {
  it('selects and deselects finding cleanly', () => {
    let state = initialRemediationState;
    const finding = { id: 'VULN-1', status: 'DETECTED', vulnType: 'SQL Injection' };

    state = remediationReducer(state, { type: RemediationActionTypes.SELECT_FINDING, finding });
    expect(state.selectedFinding).toEqual(finding);
    expect(state.entities['VULN-1']).toBeDefined();
    expect(state.entities['VULN-1'].status).toBe('DETECTED');

    state = remediationReducer(state, { type: RemediationActionTypes.SELECT_FINDING, finding: null });
    expect(state.selectedFinding).toBeNull();
  });

  it('updates selected finding attributes', () => {
    let state = initialRemediationState;
    const finding = { id: 'VULN-1', status: 'DETECTED', vulnType: 'SQL Injection' };
    state = remediationReducer(state, { type: RemediationActionTypes.SELECT_FINDING, finding });

    state = remediationReducer(state, {
      type: RemediationActionTypes.UPDATE_SELECTED_FINDING,
      finding: { status: 'FIXED' },
    });
    expect(state.selectedFinding.status).toBe('FIXED');
    expect(state.selectedFinding.vulnType).toBe('SQL Injection');
  });

  it('handles scan reset clearing all state and entities', () => {
    let state = {
      selectedFinding: { id: 'VULN-1' },
      entities: { 'VULN-1': { vulnId: 'VULN-1', status: 'ACTIVE' } },
      batch: { active: true },
    };

    state = remediationReducer(state, { type: RemediationActionTypes.SCAN_RESET });
    expect(state.selectedFinding).toBeNull();
    expect(state.entities).toEqual({});
    expect(state.batch).toBeNull();
  });

  it('progresses through analysis and 16-stage graph step lifecycle', () => {
    let state = initialRemediationState;
    const finding = { id: 'VULN-001', status: 'DETECTED' };
    state = remediationReducer(state, { type: RemediationActionTypes.SELECT_FINDING, finding });

    // 1. Analysis started
    state = remediationReducer(state, {
      type: RemediationActionTypes.ANALYSIS_STARTED,
      vulnId: 'VULN-001',
      runId: 'run-100',
    });
    expect(selectIsAnalyzing(state)).toBe(true);
    expect(selectActiveRun(state).status).toBe('ACTIVE');

    // 2. Workspace prepared
    state = remediationReducer(state, {
      type: RemediationActionTypes.WORKSPACE_PREPARED,
      vulnId: 'VULN-001',
      runId: 'run-100',
    });
    expect(state.entities['VULN-001'].run.status).toBe('ACTIVE');

    // 3. Graph steps received (deduplicated)
    state = remediationReducer(state, {
      type: RemediationActionTypes.GRAPH_STEP_RECEIVED,
      vulnId: 'VULN-001',
      step: { node: 'DISCOVERY', status: 'RUNNING' },
    });
    state = remediationReducer(state, {
      type: RemediationActionTypes.GRAPH_STEP_RECEIVED,
      vulnId: 'VULN-001',
      step: { node: 'DISCOVERY', status: 'RUNNING' },
    });
    state = remediationReducer(state, {
      type: RemediationActionTypes.GRAPH_STEP_RECEIVED,
      vulnId: 'VULN-001',
      step: { node: 'SYNTAX', status: 'RUNNING' },
    });
    expect(selectGraphSteps(state)).toHaveLength(2);

    // 4. Approval ready
    const preview = { diff: 'fixed code', approvalDigest: 'digest-123' };
    state = remediationReducer(state, {
      type: RemediationActionTypes.APPROVAL_READY,
      vulnId: 'VULN-001',
      preview,
      runId: 'run-100',
    });
    expect(selectAwaitingApproval(state)).toBe(true);
    expect(state.entities['VULN-001'].preview.approvalDigest).toBe('digest-123');

    // 5. Analysis complete
    state = remediationReducer(state, {
      type: RemediationActionTypes.ANALYSIS_COMPLETE,
      vulnId: 'VULN-001',
      requires_approval: true,
      runId: 'run-100',
    });
    expect(selectIsAnalyzing(state)).toBe(false);
    expect(selectAwaitingApproval(state)).toBe(true);
  });

  it('manages publication decision and PR creation', () => {
    let state = initialRemediationState;
    const finding = { id: 'VULN-001', status: 'AWAITING_APPROVAL' };
    state = remediationReducer(state, { type: RemediationActionTypes.SELECT_FINDING, finding });

    // Start publishing
    state = remediationReducer(state, {
      type: RemediationActionTypes.DECISION_STARTED,
      vulnId: 'VULN-001',
      isPublishing: true,
    });
    expect(selectIsPublishing(state)).toBe(true);

    // PR created
    state = remediationReducer(state, {
      type: RemediationActionTypes.PR_CREATED,
      vulnId: 'VULN-001',
      publication: {
        pull_request_number: 77,
        pull_request_url: 'https://github.com/org/repo/pull/77',
      },
    });
    expect(selectIsPublishing(state)).toBe(false);
    expect(state.entities['VULN-001'].status).toBe('PR_OPEN');
    expect(state.entities['VULN-001'].publication.pullRequestNumber).toBe(77);
    expect(selectHasOpenPr(state)).toBe(true);
  });

  it('reacts to real-time PR status update when merged via server push', () => {
    let state = initialRemediationState;
    const finding = { id: 'VULN-001', status: 'PR_OPEN' };
    state = remediationReducer(state, { type: RemediationActionTypes.SELECT_FINDING, finding });
    state = remediationReducer(state, {
      type: RemediationActionTypes.PR_CREATED,
      vulnId: 'VULN-001',
      publication: { pull_request_number: 77 },
    });

    state = remediationReducer(state, {
      type: RemediationActionTypes.PR_STATUS_UPDATED,
      vulnId: 'VULN-001',
      merged: true,
      pullRequestState: 'MERGED',
    });

    expect(state.entities['VULN-001'].status).toBe('FIXED');
    expect(state.entities['VULN-001'].publication.pullRequestState).toBe('MERGED');
    expect(state.selectedFinding.status).toBe('FIXED');
    expect(selectHasOpenPr(state)).toBe(false);
  });

  it('handles batch remediation progression and cancellation', () => {
    let state = initialRemediationState;
    const vulnIds = ['VULN-1', 'VULN-2', 'VULN-3'];

    // Start batch
    state = remediationReducer(state, {
      type: RemediationActionTypes.BATCH_STARTED,
      vulnIds,
    });
    expect(state.batch.active).toBe(true);
    expect(state.batch.total).toBe(3);
    expect(selectIsAnalyzing(state, 'VULN-1')).toBe(true);
    expect(selectIsAnalyzing(state, 'VULN-2')).toBe(true);

    // Progress
    state = remediationReducer(state, {
      type: RemediationActionTypes.BATCH_PROGRESS,
      completed: 2,
      failed: 0,
    });
    expect(state.batch.completed).toBe(2);

    // Cancel / Complete
    state = remediationReducer(state, {
      type: RemediationActionTypes.BATCH_CANCEL,
    });
    expect(state.batch).toBeNull();
    expect(selectIsAnalyzing(state, 'VULN-1')).toBe(false);
  });

  it('synchronizes findings list removing resolved items', () => {
    let state = {
      selectedFinding: { id: 'VULN-1', status: 'DETECTED' },
      entities: {
        'VULN-1': { vulnId: 'VULN-1', status: 'PR_OPEN', run: { runId: 'r1' } },
        'VULN-2': { vulnId: 'VULN-2', status: 'PR_OPEN', run: { runId: 'r2' } },
      },
      batch: null,
    };

    state = remediationReducer(state, {
      type: RemediationActionTypes.SYNC_FINDINGS,
      findings: [
        { id: 'VULN-1', status: 'FIXED' },
        { id: 'VULN-2', status: 'DETECTED' },
      ],
    });

    expect(state.entities['VULN-1']).toBeUndefined();
    expect(state.entities['VULN-2']).toBeDefined();
    expect(state.selectedFinding.status).toBe('FIXED');
  });

  it('selectRunsMap extracts map for legacy callers', () => {
    const state = {
      entities: {
        'VULN-1': { vulnId: 'VULN-1', run: { runId: 'r1', status: 'ACTIVE' } },
        'VULN-2': { vulnId: 'VULN-2', run: null },
      },
    };
    const map = selectRunsMap(state);
    expect(map['VULN-1'].runId).toBe('r1');
    expect(map['VULN-2']).toBeUndefined();
  });
});
