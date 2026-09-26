/**
 * @typedef {Object} ScanResult
 * @property {number} repositoryId
 * @property {string} repository
 * @property {string} branch
 * @property {string} baseSha
 * @property {string} htmlReport
 * @property {Array<Object>} findings
 * @property {Object|null} metadata
 * @property {string} message
 * @property {'complete'|'cached'|'report'} source
 */

/**
 * @typedef {Object} ScanState
 * @property {'idle'|'starting'|'streaming'|'completed'|'failed'} status
 * @property {number|null} requestId
 * @property {ScanProgress|null} progress
 * @property {ScanResult|null} result
 * @property {string|null} error
 * @property {number|null} startedAt
 * @property {number|null} finishedAt
 */

/**
 * @typedef {Object} ScanProgress
 * @property {string} step
 * @property {number} progress
 * @property {string} message
 */

/**
 * @typedef {Object} ScanStreamEvent
 * @property {'progress'|'status'|'workspace_prepared'|'complete'|'cached'|'error'} type
 * @property {'SCAN_TIMEOUT'|'SCAN_FAILED'=} code
 * @property {string=} step
 * @property {number=} progress
 * @property {string=} message
 * @property {string=} html_report
 * @property {boolean=} report_available
 * @property {Array<Object>=} findings
 * @property {Object=} metadata
 */

/**
 * @typedef {Object} ChatMessage
 * @property {'user'|'agent'|'system'} role
 * @property {string} text
 * @property {string=} timestamp
 */

/**
 * @typedef {Object} DurableRunState
 * @property {string} runId
 * @property {string} status
 * @property {string=} vulnerabilityId
 * @property {string=} phase
 */

export {};
