import { Filter, RotateCcw, ShieldAlert } from 'lucide-react';
import { memo } from 'react';
import { useFindingsFilter } from './hooks/useFindingsFilter';
import { FindingsToolbar } from './components/FindingsToolbar';
import { FindingRow } from './components/FindingRow';
import { FileGroup } from './components/FileGroup';
import { BulkActionBar } from './components/BulkActionBar';

/**
 * FindingsView displays security findings with filtering, grouping, and bulk remediation.
 * Refactored to strictly adhere to Single Responsibility Principle (SRP) and Open/Closed Principle (OCP).
 */
export const FindingsView = memo(function FindingsView({
  findings,
  runs,
  onSelect,
  onBulkAction,
  onRefresh,
  batchState,
  onCancelBatch,
  isAnalyzingMap,
  isUploadScan = false,
}) {
  const {
    query,
    setQuery,
    severity,
    setSeverity,
    status,
    setStatus,
    viewMode,
    setViewMode,
    selectedIds,
    clearSelection,
    filtered,
    groupedFindings,
    severityOptions,
    statusOptions,
    hasFilters,
    resetFilters,
    handleToggleSelect,
    handleToggleGroup,
    handleSelectAllFiltered,
    isAllFilteredSelected,
    isFilteredIndeterminate,
  } = useFindingsFilter(findings, runs);

  return (
    <div className="animate-fade-in p-4 sm:p-6 xl:p-8 2xl:p-10 relative">
      <FindingsToolbar
        findingsCount={findings.length}
        filteredCount={filtered.length}
        query={query}
        onQueryChange={setQuery}
        status={status}
        onStatusChange={setStatus}
        statusOptions={statusOptions}
        severity={severity}
        onSeverityChange={setSeverity}
        severityOptions={severityOptions}
        viewMode={viewMode}
        onViewModeChange={setViewMode}
        onRefresh={onRefresh}
        hasFilters={hasFilters}
        onResetFilters={resetFilters}
      />

      {filtered.length === 0 ? (
        <section className="surface-card overflow-hidden rounded-2xl">
          <div className="flex min-h-72 flex-col items-center justify-center p-8 text-center">
            <span className="flex h-12 w-12 items-center justify-center rounded-2xl border border-slate-700/40 bg-slate-900/55 text-slate-500">
              {hasFilters ? <Filter size={21} /> : <ShieldAlert size={21} />}
            </span>
            <p className="mt-4 text-sm font-semibold text-slate-300">
              {hasFilters ? 'No matching findings' : 'No findings in this audit'}
            </p>
            <p className="mt-1 max-w-xs text-xs leading-5 text-slate-600">
              {hasFilters
                ? 'Adjust the search or filters to broaden the result set.'
                : 'The scanner did not return any security findings for this commit.'}
            </p>
            {hasFilters && (
              <button
                type="button"
                onClick={resetFilters}
                className="mt-4 inline-flex items-center gap-2 rounded-lg border border-cyan-400/25 bg-cyan-400/10 px-3 py-2 text-[10px] font-bold uppercase tracking-[0.08em] text-cyan-200 transition hover:bg-cyan-400/15 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/60"
              >
                <RotateCcw size={13} /> Clear filters
              </button>
            )}
          </div>
        </section>
      ) : viewMode === 'list' ? (
        <section className="surface-card overflow-hidden rounded-2xl" aria-label="Security findings">
          <div className="overflow-x-auto">
            <div className="md:min-w-200">
              <div className="hidden md:flex items-center border-b border-slate-700/25 bg-slate-950/25 px-5 py-3.5 text-[9px] font-bold uppercase tracking-[0.14em] text-slate-600">
                <div className="mr-5 flex items-center shrink-0">
                  <input
                    type="checkbox"
                    checked={isAllFilteredSelected}
                    ref={input => {
                      if (input) input.indeterminate = isFilteredIndeterminate;
                    }}
                    onChange={e => handleSelectAllFiltered(e.target.checked)}
                    className="h-4 w-4 rounded border-slate-600 bg-slate-800 text-cyan-500 focus:ring-cyan-500/50 focus:ring-offset-slate-900 cursor-pointer"
                  />
                </div>
                <div className="grid flex-1 gap-4 grid-cols-[112px_minmax(260px,1.5fr)_minmax(190px,0.8fr)_142px_28px]">
                  <span>Severity</span>
                  <span>Finding</span>
                  <span>Location</span>
                  <span>Status</span>
                  <span />
                </div>
              </div>
              {filtered.map(finding => (
                <FindingRow
                  key={finding.id}
                  finding={finding}
                  runStatus={runs?.[finding.id]?.status}
                  onSelect={onSelect}
                  isSelected={selectedIds.has(finding.id)}
                  onToggleSelect={handleToggleSelect}
                  isAnalyzing={Boolean(isAnalyzingMap?.[finding.id])}
                />
              ))}
            </div>
          </div>
        </section>
      ) : (
        <section aria-label="Security findings grouped by file" className="overflow-x-auto">
          <div className="md:min-w-200">
            {groupedFindings.map(([filePath, groupFindings]) => (
              <FileGroup
                key={filePath}
                filePath={filePath}
                findings={groupFindings}
                runs={runs}
                onSelect={onSelect}
                selectedIds={selectedIds}
                onToggleSelect={handleToggleSelect}
                onToggleGroup={handleToggleGroup}
                isAnalyzingMap={isAnalyzingMap}
              />
            ))}
          </div>
        </section>
      )}

      <BulkActionBar
        batchState={batchState}
        onCancelBatch={onCancelBatch}
        selectedCount={selectedIds.size}
        onClearSelection={clearSelection}
        onBulkAction={onBulkAction}
        selectedIds={selectedIds}
        isUploadScan={isUploadScan}
      />
    </div>
  );
});
