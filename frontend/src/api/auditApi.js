import { readJsonSse, SseProtocolError } from './sse';

export const API_BASE_URL = '/api';

const errorFor = async (response) => {
  let message = '';
  let devDetail = '';
  try {
    const body = await response.json();
    message = body.message;
    devDetail = body.error ?? JSON.stringify(body);
  } catch {
    try { devDetail = await response.text(); } catch { /* optional */ }
  }

  if (message && typeof message === 'string') {
    return new Error(message);
  }

  // Friendly fallbacks based on status code
  switch (response.status) {
    case 400: return new Error('The request was invalid. Please check your input.');
    case 401: return new Error('Your session has expired or you are not logged in. Please log in again.');
    case 403: return new Error('You do not have permission to perform this action.');
    case 404: return new Error('The requested information could not be found.');
    case 409: return new Error('There was a conflict with the current state of the application.');
    case 429: return new Error('You are making requests too quickly. Please slow down.');
    default:
      if (response.status >= 500) {
        return new Error('An unexpected system error occurred. Please try again later.');
      }
      return new Error(`An unexpected error occurred (Code ${response.status}).`);
  }
};

export const normalizeScanResult = (payload, source = 'managed') => ({
  repositoryId: payload.repositoryId,
  repository: payload.repository ?? payload.projectName ?? '',
  branch: payload.branch ?? '',
  baseSha: payload.baseSha ?? '',
  uploadId: payload.uploadId ?? '',
  projectName: payload.projectName ?? '',
  htmlReport: payload.html_report ?? payload.htmlReport ?? '',
  reportAvailable: payload.report_available ?? Boolean(payload.html_report ?? payload.htmlReport),
  findings: payload.findings ?? [],
  metadata: payload.metadata ?? null,
  message: payload.status_message ?? payload.message ?? '',
  source: payload.source ?? source,
});

export function createAuditApi({ baseUrl = API_BASE_URL, fetchImpl = fetch } = {}) {
  let csrfToken = '';

  const request = async (path, options = {}) => {
    const method = options.method ?? 'GET';
    const headers = { ...(options.headers ?? {}) };
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method) && csrfToken) headers['X-CSRF-Token'] = csrfToken;
    return fetchImpl(`${baseUrl}${path}`, { credentials: 'include', ...options, headers });
  };

  const stream = async (path, options, onEvent) => {
    const response = await request(path, options);
    if (!response.ok) throw await errorFor(response);
    await readJsonSse(response.body, onEvent);
  };

  return {
    loginUrl: () => `${baseUrl}/auth/github/login`,

    async authSession(signal) {
      const response = await request(`/auth/session?_t=${Date.now()}`, { signal });
      if (!response.ok) throw await errorFor(response);
      const session = await response.json();
      csrfToken = session.csrfToken ?? '';
      return session;
    },

    async logout() {
      const response = await request('/auth/logout', { method: 'POST' });
      if (!response.ok) throw await errorFor(response);
      csrfToken = '';
      return response.json();
    },

    async repositories(signal) {
      const response = await request('/github/repositories', { signal });
      if (!response.ok) throw await errorFor(response);
      return response.json();
    },

    async branches(repositoryId, signal) {
      const response = await request(`/github/repositories/${encodeURIComponent(repositoryId)}/branches`, { signal });
      if (!response.ok) throw await errorFor(response);
      return response.json();
    },

    async issues(repositoryId, signal) {
      const response = await request(`/github/repositories/${encodeURIComponent(repositoryId)}/issues`, { signal });
      if (!response.ok) throw await errorFor(response);
      return response.json();
    },

    async restoreThread(repositoryId, branch, signal) {
      const response = await request('/memory/threads', {
        method: 'POST', signal, headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ repositoryId: repositoryId || null, branch: branch || null }),
      });
      if (!response.ok) throw await errorFor(response);
      return response.json();
    },

    async getThread(threadId, signal) {
      const response = await request(`/memory/threads/${encodeURIComponent(threadId)}`, { signal });
      if (!response.ok) throw await errorFor(response);
      return response.json();
    },

    async getReport(repositoryId, branch, signal) {
      const response = await request(`/reports?repositoryId=${encodeURIComponent(repositoryId)}&branch=${encodeURIComponent(branch)}`, { signal });
      if (!response.ok) throw await errorFor(response);
      return normalizeScanResult(await response.json(), 'report');
    },

    async getReportHistory(repositoryId, branch, signal) {
      const response = await request(`/reports/history?repositoryId=${encodeURIComponent(repositoryId)}&branch=${encodeURIComponent(branch)}`, { signal });
      if (!response.ok) throw await errorFor(response);
      return response.json();
    },

    async clearScanHistory(repositoryId, branch) {
      const response = await request(`/reports/history?repositoryId=${encodeURIComponent(repositoryId)}&branch=${encodeURIComponent(branch)}`, { method: 'DELETE' });
      if (!response.ok) throw await errorFor(response);
    },

    async reportArtifact(repositoryId, branch, format, signal) {
      const query = `repositoryId=${encodeURIComponent(repositoryId)}&branch=${encodeURIComponent(branch)}&format=${encodeURIComponent(format)}`;
      const response = await request(`/reports/export?${query}`, { signal });
      if (!response.ok) throw await errorFor(response);
      const disposition = response.headers.get('content-disposition') ?? '';
      const encodedName = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
      const plainName = disposition.match(/filename="?([^";]+)"?/i)?.[1];
      let filename = plainName || `auditagent-security-report.${format === 'pdf' ? 'pdf' : 'md'}`;
      if (encodedName) {
        try { filename = decodeURIComponent(encodedName); } catch { /* retain the safe fallback */ }
      }
      return { blob: await response.blob(), filename, contentType: response.headers.get('content-type') ?? '' };
    },

    async scan(payload, { signal, onEvent } = {}) {
      let result = null;
      let terminalError = null;
      await stream('/scan', {
        method: 'POST', signal, headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      }, event => {
        onEvent?.(event);
        if (event.type === 'complete' || event.type === 'cached') {
          result = normalizeScanResult(event, event.type);
        } else if (event.type === 'error') {
          terminalError = event;
        }
      });
      if (terminalError) {
        const error = new SseProtocolError(terminalError.message || 'Security scan failed.');
        error.code = terminalError.code || 'SCAN_FAILED';
        throw error;
      }
      if (!result) throw new SseProtocolError('Scan stream ended without a result event.');
      return result;
    },

    async uploadScan({ file, projectName, scannerName = 'semgrep' }, { signal, onEvent } = {}) {
      const formData = new FormData();
      formData.append('file', file);
      if (projectName) formData.append('projectName', projectName);
      formData.append('scannerName', scannerName);

      let result = null;
      let terminalError = null;

      await stream('/scan/upload', {
        method: 'POST',
        signal,
        body: formData,
      }, event => {
        onEvent?.(event);
        if (event.type === 'complete') {
          result = normalizeScanResult(event, 'upload');
        } else if (event.type === 'error') {
          terminalError = event;
        }
      });

      if (terminalError) {
        const error = new SseProtocolError(terminalError.message || 'Project upload scan failed.');
        error.code = terminalError.code || 'SCAN_FAILED';
        throw error;
      }
      if (!result) throw new SseProtocolError('Upload scan stream ended without a result event.');
      return result;
    },

    async getUploadReport(uploadId, signal) {
      const response = await request(`/reports/upload?uploadId=${encodeURIComponent(uploadId)}`, { signal });
      if (!response.ok) throw await errorFor(response);
      return normalizeScanResult(await response.json(), 'upload');
    },

    async uploadReportArtifact(uploadId, format, signal) {
      const query = `uploadId=${encodeURIComponent(uploadId)}&format=${encodeURIComponent(format)}`;
      const response = await request(`/reports/upload/export?${query}`, { signal });
      if (!response.ok) throw await errorFor(response);
      const disposition = response.headers.get('content-disposition') ?? '';
      const encodedName = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
      const plainName = disposition.match(/filename="?([^";]+)"?/i)?.[1];
      let filename = plainName || `auditagent-upload-report.${format === 'pdf' ? 'pdf' : 'md'}`;
      if (encodedName) {
        try { filename = decodeURIComponent(encodedName); } catch { /* retain safe fallback */ }
      }
      return { blob: await response.blob(), filename, contentType: response.headers.get('content-type') ?? '' };
    },

    analyze(payload, onEvent, signal) {
      return stream('/analyze', {
        method: 'POST', signal, headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      }, onEvent);
    },

    analyzeBatch(payload, onEvent, signal) {
      return stream('/analyze/batch', {
        method: 'POST', signal, headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      }, onEvent);
    },

    async approvalPreview(runId, signal) {
      const response = await request(`/runs/${encodeURIComponent(runId)}/approval-preview`, { signal });
      if (!response.ok) throw await errorFor(response);
      return response.json();
    },

    decide(runId, payload, onEvent, signal) {
      return stream(`/runs/${encodeURIComponent(runId)}/decision`, {
        method: 'POST', signal, headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      }, onEvent);
    },

    retryPublish(runId, onEvent, signal) {
      return stream(`/runs/${encodeURIComponent(runId)}/retry-publish`, { method: 'POST', signal }, onEvent);
    },

    resumeRun(runId, threadId, onEvent, signal) {
      return stream(`/runs/${encodeURIComponent(runId)}/resume?threadId=${encodeURIComponent(threadId)}`,
        { method: 'POST', signal }, onEvent);
    },

    async discardRun(runId) {
      const response = await request(`/runs/${encodeURIComponent(runId)}/discard`, { method: 'POST' });
      if (!response.ok) throw await errorFor(response);
      return response.json();
    },

    chat(payload, onEvent, signal) {
      return stream('/chat', {
        method: 'POST', signal, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload),
      }, onEvent);
    },

    async forgetThread(threadId) {
      const response = await request(`/memory/threads/${encodeURIComponent(threadId)}`, { method: 'DELETE' });
      if (!response.ok) throw await errorFor(response);
    },

    async observabilityTokens(signal) {
      const response = await request(`/observability/tokens`, { signal });
      if (!response.ok) throw await errorFor(response);
      return response.json();
    },

    async forgetRepository(repositoryId, branch) {
      const response = await request(`/memory/repositories?repositoryId=${encodeURIComponent(repositoryId)}&branch=${encodeURIComponent(branch)}`,
        { method: 'DELETE' });
      if (!response.ok) throw await errorFor(response);
      return response.json();
    },

    async getNotifications(signal) {
      const response = await request(`/notifications`, { signal });
      if (!response.ok) throw await errorFor(response);
      return response.json();
    },

    async saveNotification(type, message) {
      const response = await request(`/notifications`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ type, message })
      });
      if (!response.ok) throw await errorFor(response);
    },

    async markNotificationsRead() {
      const response = await request(`/notifications/read`, { method: 'POST' });
      if (!response.ok) throw await errorFor(response);
    },

    async getDocs(signal) {
      const response = await request(`/docs`, { signal });
      if (!response.ok) throw await errorFor(response);
      return response.json();
    },

    async getDocContent(filename, signal) {
      const response = await request(`/docs/${encodeURIComponent(filename)}`, { signal });
      if (!response.ok) throw await errorFor(response);
      return response.text();
    },
  };
}

export const auditApi = createAuditApi();
