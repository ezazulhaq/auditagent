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
});
