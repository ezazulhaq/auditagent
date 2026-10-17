import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useRemediationController } from './useRemediationController';

describe('useRemediationController', () => {
  it('clears stale finding and approval state when a new scan starts', () => {
    const { result } = renderHook(() => useRemediationController({ api: {}, onMessage: vi.fn(), onReport: vi.fn(), onFindings: vi.fn() }));
    act(() => result.current.hydrate({ activeRun: { runId: 'run-1', status: 'AWAITING_APPROVAL' }, activeFinding: { id: 'VULN-OLD001' } }));
    expect(result.current.awaitingApproval).toBe(true);
    act(() => result.current.resetForScan());
    expect(result.current.awaitingApproval).toBe(false);
    expect(result.current.selectedFinding).toBeNull();
    expect(result.current.activeRunId).toBeNull();
  });

  it('binds publication to the durable run, vulnerability, and reviewed digest', async () => {
    const preview = { runId: 'run-1', vulnerabilityId: 'VULN-000001', approvalDigest: 'digest-1' };
    const api = {
      analyze: vi.fn(async (_payload, onEvent) => {
        onEvent({ type: 'workspace_prepared', runId: 'run-1', baseSha: 'abcdef' });
        onEvent({ type: 'approval_ready', runId: 'run-1', preview });
      }),
      decide: vi.fn(async (_runId, _payload, onEvent) => {
        onEvent({ type: 'pr_created', runId: 'run-1', pullRequestNumber: 7,
          pullRequestUrl: 'https://github.test/octo/repo/pull/7' });
      }),
      getReport: vi.fn().mockResolvedValue({ findings: [], metadata: null }),
    };
    const { result } = renderHook(() => useRemediationController({ api, onMessage: vi.fn(),
      onReport: vi.fn(), onFindings: vi.fn() }));
    await act(() => result.current.analyze({ id: 'VULN-000001' }, 42, 'main', 'thread-1'));
    expect(result.current.awaitingApproval).toBe(true);

    await act(() => result.current.decide('APPROVE_AND_CREATE_PR', 42, 'main'));

    expect(api.decide).toHaveBeenCalledWith('run-1', {
      vulnId: 'VULN-000001', decision: 'APPROVE_AND_CREATE_PR', approvalDigest: 'digest-1', reason: null
    }, expect.any(Function));
    expect(result.current.activeRun.status).toBe('PR_OPEN');
    expect(result.current.publication.pullRequestNumber).toBe(7);
  });

  it('orchestrates batch analysis streaming events and updates per-vulnerability state', async () => {
    const preview1 = { runId: 'run-1', vulnerabilityId: 'VULN-000001', approvalDigest: 'digest-1' };
    const api = {
      analyzeBatch: vi.fn(async (payload, onEvent) => {
        onEvent({ type: 'batch_start', total: 2, vulnIds: payload.vulnIds });
        onEvent({ type: 'graph_step', vulnId: 'VULN-000001', runId: 'run-1', node: 'clone_workspace', status: 'completed' });
        onEvent({ type: 'workspace_prepared', vulnId: 'VULN-000001', runId: 'run-1' });
        onEvent({ type: 'approval_ready', vulnId: 'VULN-000001', runId: 'run-1', preview: preview1 });
        onEvent({ type: 'analysis_complete', vulnId: 'VULN-000001', runId: 'run-1', requires_approval: true, preview: preview1 });
        onEvent({ type: 'batch_progress', vulnId: 'VULN-000001', completed: 1, failed: 0, total: 2 });
        onEvent({ type: 'status', vulnId: 'VULN-000002', status: 'FAILED', message: 'Failed' });
        onEvent({ type: 'batch_progress', vulnId: 'VULN-000002', completed: 1, failed: 1, total: 2 });
        onEvent({ type: 'batch_complete', total: 2, completed: 1, failed: 1 });
      }),
      getReport: vi.fn().mockResolvedValue({ findings: [], metadata: null }),
    };
    const { result } = renderHook(() => useRemediationController({
      api, onMessage: vi.fn(), onReport: vi.fn(), onFindings: vi.fn()
    }));

    await act(() => result.current.analyzeBatch([{ id: 'VULN-000001' }, { id: 'VULN-000002' }], 42, 'main', 'thread-1'));

    expect(api.analyzeBatch).toHaveBeenCalledWith({
      repositoryId: 42,
      branch: 'main',
      threadId: 'thread-1',
      vulnIds: ['VULN-000001', 'VULN-000002']
    }, expect.any(Function), expect.any(AbortSignal));

    expect(result.current.runs['VULN-000001'].status).toBe('AWAITING_APPROVAL');
    expect(result.current.runs['VULN-000002'].status).toBe('FAILED');
    expect(result.current.batchState).toBeNull();
  });
});
