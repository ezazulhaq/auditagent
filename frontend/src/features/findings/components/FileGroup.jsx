import { useState, memo } from 'react';
import { ChevronDown, ChevronUp, Folder } from 'lucide-react';
import { getDirectoryPath, getFileName } from '../../../utils/auditSelectors';
import { FindingRow } from './FindingRow';

/**
 * FileGroup renders findings grouped under a collapsible repository directory/file header.
 * Adheres to Single Responsibility Principle (SRP).
 */
export const FileGroup = memo(function FileGroup({
  filePath,
  findings,
  runs,
  onSelect,
  selectedIds,
  onToggleSelect,
  onToggleGroup,
  isAnalyzingMap,
}) {
  const [expanded, setExpanded] = useState(true);
  const groupSelectedCount = findings.filter(f => selectedIds.has(f.id)).length;
  const isAllSelected = groupSelectedCount === findings.length && findings.length > 0;
  const isIndeterminate = groupSelectedCount > 0 && groupSelectedCount < findings.length;

  return (
    <div className="border border-[var(--border-subtle)] rounded-xl bg-[var(--surface-card)] overflow-hidden mb-4 last:mb-0 shadow-sm">
      <div className="flex items-center w-full bg-[var(--surface-inset)]/40 hover:bg-[var(--surface-inset)]/70 transition border-b border-[var(--border-subtle)]">
        <div className="flex shrink-0 items-center justify-center pl-4 pr-3 py-3 md:pl-5">
          <input
            type="checkbox"
            checked={isAllSelected}
            ref={input => { if (input) input.indeterminate = isIndeterminate; }}
            onChange={(e) => onToggleGroup(findings.map(f => f.id), e.target.checked)}
            className="h-4 w-4 rounded border-slate-600 bg-slate-800 text-cyan-500 focus:ring-cyan-500/50 focus:ring-offset-slate-900 cursor-pointer"
          />
        </div>
        <button
          onClick={() => setExpanded(!expanded)}
          className="flex-1 flex items-center justify-between py-3 pr-4 md:pr-5 min-w-0"
        >
          <div className="flex items-center gap-3 min-w-0 pr-4">
            <Folder size={16} className="text-cyan-400 shrink-0" />
            <span className="text-[13px] font-semibold text-slate-200 truncate font-sans">
              {getFileName(filePath)}
            </span>
            <span className="font-mono text-[11px] text-slate-400 truncate block">
              {getDirectoryPath(filePath) || 'Repository root'}
            </span>
          </div>
          <div className="flex items-center gap-3 shrink-0">
            <span className="font-mono text-[10px] font-semibold text-slate-400 bg-[var(--surface-inset)] px-2 py-0.5 rounded-md tabular-nums border border-[var(--border-subtle)]">
              {findings.length} findings
            </span>
            {expanded ? <ChevronUp size={16} className="text-slate-400" /> : <ChevronDown size={16} className="text-slate-400" />}
          </div>
        </button>
      </div>

      {expanded && (
        <div className="bg-slate-950/20 pl-2 border-l-2 border-slate-700/30 ml-3 mb-2">
          <div className="hidden md:flex items-center border-b border-slate-700/25 px-5 pl-12 py-2.5 text-[9px] font-bold uppercase tracking-[0.14em] text-slate-600">
            <div className="grid w-full gap-4 grid-cols-[112px_minmax(260px,2fr)_120px_142px_28px]">
              <span>Severity</span>
              <span>Finding</span>
              <span>Line</span>
              <span>Status</span>
              <span />
            </div>
          </div>
          {findings.map(finding => (
            <FindingRow
              key={finding.id}
              finding={finding}
              runStatus={runs?.[finding.id]?.status}
              onSelect={onSelect}
              hideLocation={true}
              isSelected={selectedIds.has(finding.id)}
              onToggleSelect={onToggleSelect}
              isAnalyzing={Boolean(isAnalyzingMap?.[finding.id])}
            />
          ))}
        </div>
      )}
    </div>
  );
});
