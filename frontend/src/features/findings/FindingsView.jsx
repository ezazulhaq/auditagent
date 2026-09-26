import { ChevronRight, ChevronDown, ChevronUp, FileCode2, Filter, RotateCcw, Search, ShieldAlert, X, List, FolderTree, Folder } from 'lucide-react';
import { useMemo, useState } from 'react';
import { FINDING_SEVERITY_ORDER, filterFindings, getDirectoryPath, getFileName } from '../../utils/auditSelectors';

const severityClass = {
  CRITICAL: 'border-fuchsia-400/25 bg-fuchsia-400/10 text-fuchsia-200',
  HIGH: 'border-rose-400/25 bg-rose-400/10 text-rose-200',
  MEDIUM: 'border-amber-300/25 bg-amber-300/10 text-amber-200',
  LOW: 'border-sky-400/25 bg-sky-400/10 text-sky-200',
  INFO: 'border-slate-500/30 bg-slate-500/10 text-slate-300',
};
const statusClass = {
  FIXED: 'bg-emerald-400/10 text-emerald-300',
  PR_OPEN: 'bg-cyan-400/10 text-cyan-200',
  AWAITING_APPROVAL: 'bg-amber-300/10 text-amber-200',
  ANALYZING: 'bg-blue-400/10 text-blue-200',
  GENERATING_FIX: 'bg-blue-400/10 text-blue-200',
  VERIFYING: 'bg-violet-400/10 text-violet-200',
  PATCH_FAILED: 'bg-rose-400/10 text-rose-200',
  PUBLISH_FAILED: 'bg-rose-400/10 text-rose-200',
  DETECTED: 'bg-slate-500/10 text-slate-300',
  IGNORED: 'bg-slate-700/50 text-slate-400',
};
const statusOrder = ['DETECTED', 'ANALYZING', 'GENERATING_FIX', 'VERIFYING', 'PATCH_FAILED', 'AWAITING_APPROVAL', 'PR_OPEN', 'FIXED', 'IGNORED'];
const formatStatus = (value) => value.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, character => character.toUpperCase());

function FindingRow({ finding, activeRun, onSelect, hideLocation = false, isSelected, onToggleSelect }) {
  const findingStatus = (activeRun?.vulnerabilityId === finding.id ? activeRun.status : finding.status) || 'DETECTED';
  return (
    <div className="group flex w-full items-start md:items-stretch border-b border-slate-700/20 transition last:border-0 hover:bg-cyan-400/[0.035]">
      <div className="flex shrink-0 items-start md:items-center justify-center pl-4 pr-3 py-4 md:pl-5">
        <input
          type="checkbox"
          checked={isSelected}
          onChange={(e) => onToggleSelect(finding.id, e.target.checked)}
          className="h-4 w-4 rounded border-slate-600 bg-slate-800 text-cyan-500 focus:ring-cyan-500/50 focus:ring-offset-slate-900 cursor-pointer mt-1 md:mt-0"
        />
      </div>
      <div onClick={() => onSelect(finding)} role="button" aria-label={`Open ${finding.vulnType} finding ${finding.id}`}
        className={`flex flex-col md:grid flex-1 cursor-pointer gap-2 md:gap-4 py-4 pr-4 text-left items-start md:items-center md:pr-5 ${hideLocation ? 'md:grid-cols-[112px_minmax(260px,2fr)_120px_142px_28px]' : 'md:grid-cols-[112px_minmax(260px,1.5fr)_minmax(190px,0.8fr)_142px_28px]'}`}>

        <div className="flex w-full items-center justify-between md:contents">
          <span><span className={`inline-flex min-w-18 items-center justify-center rounded-md border px-2 py-1 text-[9px] font-bold tracking-[0.08em] ${severityClass[finding.severity] || severityClass.INFO}`}>{finding.severity}</span></span>
          <span className="md:hidden"><span className={`inline-flex rounded-md px-2 py-1 text-[9px] font-bold tracking-wide ${statusClass[findingStatus] || statusClass.DETECTED}`}>{formatStatus(findingStatus)}</span></span>
        </div>

        <span className="min-w-0 w-full mt-1 md:mt-0">
          <span className="block truncate text-[13px] font-semibold text-slate-200 transition group-hover:text-white">{finding.vulnType}</span>
          <span className="mt-1 block truncate font-mono text-[10px] text-slate-600">{finding.id}</span>
        </span>

        {!hideLocation ? (
          <span className="min-w-0 rounded-lg border border-slate-700/20 bg-slate-950/20 px-2.5 py-2 md:border-0 md:bg-transparent md:p-0 w-full md:w-auto mt-2 md:mt-0">
            <span className="flex items-center gap-1.5 truncate text-[11px] font-medium text-slate-400"><FileCode2 size={12} className="shrink-0 text-slate-600" /> {getFileName(finding.filePath)}:{finding.lineNumber}</span>
            <span className="mt-1 block truncate text-[9px] text-slate-600">{getDirectoryPath(finding.filePath) || 'Repository root'}</span>
          </span>
        ) : (
          <span className="min-w-0 rounded-lg border border-slate-700/20 bg-slate-950/20 px-2.5 py-2 md:border-0 md:bg-transparent md:p-0 w-full md:w-auto mt-2 md:mt-0">
            <span className="flex items-center gap-1.5 truncate text-[11px] font-medium text-slate-400"><FileCode2 size={12} className="shrink-0 text-slate-600" /> Line {finding.lineNumber}</span>
          </span>
        )}

        <span className="hidden md:block"><span className={`inline-flex rounded-md px-2 py-1 text-[9px] font-bold tracking-wide ${statusClass[findingStatus] || statusClass.DETECTED}`}>{formatStatus(findingStatus)}</span></span>

        <ChevronRight size={17} className="text-slate-700 transition group-hover:translate-x-0.5 group-hover:text-cyan-300 hidden md:block" />
      </div>
    </div>
  );
}

function FileGroup({ filePath, findings, activeRun, onSelect, selectedIds, onToggleSelect, onToggleGroup }) {
  const [expanded, setExpanded] = useState(true);
  const groupSelectedCount = findings.filter(f => selectedIds.has(f.id)).length;
  const isAllSelected = groupSelectedCount === findings.length && findings.length > 0;
  const isIndeterminate = groupSelectedCount > 0 && groupSelectedCount < findings.length;

  return (
    <div className="border border-slate-700/30 rounded-xl bg-[#0a1421] overflow-hidden mb-4 last:mb-0">
      <div className="flex items-center w-full bg-slate-800/30 hover:bg-slate-800/50 transition border-b border-slate-700/30">
        <div className="flex shrink-0 items-center justify-center pl-4 pr-3 py-3 md:pl-5">
          <input
            type="checkbox"
            checked={isAllSelected}
            ref={input => { if (input) input.indeterminate = isIndeterminate; }}
            onChange={(e) => onToggleGroup(findings.map(f => f.id), e.target.checked)}
            className="h-4 w-4 rounded border-slate-600 bg-slate-800 text-cyan-500 focus:ring-cyan-500/50 focus:ring-offset-slate-900 cursor-pointer"
          />
        </div>
        <button onClick={() => setExpanded(!expanded)} className="flex-1 flex items-center justify-between py-3 pr-4 md:pr-5 min-w-0">
          <div className="flex items-center gap-3 min-w-0 pr-4">
            <Folder size={16} className="text-cyan-400 shrink-0" />
            <span className="text-[13px] font-semibold text-slate-200 truncate">{getFileName(filePath)}</span>
            <span className="text-[11px] text-slate-500 truncate block">{getDirectoryPath(filePath) || 'Repository root'}</span>
          </div>
          <div className="flex items-center gap-3 shrink-0">
            <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 bg-slate-900/50 px-2 py-1 rounded-md">{findings.length} findings</span>
            {expanded ? <ChevronUp size={16} className="text-slate-500" /> : <ChevronDown size={16} className="text-slate-500" />}
          </div>
        </button>
      </div>
      {expanded && (
        <div className="bg-slate-950/20 pl-2 border-l-2 border-slate-700/30 ml-3 mb-2">
          <div className="hidden md:flex items-center border-b border-slate-700/25 px-5 pl-12 py-2.5 text-[9px] font-bold uppercase tracking-[0.14em] text-slate-600">
            <div className="grid w-full gap-4 grid-cols-[112px_minmax(260px,2fr)_120px_142px_28px]">
              <span>Severity</span><span>Finding</span><span>Line</span><span>Status</span><span />
            </div>
          </div>
          {findings.map(finding => (
            <FindingRow
              key={finding.id}
              finding={finding}
              activeRun={activeRun}
              onSelect={onSelect}
              hideLocation={true}
              isSelected={selectedIds.has(finding.id)}
              onToggleSelect={onToggleSelect}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export function FindingsView({ findings, activeRun, onSelect, onBulkAction, onRefresh }) {
  const [query, setQuery] = useState('');
  const [severity, setSeverity] = useState('ALL');
  const [status, setStatus] = useState('ALL');
  const [viewMode, setViewMode] = useState('list'); // 'list' or 'grouped'
  const [selectedIds, setSelectedIds] = useState(new Set());

  const filtered = useMemo(() => filterFindings(findings, { query, severity, status, activeRun }), [findings, query, severity, status, activeRun]);

  const groupedFindings = useMemo(() => {
    if (viewMode !== 'grouped') return [];
    const groups = new Map();
    for (const finding of filtered) {
      if (!groups.has(finding.filePath)) groups.set(finding.filePath, []);
      groups.get(finding.filePath).push(finding);
    }
    return Array.from(groups.entries()).sort((a, b) => a[0].localeCompare(b[0]));
  }, [filtered, viewMode]);

  const severityOptions = useMemo(() => [
    { value: 'ALL', label: 'All', count: findings.length },
    ...FINDING_SEVERITY_ORDER.map(value => ({
      value,
      label: value.charAt(0) + value.slice(1).toLowerCase(),
      count: findings.filter(finding => finding.severity === value).length,
    })),
  ], [findings]);

  const statusOptions = useMemo(() => {
    const counts = findings.reduce((result, finding) => {
      const value = (activeRun?.vulnerabilityId === finding.id ? activeRun.status : finding.status) || 'DETECTED';
      result.set(value, (result.get(value) || 0) + 1);
      return result;
    }, new Map());
    const values = [...counts.keys()].sort((left, right) => {
      const leftIndex = statusOrder.indexOf(left);
      const rightIndex = statusOrder.indexOf(right);
      return (leftIndex === -1 ? statusOrder.length : leftIndex) - (rightIndex === -1 ? statusOrder.length : rightIndex)
        || left.localeCompare(right);
    });
    if (status !== 'ALL' && !counts.has(status)) values.push(status);
    return values.map(value => ({ value, count: counts.get(value) || 0 }));
  }, [findings, status, activeRun]);

  const hasFilters = Boolean(query.trim()) || severity !== 'ALL' || status !== 'ALL';
  const resetFilters = () => {
    setQuery('');
    setSeverity('ALL');
    setStatus('ALL');
  };

  const handleToggleSelect = (id, isSelected) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (isSelected) next.add(id);
      else next.delete(id);
      return next;
    });
  };

  const handleToggleGroup = (ids, isSelected) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      ids.forEach(id => {
        if (isSelected) next.add(id);
        else next.delete(id);
      });
      return next;
    });
  };

  const handleSelectAllFiltered = (isSelected) => {
    handleToggleGroup(filtered.map(f => f.id), isSelected);
  };

  const filteredSelectedCount = filtered.filter(f => selectedIds.has(f.id)).length;
  const isAllFilteredSelected = filteredSelectedCount === filtered.length && filtered.length > 0;
  const isFilteredIndeterminate = filteredSelectedCount > 0 && filteredSelectedCount < filtered.length;

  return <div className="animate-fade-in p-4 sm:p-6 xl:p-8 2xl:p-10 relative">
    <div className="mb-5 flex items-start justify-between">
      <div>
        <p className="eyebrow">Security evidence</p>
        <h2 className="mt-2 text-2xl font-semibold tracking-tight text-white">Findings inventory</h2>
        <p aria-live="polite" className="mt-1.5 text-xs text-slate-500"><span className="font-semibold text-slate-300">{filtered.length}</span> of {findings.length} findings in the current audit</p>
      </div>
      <div className="flex items-center gap-1 rounded-lg border border-slate-700/50 p-1 bg-slate-900/30">
        <button onClick={onRefresh} title="Refresh findings" className="p-1.5 rounded-md transition text-slate-500 hover:text-cyan-300"><RotateCcw size={16} /></button>
        <div className="w-px h-4 bg-slate-700/50 mx-0.5"></div>
        <button onClick={() => setViewMode('list')} title="List view" className={`p-1.5 rounded-md transition ${viewMode === 'list' ? 'bg-cyan-500/20 text-cyan-300 shadow-sm' : 'text-slate-500 hover:text-slate-300'}`}><List size={16} /></button>
        <button onClick={() => setViewMode('grouped')} title="Group by file" className={`p-1.5 rounded-md transition ${viewMode === 'grouped' ? 'bg-cyan-500/20 text-cyan-300 shadow-sm' : 'text-slate-500 hover:text-slate-300'}`}><FolderTree size={16} /></button>
      </div>
    </div>

    <section className="surface-card-muted mb-4 rounded-2xl p-3 sm:p-4" aria-label="Filter findings">
      <div className="grid gap-3 lg:grid-cols-[minmax(280px,1fr)_minmax(210px,0.42fr)_auto] lg:items-end">
        <label className="min-w-0">
          <span className="mb-1.5 block text-[9px] font-bold uppercase tracking-[0.14em] text-slate-600">Search</span>
          <span className="relative block">
            <span className="sr-only">Search findings</span>
            <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
            <input aria-label="Search findings" value={query} onChange={event => setQuery(event.target.value)} placeholder="ID, rule, type, file, description, language..." className="field-control w-full py-2 pl-9 pr-10 text-xs" />
            {query && <button type="button" onClick={() => setQuery('')} aria-label="Clear search" className="absolute right-2 top-1/2 flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-md text-slate-500 transition hover:bg-slate-700/40 hover:text-slate-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/60"><X size={14} /></button>}
          </span>
        </label>
        <label>
          <span className="mb-1.5 block text-[9px] font-bold uppercase tracking-[0.14em] text-slate-600">Lifecycle status</span>
          <select aria-label="Status" value={status} onChange={event => setStatus(event.target.value)} className="field-control w-full px-3 text-xs">
            <option value="ALL">All statuses ({findings.length})</option>
            {statusOptions.map(option => <option key={option.value} value={option.value}>{formatStatus(option.value)} ({option.count})</option>)}
          </select>
        </label>
        <button type="button" onClick={resetFilters} disabled={!hasFilters} className="inline-flex h-10.5 items-center justify-center gap-2 rounded-xl border border-slate-700/40 bg-slate-900/45 px-4 text-[10px] font-bold uppercase tracking-[0.08em] text-slate-400 transition hover:border-slate-600/60 hover:text-slate-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/60 disabled:cursor-not-allowed disabled:opacity-40"><RotateCcw size={14} /> Reset</button>
      </div>
      <div className="mt-4 flex flex-col gap-3 border-t border-slate-700/25 pt-3 xl:flex-row xl:items-center xl:justify-between">
        <fieldset className="min-w-0">
          <legend className="sr-only">Filter by severity</legend>
          <div className="flex gap-1.5 overflow-x-auto pb-1">
            {severityOptions.map(option => {
              const active = severity === option.value;
              return <button key={option.value} type="button" aria-pressed={active} onClick={() => setSeverity(option.value)}
                className={`inline-flex shrink-0 items-center gap-2 rounded-lg border px-3 py-2 text-[10px] font-bold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/60 ${active ? 'border-cyan-400/35 bg-cyan-400/10 text-cyan-200' : 'border-slate-700/35 bg-slate-950/25 text-slate-500 hover:border-slate-600/60 hover:text-slate-300'}`}>
                {option.label}<span className={`rounded-md px-1.5 py-0.5 font-mono text-[9px] ${active ? 'bg-cyan-300/10 text-cyan-100' : 'bg-slate-800/70 text-slate-500'}`}>{option.count}</span>
              </button>;
            })}
          </div>
        </fieldset>
        <p className="shrink-0 text-[9px] font-semibold uppercase tracking-[0.12em] text-slate-600">Priority order: High to Medium to Low to Info</p>
      </div>
    </section>

    {filtered.length === 0 ? (
      <section className="surface-card overflow-hidden rounded-2xl">
        <div className="flex min-h-72 flex-col items-center justify-center p-8 text-center"><span className="flex h-12 w-12 items-center justify-center rounded-2xl border border-slate-700/40 bg-slate-900/55 text-slate-500">{hasFilters ? <Filter size={21} /> : <ShieldAlert size={21} />}</span><p className="mt-4 text-sm font-semibold text-slate-300">{hasFilters ? 'No matching findings' : 'No findings in this audit'}</p><p className="mt-1 max-w-xs text-xs leading-5 text-slate-600">{hasFilters ? 'Adjust the search or filters to broaden the result set.' : 'The scanner did not return any security findings for this commit.'}</p>{hasFilters && <button type="button" onClick={resetFilters} className="mt-4 inline-flex items-center gap-2 rounded-lg border border-cyan-400/25 bg-cyan-400/10 px-3 py-2 text-[10px] font-bold uppercase tracking-[0.08em] text-cyan-200 transition hover:bg-cyan-400/15 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/60"><RotateCcw size={13} /> Clear filters</button>}</div>
      </section>
    ) : viewMode === 'list' ? (
      <section className="surface-card overflow-hidden rounded-2xl" aria-label="Security findings">
        <div className="overflow-x-auto">
          <div className="md:min-w-[800px]">
            <div className="hidden md:flex items-center border-b border-slate-700/25 bg-slate-950/25 px-5 py-3.5 text-[9px] font-bold uppercase tracking-[0.14em] text-slate-600">
              <div className="mr-5 flex items-center shrink-0">
                <input
                  type="checkbox"
                  checked={isAllFilteredSelected}
                  ref={input => { if (input) input.indeterminate = isFilteredIndeterminate; }}
                  onChange={(e) => handleSelectAllFiltered(e.target.checked)}
                  className="h-4 w-4 rounded border-slate-600 bg-slate-800 text-cyan-500 focus:ring-cyan-500/50 focus:ring-offset-slate-900 cursor-pointer"
                />
              </div>
              <div className="grid flex-1 gap-4 grid-cols-[112px_minmax(260px,1.5fr)_minmax(190px,0.8fr)_142px_28px]">
                <span>Severity</span><span>Finding</span><span>Location</span><span>Status</span><span />
              </div>
            </div>
            {filtered.map(finding => (
              <FindingRow
                key={finding.id}
                finding={finding}
                activeRun={activeRun}
                onSelect={onSelect}
                isSelected={selectedIds.has(finding.id)}
                onToggleSelect={handleToggleSelect}
              />
            ))}
          </div>
        </div>
      </section>
    ) : (
      <section aria-label="Security findings grouped by file" className="overflow-x-auto">
        <div className="md:min-w-[800px]">
          {groupedFindings.map(([filePath, groupFindings]) => (
            <FileGroup
              key={filePath}
              filePath={filePath}
              findings={groupFindings}
              activeRun={activeRun}
              onSelect={onSelect}
              selectedIds={selectedIds}
              onToggleSelect={handleToggleSelect}
              onToggleGroup={handleToggleGroup}
            />
          ))}
        </div>
      </section>
    )}

    {selectedIds.size > 0 && (
      <div className="sticky bottom-6 z-40 flex justify-center pointer-events-none mt-4 animate-fade-in">
        <div className="pointer-events-auto flex items-center gap-4 rounded-2xl border border-slate-700/50 bg-[#0b1a2b]/95 px-6 py-3 shadow-[0_20px_50px_rgba(0,0,0,0.5)] backdrop-blur-xl">
          <span className="flex items-center gap-2 text-[13px] font-semibold text-slate-200">
            <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-cyan-500/20 px-1.5 text-[11px] text-cyan-300">{selectedIds.size}</span>
            selected
          </span>
          <div className="h-5 w-px bg-slate-700/50" />
          <button
            onClick={() => { if (onBulkAction) onBulkAction('analyze', Array.from(selectedIds)); }}
            className="flex items-center gap-1.5 rounded-lg bg-cyan-500 px-3 py-1.5 text-xs font-bold text-[#04202b] transition hover:bg-cyan-400">
            Analyze
          </button>
          <button
            onClick={() => { if (onBulkAction) onBulkAction('ignore', Array.from(selectedIds)); }}
            className="flex items-center gap-1.5 rounded-lg border border-slate-600 bg-slate-800 px-3 py-1.5 text-xs font-bold text-slate-300 transition hover:bg-slate-700">
            Ignore
          </button>
          <button
            onClick={() => setSelectedIds(new Set())}
            className="ml-2 rounded-lg p-1.5 text-slate-500 transition hover:bg-slate-800 hover:text-slate-300"
            aria-label="Clear selection">
            <X size={16} />
          </button>
        </div>
      </div>
    )}
  </div>;
}
