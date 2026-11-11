import { memo } from 'react';
import { GitFork, GitBranch, ScanLine, Play, ChevronRight, RefreshCw } from 'lucide-react';
import { CustomSelect } from '../../../components/CustomSelect';
import { formatTime } from '../../../utils/auditSelectors';

/**
 * RepoBranchSelector handles repository and branch selection, scanner settings, and scan trigger.
 * Adheres to Single Responsibility Principle (SRP).
 */
export const RepoBranchSelector = memo(function RepoBranchSelector({
  config,
  onConfigChange,
  onScan,
  scan,
  scanTime,
  repositories = [],
  branches = [],
  loadingRepositories,
}) {
  const selectedRepository = repositories.find(
    repository => String(repository.repositoryId) === String(config?.repositoryId)
  );

  const repositoryOptions = repositories.map(r => ({
    value: r.repositoryId,
    label: `${r.fullName} (${r.permission.toLowerCase()})`
  }));

  const branchOptions = branches.map(b => ({
    value: b,
    label: b
  }));

  const scannerOptions = [
    { value: 'semgrep', label: 'Semgrep · managed ruleset' }
  ];

  const handleSubmit = (event) => {
    event.preventDefault();
    onScan();
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <label className="block text-[11px] font-semibold text-slate-400">
        <span className="mb-1.5 flex items-center justify-between">
          <span className="flex items-center gap-1.5"><GitFork size={13} className="text-slate-500" /> Repository</span>
          {selectedRepository?.permission && (
            <span className="rounded border border-emerald-400/15 bg-emerald-400/8 px-1.5 py-0.5 text-[9px] uppercase tracking-wide text-emerald-300">
              {selectedRepository.permission}
            </span>
          )}
        </span>
        <CustomSelect
          value={config.repositoryId}
          onChange={val => onConfigChange('repositoryId', val)}
          disabled={loadingRepositories}
          options={repositoryOptions}
          placeholder={loadingRepositories ? 'Loading installations...' : 'Select repository'}
        />
      </label>

      <label className="block text-[11px] font-semibold text-slate-400">
        <span className="mb-1.5 flex items-center gap-1.5"><GitBranch size={13} className="text-slate-500" /> Branch</span>
        <CustomSelect
          value={config.branch}
          onChange={val => onConfigChange('branch', val)}
          disabled={!config.repositoryId}
          options={branchOptions}
          placeholder="Select branch"
        />
      </label>

      <label className="hidden text-[11px] font-semibold text-slate-400">
        <span className="mb-1.5 flex items-center gap-1.5"><ScanLine size={13} className="text-slate-500" /> Scanner engine</span>
        <CustomSelect
          value={config.scannerName}
          onChange={val => onConfigChange('scannerName', val)}
          options={scannerOptions}
          placeholder="Select scanner"
        />
      </label>

      <label className="flex cursor-pointer items-center justify-between gap-4 rounded-xl border border-slate-700/30 bg-slate-900/30 p-3.5 transition hover:border-slate-600/50 hover:bg-slate-900/50">
        <span>
          <span className="block text-xs font-semibold text-slate-300">Force fresh rescan</span>
          <span className="mt-1 block text-[10px] leading-4 text-slate-500">Bypass a valid commit cache and run a new scan.</span>
        </span>
        <span className="relative inline-flex shrink-0">
          <input
            type="checkbox"
            checked={config.forceRescan}
            onChange={event => onConfigChange('forceRescan', event.target.checked)}
            className="peer sr-only"
          />
          <span className="h-6 w-11 rounded-full border border-slate-600 bg-slate-800 transition peer-checked:border-cyan-400/60 peer-checked:bg-cyan-400/20" />
          <span className="absolute left-1 top-1 h-4 w-4 rounded-full bg-slate-500 shadow transition peer-checked:translate-x-5 peer-checked:bg-[#06b6d4]" />
        </span>
      </label>

      {scan.isScanning ? (
        <div className="flex gap-2">
          <div className="flex flex-1 items-center justify-center gap-2 rounded-xl bg-slate-800 px-4 py-2.5 text-sm font-bold text-slate-400">
            <RefreshCw size={16} className="animate-spin" /> Scanning <span className="font-mono">{formatTime(scanTime)}</span>
          </div>
          <button
            type="button"
            onClick={scan.cancel}
            className="flex shrink-0 items-center justify-center rounded-xl border border-rose-500/20 bg-rose-500/10 px-4 py-2.5 text-sm font-bold text-rose-300 transition hover:border-rose-500/30 hover:bg-rose-500/20"
          >
            Cancel
          </button>
        </div>
      ) : (
        <button
          disabled={!config.repositoryId || !config.branch}
          className="primary-action flex w-full items-center justify-center gap-2 px-4 py-2.5 text-sm font-bold"
        >
          <Play size={16} fill="currentColor" /> Run Scan <ChevronRight size={15} />
        </button>
      )}
    </form>
  );
});
