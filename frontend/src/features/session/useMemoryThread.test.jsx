import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useMemoryThread } from './useMemoryThread';

const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; };

describe('useMemoryThread', () => {
  it('ignores an older restoration response after the repository changes', async () => {
    const older = deferred(); const newer = deferred();
    const api = { restoreThread: vi.fn().mockReturnValueOnce(older.promise).mockReturnValueOnce(newer.promise) };
    const onRestore = vi.fn();
    const { result } = renderHook(() => useMemoryThread({ api, onRestore }));
    let first; let second;
    act(() => { first = result.current.restoreThread(1, 'main'); second = result.current.restoreThread(2, 'develop'); });
    await act(async () => { newer.resolve({ threadId: 'new-thread', messages: [] }); await second; });
    await act(async () => { older.resolve({ threadId: 'old-thread', messages: [] }); await first; });
    expect(result.current.threadId).toBe('new-thread');
    expect(onRestore).toHaveBeenCalledTimes(1);
    expect(onRestore).toHaveBeenCalledWith(expect.objectContaining({ threadId: 'new-thread' }));
  });
});
