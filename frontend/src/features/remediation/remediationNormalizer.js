/**
 * Utility functions for normalizing remediation backend entities and event payloads.
 * Adheres to SRP by isolating all data shape normalization in one place.
 */

export function normalizeRun(run, fallbackVulnId = null) {
  if (!run) return null;
  const vulnerabilityId = run.vulnerabilityId || run.vulnerability_id || fallbackVulnId || null;
  const runId = run.runId || run.run_id || null;
  return {
    ...run,
    vulnerabilityId,
    runId,
  };
}

export function normalizePublication(publication, fallbackVulnId = null) {
  if (!publication) return null;
  return {
    ...publication,
    runId: publication.runId || publication.run_id || null,
    vulnerabilityId: publication.vulnerabilityId || publication.vulnerability_id || fallbackVulnId || null,
    pullRequestNumber: publication.pullRequestNumber ?? publication.pull_request_number ?? publication.prNumber ?? null,
    pullRequestUrl: publication.pullRequestUrl || publication.pull_request_url || publication.prUrl || null,
    pullRequestState: publication.pullRequestState || publication.pull_request_state || null,
  };
}

export function normalizePreview(preview, fallbackVulnId = null) {
  if (!preview) return null;
  return {
    ...preview,
    runId: preview.runId || preview.run_id || null,
    vulnerabilityId: preview.vulnerabilityId || preview.vulnerability_id || fallbackVulnId || null,
    approvalDigest: preview.approvalDigest || preview.approval_digest || null,
  };
}

export function normalizeGraphSteps(steps = []) {
  if (!Array.isArray(steps)) return [];
  const seen = new Set();
  const normalized = [];
  steps.forEach(step => {
    if (!step) return;
    const node = step.node || step.stage || 'UNKNOWN';
    if (!seen.has(node)) {
      seen.add(node);
      normalized.push({
        node,
        status: step.status || 'COMPLETED',
        time: step.time || Date.now(),
      });
    }
  });
  return normalized;
}

export function createRemediationEntity(vulnId, initial = {}) {
  return {
    vulnId,
    status: initial.status || 'DETECTED',
    run: initial.run ? normalizeRun(initial.run, vulnId) : null,
    publication: initial.publication ? normalizePublication(initial.publication, vulnId) : null,
    preview: initial.preview ? normalizePreview(initial.preview, vulnId) : null,
    graphSteps: normalizeGraphSteps(initial.graphSteps || []),
    isAnalyzing: Boolean(initial.isAnalyzing),
    isPublishing: Boolean(initial.isPublishing),
    isLoadingPreview: Boolean(initial.isLoadingPreview),
    isLoadingRun: Boolean(initial.isLoadingRun),
    hasCheckedRun: Boolean(initial.hasCheckedRun),
    error: initial.error || null,
  };
}

/**
 * Normalizes thread data payload into a map of entities keyed by vulnId.
 */
export function normalizeHydrateData(data, existingEntities = {}) {
  if (!data) return { entities: existingEntities, activeFinding: null };

  const nextEntities = { ...existingEntities };
  const runsByVulnId = {};

  if (Array.isArray(data.activeRuns)) {
    data.activeRuns.forEach(rawRun => {
      const run = normalizeRun(rawRun, data.activeFinding?.id);
      if (run?.vulnerabilityId) {
        runsByVulnId[run.vulnerabilityId] = run;
      }
    });
  } else if (data.activeRun) {
    const run = normalizeRun(data.activeRun, data.activeFinding?.id);
    if (run?.vulnerabilityId) {
      runsByVulnId[run.vulnerabilityId] = run;
    }
  }

  // Map publications: publication dict might be keyed by runId or vulnId
  const pubsByVulnId = {};
  if (data.publications && typeof data.publications === 'object') {
    Object.entries(data.publications).forEach(([key, rawPub]) => {
      const pub = normalizePublication(rawPub);
      // If key is a runId, find matching vulnId
      let targetVulnId = pub?.vulnerabilityId;
      if (!targetVulnId) {
        const matchingRun = Object.values(runsByVulnId).find(r => r.runId === key || r.runId === pub?.runId);
        targetVulnId = matchingRun?.vulnerabilityId || key;
      }
      if (targetVulnId) {
        pubsByVulnId[targetVulnId] = { ...pub, vulnerabilityId: targetVulnId };
      }
    });
  } else if (data.publication && data.activeRun) {
    const targetVulnId = data.activeRun.vulnerabilityId || data.activeRun.vulnerability_id;
    if (targetVulnId) {
      pubsByVulnId[targetVulnId] = normalizePublication(data.publication, targetVulnId);
    }
  }

  // Reconcile into nextEntities
  const allVulnIds = new Set([
    ...Object.keys(runsByVulnId),
    ...Object.keys(pubsByVulnId),
    ...(data.previews ? Object.keys(data.previews) : []),
    ...(data.activeFinding?.id ? [data.activeFinding.id] : []),
  ]);

  allVulnIds.forEach(vulnId => {
    const prev = nextEntities[vulnId] || createRemediationEntity(vulnId);
    const run = runsByVulnId[vulnId] || prev.run;
    const publication = pubsByVulnId[vulnId] || prev.publication;

    // Preserve preview if runId hasn't changed
    let preview = prev.preview;
    if (preview && run && preview.runId && preview.runId !== run.runId) {
      preview = null;
    }
    if (data.previews?.[vulnId]) {
      preview = normalizePreview(data.previews[vulnId], vulnId);
    }

    const status = run?.status || prev.status;

    nextEntities[vulnId] = {
      ...prev,
      vulnId,
      status,
      run,
      publication,
      preview,
    };
  });

  return {
    entities: nextEntities,
    activeFinding: data.activeFinding ?? null,
  };
}
