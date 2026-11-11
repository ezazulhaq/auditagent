import { memo } from 'react';
import { ChevronRight, FileCode2, LoaderCircle } from 'lucide-react';
import { getDirectoryPath, getFileName } from '../../../utils/auditSelectors';
import { severityClass, statusClass, formatStatus } from '../constants/findingStyles';

/**
 * FindingRow renders a single vulnerability entry in the findings table.
 * Adheres to Single Responsibility Principle (SRP).
 */
export const FindingRow = memo(function FindingRow({
  finding,
  runs,
  runStatus: explicitRunStatus,
  onSelect,
  hideLocation = false,
  isSelected,
  onToggleSelect,
  isAnalyzing = false,
}) {
  const isTerminal = finding.status === 'FIXED' || finding.status === 'IGNORED';
  const effectiveStatus = explicitRunStatus !== undefined ? explicitRunStatus : runs?.[finding.id]?.status;
  const findingStatus = (isTerminal ? finding.status : (effectiveStatus || finding.status)) || 'DETECTED';

  return (
    <div className="group flex w-full items-start md:items-stretch border-b border-slate-700/20 transition last:border-0 hover:bg-cyan-400/[0.035] finding-row-contained">
      <div className="flex shrink-0 items-start md:items-center justify-center pl-4 pr-3 py-4 md:pl-5">
        <input
          type="checkbox"
          checked={isSelected}
          onChange={(e) => onToggleSelect(finding.id, e.target.checked)}
          className="h-4 w-4 rounded border-slate-600 bg-slate-800 text-cyan-500 focus:ring-cyan-500/50 focus:ring-offset-slate-900 cursor-pointer mt-1 md:mt-0"
        />
      </div>
      <div
        onClick={() => onSelect(finding)}
        role="button"
        aria-label={`Open ${finding.vulnType} finding ${finding.id}`}
        className={`flex flex-col md:grid flex-1 cursor-pointer gap-2 md:gap-4 py-4 pr-4 text-left items-start md:items-center md:pr-5 ${
          hideLocation
            ? 'md:grid-cols-[112px_minmax(260px,2fr)_120px_142px_28px]'
            : 'md:grid-cols-[112px_minmax(260px,1.5fr)_minmax(190px,0.8fr)_142px_28px]'
        }`}
      >
        <div className="flex w-full items-center justify-between md:contents">
          <span className="flex items-center gap-1.5 flex-wrap">
            <span className={`inline-flex min-w-18 items-center justify-center rounded-md px-2 py-1 font-mono text-[9px] font-bold tracking-[0.08em] ${severityClass[finding.severity] || severityClass.INFO}`}>
              {finding.severity}
            </span>
            {finding.triageActionable === false && (
              <span
                className="inline-flex items-center rounded-md border border-amber-500/30 bg-amber-500/10 px-1.5 py-0.5 text-[8px] font-semibold text-amber-300"
                title={`Triaged as false positive (${Math.round((finding.triageConfidence || 0) * 100)}% confidence)`}
                data-testid="badge-likely-fp"
              >
                Likely FP
              </span>
            )}
          </span>
          <span className="md:hidden">
            <span className={`inline-flex items-center gap-1 rounded-md px-2 py-1 text-[9px] font-bold tracking-wide ${statusClass[findingStatus] || statusClass.DETECTED}`}>
              {isAnalyzing && <LoaderCircle size={10} className="animate-spin text-cyan-400" />}
              {formatStatus(findingStatus)}
            </span>
          </span>
        </div>

        <span className="min-w-0 w-full mt-1 md:mt-0">
          <span className="block truncate text-[13px] font-semibold text-slate-200 transition group-hover:text-white font-sans">
            {finding.vulnType}
          </span>
          <span className="mt-1 block truncate font-mono text-[10px] text-slate-400">
            {finding.id}
          </span>
        </span>

        {!hideLocation ? (
          <span className="min-w-0 rounded-lg border border-slate-700/20 bg-slate-950/20 px-2.5 py-2 md:border-0 md:bg-transparent md:p-0 w-full md:w-auto mt-2 md:mt-0">
            <span className="flex items-center gap-1.5 truncate font-mono text-[11px] font-medium text-slate-300">
              <FileCode2 size={12} className="shrink-0 text-slate-400" /> {getFileName(finding.filePath)}:{finding.lineNumber}
            </span>
            <span className="mt-1 block truncate font-mono text-[9px] text-slate-400">
              {getDirectoryPath(finding.filePath) || 'Repository root'}
            </span>
          </span>
        ) : (
          <span className="min-w-0 rounded-lg border border-slate-700/20 bg-slate-950/20 px-2.5 py-2 md:border-0 md:bg-transparent md:p-0 w-full md:w-auto mt-2 md:mt-0">
            <span className="flex items-center gap-1.5 truncate font-mono text-[11px] font-medium text-slate-300">
              <FileCode2 size={12} className="shrink-0 text-slate-400" /> Line {finding.lineNumber}
            </span>
          </span>
        )}

        <span className="hidden md:block">
          <span className={`inline-flex items-center gap-1 rounded-md px-2 py-1 text-[9px] font-bold tracking-wide ${statusClass[findingStatus] || statusClass.DETECTED}`}>
            {isAnalyzing && <LoaderCircle size={10} className="animate-spin text-cyan-400" />}
            {formatStatus(findingStatus)}
          </span>
        </span>

        <ChevronRight size={17} className="text-slate-600 transition group-hover:translate-x-0.5 group-hover:text-cyan-300 hidden md:block" />
      </div>
    </div>
  );
});
