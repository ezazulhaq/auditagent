import {
  AlertTriangle,
  CheckCircle2,
  CircleDot,
  ExternalLink,
  Filter,
  GitPullRequest,
  LoaderCircle,
  RotateCcw,
  Search,
  ShieldAlert,
  Tag,
  XCircle,
} from 'lucide-react';
import { useMemo, useState } from 'react';

const statusConfig = {
  PR_OPEN: {
    label: 'PR Open',
    className: 'border-cyan-500/30 bg-cyan-500/10 text-cyan-300',
    icon: GitPullRequest,
  },
  FIXED: {
    label: 'Fixed',
    className: 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300',
    icon: CheckCircle2,
  },
  ANALYZING: {
    label: 'Analyzing',
    className: 'border-blue-500/30 bg-blue-500/10 text-blue-300',
    icon: LoaderCircle,
    spin: true,
  },
  AWAITING_APPROVAL: {
    label: 'Awaiting Approval',
    className: 'border-amber-500/30 bg-amber-500/10 text-amber-300',
    icon: AlertTriangle,
  },
  UNMATCHED: {
    label: 'Unmatched',
    className: 'border-purple-500/30 bg-purple-500/10 text-purple-300',
    icon: AlertTriangle,
  },
  FAILED: {
    label: 'Failed',
    className: 'border-rose-500/30 bg-rose-500/10 text-rose-300',
    icon: XCircle,
  },
  DISCARDED: {
    label: 'Discarded',
    className: 'border-slate-700/50 bg-slate-800/50 text-slate-400',
    icon: XCircle,
  },
};

function formatDate(isoString) {
  if (!isoString) return '';
  try {
    const date = new Date(isoString);
    return date.toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return isoString;
  }
}

export function IssuesView({
  issues = [],
  loading = false,
  onRefresh,
  onSelectFinding,
  repository,
}) {
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');

  const filteredIssues = useMemo(() => {
    return issues.filter((issue) => {
      if (statusFilter !== 'ALL') {
        if (issue.remediationStatus !== statusFilter) return false;
      }
      if (!searchTerm.trim()) return true;
      const term = searchTerm.toLowerCase();
      return (
        String(issue.issueNumber).includes(term) ||
        (issue.title && issue.title.toLowerCase().includes(term)) ||
        (issue.targetVulnerabilityId &&
          issue.targetVulnerabilityId.toLowerCase().includes(term)) ||
        (issue.statusMessage &&
          issue.statusMessage.toLowerCase().includes(term)) ||
        (issue.labels &&
          issue.labels.some((l) => l.toLowerCase().includes(term)))
      );
    });
  }, [issues, searchTerm, statusFilter]);

  const counts = useMemo(() => {
    const acc = {
      TOTAL: issues.length,
      PR_OPEN: 0,
      FIXED: 0,
      ANALYZING: 0,
      FAILED: 0,
      UNMATCHED: 0,
    };
    issues.forEach((i) => {
      if (acc[i.remediationStatus] !== undefined) {
        acc[i.remediationStatus]++;
      }
    });
    return acc;
  }, [issues]);

  const repoFullName = repository?.fullName || '';

  return (
    <div className="flex h-full flex-col p-4 sm:p-6 lg:p-8">
      {/* Header bar */}
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-bold tracking-tight text-slate-100 sm:text-2xl">
              Tracked GitHub Issues
            </h1>
            <span className="rounded-full bg-cyan-500/10 px-2.5 py-0.5 font-mono text-xs font-semibold text-cyan-300">
              {issues.length}
            </span>
          </div>
          <p className="mt-1 text-xs text-slate-400 sm:text-sm">
            Issues automatically triggered via webhooks, labels (
            <code className="text-cyan-300">auditagent-fix</code>), or{' '}
            <code className="text-cyan-300">@auditagent</code> mentions.
          </p>
        </div>

        <button
          onClick={onRefresh}
          disabled={loading}
          className="flex items-center gap-2 self-start rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-card)] px-3 py-2 text-xs font-medium text-slate-300 transition hover:bg-[var(--surface-inset)] hover:text-white disabled:opacity-50 sm:self-center"
        >
          <RotateCcw size={14} className={loading ? 'animate-spin text-cyan-400' : ''} />
          Refresh
        </button>
      </div>

      {/* Metrics Row */}
      {issues.length > 0 && (
        <div className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-5">
          <div className="rounded-xl border border-[var(--border-subtle)] bg-[var(--surface-card)] p-3">
            <div className="text-[11px] font-medium uppercase tracking-wider text-slate-400">
              Total Tagged
            </div>
            <div className="mt-1 text-xl font-bold font-mono text-slate-100">
              {counts.TOTAL}
            </div>
          </div>
          <div className="rounded-xl border border-[var(--border-subtle)] bg-[var(--surface-card)] p-3">
            <div className="flex items-center gap-1.5 text-[11px] font-medium uppercase tracking-wider text-cyan-400">
              <GitPullRequest size={13} /> PR Open
            </div>
            <div className="mt-1 text-xl font-bold font-mono text-cyan-300">
              {counts.PR_OPEN}
            </div>
          </div>
          <div className="rounded-xl border border-[var(--border-subtle)] bg-[var(--surface-card)] p-3">
            <div className="flex items-center gap-1.5 text-[11px] font-medium uppercase tracking-wider text-emerald-400">
              <CheckCircle2 size={13} /> Fixed
            </div>
            <div className="mt-1 text-xl font-bold font-mono text-emerald-300">
              {counts.FIXED}
            </div>
          </div>
          <div className="rounded-xl border border-[var(--border-subtle)] bg-[var(--surface-card)] p-3">
            <div className="flex items-center gap-1.5 text-[11px] font-medium uppercase tracking-wider text-blue-400">
              <LoaderCircle size={13} /> In Progress
            </div>
            <div className="mt-1 text-xl font-bold font-mono text-blue-300">
              {counts.ANALYZING}
            </div>
          </div>
          <div className="rounded-xl border border-[var(--border-subtle)] bg-[var(--surface-card)] p-3">
            <div className="flex items-center gap-1.5 text-[11px] font-medium uppercase tracking-wider text-rose-400">
              <XCircle size={13} /> Needs Attention
            </div>
            <div className="mt-1 text-xl font-bold font-mono text-rose-300">
              {counts.FAILED + counts.UNMATCHED}
            </div>
          </div>
        </div>
      )}

      {/* Filter and Search Bar */}
      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative flex-1 max-w-md">
          <Search
            size={15}
            className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
          />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Search issues, numbers, findings, or labels..."
            className="w-full rounded-xl border border-[var(--border-subtle)] bg-[var(--surface-card)] py-2 pl-9 pr-4 text-xs text-slate-200 placeholder-slate-500 focus:border-cyan-500/50 focus:outline-none focus:ring-1 focus:ring-cyan-500/50"
          />
        </div>

        <div className="flex items-center gap-2">
          <Filter size={14} className="text-slate-400 shrink-0" />
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="rounded-xl border border-[var(--border-subtle)] bg-[var(--surface-card)] px-3 py-2 text-xs font-medium text-slate-200 focus:border-cyan-500/50 focus:outline-none focus:ring-1 focus:ring-cyan-500/50"
          >
            <option value="ALL">All Statuses</option>
            <option value="ANALYZING">Analyzing</option>
            <option value="AWAITING_APPROVAL">Awaiting Approval</option>
            <option value="PR_OPEN">PR Open</option>
            <option value="FIXED">Fixed</option>
            <option value="UNMATCHED">Unmatched</option>
            <option value="FAILED">Failed</option>
            <option value="DISCARDED">Discarded</option>
          </select>
        </div>
      </div>

      {/* Main Content Area */}
      {issues.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center rounded-2xl border border-dashed border-slate-700/60 bg-[var(--surface-card)]/40 p-8 text-center sm:p-12">
          <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-cyan-500/10 text-cyan-400 mb-4">
            <CircleDot size={28} />
          </div>
          <h3 className="text-base font-semibold text-slate-200 sm:text-lg">
            No issues tagged yet
          </h3>
          <p className="mt-2 max-w-md text-xs text-slate-400 sm:text-sm">
            When you tag GitHub issues with <code className="rounded bg-slate-800 px-1.5 py-0.5 text-cyan-300 font-mono">auditagent-fix</code> or mention <code className="rounded bg-slate-800 px-1.5 py-0.5 text-cyan-300 font-mono">@auditagent</code>, they will appear here along with autonomous fix progress.
          </p>
          <div className="mt-6 flex flex-wrap items-center justify-center gap-2 text-left text-xs text-slate-300">
            <div className="rounded-lg border border-slate-700/40 bg-slate-800/40 px-3 py-2">
              <span className="font-semibold text-cyan-400">Step 1:</span> Open or label an issue in GitHub
            </div>
            <div className="rounded-lg border border-slate-700/40 bg-slate-800/40 px-3 py-2">
              <span className="font-semibold text-cyan-400">Step 2:</span> Mention a finding or describe the security issue
            </div>
            <div className="rounded-lg border border-slate-700/40 bg-slate-800/40 px-3 py-2">
              <span className="font-semibold text-cyan-400">Step 3:</span> AuditAgent generates a verified PR automatically
            </div>
          </div>
        </div>
      ) : filteredIssues.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center rounded-2xl border border-[var(--border-subtle)] bg-[var(--surface-card)] p-8 text-center">
          <p className="text-sm text-slate-400">
            No issues match the selected filter or search criteria.
          </p>
          <button
            onClick={() => {
              setSearchTerm('');
              setStatusFilter('ALL');
            }}
            className="mt-3 text-xs font-medium text-cyan-400 hover:underline"
          >
            Clear filters
          </button>
        </div>
      ) : (
        <div className="overflow-hidden rounded-xl border border-[var(--border-subtle)] bg-[var(--surface-card)] shadow-sm">
          <div className="divide-y divide-[var(--border-subtle)]">
            {filteredIssues.map((issue) => {
              const statusCfg =
                statusConfig[issue.remediationStatus] || statusConfig.DISCARDED;
              const StatusIcon = statusCfg.icon;
              const issueUrl = repoFullName
                ? `https://github.com/${repoFullName}/issues/${issue.issueNumber}`
                : null;

              return (
                <div
                  key={issue.id}
                  className="flex flex-col gap-3 p-4 transition hover:bg-cyan-500/[0.02] sm:flex-row sm:items-center sm:justify-between"
                >
                  {/* Left Column: Number, Title, Labels, Message */}
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-mono text-xs font-bold text-slate-400">
                        #{issue.issueNumber}
                      </span>
                      {issueUrl ? (
                        <a
                          href={issueUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="flex items-center gap-1 text-sm font-semibold text-slate-200 hover:text-cyan-300 transition"
                        >
                          <span className="truncate">{issue.title}</span>
                          <ExternalLink size={12} className="shrink-0 text-slate-500" />
                        </a>
                      ) : (
                        <span className="text-sm font-semibold text-slate-200 truncate">
                          {issue.title}
                        </span>
                      )}
                    </div>

                    {/* Labels row */}
                    {issue.labels && issue.labels.length > 0 && (
                      <div className="mt-1.5 flex flex-wrap items-center gap-1">
                        {issue.labels.map((lbl, idx) => (
                          <span
                            key={idx}
                            className="inline-flex items-center gap-1 rounded bg-slate-800/80 px-1.5 py-0.5 text-[10px] font-medium text-slate-300"
                          >
                            <Tag size={9} className="text-slate-500" />
                            {lbl}
                          </span>
                        ))}
                      </div>
                    )}

                    {/* Status message */}
                    {issue.statusMessage && (
                      <div className="mt-2 text-xs text-slate-400 flex items-start gap-1.5">
                        <span className="line-clamp-2">
                          {issue.statusMessage}
                        </span>
                      </div>
                    )}
                  </div>

                  {/* Right Column: Finding, PR link, Status Badge, Updated Date */}
                  <div className="flex flex-wrap items-center gap-3 sm:flex-col sm:items-end sm:gap-2 shrink-0">
                    <div className="flex items-center gap-2">
                      {/* Target finding pill */}
                      {issue.targetVulnerabilityId && (
                        <button
                          type="button"
                          onClick={() =>
                            onSelectFinding &&
                            onSelectFinding(issue.targetVulnerabilityId)
                          }
                          title="View finding details"
                          className="flex items-center gap-1 rounded-md border border-cyan-500/30 bg-cyan-500/10 px-2 py-0.5 font-mono text-[10px] font-semibold text-cyan-300 transition hover:bg-cyan-500/20"
                        >
                          <ShieldAlert size={11} />
                          {issue.targetVulnerabilityId}
                        </button>
                      )}

                      {/* Pull request pill */}
                      {issue.pullRequestNumber && (
                        <a
                          href={issue.pullRequestUrl || '#'}
                          target="_blank"
                          rel="noreferrer"
                          className="flex items-center gap-1 rounded-md border border-cyan-500/40 bg-cyan-500/20 px-2 py-0.5 font-mono text-[10px] font-semibold text-cyan-200 transition hover:bg-cyan-500/30"
                        >
                          <GitPullRequest size={11} />
                          PR #{issue.pullRequestNumber}
                          <ExternalLink size={10} className="ml-0.5 text-cyan-400" />
                        </a>
                      )}

                      {/* Status Badge */}
                      <span
                        className={`inline-flex items-center gap-1.5 rounded-md border px-2.5 py-1 text-[11px] font-semibold tracking-wide ${statusCfg.className}`}
                      >
                        <StatusIcon
                          size={12}
                          className={statusCfg.spin ? 'animate-spin' : ''}
                        />
                        {statusCfg.label}
                      </span>
                    </div>

                    <div className="text-[10px] text-slate-500 font-mono">
                      {formatDate(issue.updatedAt || issue.createdAt)}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
