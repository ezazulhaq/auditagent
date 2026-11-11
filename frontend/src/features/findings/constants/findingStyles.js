export const severityClass = {
  CRITICAL: 'badge-critical',
  HIGH: 'badge-high',
  MEDIUM: 'badge-medium',
  LOW: 'badge-low',
  INFO: 'badge-info',
};

export const statusClass = {
  FIXED: 'badge-fixed',
  PR_OPEN: 'border border-cyan-500/30 bg-cyan-500/10 text-cyan-300',
  AWAITING_APPROVAL: 'border border-amber-500/30 bg-amber-500/10 text-amber-300',
  ANALYZING: 'border border-blue-500/30 bg-blue-500/10 text-blue-300',
  GENERATING_FIX: 'border border-blue-500/30 bg-blue-500/10 text-blue-300',
  VERIFYING: 'border border-violet-500/30 bg-violet-500/10 text-violet-300',
  PATCH_FAILED: 'border border-rose-500/30 bg-rose-500/10 text-rose-300',
  PUBLISH_FAILED: 'border border-rose-500/30 bg-rose-500/10 text-rose-300',
  DETECTED: 'border border-slate-600/30 bg-slate-600/10 text-slate-300',
  IGNORED: 'border border-slate-700/50 bg-slate-800/50 text-slate-400',
};

export const formatStatus = (value) =>
  value.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, character => character.toUpperCase());
