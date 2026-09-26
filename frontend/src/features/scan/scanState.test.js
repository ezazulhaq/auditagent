import { describe, expect, it } from 'vitest';
import { normalizeScanResult } from '../../api/auditApi';
import { initialScanState, scanReducer } from './scanState';

const oldResult = { htmlReport: '<p>old</p>', findings: [{ id: 'OLD' }], metadata: { projectName: 'Old' } };

describe('scan state', () => {
  it('normalizes cached and fresh payloads to the same shape', () => {
    const cached = normalizeScanResult({ html_report: '<p>x</p>', findings: [], metadata: {} }, 'repo', 'cached');
    const fresh = normalizeScanResult({ html_report: '<p>x</p>', findings: [], metadata: {} }, 'repo', 'fresh');
    expect({ ...cached, source: undefined }).toEqual({ ...fresh, source: undefined });
  });

  it('preserves the last successful report while a new scan starts or fails', () => {
    const existing = { ...initialScanState, result: oldResult };
    const started = scanReducer(existing, { type: 'START', requestId: 2, startedAt: 1_000 });
    const failed = scanReducer(started, { type: 'FAILURE', requestId: 2, error: 'network', finishedAt: 5_000 });
    expect(started.result).toBe(oldResult);
    expect(failed.result).toBe(oldResult);
    expect(failed.status).toBe('failed');
    expect(failed.startedAt).toBe(1_000);
    expect(failed.finishedAt).toBe(5_000);
  });

  it('commits all terminal report data atomically', () => {
    const started = scanReducer(initialScanState, { type: 'START', requestId: 3 });
    const result = { htmlReport: '<p>new</p>', findings: [{ id: 'NEW' }], metadata: { projectName: 'New' } };
    const completed = scanReducer(started, { type: 'SUCCESS', requestId: 3, result });
    expect(completed.result).toBe(result);
    expect(completed.status).toBe('completed');
  });

  it('ignores stale progress and terminal responses', () => {
    const started = scanReducer(initialScanState, { type: 'START', requestId: 8 });
    expect(scanReducer(started, { type: 'SUCCESS', requestId: 7, result: oldResult })).toBe(started);
    expect(scanReducer(started, { type: 'PROGRESS', requestId: 7, progress: { progress: 90 } })).toBe(started);
  });

  it('does not reset timing when a Semgrep heartbeat updates progress', () => {
    const started = scanReducer(initialScanState, { type: 'START', requestId: 9, startedAt: 10_000 });
    const heartbeat = scanReducer(started, { type: 'PROGRESS', requestId: 9,
      progress: { step: 'run_semgrep', progress: 60, message: 'Still running' } });
    expect(heartbeat.startedAt).toBe(10_000);
    expect(heartbeat.finishedAt).toBeNull();
  });
});
