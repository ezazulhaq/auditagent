import { memo, useState } from 'react';
import {
  FileDiff,
  GitPullRequest,
  LoaderCircle,
  RotateCcw,
  ShieldCheck,
  Check,
  ExternalLink,
} from 'lucide-react';

const statusTone = (status) =>
  status === 'FIXED'
    ? 'border-emerald-400/20 bg-emerald-400/8 text-emerald-200'
    : (status === 'PR_OPEN' || status === 'PUBLISHED')
      ? 'border-cyan-400/20 bg-cyan-400/8 text-cyan-100'
      : 'border-slate-700/60 bg-slate-800/45 text-slate-300';

/**
 * RemediationDecisionBar handles the interactive actions at the bottom of the FindingDrawer:
 * Review Diff, Approve & Create PR, Reject with Reason, Retry Publishing, or Run Analysis.
 * Adheres to Single Responsibility Principle (SRP).
 */
export const RemediationDecisionBar = memo(function RemediationDecisionBar({
  finding,
  isAnalyzing,
  isPublishing,
  isLoadingPreview = false,
  awaitingApproval,
  approvalPreview,
  activeRun,
  publication,
  onAnalyze,
  onLoadPreview,
  onDecision,
  onRetryPublish,
  theme,
  isUploadScan,
}) {
  const [rejectMode, setRejectMode] = useState(false);
  const [rejectReason, setRejectReason] = useState('');

  const prUrl = publication?.pullRequestUrl;
  const prNumber = publication?.pullRequestNumber;
  const publishFailed = activeRun?.status === 'PUBLISH_FAILED';
  const isFixedOrIgnored = finding.status === 'FIXED' || finding.status === 'IGNORED';
  const openPr = !isFixedOrIgnored && (
    finding.status === 'PR_OPEN' ||
    finding.status === 'PUBLISHED' ||
    activeRun?.status === 'PR_OPEN' ||
    activeRun?.status === 'PUBLISHED' ||
    Boolean(prUrl)
  );

  return (
    <footer className={`shrink-0 border-t border-slate-700/30 p-4 sm:p-5 backdrop-blur ${theme === 'light' ? 'bg-[#081421]/60' : 'bg-[#081421]/96'}`}>
      {awaitingApproval && !approvalPreview && (
        <button
          disabled={isAnalyzing || isLoadingPreview}
          onClick={onLoadPreview}
          className="flex min-h-11 w-full items-center justify-center gap-2 rounded-xl border border-cyan-400/35 bg-cyan-400/7 px-4 text-sm font-semibold text-cyan-200 transition hover:bg-cyan-400/12 disabled:opacity-50"
        >
          {isLoadingPreview ? (
            <><LoaderCircle size={16} className="animate-spin" /> Loading verified diff...</>
          ) : (
            <><FileDiff size={16} /> Review verified diff</>
          )}
        </button>
      )}

      {awaitingApproval && approvalPreview && (
        rejectMode ? (
          <div className="flex flex-col gap-3">
            <label className="text-sm font-semibold text-slate-300">Why are you rejecting this? (Optional)</label>
            <textarea
              value={rejectReason}
              onChange={e => setRejectReason(e.target.value)}
              placeholder="e.g. Breaks tests, Uses deprecated API..."
              className="w-full rounded-xl border border-slate-700/50 bg-slate-900/50 p-3 text-sm text-slate-200 placeholder-slate-500 focus:border-cyan-400 focus:outline-none focus:ring-1 focus:ring-cyan-400"
              rows={2}
            />
            <div className="flex gap-2.5">
              <button
                onClick={() => setRejectMode(false)}
                className="min-h-11 flex-1 rounded-xl border border-slate-600/55 bg-slate-900/35 px-4 text-sm font-semibold text-slate-300 transition hover:bg-slate-800"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  onDecision('REJECT', rejectReason);
                  setRejectMode(false);
                  setRejectReason('');
                }}
                className="min-h-11 flex-[1.5] rounded-xl bg-rose-500/90 px-4 text-sm font-bold text-white shadow-lg transition hover:bg-rose-400"
              >
                Confirm Rejection
              </button>
            </div>
          </div>
        ) : (
          <div className="flex flex-col-reverse gap-2.5 sm:flex-row">
            <button
              disabled={isPublishing}
              onClick={() => setRejectMode(true)}
              className="min-h-11 flex-1 rounded-xl border border-slate-600/55 bg-slate-900/35 px-4 text-sm font-semibold text-slate-300 transition hover:bg-slate-800 disabled:opacity-50"
            >
              Reject
            </button>
            <button
              disabled={isPublishing}
              onClick={() => onDecision('APPROVE_AND_CREATE_PR')}
              className="flex min-h-11 flex-[1.5] items-center justify-center gap-2 rounded-xl bg-emerald-400 px-4 text-sm font-bold text-[#05261d] shadow-lg shadow-emerald-950/25 transition hover:bg-emerald-300 disabled:opacity-50"
            >
              {isPublishing ? (
                <><LoaderCircle size={16} className="animate-spin" /> Publishing</>
              ) : (
                <><GitPullRequest size={16} /> Approve &amp; create PR</>
              )}
            </button>
          </div>
        )
      )}

      {publishFailed && (
        <div className="space-y-3">
          <div className="rounded-xl border border-rose-400/20 bg-rose-400/8 p-3.5 text-xs leading-5 text-rose-200">
            Publication failed after approval. Retry reuses the stored branch, commit, and existing PR lookup.
          </div>
          <button
            disabled={isPublishing}
            onClick={onRetryPublish}
            className="flex min-h-11 w-full items-center justify-center gap-2 rounded-xl bg-amber-300 px-4 text-sm font-bold text-[#2d2105] transition hover:bg-amber-200 disabled:opacity-50"
          >
            <RotateCcw size={16} /> Retry publishing
          </button>
        </div>
      )}

      {openPr && (
        <div className={`rounded-xl border p-3.5 text-center text-xs font-semibold ${statusTone('PR_OPEN')}`}>
          <p>Fix is in pull request {prNumber ? `#${prNumber}` : ''}; it remains open until GitHub reports a merge.</p>
          {prUrl && (
            <a
              href={prUrl}
              target="_blank"
              rel="noreferrer"
              className="mt-2 inline-flex items-center gap-1 text-cyan-200 underline decoration-cyan-300/30 underline-offset-4"
            >
              <ExternalLink size={14} /> Open pull request
            </a>
          )}
        </div>
      )}

      {isUploadScan && (
        <div className="rounded-xl border border-amber-400/20 bg-amber-400/8 p-3.5 text-center text-xs leading-5 text-amber-200">
          Autonomous PR creation is disabled for uploaded ZIP archives. Export findings or scan a connected GitHub repository to remediate.
        </div>
      )}

      {!isUploadScan && !awaitingApproval && !publishFailed && !openPr && (finding.status === 'DETECTED' || finding.status === 'PATCH_FAILED' || activeRun?.status === 'FAILED') && (
        <button
          disabled={isAnalyzing}
          onClick={onAnalyze}
          className="primary-action flex w-full items-center justify-center gap-2 px-4 py-2.5 text-sm font-bold"
        >
          {isAnalyzing ? (
            <><LoaderCircle size={16} className="animate-spin" /> Analyzing</>
          ) : (
            <><ShieldCheck size={16} /> {finding.status === 'PATCH_FAILED' || activeRun?.status === 'FAILED' ? 'Retry analysis in isolated workspace' : 'Analyze in isolated workspace'}</>
          )}
        </button>
      )}

      {!awaitingApproval && !publishFailed && !openPr && finding.status !== 'DETECTED' && finding.status !== 'PATCH_FAILED' && activeRun?.status !== 'FAILED' && (
        <div className={`rounded-xl border p-3.5 text-center text-xs font-semibold ${statusTone(finding.status)}`}>
          {finding.status === 'FIXED' ? (
            <span className="inline-flex items-center gap-2">
              <Check size={16} /> Merged on GitHub · finding fixed
            </span>
          ) : (
            `Finding ${finding.status.toLowerCase().replaceAll('_', ' ')}`
          )}
        </div>
      )}
    </footer>
  );
});
