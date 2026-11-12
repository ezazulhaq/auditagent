import { AlertTriangle, Code2, MapPin, X } from 'lucide-react';
import { memo, useEffect, useRef, useState } from 'react';
import { CopyButton } from '../../components/CopyButton';
import { GraphTimeline } from '../../components/GraphTimeline';
import { RemediationTimeline } from './components/RemediationTimeline';
import { VulnerableCodeViewer } from './components/VulnerableCodeViewer';
import { ApprovalPackageViewer } from './components/ApprovalPackageViewer';
import { RemediationDecisionBar } from './components/RemediationDecisionBar';

const statusTone = (status) =>
  status === 'FIXED'
    ? 'border-emerald-400/20 bg-emerald-400/8 text-emerald-200'
    : (status === 'PR_OPEN' || status === 'PUBLISHED')
      ? 'border-cyan-400/20 bg-cyan-400/8 text-cyan-100'
      : 'border-slate-700/60 bg-slate-800/45 text-slate-300';

/**
 * FindingDrawer presents complete remediation context, code diffs, AI timeline, and approval flow.
 * Refactored to strictly adhere to Single Responsibility Principle (SRP) and Interface Segregation Principle (ISP).
 */
export const FindingDrawer = memo(function FindingDrawer({
  finding,
  isAnalyzing,
  isPublishing,
  isLoadingPreview = false,
  awaitingApproval,
  approvalPreview,
  publication,
  activeRun,
  graphSteps,
  onClose,
  onAnalyze,
  onLoadPreview,
  onDecision,
  onRetryPublish,
  theme,
  isUploadScan = false,
}) {
  const closeButtonRef = useRef(null);
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
  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    if (!finding) return undefined;
    closeButtonRef.current?.focus();
    const closeOnEscape = event => {
      if (event.key === 'Escape') onCloseRef.current();
    };
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [finding]);

  if (!finding) return null;

  const isFixedOrIgnored = finding.status === 'FIXED' || finding.status === 'IGNORED';
  const severityTone = `badge-${(finding.severity || 'info').toLowerCase()}`;

  return (
    <div
      className={`fixed inset-0 z-40 flex justify-end backdrop-blur-sm ${
        theme === 'light' ? 'bg-slate-900/40' : 'bg-black/60'
      }`}
      onMouseDown={event => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        role="dialog"
        aria-modal="true"
        aria-label="Finding details"
        style={{ width: window.innerWidth < 1024 ? '100%' : `${drawerWidth}px`, maxWidth: '100%' }}
        className={`animate-slide-in-right relative flex h-full w-full flex-col lg:border-l border-[var(--surface-panel-border)] shadow-[-30px_0_80px_rgba(0,0,0,0.36)] ${
          theme === 'light' ? 'bg-white text-slate-900' : 'bg-[#0e1628] text-slate-200'
        }`}
      >
        <div
          className="hidden lg:block absolute left-0 top-0 bottom-0 w-1.5 cursor-col-resize hover:bg-cyan-500/50 active:bg-cyan-500/50 z-50 transition-colors"
          onMouseDown={(e) => {
            e.preventDefault();
            setIsResizing(true);
          }}
        />

        <header className={`shrink-0 border-b border-[var(--border-subtle)] px-4 py-4 sm:px-6 sm:py-5 ${
          theme === 'light' ? 'bg-white' : 'bg-[#10192e]'
        }`}>
          <div className="flex items-start justify-between gap-4">
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <span className={`rounded-md px-2 py-0.5 font-mono text-[9px] font-bold tracking-widest ${severityTone}`}>
                  {finding.severity}
                </span>
                <div className="inline-flex items-center gap-1.5">
                  <span className="font-mono text-[10px] text-slate-500">{finding.id}</span>
                  <CopyButton
                    text={finding.id}
                    size="xs"
                    title="Copy finding ID to clipboard"
                    ariaLabel="Copy finding ID to clipboard"
                  />
                </div>
                <span className={`rounded-md px-2 py-0.5 text-[9px] font-bold tracking-wide ${statusTone(finding.status)}`}>
                  {(finding.status || 'DETECTED').replaceAll('_', ' ')}
                </span>
                {finding.triageActionable === false && (
                  <span className="rounded-md border border-amber-500/30 bg-amber-500/10 px-2 py-0.5 text-[9px] font-semibold text-amber-300">
                    Likely False Positive ({Math.round((finding.triageConfidence || 0) * 100)}% conf.)
                  </span>
                )}
                {finding.triageActionable === true && finding.triageConfidence != null && (
                  <span className="rounded-md border border-emerald-500/30 bg-emerald-500/10 px-2 py-0.5 text-[9px] font-semibold text-emerald-300">
                    Triaged Actionable ({Math.round(finding.triageConfidence * 100)}% conf.)
                  </span>
                )}
              </div>
              <h2 className="mt-3 truncate text-xl font-bold tracking-tight text-white sm:text-2xl font-sans">
                {finding.vulnType}
              </h2>
              <p className="mt-1.5 flex items-center gap-1.5 truncate font-mono text-[11px] text-slate-400">
                <MapPin size={12} className="text-cyan-400" /> {finding.filePath}:{finding.lineNumber}
              </p>
            </div>
            <button
              ref={closeButtonRef}
              aria-label="Close finding"
              onClick={onClose}
              className="rounded-xl border border-slate-700/35 bg-slate-900/40 p-2.5 text-slate-400 transition hover:border-slate-600 hover:bg-slate-800 hover:text-white"
            >
              <X size={18} />
            </button>
          </div>
        </header>

        <div className="flex-1 space-y-6 overflow-y-auto p-4 sm:p-6">
          <section className="surface-card-muted grid gap-4 rounded-2xl p-4 text-xs sm:grid-cols-2">
            <div>
              <span className="text-[9px] font-bold uppercase tracking-[0.12em] text-slate-600">File path</span>
              <p className="mt-1.5 break-all font-medium text-slate-300">{finding.filePath}</p>
            </div>
            <div>
              <span className="text-[9px] font-bold uppercase tracking-[0.12em] text-slate-600">Source location</span>
              <p className="mt-1.5 text-slate-300">Line {finding.lineNumber} <span className="text-slate-600">·</span> {finding.language || 'unknown'}</p>
            </div>
          </section>

          <RemediationTimeline currentStatus={isFixedOrIgnored ? finding.status : (activeRun?.status || finding.status || 'DETECTED')} />
          <GraphTimeline steps={graphSteps} />

          {(finding.triageClassification || finding.triageConfidence != null) && (
            <section className={`rounded-2xl border p-4 text-xs ${
              finding.triageActionable === false
                ? 'border-amber-500/30 bg-amber-500/[0.04]'
                : 'border-emerald-500/30 bg-emerald-500/[0.04]'
            }`}>
              <div className="flex items-center justify-between">
                <span className={`text-[10px] font-bold uppercase tracking-[0.14em] ${
                  finding.triageActionable === false ? 'text-amber-400' : 'text-emerald-400'
                }`}>
                  TypeSafe Jev Pre-Flight Assessment
                </span>
                <span className="font-mono text-[11px] font-bold text-slate-300">
                  Confidence: {Math.round((finding.triageConfidence || 0) * 100)}%
                </span>
              </div>
              <p className="mt-2 text-slate-300 leading-5">
                Classification: <strong className="text-white">{finding.triageClassification?.replaceAll('_', ' ')}</strong>.
                {finding.triageActionable === false
                  ? ' This finding was evaluated as unlikely to represent an exploitable production vulnerability. It is excluded from bulk fixes by default.'
                  : ' This finding was verified as an actionable production vulnerability.'}
              </p>
            </section>
          )}

          <section>
            <h3 className="mb-2.5 flex items-center gap-2 text-[10px] font-bold uppercase tracking-[0.14em] text-slate-500">
              <AlertTriangle size={14} className="text-amber-300" /> Finding summary
            </h3>
            <p className="text-sm leading-6 text-slate-300">{finding.description}</p>
          </section>

          {finding.codeSnippet && (
            <section>
              <h3 className="mb-2.5 flex items-center gap-2 text-[10px] font-bold uppercase tracking-[0.14em] text-slate-500">
                <Code2 size={14} className="text-cyan-300" /> Vulnerable code
              </h3>
              <VulnerableCodeViewer
                snippet={finding.codeSnippet}
                language={finding.language}
                lineNumber={finding.lineNumber}
              />
            </section>
          )}

          <ApprovalPackageViewer approvalPreview={approvalPreview} />
        </div>

        <RemediationDecisionBar
          finding={finding}
          isAnalyzing={isAnalyzing}
          isPublishing={isPublishing}
          isLoadingPreview={isLoadingPreview}
          awaitingApproval={awaitingApproval}
          approvalPreview={approvalPreview}
          activeRun={activeRun}
          publication={publication}
          onAnalyze={onAnalyze}
          onLoadPreview={onLoadPreview}
          onDecision={onDecision}
          onRetryPublish={onRetryPublish}
          theme={theme}
          isUploadScan={isUploadScan}
        />
      </section>
    </div>
  );
});
