import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { FindingsView } from './FindingsView';

const findings = [
  { id: 'VULN-INFO01', filePath: 'docs/Guide.md', vulnType: 'Security note', severity: 'INFO', status: 'IGNORED', lineNumber: 4, language: 'markdown' },
  { id: 'VULN-MED001', filePath: 'src/View.java', vulnType: 'Cross-site scripting', severity: 'MEDIUM', status: 'FIXED', lineNumber: 28, language: 'java' },
  { id: 'VULN-HIGH01', filePath: 'src/Auth.java', vulnType: 'SQL Injection', severity: 'HIGH', status: 'AWAITING_APPROVAL', lineNumber: 12, language: 'java', description: 'Unsafe authorization flow', ruleId: 'java.lang.security.audit' },
  { id: 'VULN-LOW001', filePath: 'src/Config.java', vulnType: 'Debug configuration', severity: 'LOW', status: 'DETECTED', lineNumber: 8, language: 'java' },
  { id: 'VULN-HIGH02', filePath: 'src/Legacy.java', vulnType: 'Command injection', severity: 'HIGH', status: 'PATCH_FAILED', lineNumber: 42, language: 'java' },
];

describe('FindingsView', () => {
  it('sorts findings by severity while preserving scanner order within a severity', () => {
    render(<FindingsView findings={findings} onSelect={vi.fn()} />);

    expect(screen.getAllByRole('button', { name: /^Open / }).map(button => button.getAttribute('aria-label'))).toEqual([
      'Open SQL Injection finding VULN-HIGH01',
      'Open Command injection finding VULN-HIGH02',
      'Open Cross-site scripting finding VULN-MED001',
      'Open Debug configuration finding VULN-LOW001',
      'Open Security note finding VULN-INFO01',
    ]);
  });

  it('filters by severity and reports the original selected finding', () => {
    const onSelect = vi.fn();
    render(<FindingsView findings={findings} onSelect={onSelect} />);

    fireEvent.click(screen.getByText('High'));
    expect(screen.getByText('SQL Injection')).toBeInTheDocument();
    expect(screen.queryByText('Cross-site scripting')).not.toBeInTheDocument();
    fireEvent.click(screen.getByText('SQL Injection'));
    expect(onSelect).toHaveBeenCalledWith(findings[2]);
  });

  it('searches multiple metadata fields and clears all active filters', () => {
    render(<FindingsView findings={findings} onSelect={vi.fn()} />);

    fireEvent.change(screen.getByLabelText('Search findings'), { target: { value: 'authorization java.lang' } });
    expect(screen.getByText('SQL Injection')).toBeInTheDocument();
    expect(screen.queryByText('Command injection')).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'AWAITING_APPROVAL' } });
    expect(screen.getByRole('option', { name: 'Awaiting Approval (1)' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Reset' }));
    expect(screen.getByLabelText('Search findings')).toHaveValue('');
    expect(screen.getByLabelText('Status')).toHaveValue('ALL');
    expect(screen.getByText(/of 5 findings in the current audit/)).toHaveTextContent('5 of 5 findings');
  });

  it('offers a recovery action when filters have no matches', () => {
    render(<FindingsView findings={findings} onSelect={vi.fn()} />);

    fireEvent.change(screen.getByLabelText('Search findings'), { target: { value: 'does-not-exist' } });
    expect(screen.getByText('No matching findings')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Clear filters' }));
    expect(screen.getByText('SQL Injection')).toBeInTheDocument();
  });
});
