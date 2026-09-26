import { AlertTriangle, Check, Code2, ExternalLink, FileDiff, GitPullRequest, LoaderCircle, MapPin, RotateCcw, ShieldCheck, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { CopyButton } from '../../components/CopyButton';
import { DiffViewer } from '../../components/DiffViewer';

const timelineSteps = [
  { label: 'Detected', states: ['DETECTED'] },
  { label: 'Analyzing & fixing', states: ['ANALYZING', 'GENERATING_FIX', 'VERIFYING'] },
  { label: 'Verified & awaiting approval', states: ['AWAITING_APPROVAL'] },
  { label: 'Pull request created', states: ['PR_OPEN', 'PUBLISHED', 'PUBLISH_FAILED'] },
  { label: 'Fix merged', states: ['FIXED'] }
];

function RemediationTimeline({ currentStatus }) {
  if (currentStatus === 'IGNORED') return null;

  let activeIndex = 0;
  if (timelineSteps[4].states.includes(currentStatus)) activeIndex = 4;
  else if (timelineSteps[3].states.includes(currentStatus)) activeIndex = 3;
  else if (timelineSteps[2].states.includes(currentStatus)) activeIndex = 2;
  else if (timelineSteps[1].states.includes(currentStatus) || currentStatus === 'PATCH_FAILED') activeIndex = 1;

  return (
    <section className="rounded-2xl border border-slate-700/35 bg-slate-900/25 p-4 sm:p-5" aria-label="Remediation timeline">
      <h3 className="mb-4 text-[10px] font-bold uppercase tracking-[0.14em] text-slate-500">Remediation Progress</h3>
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between relative">
        <div className="hidden sm:block absolute top-2.5 left-2.5 right-2.5 h-px bg-slate-700/50 z-0" />
        {timelineSteps.map((step, index) => {
          const isComplete = index < activeIndex || (index === 4 && activeIndex === 4);
          const isActive = index === activeIndex && !isComplete;
          const isFailed = (currentStatus === 'PATCH_FAILED' && index === 1) || (currentStatus === 'PUBLISH_FAILED' && index === 3);

          let icon = <div className="h-1.5 w-1.5 rounded-full bg-slate-600" />;
          if (isComplete) icon = <Check size={10} className="text-emerald-400" />;
          else if (isFailed) icon = <AlertTriangle size={10} className="text-rose-400" />;
          else if (isActive && currentStatus !== 'DETECTED') icon = <div className="h-1.5 w-1.5 rounded-full bg-cyan-400 animate-pulse" />;
          else if (isActive && currentStatus === 'DETECTED') icon = <div className="h-1.5 w-1.5 rounded-full bg-cyan-400" />;

          return (
            <div key={step.label} className="relative z-10 flex sm:flex-col items-center sm:items-center gap-3 sm:gap-2 pb-5 sm:pb-0 last:pb-0">
              <div className="sm:hidden absolute left-2.5 top-5 h-full w-px -translate-x-1/2 bg-slate-700/50 -z-10" />
              <div className={`relative flex h-5 w-5 shrink-0 items-center justify-center rounded-full mt-0.5 sm:mt-0 ${isComplete ? 'bg-emerald-400/10 ring-4 ring-slate-900/80' : isFailed ? 'bg-rose-400/10 ring-4 ring-slate-900/80' : isActive ? 'bg-cyan-400/10 ring-4 ring-slate-900/80' : 'bg-slate-800/50 ring-4 ring-slate-900/80'}`}>
                {icon}
              </div>
              <div className="flex flex-col sm:items-center sm:text-center mt-0.5 sm:mt-1">
                <span className={`text-[11px] font-semibold ${isComplete ? 'text-emerald-300' : isFailed ? 'text-rose-300' : isActive ? 'text-white' : 'text-slate-500'}`}>
                  {step.label}
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}

import { GraphTimeline } from '../../components/GraphTimeline';

const statusTone = (status) => status === 'FIXED'
  ? 'border-emerald-400/20 bg-emerald-400/8 text-emerald-200'
  : (status === 'PR_OPEN' || status === 'PUBLISHED')
    ? 'border-cyan-400/20 bg-cyan-400/8 text-cyan-100'
    : 'border-slate-700/60 bg-slate-800/45 text-slate-300';

export function FindingDrawer({ finding, isAnalyzing, isPublishing, awaitingApproval, approvalPreview,
  publication, activeRun, graphSteps, onClose, onAnalyze, onLoadPreview, onDecision, onRetryPublish, theme }) {
  const closeButtonRef = useRef(null);
  const [rejectMode, setRejectMode] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [drawerWidth, setDrawerWidth] = useState(768);
  const [isResizing, setIsResizing] = useState(false);

  useEffect(() => {
    if (!isResizing) return;

    const handleMouseMove = (e) => {
      const newWidth = window.innerWidth - e.clientX;
      const minWidth = 320;
      const maxWidth = window.innerWidth * 0.9;
      setDrawerWidth(Math.min(Math.max(newWidth, minWidth), maxWidth));
    };

    const handleMouseUp = () => {
      setIsResizing(false);
    };

    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp);

    document.body.style.userSelect = 'none';
    document.body.style.cursor = 'col-resize';

    return () => {
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
      document.body.style.userSelect = '';
      document.body.style.cursor = '';
    };
  }, [isResizing]);

  const onCloseRef = useRef(onClose);
  useEffect(() => { onCloseRef.current = onClose; }, [onClose]);
  useEffect(() => {
    if (!finding) return undefined;
    closeButtonRef.current?.focus();
    const closeOnEscape = event => { if (event.key === 'Escape') onCloseRef.current(); };
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [finding]);

  if (!finding) return null;
  const prUrl = publication?.pullRequestUrl;
  const prNumber = publication?.pullRequestNumber;
  const publishFailed = activeRun?.status === 'PUBLISH_FAILED';
  const openPr = finding.status === 'PR_OPEN' || finding.status === 'PUBLISHED' || activeRun?.status === 'PR_OPEN' || activeRun?.status === 'PUBLISHED' || Boolean(prUrl);
  const severityTone = finding.severity === 'HIGH' || finding.severity === 'CRITICAL'
    ? 'border-rose-400/25 bg-rose-400/10 text-rose-200'
    : finding.severity === 'MEDIUM'
      ? 'border-amber-300/25 bg-amber-300/10 text-amber-200'
      : 'border-emerald-400/25 bg-emerald-400/10 text-emerald-200';

  return <div className={`fixed inset-0 z-40 flex justify-end backdrop-blur-sm ${theme === 'light' ? 'bg-slate-800/30' : 'bg-[#020812]/72'}`} onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section role="dialog" aria-modal="true" aria-label="Finding details" style={{ width: window.innerWidth < 1024 ? '100%' : `min(100%, ${drawerWidth}px)` }} className={`animate-slide-in-right relative flex h-full w-full flex-col lg:border-l border-slate-700/35 shadow-[-30px_0_80px_rgba(0,0,0,0.36)] ${theme === 'light' ? 'bg-[#091624]/85 backdrop-blur-2xl' : 'bg-[#091624]'}`}>
      <div
        className="hidden lg:block absolute left-0 top-0 bottom-0 w-1.5 cursor-col-resize hover:bg-cyan-500/50 active:bg-cyan-500/50 z-50 transition-colors"
        onMouseDown={(e) => {
          e.preventDefault();
          setIsResizing(true);
        }}
      />
      <header className={`shrink-0 border-b border-slate-700/30 px-4 py-4 sm:px-6 sm:py-5 ${theme === 'light' ? 'bg-[#0a1828]/60' : 'bg-[#0a1828]/95'}`}>
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <span className={`rounded-md border px-2 py-1 text-[9px] font-bold tracking-widest ${severityTone}`}>{finding.severity}</span>
              <span className="font-mono text-[10px] text-slate-500">{finding.id}</span>
              <span className={`rounded-md px-2 py-1 text-[9px] font-bold tracking-wide ${statusTone(finding.status)}`}>{(finding.status || 'DETECTED').replaceAll('_', ' ')}</span>
            </div>
            <h2 className="mt-3 truncate text-xl font-semibold tracking-tight text-white sm:text-2xl">{finding.vulnType}</h2>
            <p className="mt-1.5 flex items-center gap-1.5 truncate text-[11px] text-slate-500"><MapPin size={12} /> {finding.filePath}:{finding.lineNumber}</p>
          </div>
          <button ref={closeButtonRef} aria-label="Close finding" onClick={onClose} className="rounded-xl border border-slate-700/35 bg-slate-900/40 p-2.5 text-slate-500 transition hover:border-slate-600 hover:bg-slate-800 hover:text-white"><X size={18} /></button>
        </div>
      </header>

      <div className="flex-1 space-y-6 overflow-y-auto p-4 sm:p-6">
        <section className="surface-card-muted grid gap-4 rounded-2xl p-4 text-xs sm:grid-cols-2">
          <div><span className="text-[9px] font-bold uppercase tracking-[0.12em] text-slate-600">File path</span><p className="mt-1.5 break-all font-medium text-slate-300">{finding.filePath}</p></div>
          <div><span className="text-[9px] font-bold uppercase tracking-[0.12em] text-slate-600">Source location</span><p className="mt-1.5 text-slate-300">Line {finding.lineNumber} <span className="text-slate-600">·</span> {finding.language || 'unknown'}</p></div>
        </section>

        <RemediationTimeline currentStatus={activeRun?.status || finding.status || 'DETECTED'} />
        <GraphTimeline steps={graphSteps} />

        <section>
          <h3 className="mb-2.5 flex items-center gap-2 text-[10px] font-bold uppercase tracking-[0.14em] text-slate-500"><AlertTriangle size={14} className="text-amber-300" /> Finding summary</h3>
          <p className="text-sm leading-6 text-slate-300">{finding.description}</p>
        </section>

        {finding.codeSnippet && <section>
          <h3 className="mb-2.5 flex items-center gap-2 text-[10px] font-bold uppercase tracking-[0.14em] text-slate-500"><Code2 size={14} className="text-cyan-300" /> Vulnerable code</h3>
          <div className="relative group overflow-hidden rounded-2xl border border-slate-700/35 shadow-inner">
            <CopyButton text={finding.codeSnippet} className="absolute right-2 top-2 z-10 opacity-0 transition-opacity group-hover:opacity-100 focus-within:opacity-100" />
            <SyntaxHighlighter
              language={finding.language?.toLowerCase() || 'javascript'}
              style={vscDarkPlus}
              showLineNumbers={true}
              startingLineNumber={Math.max(1, finding.lineNumber - 10)}
              wrapLines={true}
              lineProps={lineNumber => ({
                style: {
                  display: 'block',
                  backgroundColor: lineNumber === finding.lineNumber ? 'rgba(239, 68, 68, 0.2)' : 'transparent',
                }
              })}
              customStyle={{ margin: 0, padding: '1rem', paddingTop: '2rem', background: '#050d18', fontSize: '0.75rem', lineHeight: '1.25rem' }}
            >
              {finding.codeSnippet}
            </SyntaxHighlighter>
          </div>
        </section>}

        {approvalPreview && <section className="overflow-hidden rounded-2xl border border-cyan-400/22 bg-cyan-400/[0.035]">
          <header className="border-b border-cyan-400/15 bg-cyan-400/4.5 p-4 sm:p-5">
            <div className="flex items-start gap-3"><span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-cyan-400/20 bg-cyan-400/10 text-cyan-300"><ShieldCheck size={18} /></span><div><h3 className="text-sm font-semibold text-cyan-100">Verified approval package</h3><p className="mt-1 text-[11px] leading-5 text-slate-400">Review the exact verified change. Approval authorizes one bot branch, commit, push, and ready-for-review pull request.</p></div></div>
          </header>
          <div className="space-y-5 p-4 sm:p-5">
            <dl className="grid gap-3 text-xs sm:grid-cols-2">
              <div className="rounded-xl border border-slate-700/30 bg-slate-950/25 p-3"><dt className="text-[9px] font-bold uppercase tracking-wider text-slate-600">Repository / base</dt><dd className="mt-1.5 break-all text-slate-300">{approvalPreview.repository} · {approvalPreview.baseBranch}</dd></div>
              <div className="rounded-xl border border-slate-700/30 bg-slate-950/25 p-3"><dt className="text-[9px] font-bold uppercase tracking-wider text-slate-600">Pinned commit</dt><dd className="mt-1.5 break-all font-mono text-[11px] text-slate-300">{approvalPreview.baseSha}</dd></div>
              <div className="rounded-xl border border-slate-700/30 bg-slate-950/25 p-3 sm:col-span-2"><dt className="text-[9px] font-bold uppercase tracking-wider text-slate-600">Planned branch</dt><dd className="mt-1.5 break-all font-mono text-[11px] text-slate-300">{approvalPreview.branchName}</dd></div>
            </dl>

            <div><h4 className="text-[9px] font-bold uppercase tracking-[0.12em] text-slate-600">Changed files</h4><ul className="mt-2 space-y-1.5">{approvalPreview.changedFiles?.map(file => <li key={file} className="flex items-start gap-2 break-all rounded-lg border border-slate-700/25 bg-slate-950/20 px-3 py-2 font-mono text-[10px] text-slate-300"><FileDiff size={12} className="mt-0.5 shrink-0 text-cyan-300" />{file}</li>)}</ul></div>

            <div><h4 className="text-[9px] font-bold uppercase tracking-[0.12em] text-slate-600">Verification evidence</h4><div className="mt-2 grid gap-2 sm:grid-cols-2">{Object.entries(approvalPreview.verification ?? {}).map(([name, value]) => <div key={name} className="rounded-xl border border-emerald-400/12 bg-emerald-400/[0.035] p-3 text-xs"><span className="flex items-center gap-1.5 font-semibold text-emerald-300"><Check size={12} /> {name}</span><p className="mt-1.5 wrap-break-word text-[10px] leading-4 text-slate-500">{String(value)}</p></div>)}</div></div>

            <div data-testid="approval-diff">
              <DiffViewer diffText={approvalPreview.diff} initialMode="side-by-side" />
            </div>
          </div>
        </section>}
      </div>

      <footer className={`shrink-0 border-t border-slate-700/30 p-4 sm:p-5 backdrop-blur ${theme === 'light' ? 'bg-[#081421]/60' : 'bg-[#081421]/96'}`}>
        {awaitingApproval && !approvalPreview && <button disabled={isAnalyzing} onClick={onLoadPreview} className="flex min-h-11 w-full items-center justify-center gap-2 rounded-xl border border-cyan-400/35 bg-cyan-400/7 px-4 text-sm font-semibold text-cyan-200 transition hover:bg-cyan-400/12 disabled:opacity-50"><FileDiff size={16} /> Review verified diff</button>}
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
                <button onClick={() => setRejectMode(false)} className="min-h-11 flex-1 rounded-xl border border-slate-600/55 bg-slate-900/35 px-4 text-sm font-semibold text-slate-300 transition hover:bg-slate-800">Cancel</button>
                <button onClick={() => { onDecision('REJECT', rejectReason); setRejectMode(false); setRejectReason(''); }} className="min-h-11 flex-[1.5] rounded-xl bg-rose-500/90 px-4 text-sm font-bold text-white shadow-lg transition hover:bg-rose-400">Confirm Rejection</button>
              </div>
            </div>
          ) : (
            <div className="flex flex-col-reverse gap-2.5 sm:flex-row">
              <button disabled={isPublishing} onClick={() => setRejectMode(true)} className="min-h-11 flex-1 rounded-xl border border-slate-600/55 bg-slate-900/35 px-4 text-sm font-semibold text-slate-300 transition hover:bg-slate-800 disabled:opacity-50">Reject</button>
              <button disabled={isPublishing} onClick={() => onDecision('APPROVE_AND_CREATE_PR')} className="flex min-h-11 flex-[1.5] items-center justify-center gap-2 rounded-xl bg-emerald-400 px-4 text-sm font-bold text-[#05261d] shadow-lg shadow-emerald-950/25 transition hover:bg-emerald-300 disabled:opacity-50">
                {isPublishing ? <><LoaderCircle size={16} className="animate-spin" /> Publishing</> : <><GitPullRequest size={16} /> Approve &amp; create PR</>}
              </button>
            </div>
          )
        )}
        {publishFailed && <div className="space-y-3"><div className="rounded-xl border border-rose-400/20 bg-rose-400/8 p-3.5 text-xs leading-5 text-rose-200">Publication failed after approval. Retry reuses the stored branch, commit, and existing PR lookup.</div><button disabled={isPublishing} onClick={onRetryPublish} className="flex min-h-11 w-full items-center justify-center gap-2 rounded-xl bg-amber-300 px-4 text-sm font-bold text-[#2d2105] transition hover:bg-amber-200 disabled:opacity-50"><RotateCcw size={16} /> Retry publishing</button></div>}
        {openPr && <div className={`rounded-xl border p-3.5 text-center text-xs font-semibold ${statusTone('PR_OPEN')}`}><p>Fix is in pull request {prNumber ? `#${prNumber}` : ''}; it remains open until GitHub reports a merge.</p>{prUrl && <a href={prUrl} target="_blank" rel="noreferrer" className="mt-2 inline-flex items-center gap-1 text-cyan-200 underline decoration-cyan-300/30 underline-offset-4"><ExternalLink size={14} /> Open pull request</a>}</div>}
        {!awaitingApproval && !publishFailed && !openPr && finding.status === 'DETECTED' && <button disabled={isAnalyzing} onClick={onAnalyze} className="primary-action flex w-full items-center justify-center gap-2 px-4 py-2.5 text-sm font-bold">{isAnalyzing ? <><LoaderCircle size={16} className="animate-spin" /> Analyzing</> : <><ShieldCheck size={16} /> Analyze in isolated workspace</>}</button>}
        {!awaitingApproval && !publishFailed && !openPr && finding.status !== 'DETECTED' && <div className={`rounded-xl border p-3.5 text-center text-xs font-semibold ${statusTone(finding.status)}`}>{finding.status === 'FIXED' ? <span className="inline-flex items-center gap-2"><Check size={16} /> Merged on GitHub · finding fixed</span> : `Finding ${finding.status.toLowerCase().replaceAll('_', ' ')}`}</div>}
      </footer>
    </section>
  </div>;
}