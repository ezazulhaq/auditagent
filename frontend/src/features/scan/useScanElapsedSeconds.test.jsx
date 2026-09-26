import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { elapsedScanSeconds, useScanElapsedSeconds } from './useScanElapsedSeconds';

describe('scan elapsed time', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('derives elapsed time from wall clock after a throttled interval gets one callback', () => {
    const startedAt = Date.UTC(2026, 6, 30, 7, 31, 0);
    vi.useFakeTimers();
    vi.setSystemTime(startedAt);
    const { result } = renderHook(() => useScanElapsedSeconds(startedAt, null));

    vi.setSystemTime(startedAt + (2 * 60 * 60 * 1000));
    act(() => vi.advanceTimersByTime(1000));

    expect(result.current).toBeGreaterThanOrEqual(2 * 60 * 60);
    expect(result.current).toBeLessThan((2 * 60 * 60) + 2);
  });

  it('keeps the terminal elapsed time stable after a scan ends', () => {
    expect(elapsedScanSeconds(1_000, 41_500, 500_000)).toBe(40);
  });
});
