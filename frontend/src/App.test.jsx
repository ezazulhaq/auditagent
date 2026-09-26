import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import App from './App';
import { ToastProvider } from './components/Toast';

describe('App scan lifecycle', () => {
  it('opens the enterprise PDF report tab after a fresh completion', async () => {
    const api = {
      authSession: vi.fn().mockResolvedValue({
        configured: true, authenticated: true, csrfToken: 'csrf',
        user: { id: 'user-1', login: 'octocat', name: 'Octocat', avatarUrl: '' }
      }),
      repositories: vi.fn().mockResolvedValue([{ repositoryId: 42, fullName: 'octo/repo', name: 'repo', defaultBranch: 'main', permission: 'WRITE' }]),
      branches: vi.fn().mockResolvedValue(['main']),
      restoreThread: vi.fn().mockResolvedValue({ threadId: 'thread-1', messages: [] }),
      getReport: vi.fn().mockRejectedValue(new Error('Server returned code 404')),
      getReportHistory: vi.fn().mockResolvedValue([]),
      scan: vi.fn().mockResolvedValue({
        repositoryId: 42, repository: 'octo/repo', branch: 'main', baseSha: 'abc', source: 'fresh',
        htmlReport: '<html><body>Legacy compatibility</body></html>', reportAvailable: true,
        findings: [], metadata: { projectName: 'repo', totalFilesScanned: 2 }, message: 'Complete',
      }),
      reportArtifact: vi.fn().mockResolvedValue({ blob: new Blob(['pdf'], { type: 'application/pdf' }), filename: 'repo-report.pdf' }),
      forgetThread: vi.fn(), forgetRepository: vi.fn(), chat: vi.fn(), analyze: vi.fn(),
      decide: vi.fn(), resumeRun: vi.fn(), discardRun: vi.fn(), logout: vi.fn(), loginUrl: vi.fn(),
      getNotifications: vi.fn().mockResolvedValue([]), markNotificationsRead: vi.fn().mockResolvedValue({}),
    };
    render(<ToastProvider><App api={api} /></ToastProvider>);
    const repositorySelect = await screen.findByLabelText(/Repository/i);
    await waitFor(() => expect(repositorySelect.options).toHaveLength(2));
    fireEvent.change(repositorySelect, { target: { value: '42' } });
    const branchSelect = screen.getByLabelText(/Branch/i);
    await waitFor(() => expect(branchSelect.options).toHaveLength(2));
    fireEvent.click(screen.getByRole('button', { name: /Run Scan/i }));
    await waitFor(() => expect(api.scan).toHaveBeenCalled());
    expect(await screen.findByTitle('AuditAgent PDF Report')).toHaveAttribute('src', 'blob:audit-report');
    expect(screen.getByRole('button', { name: /Audit Report/i })).toHaveClass('border-cyan-400');
  }, 10_000);
});
