import { AlertTriangle, ArrowRight, CheckCircle2, Clock3, Code2, FileSearch, GitBranch, ShieldCheck, Sparkles, Shield, Info, Flame, Activity } from 'lucide-react';
import { useMemo } from 'react';
import { getFileName } from '../../utils/auditSelectors';

export function DashboardView({ metadata, findings, counts, scanHistory = [], repositoryLabel, onSelect, onShowFindings }) {
  if (!metadata) return <EmptyWorkspace />;

  const cards = [
    { id: 'total', label: 'Total findings', value: findings.length, Icon: FileSearch, tone: 'text-cyan-300', detail: 'Across the pinned scan commit', modalFindings: findings, title: 'Total Findings' },
    { id: 'high', label: 'High severity', value: counts.high, Icon: AlertTriangle, tone: 'text-rose-400', detail: counts.high ? 'Prioritize remediation review' : 'No high-severity exposure', modalFindings: findings.filter(f => f.severity === 'HIGH'), title: 'High Severity Findings' },
    { id: 'medium', label: 'Medium severity', value: counts.medium, Icon: ShieldCheck, tone: 'text-amber-300', detail: counts.medium ? 'Validate in planned work' : 'No medium-severity exposure', modalFindings: findings.filter(f => f.severity === 'MEDIUM'), title: 'Medium Severity Findings' },
    { id: 'low', label: 'Low severity', value: counts.low, Icon: Shield, tone: 'text-sky-400', detail: counts.low ? 'Consider for future sprints' : 'No low-severity exposure', modalFindings: findings.filter(f => f.severity === 'LOW'), title: 'Low Severity Findings' },
    { id: 'info', label: 'Info / Best practice', value: counts.info, Icon: Info, tone: 'text-slate-400', detail: counts.info ? 'Informational guidance only' : 'No informational findings', modalFindings: findings.filter(f => f.severity === 'INFO'), title: 'Informational Findings' },
    { id: 'files', label: 'Files analyzed', value: metadata.totalFilesScanned ?? 0, Icon: Code2, tone: 'text-emerald-400', detail: `${(metadata.totalLocScanned ?? 0).toLocaleString()} lines inspected`, modalFindings: findings, title: 'All Findings for Analyzed Files' },
  ];
  const lowAndInfo = Math.max(0, findings.length - counts.high - counts.medium);
  const total = Math.max(findings.length, 1);
  const highEnd = (counts.high / total) * 100;
  const mediumEnd = highEnd + (counts.medium / total) * 100;
  const distribution = findings.length
    ? `conic-gradient(#fb7185 0 ${highEnd}%, #fbbf24 ${highEnd}% ${mediumEnd}%, #38bdf8 ${mediumEnd}% 100%)`
    : 'conic-gradient(#34d399 0 100%)';
  const posture = counts.high > 0 ? 'Action required' : counts.medium > 0 ? 'Review recommended' : 'No priority findings';
  const postureTone = counts.high > 0 ? 'text-rose-300' : counts.medium > 0 ? 'text-amber-200' : 'text-emerald-300';

  const fileVulnerabilityCounts = useMemo(() => {
    const fileCounts = new Map();
    findings.forEach(f => {
      if (!f.filePath) return;
      fileCounts.set(f.filePath, (fileCounts.get(f.filePath) || 0) + 1);
    });
    return Array.from(fileCounts.entries())
      .sort((a, b) => b[1] - a[1])
      .slice(0, 6)
      .map(([filePath, count]) => ({ filePath, count, fileFindings: findings.filter(f => f.filePath === filePath) }));
  }, [findings]);

  return <div className="animate-fade-in space-y-6 p-4 sm:p-6 xl:p-8 2xl:p-10">
    <section className="flex flex-col gap-4 border-b border-slate-700/25 pb-6 xl:flex-row xl:items-end xl:justify-between">
      <div className="min-w-0">
        <p className="eyebrow">Latest security audit</p>
        <h2 className="mt-2 truncate text-2xl font-semibold tracking-[-0.025em] text-white sm:text-3xl">{metadata.projectName || 'Repository security overview'}</h2>
        <p className="mt-2 flex items-center gap-1.5 truncate text-xs text-slate-500"><GitBranch size={13} className="shrink-0" /> {repositoryLabel}</p>
      </div>
      <div className="flex items-center gap-2 self-start rounded-full border border-emerald-400/20 bg-emerald-400/8 px-3 py-1.5 text-[10px] font-semibold text-emerald-300 xl:self-auto">
        <span className="relative flex h-2 w-2"><span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-35" /><span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-400" /></span>
        Scan complete · evidence available
      </div>
    </section>

    <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-6" aria-label="Audit metrics">
      {cards.map(({ id, label, value, Icon, tone, detail, modalFindings, title }) => <button key={id} onClick={() => onShowFindings(title, modalFindings)} className="surface-card group rounded-2xl p-4 transition duration-200 hover:-translate-y-0.5 hover:border-slate-600/50 sm:p-5 text-left flex flex-col focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/60 w-full">
        <div className="flex w-full items-start justify-between"><span className={`flex h-9 w-9 items-center justify-center rounded-xl border border-slate-700/40 bg-slate-950/30 ${tone}`}><Icon size={18} /></span><ArrowRight size={15} className="text-slate-700 transition group-hover:translate-x-0.5 group-hover:text-slate-500" /></div>
        <div className="mt-5 text-3xl font-semibold tracking-[-0.04em] text-white">{value.toLocaleString()}</div>
        <div className="mt-1 text-xs font-semibold text-slate-300">{label}</div>
        <p className="mt-2 text-[10px] leading-4 text-slate-600">{detail}</p>
      </button>)}
    </section>

    <section className="grid gap-5 xl:grid-cols-[1.2fr_0.8fr]">
      <article className="surface-card rounded-2xl p-5 sm:p-6">
        <div className="flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="eyebrow">Risk posture</p>
            <h3 className="mt-2 text-lg font-semibold text-white">Finding distribution</h3>
            <p className="mt-1 text-xs leading-5 text-slate-500">Severity mix across the current commit-bound audit.</p>
          </div>
          <div className="flex items-center gap-5">
            <div className="relative h-24 w-24 shrink-0 rounded-full" style={{ background: distribution }} role="img" aria-label={`${counts.high} high, ${counts.medium} medium, and ${lowAndInfo} lower-severity findings`}>
              <div className="absolute inset-[10px] flex flex-col items-center justify-center rounded-full bg-[#0b192a]"><b className="text-xl text-white">{findings.length}</b><span className="text-[9px] uppercase tracking-wider text-slate-500">Findings</span></div>
            </div>
            <div className="space-y-2 text-[11px]">
              <p className="flex items-center justify-between gap-7 text-slate-400"><span className="flex items-center gap-2"><i className="h-2 w-2 rounded-full bg-rose-400" /> High</span><b className="text-slate-200">{counts.high}</b></p>
              <p className="flex items-center justify-between gap-7 text-slate-400"><span className="flex items-center gap-2"><i className="h-2 w-2 rounded-full bg-amber-300" /> Medium</span><b className="text-slate-200">{counts.medium}</b></p>
              <p className="flex items-center justify-between gap-7 text-slate-400"><span className="flex items-center gap-2"><i className="h-2 w-2 rounded-full bg-sky-400" /> Low / info</span><b className="text-slate-200">{lowAndInfo}</b></p>
            </div>
          </div>
        </div>
        <div className="mt-6 flex items-center justify-between rounded-xl border border-slate-700/30 bg-slate-950/25 p-3.5">
          <div className="flex items-center gap-3"><span className="flex h-8 w-8 items-center justify-center rounded-lg bg-slate-800/70"><Sparkles size={15} className="text-cyan-300" /></span><div><p className="text-[10px] text-slate-500">Recommended posture</p><p className={`text-xs font-semibold ${postureTone}`}>{posture}</p></div></div>
          <span className="hidden text-[10px] text-slate-600 sm:inline">Open Findings to review evidence</span>
        </div>
      </article>

      <article className="surface-card rounded-2xl p-5 sm:p-6">
        <p className="eyebrow">Audit execution</p>
        <h3 className="mt-2 flex items-center gap-2 text-lg font-semibold text-white"><CheckCircle2 size={18} className="text-emerald-400" /> Completed successfully</h3>
        <dl className="mt-6 divide-y divide-slate-700/25">
          <div className="flex items-center justify-between py-3 first:pt-0"><dt className="flex items-center gap-2 text-xs text-slate-500"><ShieldCheck size={14} /> Scanner</dt><dd className="text-xs font-semibold text-slate-200">{metadata.scannerName || 'Semgrep'}</dd></div>
          <div className="flex items-center justify-between py-3"><dt className="flex items-center gap-2 text-xs text-slate-500"><Clock3 size={14} /> Duration</dt><dd className="font-mono text-xs text-slate-200">{metadata.scanDuration || '—'}</dd></div>
          <div className="flex items-center justify-between py-3"><dt className="flex items-center gap-2 text-xs text-slate-500"><Code2 size={14} /> Lines analyzed</dt><dd className="text-xs font-semibold text-slate-200">{(metadata.totalLocScanned ?? 0).toLocaleString()}</dd></div>
          <div className="flex items-center justify-between py-3 last:pb-0"><dt className="flex items-center gap-2 text-xs text-slate-500"><FileSearch size={14} /> Files covered</dt><dd className="text-xs font-semibold text-slate-200">{(metadata.totalFilesScanned ?? 0).toLocaleString()}</dd></div>
        </dl>
      </article>

      {fileVulnerabilityCounts.length > 0 && (
        <article className="surface-card rounded-2xl p-5 sm:p-6 xl:col-span-2">
          <p className="eyebrow">Hotspots</p>
          <h3 className="mt-2 flex items-center gap-2 text-lg font-semibold text-white"><Flame size={18} className="text-orange-400" /> Top vulnerable files</h3>
          <p className="mt-1 text-xs leading-5 text-slate-500">Files with the highest concentration of security findings.</p>
          
          <div className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
             {fileVulnerabilityCounts.map(file => (
                <button key={file.filePath} onClick={() => onShowFindings(`Findings in ${getFileName(file.filePath)}`, file.fileFindings)} className="group flex items-start justify-between rounded-xl border border-slate-700/40 bg-slate-900/40 p-4 text-left transition hover:bg-slate-800/60 hover:border-slate-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/60">
                  <div className="min-w-0 pr-4">
                    <p className="truncate text-sm font-semibold text-slate-200 transition group-hover:text-white">{getFileName(file.filePath)}</p>
                    <p className="mt-1 truncate text-[10px] text-slate-500" title={file.filePath}>{file.filePath}</p>
                  </div>
                  <span className="flex h-7 shrink-0 items-center justify-center rounded-lg bg-rose-500/10 px-2.5 text-[11px] font-bold text-rose-400">{file.count}</span>
                </button>
             ))}
          </div>
        </article>
      )}
      
      {scanHistory && scanHistory.length > 1 && (
        <article className="surface-card rounded-2xl p-5 sm:p-6 xl:col-span-2">
          <p className="eyebrow">Trends</p>
          <h3 className="mt-2 flex items-center gap-2 text-lg font-semibold text-white"><Activity size={18} className="text-indigo-400" /> Scan history</h3>
          <p className="mt-1 text-xs leading-5 text-slate-500">Finding trends over recent scans.</p>
          <div className="mt-2 flex h-56 items-end gap-3 overflow-x-auto pt-20 pb-4">
            {scanHistory.map((scan, index) => {
              const maxFindings = Math.max(...scanHistory.map(s => s.totalFindings), 1);
              const heightPct = (scan.totalFindings / maxFindings) * 100;
              return (
                <div key={scan.historyId || index} className="group relative flex w-12 shrink-0 flex-col items-center justify-end gap-2 h-full">
                  <div className="w-full rounded-t-md bg-indigo-500/20 transition-colors group-hover:bg-indigo-400/40 relative flex items-end justify-center" style={{ height: `${Math.max(heightPct, 5)}%` }}>
                    <div className="h-full w-full rounded-t-md bg-gradient-to-t from-transparent to-indigo-500/40 absolute inset-0"></div>
                    <span className="relative z-10 text-[10px] font-bold text-indigo-200 mb-1 opacity-0 transition-opacity group-hover:opacity-100">{scan.totalFindings}</span>
                  </div>
                  <span className="text-[10px] text-slate-500 whitespace-nowrap">{new Date(scan.createdAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}</span>
                  
                  {/* Tooltip */}
                  <div className="absolute -top-12 z-20 hidden whitespace-nowrap rounded-lg border border-slate-700 bg-slate-800 px-3 py-2 text-xs font-medium text-white shadow-xl group-hover:block">
                    {scan.totalFindings} findings
                    <br/>
                    <span className="text-[10px] text-slate-400">{new Date(scan.createdAt).toLocaleString()}</span>
                    <div className="absolute -bottom-1.5 left-1/2 h-3 w-3 -translate-x-1/2 rotate-45 border-b border-r border-slate-700 bg-slate-800"></div>
                  </div>
                </div>
              );
            })}
          </div>
        </article>
      )}
    </section>
  </div>;
}

function EmptyWorkspace() {
  return <div className="flex min-h-[560px] items-center justify-center p-6 text-center sm:p-10">
    <div className="max-w-lg">
      <div className="relative mx-auto flex h-20 w-20 items-center justify-center rounded-3xl border border-cyan-300/15 bg-gradient-to-br from-cyan-300/10 to-blue-500/5 text-cyan-300 shadow-2xl shadow-cyan-950/30">
        <ShieldCheck size={34} strokeWidth={1.6} />
        <span className="absolute -bottom-1 -right-1 flex h-7 w-7 items-center justify-center rounded-xl border-4 border-[#091625] bg-emerald-400 text-[#06251c]"><CheckCircle2 size={13} strokeWidth={3} /></span>
      </div>
      <p className="eyebrow mt-7">Secure workspace ready</p>
      <h2 className="mt-2 text-2xl font-semibold tracking-tight text-white">Start with an audit scope</h2>
      <p className="mx-auto mt-3 max-w-md text-sm leading-6 text-slate-500">Choose an installed GitHub repository and branch. AuditAgent pins the commit, scans it in isolation, and keeps remediation governed through pull-request approval.</p>
      <div className="mx-auto mt-7 grid max-w-md gap-2 text-left sm:grid-cols-3">
        {['Select repository', 'Run security scan', 'Review verified fixes'].map((step, index) => <div key={step} className="rounded-xl border border-slate-700/25 bg-slate-900/25 p-3"><span className="text-[9px] font-bold text-cyan-400">0{index + 1}</span><p className="mt-1 text-[10px] font-medium text-slate-400">{step}</p></div>)}
      </div>
    </div>
  </div>;
}