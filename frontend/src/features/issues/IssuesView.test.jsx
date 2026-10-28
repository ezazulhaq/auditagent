import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { IssuesView } from './IssuesView';

describe('IssuesView', () => {
  const sampleIssues = [
    {
      id: '42-18',
      repositoryId: 42,
      issueNumber: 18,
      title: 'Fix SQL injection in Database.java',
      body: 'Identified SQL injection @auditagent',
      labels: ['security', 'auditagent-fix'],
      state: 'OPEN',
      targetVulnerabilityId: 'VULN-000042',
      runId: 'run-101',
      remediationStatus: 'PR_OPEN',
      pullRequestNumber: 19,
      pullRequestUrl: 'https://github.com/octo/repo/pull/19',
      statusMessage: 'Pull request #19 opened',
      createdAt: '2026-10-28T10:00:00Z',
      updatedAt: '2026-10-28T10:05:00Z',
    },
    {
      id: '42-20',
      repositoryId: 42,
      issueNumber: 20,
      title: 'Resolve Cross-Site Scripting in App.jsx',
      body: 'XSS found',
      labels: ['bug'],
      state: 'OPEN',
      targetVulnerabilityId: null,
      runId: null,
      remediationStatus: 'UNMATCHED',
      pullRequestNumber: null,
      pullRequestUrl: null,
      statusMessage: 'Could not match finding',
      createdAt: '2026-10-28T11:00:00Z',
      updatedAt: '2026-10-28T11:02:00Z',
    },
  ];

  it('renders empty state when no issues exist', () => {
    render(<IssuesView issues={[]} />);
    expect(screen.getByText(/No issues tagged yet/i)).toBeInTheDocument();
    expect(screen.getAllByText(/auditagent-fix/i).length).toBeGreaterThan(0);
  });

  it('renders issues list with counts, badges, and labels', () => {
    const onSelectFinding = vi.fn();
    render(
      <IssuesView
        issues={sampleIssues}
        repository={{ fullName: 'octo/repo' }}
        onSelectFinding={onSelectFinding}
      />
    );

    expect(screen.getByText('#18')).toBeInTheDocument();
    expect(screen.getByText('Fix SQL injection in Database.java')).toBeInTheDocument();
    expect(screen.getAllByText('PR Open').length).toBeGreaterThan(0);
    expect(screen.getByText('PR #19')).toBeInTheDocument();
    expect(screen.getByText('VULN-000042')).toBeInTheDocument();
    expect(screen.getByText('Pull request #19 opened')).toBeInTheDocument();

    // Clicking target finding button triggers onSelectFinding
    fireEvent.click(screen.getByRole('button', { name: /VULN-000042/i }));
    expect(onSelectFinding).toHaveBeenCalledWith('VULN-000042');
  });

  it('filters issues by search term', () => {
    render(<IssuesView issues={sampleIssues} repository={{ fullName: 'octo/repo' }} />);

    const searchInput = screen.getByPlaceholderText(/Search issues/i);
    fireEvent.change(searchInput, { target: { value: 'Cross-Site' } });

    expect(screen.queryByText('Fix SQL injection in Database.java')).not.toBeInTheDocument();
    expect(screen.getByText('Resolve Cross-Site Scripting in App.jsx')).toBeInTheDocument();
  });

  it('filters issues by status dropdown', () => {
    render(<IssuesView issues={sampleIssues} repository={{ fullName: 'octo/repo' }} />);

    const select = screen.getByRole('combobox');
    fireEvent.change(select, { target: { value: 'UNMATCHED' } });

    expect(screen.queryByText('Fix SQL injection in Database.java')).not.toBeInTheDocument();
    expect(screen.getByText('Resolve Cross-Site Scripting in App.jsx')).toBeInTheDocument();
  });
});
