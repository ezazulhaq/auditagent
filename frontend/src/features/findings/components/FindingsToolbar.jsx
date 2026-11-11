import { memo } from 'react';
import { FolderTree, List, RotateCcw, Search, X } from 'lucide-react';
import { formatStatus } from '../constants/findingStyles';

/**
 * FindingsToolbar renders search filters, status dropdown, severity pills, and view switcher.
 * Adheres to Single Responsibility Principle (SRP).
 */
export const FindingsToolbar = memo(function FindingsToolbar({
  findingsCount,
  filteredCount,
  query,
  onQueryChange,
  status,
  onStatusChange,
  statusOptions,
  severity,
  onSeverityChange,
  severityOptions,
  viewMode,
  onViewModeChange,
  onRefresh,
  hasFilters,
  onResetFilters,
}) {
  return (
    <>
      <div className="mb-5 flex items-start justify-between">
        <div>
          <p className="eyebrow">Security evidence</p>
          <h2 className="mt-2 text-2xl font-semibold tracking-tight text-white">Findings inventory</h2>
          <p aria-live="polite" className="mt-1.5 text-xs text-slate-500">
            <span className="font-semibold text-slate-300">{filteredCount}</span> of {findingsCount} findings in the current audit
          </p>
        </div>
        <div className="flex items-center gap-1 rounded-lg border border-slate-700/50 p-1 bg-slate-900/30">
          <button
            onClick={onRefresh}
            title="Refresh findings"
            className="p-1.5 rounded-md transition text-slate-500 hover:text-cyan-300"
          >
            <RotateCcw size={16} />
          </button>
          <div className="w-px h-4 bg-slate-700/50 mx-0.5" />
          <button
            onClick={() => onViewModeChange('list')}
            title="List view"
            className={`p-1.5 rounded-md transition ${viewMode === 'list' ? 'bg-cyan-500/20 text-cyan-300 shadow-sm' : 'text-slate-500 hover:text-slate-300'}`}
          >
            <List size={16} />
          </button>
          <button
            onClick={() => onViewModeChange('grouped')}
            title="Group by file"
            className={`p-1.5 rounded-md transition ${viewMode === 'grouped' ? 'bg-cyan-500/20 text-cyan-300 shadow-sm' : 'text-slate-500 hover:text-slate-300'}`}
          >
            <FolderTree size={16} />
          </button>
        </div>
      </div>

      <section className="surface-card-muted mb-4 rounded-2xl p-3 sm:p-4" aria-label="Filter findings">
        <div className="grid gap-3 lg:grid-cols-[minmax(280px,1fr)_minmax(210px,0.42fr)_auto] lg:items-end">
          <label className="min-w-0">
            <span className="mb-1.5 block text-[9px] font-bold uppercase tracking-[0.14em] text-slate-600">Search</span>
            <span className="relative block">
              <span className="sr-only">Search findings</span>
              <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
              <input
                aria-label="Search findings"
                value={query}
                onChange={event => onQueryChange(event.target.value)}
                placeholder="ID, rule, type, file, description, language..."
                className="field-control w-full py-2 pl-9 pr-10 text-xs"
              />
              {query && (
                <button
                  type="button"
                  onClick={() => onQueryChange('')}
                  aria-label="Clear search"
                  className="absolute right-2 top-1/2 flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-md text-slate-500 transition hover:bg-slate-700/40 hover:text-slate-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/60"
                >
                  <X size={14} />
                </button>
              )}
            </span>
          </label>

          <label>
            <span className="mb-1.5 block text-[9px] font-bold uppercase tracking-[0.14em] text-slate-600">Lifecycle status</span>
            <select
              aria-label="Status"
              value={status}
              onChange={event => onStatusChange(event.target.value)}
              className="field-control w-full px-3 text-xs"
            >
              <option value="ALL">All statuses ({findingsCount})</option>
              {statusOptions.map(option => (
                <option key={option.value} value={option.value}>
                  {formatStatus(option.value)} ({option.count})
                </option>
              ))}
            </select>
          </label>

          <button
            type="button"
            onClick={onResetFilters}
            disabled={!hasFilters}
            className="inline-flex h-10.5 items-center justify-center gap-2 rounded-xl border border-slate-700/40 bg-slate-900/45 px-4 text-[10px] font-bold uppercase tracking-[0.08em] text-slate-400 transition hover:border-slate-600/60 hover:text-slate-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/60 disabled:cursor-not-allowed disabled:opacity-40"
          >
            <RotateCcw size={14} /> Reset
          </button>
        </div>

        <div className="mt-4 flex flex-col gap-3 border-t border-slate-700/25 pt-3 xl:flex-row xl:items-center xl:justify-between">
          <fieldset className="min-w-0">
            <legend className="sr-only">Filter by severity</legend>
            <div className="flex gap-1.5 overflow-x-auto pb-1">
              {severityOptions.map(option => {
                const active = severity === option.value;
                return (
                  <button
                    key={option.value}
                    type="button"
                    aria-pressed={active}
                    onClick={() => onSeverityChange(option.value)}
                    className={`inline-flex shrink-0 items-center gap-2 rounded-lg border px-3 py-2 text-[10px] font-bold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/60 ${
                      active
                        ? 'border-cyan-400/35 bg-cyan-400/10 text-cyan-200'
                        : 'border-slate-700/35 bg-slate-950/25 text-slate-500 hover:border-slate-600/60 hover:text-slate-300'
                    }`}
                  >
                    {option.label}
                    <span className={`rounded-md px-1.5 py-0.5 font-mono text-[9px] ${active ? 'bg-cyan-300/10 text-cyan-100' : 'bg-slate-800/70 text-slate-500'}`}>
                      {option.count}
                    </span>
                  </button>
                );
              })}
            </div>
          </fieldset>
          <p className="shrink-0 text-[9px] font-semibold uppercase tracking-[0.12em] text-slate-600">
            Priority order: High to Medium to Low to Info
          </p>
        </div>
      </section>
    </>
  );
});
