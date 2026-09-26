/** @type {import('../../types').ScanState} */
export const initialScanState = {
  status: 'idle',
  requestId: null,
  progress: null,
  result: null,
  error: null,
  startedAt: null,
  finishedAt: null,
};

const isCurrent = (state, action) => !action.requestId || action.requestId === state.requestId;

export function scanReducer(state, action) {
  switch (action.type) {
    case 'START':
      return {
        ...state,
        status: 'starting',
        requestId: action.requestId,
        progress: { step: 'init', progress: 5, message: 'Initializing security scan...' },
        error: null,
        startedAt: action.startedAt,
        finishedAt: null,
      };
    case 'PROGRESS':
      return isCurrent(state, action)
        ? { ...state, status: 'streaming', progress: action.progress, error: null }
        : state;
    case 'SUCCESS':
      return isCurrent(state, action)
        ? {
            ...state,
            status: 'completed',
            progress: { step: 'complete', progress: 100, message: action.result.message },
            result: action.result,
            error: null,
            finishedAt: action.finishedAt,
          }
        : state;
    case 'FAILURE':
      return isCurrent(state, action)
        ? { ...state, status: 'failed', progress: null, error: action.error, finishedAt: action.finishedAt }
        : state;
    case 'CANCEL':
      return { ...state, status: state.result ? 'completed' : 'idle', requestId: null,
        progress: null, finishedAt: action.finishedAt };
    case 'REPLACE_RESULT':
      return { ...state, result: action.result, error: null };
    default:
      return state;
  }
}
