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
      issues: vi.fn().mockResolvedValue([]),
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
    const scanButton = await screen.findByRole('button', { name: /Run Scan/i });
    const form = scanButton.closest('form');
    const repositoryButton = form.querySelector('button');
    await waitFor(() => expect(repositoryButton).not.toBeDisabled());
    fireEvent.click(repositoryButton);
    const repoOption = await screen.findByText(/octo\/repo/i);
    fireEvent.click(repoOption);
    await waitFor(() => expect(screen.getByRole('button', { name: /Run Scan/i })).not.toBeDisabled());
    fireEvent.click(screen.getByRole('button', { name: /Run Scan/i }));
    await waitFor(() => expect(api.scan).toHaveBeenCalled());
    expect(await screen.findByTitle('AuditAgent PDF Report')).toHaveAttribute('src', 'blob:audit-report');
    expect(screen.getByRole('button', { name: /Audit Report/i })).toHaveClass('border-cyan-400');
  }, 10_000);

  it('renders the Issues tab beside Findings and displays tracked issues', async () => {
    const api = {
      authSession: vi.fn().mockResolvedValue({
        configured: true, authenticated: true, csrfToken: 'csrf',
        user: { id: 'user-1', login: 'octocat', name: 'Octocat', avatarUrl: '' }
      }),
      repositories: vi.fn().mockResolvedValue([{ repositoryId: 42, fullName: 'octo/repo', name: 'repo', defaultBranch: 'main', permission: 'WRITE' }]),
      branches: vi.fn().mockResolvedValue(['main']),
      issues: vi.fn().mockResolvedValue([
        {
          id: '42-18', repositoryId: 42, issueNumber: 18, title: 'SQL Injection in Database.java',
          body: 'Fix please @auditagent', labels: ['security'], state: 'OPEN',
          targetVulnerabilityId: 'VULN-001', runId: 'run-1', remediationStatus: 'PR_OPEN',
          pullRequestNumber: 19, pullRequestUrl: 'https://github.com/octo/repo/pull/19',
          statusMessage: 'Pull request #19 opened', createdAt: '2026-10-28T10:00:00Z', updatedAt: '2026-10-28T10:05:00Z'
        }
      ]),
      restoreThread: vi.fn().mockResolvedValue({ threadId: 'thread-1', messages: [] }),
      getReport: vi.fn().mockRejectedValue(new Error('Server returned code 404')),
      getReportHistory: vi.fn().mockResolvedValue([]),
      forgetThread: vi.fn(), forgetRepository: vi.fn(), chat: vi.fn(), analyze: vi.fn(),
      decide: vi.fn(), resumeRun: vi.fn(), discardRun: vi.fn(), logout: vi.fn(), loginUrl: vi.fn(),
      getNotifications: vi.fn().mockResolvedValue([]), markNotificationsRead: vi.fn().mockResolvedValue({}),
    };
    render(<ToastProvider><App api={api} /></ToastProvider>);
    const issuesTab = await screen.findByRole('button', { name: /Issues/i });
    expect(issuesTab).toBeInTheDocument();
    fireEvent.click(issuesTab);
    expect(await screen.findByText('Tracked GitHub Issues')).toBeInTheDocument();
  });
});
