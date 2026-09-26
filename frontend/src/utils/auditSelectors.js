export const getFileName = (path = '') => path.split(/[/\\]/).pop() ?? '';
export const getDirectoryPath = (path = '') => path.split(/[/\\]/).slice(0, -1).join('/');

export const FINDING_SEVERITY_ORDER = ['HIGH', 'MEDIUM', 'LOW', 'INFO'];

const severityPriority = new Map(FINDING_SEVERITY_ORDER.map((severity, index) => [severity, index]));
const normalizeValue = (value) => String(value ?? '').toLowerCase();

export const filterFindings = (findings, { query, severity, status, activeRun }) => {
  const queryTerms = normalizeValue(query).trim().split(/\s+/).filter(Boolean);
  const selectedSeverity = String(severity || 'ALL').toUpperCase();
  const selectedStatus = String(status || 'ALL').toUpperCase();

  const activeRunFinding = activeRun ? findings.find(f => f.id === activeRun.vulnerabilityId) : null;
  const activeFilePath = activeRunFinding?.filePath;

  return findings
    .map((finding, originalIndex) => ({ finding, originalIndex }))
    .filter(({ finding }) => {
      const findingSeverity = String(finding.severity || 'INFO').toUpperCase();
      
      const isActiveGroup = activeFilePath && finding.filePath === activeFilePath 
                            && finding.status !== 'FIXED' && finding.status !== 'IGNORED';
      const findingStatus = String((isActiveGroup ? activeRun.status : finding.status) || 'DETECTED').toUpperCase();

      const searchableText = [
        finding.id,
        finding.vulnType,
        finding.filePath,
        finding.description,
        finding.ruleId,
        finding.language,
        findingSeverity,
        findingStatus,
      ].map(normalizeValue).join(' ');

      return queryTerms.every(term => searchableText.includes(term))
        && (selectedSeverity === 'ALL' || findingSeverity === selectedSeverity)
        && (selectedStatus === 'ALL' || findingStatus === selectedStatus);
    })
    .sort((left, right) => {
      const leftPriority = severityPriority.get(String(left.finding.severity || 'INFO').toUpperCase()) ?? FINDING_SEVERITY_ORDER.length;
      const rightPriority = severityPriority.get(String(right.finding.severity || 'INFO').toUpperCase()) ?? FINDING_SEVERITY_ORDER.length;
      return leftPriority - rightPriority || left.originalIndex - right.originalIndex;
    })
    .map(({ finding }) => finding);
};

export const severityCounts = (findings) => ({
  high: findings.filter(item => item.severity === 'HIGH').length,
  medium: findings.filter(item => item.severity === 'MEDIUM').length,
  low: findings.filter(item => item.severity === 'LOW').length,
  info: findings.filter(item => item.severity === 'INFO').length,
});

export const formatTime = (seconds) => `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;

export const SCAN_STEPS = [
  { id: 'init', name: 'Initialize Scan Engine', percent: 5 },
  { id: 'detect_languages', name: 'Analyze Project Structure', percent: 20 },
  { id: 'select_rules', name: 'Load Compliance Rules', percent: 40 },
  { id: 'run_semgrep', name: 'Run Security Analysis', percent: 60 },
  { id: 'parse_report', name: 'Generate Audit Report', percent: 85 },
];
