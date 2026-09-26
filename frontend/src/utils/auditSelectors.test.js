import { describe, expect, it } from 'vitest';
import { filterFindings, severityCounts } from './auditSelectors';

const findings = [
  { id: 'INFO', severity: 'INFO', status: 'IGNORED', vulnType: 'Note', filePath: 'docs/readme.md' },
  { id: 'HIGH-1', severity: 'HIGH', status: 'DETECTED', vulnType: 'Injection', filePath: 'src/db.java', description: 'Unsafe query construction', ruleId: 'java.sql.injection', language: 'java' },
  { id: 'LOW', severity: 'LOW', status: 'FIXED', vulnType: 'Debug mode', filePath: 'src/config.js' },
  { id: 'HIGH-2', severity: 'HIGH', status: 'AWAITING_APPROVAL', vulnType: 'Authorization', filePath: 'src/auth.java' },
  { id: 'MEDIUM', severity: 'MEDIUM', status: 'VERIFYING', vulnType: 'Cross-site scripting', filePath: 'src/view.java' },
];

describe('filterFindings', () => {
  it('returns a stable severity-priority order without mutating the source list', () => {
    const originalOrder = findings.map(finding => finding.id);

    const result = filterFindings(findings, { query: '', severity: 'ALL', status: 'ALL' });

    expect(result.map(finding => finding.id)).toEqual(['HIGH-1', 'HIGH-2', 'MEDIUM', 'LOW', 'INFO']);
    expect(findings.map(finding => finding.id)).toEqual(originalOrder);
  });

  it('matches every search term across finding metadata', () => {
    const result = filterFindings(findings, { query: '  unsafe JAVA.SQL  ', severity: 'ALL', status: 'ALL' });

    expect(result.map(finding => finding.id)).toEqual(['HIGH-1']);
  });

  it('combines severity and lifecycle-status filters', () => {
    const result = filterFindings(findings, { query: 'approval', severity: 'HIGH', status: 'AWAITING_APPROVAL' });

    expect(result.map(finding => finding.id)).toEqual(['HIGH-2']);
  });

  it('handles absent optional metadata and defaults a missing status to detected', () => {
    const incompleteFinding = { id: 'PARTIAL', severity: 'LOW' };

    expect(filterFindings([incompleteFinding], { query: 'detected', severity: 'ALL', status: 'DETECTED' })).toEqual([incompleteFinding]);
  });
});

describe('severityCounts', () => {
  it('includes informational findings', () => {
    expect(severityCounts(findings)).toEqual({ high: 2, medium: 1, low: 1, info: 1 });
  });
});
