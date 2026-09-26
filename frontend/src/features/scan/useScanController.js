import { useCallback, useEffect, useReducer, useRef } from 'react';
import { initialScanState, scanReducer } from './scanState';

export function useScanController({ api, ensureThread, onMessage, onSuccess, onStart }) {
  const [state, dispatch] = useReducer(scanReducer, initialScanState);
  const requestRef = useRef(0);
  const abortRef = useRef(null);

  useEffect(() => () => abortRef.current?.abort(), []);

  const startScan = useCallback(async ({ repositoryId, branch, scannerName, forceRescan }) => {
    if (!repositoryId || !branch) {
      onMessage('agent', 'Select an installed GitHub repository and branch first.');
      return null;
    }

    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    const requestId = ++requestRef.current;
    dispatch({ type: 'START', requestId, startedAt: Date.now() });
    onStart?.();
    onMessage('system', `Initializing managed GitHub scan on branch ${branch}`);

    try {
      const threadId = await ensureThread(repositoryId, branch, controller.signal);
      const result = await api.scan({ repositoryId: Number(repositoryId), branch, scannerName, forceRescan, threadId }, {
        signal: controller.signal,
        onEvent: (event) => {
          if (requestId !== requestRef.current) return;
          if (event.type === 'progress') {
            dispatch({ type: 'PROGRESS', requestId, progress: {
              step: event.step, progress: event.progress, message: event.message,
            } });
          } else if (event.type === 'status' && event.message) {
            onMessage('agent', event.message);
          }
        },
      });
      if (requestId !== requestRef.current) return null;
      dispatch({ type: 'SUCCESS', requestId, result, finishedAt: Date.now() });
      if (result.message) onMessage('agent', result.message);
      onSuccess?.(result);
      return result;
    } catch (error) {
      if (error.name === 'AbortError' || requestId !== requestRef.current) return null;
      dispatch({ type: 'FAILURE', requestId, error: error.message, finishedAt: Date.now() });
      onMessage('agent', `Scan failed: ${error.message}`);
      return null;
    }
  }, [api, ensureThread, onMessage, onStart, onSuccess]);

  const replaceResult = useCallback((result) => dispatch({ type: 'REPLACE_RESULT', result }), []);

  const cancel = useCallback(() => {
    abortRef.current?.abort();
    requestRef.current += 1;
    dispatch({ type: 'CANCEL', finishedAt: Date.now() });
  }, []);

  return {
    ...state,
    isScanning: state.status === 'starting' || state.status === 'streaming',
    startScan,
    cancel,
    replaceResult,
  };
}
