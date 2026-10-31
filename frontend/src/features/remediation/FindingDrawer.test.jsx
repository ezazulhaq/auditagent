import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { FindingDrawer } from './FindingDrawer';

const finding = {
  id: 'VULN-000001', severity: 'HIGH', vulnType: 'SQL Injection', status: 'DETECTED',
  filePath: 'src/UserDao.java', lineNumber: 10, language: 'java', description: 'Unsafe query',
};

const preview = {
  runId: 'run-12345678', vulnerabilityId: finding.id, repository: 'octo/repo',
  baseBranch: 'main', baseSha: 'abcdef1234567890',
  branchName: 'auditagent/fix-vuln-000001-run-1234',
  changedFiles: ['src/UserDao.java'],
  verification: { COMPILE: 'PASS', SEMGREP: 'PASS', TESTS: 'PASS' },
  diff: 'diff --git a/src/UserDao.java b/src/UserDao.java\n@@ -9,2 +9,2 @@\n- unsafe query\n+ prepared statement',
  approvalDigest: 'digest-1',
};

describe('FindingDrawer structured approval', () => {
  it('shows the bound evidence and emits only the explicit PR decision', () => {
    const onDecision = vi.fn();
    render(<FindingDrawer finding={finding} awaitingApproval approvalPreview={preview}
      activeRun={{ runId: preview.runId, status: 'AWAITING_APPROVAL' }}
      onClose={vi.fn()} onDecision={onDecision} />);

    expect(screen.getByText('abcdef1234567890')).toBeInTheDocument();
    expect(screen.getByText(preview.branchName)).toBeInTheDocument();
    expect(screen.getByTestId('approval-diff')).toHaveTextContent('prepared statement');
    fireEvent.click(screen.getByRole('button', { name: /Approve & create PR/i }));
    expect(onDecision).toHaveBeenCalledWith('APPROVE_AND_CREATE_PR');
    expect(screen.queryByText(/Approve & apply/i)).not.toBeInTheDocument();
  });

  it('emits rejection without presenting a publish action when no preview is loaded', () => {
    const onLoadPreview = vi.fn();
    render(<FindingDrawer finding={finding} awaitingApproval
      activeRun={{ runId: preview.runId, status: 'AWAITING_APPROVAL' }}
      onClose={vi.fn()} onLoadPreview={onLoadPreview} />);

    fireEvent.click(screen.getByRole('button', { name: /Review verified diff/i }));
    expect(onLoadPreview).toHaveBeenCalledOnce();
    expect(screen.queryByRole('button', { name: /Approve & create PR/i })).not.toBeInTheDocument();
  });

  it('displays fixed and merged status when finding is FIXED even if publication prUrl is present', () => {
    const fixedFinding = { ...finding, status: 'FIXED' };
    const pub = { pullRequestNumber: 42, pullRequestUrl: 'https://github.com/org/repo/pull/42' };
    render(<FindingDrawer finding={fixedFinding} publication={pub} activeRun={{ runId: 'run-1', status: 'PR_OPEN' }} onClose={vi.fn()} />);

    expect(screen.getByText(/Merged on GitHub · finding fixed/i)).toBeInTheDocument();
    expect(screen.queryByText(/Fix is in pull request/i)).not.toBeInTheDocument();
  });
});
