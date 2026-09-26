import { describe, expect, it, vi } from 'vitest';
import { createAuditApi } from './auditApi';
import { SseProtocolError } from './sse';

const response = (body, contentType) => new Response(body, {
  status: 200,
  headers: { 'content-type': contentType },
});

describe('audit API scan adapter', () => {
  it('returns the same normalized shape for cached and fresh scans', async () => {
    const payload = { repositoryId: 42, repository: 'octo/repo', branch: 'main', baseSha: 'abc123',
      html_report: '<html>ok</html>', findings: [{ id: 'V1' }], metadata: { projectName: 'P' } };
    const cachedApi = createAuditApi({ fetchImpl: vi.fn().mockResolvedValue(response(
      `data:${JSON.stringify({ ...payload, type: 'cached' })}`, 'text/event-stream',
    )) });
    const freshApi = createAuditApi({ fetchImpl: vi.fn().mockResolvedValue(response(
      `data:${JSON.stringify({ ...payload, type: 'complete' })}`, 'text/event-stream',
    )) });

    const request = { repositoryId: 42, branch: 'main' };
    const cached = await cachedApi.scan(request);
    const fresh = await freshApi.scan(request);
    expect({ ...cached, source: undefined }).toEqual({ ...fresh, source: undefined });
  });

  it('rejects a stream with no terminal complete event', async () => {
    const api = createAuditApi({ fetchImpl: vi.fn().mockResolvedValue(response(
      'data:{"type":"progress","progress":50}', 'text/event-stream',
    )) });
    await expect(api.scan({ repositoryId: 42, branch: 'main' })).rejects.toBeInstanceOf(SseProtocolError);
  });

  it('surfaces a terminal scan error with its backend code and exact message', async () => {
    const api = createAuditApi({ fetchImpl: vi.fn().mockResolvedValue(response(
      'data:{"type":"error","code":"SCAN_TIMEOUT","message":"Semgrep timed out after 900 seconds."}',
      'text/event-stream',
    )) });

    const failure = await api.scan({ repositoryId: 42, branch: 'main' }).catch(error => error);

    expect(failure).toBeInstanceOf(SseProtocolError);
    expect(failure.code).toBe('SCAN_TIMEOUT');
    expect(failure.message).toBe('Semgrep timed out after 900 seconds.');
  });

  it('loads repository-scoped report artifacts and keeps the server filename', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(new Response('pdf-bytes', { status: 200, headers: {
      'content-type': 'application/pdf',
      'content-disposition': 'attachment; filename="repo-main-security-report.pdf"',
    } }));
    const api = createAuditApi({ fetchImpl });

    const artifact = await api.reportArtifact(42, 'feature/reporting', 'pdf');

    expect(fetchImpl).toHaveBeenCalledWith(
      '/api/reports/export?repositoryId=42&branch=feature%2Freporting&format=pdf',
      expect.objectContaining({ credentials: 'include' }),
    );
    expect(artifact.filename).toBe('repo-main-security-report.pdf');
    expect(artifact.contentType).toBe('application/pdf');
    expect(await artifact.blob.text()).toBe('pdf-bytes');
  });
});
