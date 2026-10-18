import { BarChart3, FileText, ShieldAlert } from 'lucide-react';

const tabs = [
  { id: 'dashboard', label: 'Dashboard', icon: BarChart3 },
  { id: 'findings', label: 'Findings', icon: ShieldAlert },
  { id: 'report', label: 'Audit Report', icon: FileText },
];

export function WorkspaceTabs({ activeTab, onChange, findingCount }) {
  return <div className="sticky top-0 z-20 shrink-0 border-b border-[var(--surface-panel-border)] bg-[var(--surface-canvas)]/90 px-3 py-2.5 backdrop-blur-xl sm:px-5 xl:px-7">
    <nav aria-label="Workspace views" className="scrollbar-none flex max-w-full items-center gap-1 overflow-x-auto rounded-xl border border-[var(--border-subtle)] bg-[var(--surface-inset)]/60 p-1 sm:w-fit">
      {tabs.map(tab => {
        const Icon = tab.icon;
        const active = activeTab === tab.id;
        return <button key={tab.id} onClick={() => onChange(tab.id)} aria-current={active ? 'page' : undefined}
          className={`relative flex shrink-0 items-center gap-2 rounded-lg border-b-2 px-3 py-2 text-xs font-semibold tracking-wide transition sm:px-4 ${active ? 'border-cyan-400 bg-cyan-400/10 text-cyan-200 shadow-sm' : 'border-transparent text-slate-400 hover:bg-slate-800/40 hover:text-slate-200'}`}>
          <Icon size={15} /> {tab.label}
          {tab.id === 'findings' && <span className={`min-w-5 rounded-md px-1.5 py-0.5 text-center font-mono text-[9px] font-semibold tabular-nums ${active ? 'bg-cyan-400/20 text-cyan-200' : 'bg-slate-800 text-slate-400'}`}>{findingCount}</span>}
        </button>;
      })}
    </nav>
  </div>;
}
