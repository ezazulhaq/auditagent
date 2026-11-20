import { describe, expect, it } from 'vitest';
import {
  createRemediationEntity,
  normalizeGraphSteps,
  normalizeHydrateData,
  normalizePreview,
  normalizePublication,
  normalizeRun,
} from './remediationNormalizer';

describe('remediationNormalizer', () => {
  it('normalizes run records with snake_case and camelCase fallbacks', () => {
    const rawSnake = { run_id: 'run-123', vulnerability_id: 'VULN-001', status: 'ACTIVE' };
    const normalized = normalizeRun(rawSnake);
    expect(normalized.runId).toBe('run-123');
    expect(normalized.vulnerabilityId).toBe('VULN-001');
    expect(normalized.status).toBe('ACTIVE');

    const fallback = normalizeRun({ status: 'QUEUED' }, 'FALLBACK-ID');
    expect(fallback.vulnerabilityId).toBe('FALLBACK-ID');
  });

  it('normalizes publication records with nested pull request attributes', () => {
    const rawPub = {
      run_id: 'run-1',
      vulnerability_id: 'VULN-1',
      pull_request_number: 42,
      pull_request_url: 'https://github.com/org/repo/pull/42',
      pull_request_state: 'OPEN',
    };
    const normalized = normalizePublication(rawPub);
    expect(normalized.pullRequestNumber).toBe(42);
    expect(normalized.pullRequestUrl).toBe('https://github.com/org/repo/pull/42');
    expect(normalized.pullRequestState).toBe('OPEN');
  });

  it('normalizes preview with approval digest', () => {
    const rawPreview = {
      run_id: 'run-1',
      vulnerability_id: 'VULN-1',
      approval_digest: 'sha256-abc',
      diff: '--- a/File.java\n+++ b/File.java',
    };
    const normalized = normalizePreview(rawPreview);
    expect(normalized.approvalDigest).toBe('sha256-abc');
    expect(normalized.runId).toBe('run-1');
  });

  it('normalizes and deduplicates 16-stage graph steps', () => {
    const rawSteps = [
      { node: 'DISCOVERY', status: 'COMPLETED' },
      { node: 'SYNTAX', status: 'COMPLETED' },
      { node: 'DISCOVERY', status: 'COMPLETED' }, // duplicate
    ];
    const steps = normalizeGraphSteps(rawSteps);
    expect(steps).toHaveLength(2);
    expect(steps[0].node).toBe('DISCOVERY');
    expect(steps[1].node).toBe('SYNTAX');
  });

  it('creates a clean default remediation entity', () => {
    const entity = createRemediationEntity('VULN-001');
    expect(entity.vulnId).toBe('VULN-001');
    expect(entity.status).toBe('DETECTED');
    expect(entity.run).toBeNull();
    expect(entity.publication).toBeNull();
    expect(entity.preview).toBeNull();
    expect(entity.graphSteps).toEqual([]);
    expect(entity.isAnalyzing).toBe(false);
  });

  it('hydrates multiple runs and publications into entities preserving preview when runId matches', () => {
    const initialEntities = {
      'VULN-001': {
        ...createRemediationEntity('VULN-001'),
        preview: { runId: 'run-1', diff: 'existing-diff' },
      },
    };

    const threadPayload = {
      activeRuns: [
        { run_id: 'run-1', vulnerability_id: 'VULN-001', status: 'AWAITING_APPROVAL' },
        { run_id: 'run-2', vulnerability_id: 'VULN-002', status: 'PR_OPEN' },
      ],
      publications: {
        'run-2': { pull_request_number: 10, pull_request_url: 'http://pr/10' },
      },
      activeFinding: { id: 'VULN-001', status: 'AWAITING_APPROVAL' },
    };

    const { entities, activeFinding } = normalizeHydrateData(threadPayload, initialEntities);

    expect(activeFinding.id).toBe('VULN-001');
    expect(entities['VULN-001'].status).toBe('AWAITING_APPROVAL');
    expect(entities['VULN-001'].preview.diff).toBe('existing-diff');
    expect(entities['VULN-002'].status).toBe('PR_OPEN');
    expect(entities['VULN-002'].publication.pullRequestNumber).toBe(10);
  });
});
