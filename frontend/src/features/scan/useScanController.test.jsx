import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useScanController } from './useScanController';

describe('useScanController', () => {
  it('saves notifications on scan success and failure', async () => {
    const api = {
      scan: vi.fn()
        .mockResolvedValueOnce({ findings: [{ id: 'V1' }], message: 'Scan complete' })
        .mockRejectedValueOnce(new Error('Semgrep exited with code 1')),
      saveNotification: vi.fn().mockResolvedValue({}),
    };
    const ensureThread = vi.fn().mockResolvedValue('thread-123');

    const { result } = renderHook(() => useScanController({
      api,
      ensureThread,
      onMessage: vi.fn(),
      onSuccess: vi.fn(),
    }));

    // 1. Success scan
    await act(async () => {
      await result.current.startScan({ repositoryId: 42, branch: 'main', scannerName: 'semgrep' });
    });
    expect(api.saveNotification).toHaveBeenCalledWith('success', expect.stringContaining('Security scan completed on branch main'));

    // 2. Failed scan
    await act(async () => {
      await result.current.startScan({ repositoryId: 42, branch: 'main', scannerName: 'semgrep' });
    });
    expect(api.saveNotification).toHaveBeenCalledWith('error', expect.stringContaining('Security scan failed on branch main: Semgrep exited with code 1'));
  });
});
