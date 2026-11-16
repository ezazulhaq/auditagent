import { memo, useMemo } from 'react';
import { GitFork, GitBranch, ScanLine, Play, ChevronRight, RefreshCw } from 'lucide-react';
import { Switch, SwitchThumb } from '@ui-sdk/react/switch';
import { CustomSelect } from '../../../components/CustomSelect';
import { formatTime } from '../../../utils/auditSelectors';

const ScanActionButton = memo(function ScanActionButton({ isScanning, scanTime, onCancel, canRunScan }) {
  if (isScanning) {
    return (
      <div className="flex gap-2">
        <div className="flex flex-1 items-center justify-center gap-2 rounded-xl bg-slate-800 px-4 py-2.5 text-sm font-bold text-slate-400">
          <RefreshCw size={16} className="animate-spin" /> Scanning <span className="font-mono">{formatTime(scanTime)}</span>
        </div>
        <button
          type="button"
          onClick={onCancel}
          className="flex shrink-0 items-center justify-center rounded-xl border border-rose-500/20 bg-rose-500/10 px-4 py-2.5 text-sm font-bold text-rose-300 transition hover:border-rose-500/30 hover:bg-rose-500/20"
        >
          Cancel
        </button>
      </div>
    );
  }

  return (
    <button
      disabled={!canRunScan}
      className="primary-action flex w-full items-center justify-center gap-2 px-4 py-2.5 text-sm font-bold"
    >
      <Play size={16} fill="currentColor" /> Run Scan <ChevronRight size={15} />
    </button>
  );
});

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
  const selectedRepository = useMemo(() => {
    return repositories.find(
      repository => String(repository.repositoryId) === String(config?.repositoryId)
    );
  }, [repositories, config?.repositoryId]);

  const repositoryOptions = useMemo(() => {
    return repositories.map(r => ({
      value: r.repositoryId,
      label: `${r.fullName} (${r.permission.toLowerCase()})`
    }));
  }, [repositories]);

  const branchOptions = useMemo(() => {
    return branches.map(b => ({
      value: b,
      label: b
    }));
  }, [branches]);

  const scannerOptions = useMemo(() => [
    { value: 'semgrep', label: 'Semgrep · managed ruleset' }
  ], []);

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
        <Switch
          checked={Boolean(config.forceRescan)}
          onCheckedChange={checked => onConfigChange('forceRescan', checked)}
          aria-label="Force fresh rescan"
          className="relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full border border-slate-600 bg-slate-800 p-0.5 transition data-checked:border-cyan-400/60 data-checked:bg-cyan-400/20"
        >
          <SwitchThumb className="block h-4 w-4 rounded-full bg-slate-500 shadow transition-transform data-checked:translate-x-5 data-checked:bg-[#06b6d4]" />
        </Switch>
      </label>

      <ScanActionButton
        isScanning={scan.isScanning}
        scanTime={scanTime}
        onCancel={scan.cancel}
        canRunScan={Boolean(config.repositoryId && config.branch)}
      />
    </form>
  );
});
