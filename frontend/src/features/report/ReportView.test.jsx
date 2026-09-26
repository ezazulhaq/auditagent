import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ReportView } from './ReportView';

describe('ReportView', () => {
  beforeEach(() => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:audit-report');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
  });
  afterEach(() => vi.restoreAllMocks());

  it('loads the authenticated PDF and offers both download formats', async () => {
    const api = { reportArtifact: vi.fn().mockResolvedValue({
      blob: new Blob(['pdf'], { type: 'application/pdf' }), filename: 'repo-security-report.pdf',
    }) };

    render(<ReportView api={api} repositoryId="42" branch="main" reportAvailable projectName="repo" />);

    expect(await screen.findByTitle('AuditAgent PDF Report')).toHaveAttribute('src', 'blob:audit-report');
    expect(api.reportArtifact).toHaveBeenCalledWith('42', 'main', 'pdf', expect.any(AbortSignal));
    expect(screen.getByRole('button', { name: /Download PDF/i })).toBeEnabled();
    expect(screen.getByRole('button', { name: /Download Markdown/i })).toBeEnabled();
  });

  it('downloads Markdown from the server as a separate artifact', async () => {
    const pdf = { blob: new Blob(['pdf']), filename: 'report.pdf' };
    const markdown = { blob: new Blob(['# report']), filename: 'report.md' };
    const api = { reportArtifact: vi.fn().mockResolvedValueOnce(pdf).mockResolvedValueOnce(markdown) };
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    render(<ReportView api={api} repositoryId="42" branch="main" reportAvailable projectName="repo" />);
    await screen.findByTitle('AuditAgent PDF Report');

    fireEvent.click(screen.getByRole('button', { name: /Download Markdown/i }));

    await waitFor(() => expect(api.reportArtifact).toHaveBeenLastCalledWith('42', 'main', 'markdown'));
    expect(click).toHaveBeenCalled();
  });

  it('surfaces a PDF load failure and retries without losing report actions', async () => {
    const artifact = { blob: new Blob(['pdf']), filename: 'report.pdf' };
    const api = { reportArtifact: vi.fn()
      .mockRejectedValueOnce(new Error('PDF generation unavailable'))
      .mockResolvedValueOnce(artifact) };
    render(<ReportView api={api} repositoryId="42" branch="main" reportAvailable projectName="repo" />);

    expect(await screen.findByRole('alert')).toHaveTextContent('PDF generation unavailable');
    expect(screen.getByRole('button', { name: /Download Markdown/i })).toBeEnabled();
    fireEvent.click(screen.getByRole('button', { name: /Retry PDF/i }));

    expect(await screen.findByTitle('AuditAgent PDF Report')).toHaveAttribute('src', 'blob:audit-report');
    expect(api.reportArtifact).toHaveBeenCalledTimes(2);
  });
});
