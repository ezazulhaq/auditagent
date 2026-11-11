import { memo } from 'react';
import { LoaderCircle, X } from 'lucide-react';

/**
 * BulkActionBar displays the floating batch progress or selection toolbar for bulk actions.
 * Adheres to Single Responsibility Principle (SRP).
 */
export const BulkActionBar = memo(function BulkActionBar({
  batchState,
  onCancelBatch,
  selectedCount,
  onClearSelection,
  onBulkAction,
  selectedIds,
  isUploadScan,
}) {
  if (batchState?.active) {
    return (
      <div className="sticky bottom-6 z-40 flex justify-center pointer-events-none mt-4 animate-fade-in" data-testid="batch-progress-bar">
        <div className="pointer-events-auto flex items-center gap-4 rounded-2xl border border-cyan-500/30 bg-[#0b1a2b]/95 px-6 py-3 shadow-[0_20px_50px_rgba(0,0,0,0.5)] backdrop-blur-xl">
          <div className="flex items-center gap-3">
            <LoaderCircle size={16} className="animate-spin text-cyan-400 shrink-0" />
            <div className="flex flex-col">
              <span className="text-xs font-semibold text-slate-200">
                Analyzing {batchState.completed + batchState.failed} of {batchState.total} vulnerabilities...
              </span>
              <div className="mt-1 h-1.5 w-40 overflow-hidden rounded-full bg-slate-800">
                <div
                  className="h-full bg-cyan-400 transition-all duration-300"
                  style={{
                    width: `${batchState.total > 0 ? Math.round(((batchState.completed + batchState.failed) / batchState.total) * 100) : 0}%`
                  }}
                />
              </div>
            </div>
          </div>
          {batchState.failed > 0 && (
            <span className="rounded bg-rose-400/10 px-1.5 py-0.5 text-[10px] font-medium text-rose-300">
              {batchState.failed} failed
            </span>
          )}
          <div className="h-5 w-px bg-slate-700/50" />
          <button
            type="button"
            onClick={onCancelBatch}
            className="flex items-center gap-1.5 rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-1.5 text-xs font-bold text-rose-300 transition hover:bg-rose-500/20"
          >
            Cancel Batch
          </button>
        </div>
      </div>
    );
  }

  if (selectedCount > 0) {
    return (
      <div className="sticky bottom-6 z-40 flex justify-center pointer-events-none mt-4 animate-fade-in">
        <div className="pointer-events-auto flex items-center gap-4 rounded-2xl border border-slate-700/50 bg-[#0b1a2b]/95 px-6 py-3 shadow-[0_20px_50px_rgba(0,0,0,0.5)] backdrop-blur-xl">
          <span className="flex items-center gap-2 text-[13px] font-semibold text-slate-200">
            <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-cyan-500/20 px-1.5 text-[11px] text-cyan-300">
              {selectedCount}
            </span>
            selected
          </span>
          <div className="h-5 w-px bg-slate-700/50" />
          <button
            disabled={isUploadScan}
            title={isUploadScan ? 'Remediation is disabled for uploaded ZIP archives' : undefined}
            onClick={() => {
              if (onBulkAction && !isUploadScan) onBulkAction('analyze', Array.from(selectedIds));
              onClearSelection();
            }}
            className="flex items-center gap-1.5 rounded-lg bg-cyan-500 px-3 py-1.5 text-xs font-bold text-[#04202b] transition hover:bg-cyan-400 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Analyze
          </button>
          <button
            onClick={() => {
              if (onBulkAction) onBulkAction('ignore', Array.from(selectedIds));
            }}
            className="flex items-center gap-1.5 rounded-lg border border-slate-600 bg-slate-800 px-3 py-1.5 text-xs font-bold text-slate-300 transition hover:bg-slate-700"
          >
            Ignore
          </button>
          <button
            onClick={onClearSelection}
            className="ml-2 rounded-lg p-1.5 text-slate-500 transition hover:bg-slate-800 hover:text-slate-300"
            aria-label="Clear selection"
          >
            <X size={16} />
          </button>
        </div>
      </div>
    );
  }

  return null;
});
