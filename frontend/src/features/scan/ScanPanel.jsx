import { Check, ChevronLeft, ChevronRight, CircleDot, FileArchive, GitBranch, GitFork, Layers3, Play, RefreshCw, ScanLine, Terminal, Upload, UploadCloud, X } from 'lucide-react';
import { useRef, useState } from 'react';
import { formatTime, SCAN_STEPS } from '../../utils/auditSelectors';
import { CustomSelect } from '../../components/CustomSelect';

function formatFileSize(bytes) {
  if (!bytes) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
}

export function ScanPanel({
  config, onConfigChange, onScan, onUploadScan, scan, repositories = [], branches = [],
  loadingRepositories, installationUrl, isOpen = true, onToggle,
  scanMode: externalScanMode, onScanModeChange
}) {
  const [internalScanMode, setInternalScanMode] = useState('github');
  const scanMode = externalScanMode !== undefined ? externalScanMode : internalScanMode;

  const [selectedFile, setSelectedFile] = useState(null);
  const [uploadProjectName, setUploadProjectName] = useState('');
  const [isDragging, setIsDragging] = useState(false);
  const [fileError, setFileError] = useState('');
  const fileInputRef = useRef(null);

  const toggleMode = () => {
    const next = scanMode === 'github' ? 'upload' : 'github';
    if (onScanModeChange) {
      onScanModeChange(next);
    } else {
      setInternalScanMode(next);
    }
  };

  const handleFileSelect = (file) => {
    if (!file) return;
    setFileError('');
    if (!file.name.toLowerCase().endsWith('.zip')) {
      setFileError('Only .zip files are supported.');
      return;
    }
    if (file.size > 50 * 1024 * 1024) {
      setFileError('File size exceeds the 50MB limit.');
      return;
    }
    setSelectedFile(file);
    if (!uploadProjectName) {
      const derived = file.name.replace(/\.zip$/i, '').replace(/[^a-zA-Z0-9-_.]/g, '-');
      setUploadProjectName(derived);
    }
  };

  const clearFile = () => {
    setSelectedFile(null);
    setUploadProjectName('');
    setFileError('');
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      handleFileSelect(e.dataTransfer.files[0]);
    }
  };

  const handleUploadSubmit = (e) => {
    e.preventDefault();
    if (!selectedFile || scan.isScanning) return;
    if (onUploadScan) {
      onUploadScan(selectedFile, uploadProjectName);
    }
  };

  const currentIndex = SCAN_STEPS.findIndex(step => step.id === scan.progress?.step);
  const selectedRepository = repositories.find(repository => String(repository.repositoryId) === String(config?.repositoryId));

  if (!isOpen) {
    return (
      <aside className="hidden lg:flex shrink-0 border-b border-[var(--surface-panel-border)] bg-[var(--surface-panel)]/90 lg:h-[calc(100vh-72px)] lg:w-14 lg:border-b-0 lg:border-r flex-col items-center py-5">
        <button onClick={onToggle} className="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-700/40 bg-slate-900/45 text-slate-400 hover:text-slate-200 hover:bg-slate-800 transition" title="Expand panel">
          <ChevronRight size={16} />
        </button>
      </aside>
    );
  }

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

  return (
    <aside className="fixed inset-y-0 left-0 z-40 w-4/5 max-w-88 flex-col overflow-y-auto bg-[var(--surface-panel)] shadow-2xl lg:static lg:h-[calc(100vh-72px)] lg:w-88 lg:shrink-0 lg:border-r lg:border-[var(--surface-panel-border)] lg:bg-[var(--surface-panel)]/85 lg:shadow-none">
      <div className="p-5 sm:p-6 lg:p-5 xl:p-6">
        <div className="mb-6 flex items-start justify-between gap-3">
          <div>
            <p className="eyebrow">{scanMode === 'upload' ? 'Manual audit' : 'Audit scope'}</p>
            <h2 className="mt-2 flex items-center gap-2 text-base font-semibold text-white">
              {scanMode === 'upload' ? (
                <><Upload size={18} className="text-cyan-300" /> Manual project upload</>
              ) : (
                <><Layers3 size={18} className="text-cyan-300" /> Scan configuration</>
              )}
            </h2>
            <p className="mt-1 text-[11px] leading-5 text-slate-500">
              {scanMode === 'upload'
                ? 'Upload a project in ZIP archive format (max 50MB) to scan for vulnerabilities.'
                : 'Choose an installed repository and branch to create a commit-bound audit.'}
            </p>
          </div>
          <div className="flex shrink-0 gap-2">
            <button
              type="button"
              onClick={toggleMode}
              className="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-700/40 bg-slate-900/45 text-slate-400 hover:text-cyan-300 hover:bg-slate-800 transition"
              title={scanMode === 'upload' ? 'Switch to GitHub repository scan' : 'Switch to manual ZIP upload'}
              aria-label={scanMode === 'upload' ? 'Switch to GitHub repository scan' : 'Switch to manual ZIP upload'}
            >
              {scanMode === 'upload' ? <GitBranch size={15} /> : <Upload size={15} />}
            </button>
            {onToggle && (
              <button onClick={onToggle} className="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-700/40 bg-slate-900/45 text-slate-500 hover:bg-slate-800 hover:text-slate-300 transition" title="Collapse panel">
                <ChevronLeft size={16} />
              </button>
            )}
          </div>
        </div>

        {scanMode === 'upload' ? (
          <form onSubmit={handleUploadSubmit} className="space-y-4">
            {!selectedFile ? (
              <div
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDrop={handleDrop}
                onClick={() => fileInputRef.current?.click()}
                className={`flex cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed p-6 text-center transition ${
                  isDragging
                    ? 'border-cyan-400 bg-cyan-950/20'
                    : 'border-slate-700/60 bg-slate-900/30 hover:border-slate-600 hover:bg-slate-900/50'
                }`}
              >
                <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-xl border border-cyan-500/20 bg-cyan-500/10 text-cyan-400">
                  <UploadCloud size={20} />
                </div>
                <p className="text-xs font-semibold text-slate-200">
                  Click to browse or drop ZIP archive
                </p>
                <p className="mt-1 text-[10px] text-slate-500">
                  Max archive size: 50MB
                </p>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".zip,application/zip,application/x-zip-compressed"
                  className="hidden"
                  onChange={e => handleFileSelect(e.target.files?.[0])}
                />
              </div>
            ) : (
              <div className="flex items-center justify-between rounded-xl border border-slate-700/60 bg-slate-900/45 p-3.5">
                <div className="flex items-center gap-3 min-w-0">
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-cyan-500/20 bg-cyan-500/10 text-cyan-400">
                    <FileArchive size={18} />
                  </div>
                  <div className="min-w-0">
                    <p className="truncate text-xs font-semibold text-slate-200" title={selectedFile.name}>
                      {selectedFile.name}
                    </p>
                    <p className="text-[10px] text-slate-500">
                      {formatFileSize(selectedFile.size)}
                    </p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={clearFile}
                  disabled={scan.isScanning}
                  className="ml-2 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-800 hover:text-slate-200 disabled:opacity-50"
                  title="Remove file"
                >
                  <X size={15} />
                </button>
              </div>
            )}

            {fileError && (
              <div className="rounded-xl border border-rose-500/20 bg-rose-500/10 p-3 text-[11px] text-rose-300">
                {fileError}
              </div>
            )}

            <label className="block text-[11px] font-semibold text-slate-400">
              <span className="mb-1.5 flex items-center gap-1.5"><Layers3 size={13} className="text-slate-500" /> Project name</span>
              <input
                type="text"
                value={uploadProjectName}
                onChange={e => setUploadProjectName(e.target.value)}
                placeholder="e.g. my-service"
                className="w-full rounded-xl border border-slate-700/60 bg-slate-900/60 px-3.5 py-2.5 text-xs text-slate-200 placeholder-slate-600 focus:border-cyan-400 focus:outline-none focus:ring-1 focus:ring-cyan-400"
              />
            </label>

            {scan.isScanning ? (
              <div className="flex gap-2">
                <div className="flex flex-1 items-center justify-center gap-2 rounded-xl bg-slate-800 px-4 py-2.5 text-sm font-bold text-slate-400">
                  <RefreshCw size={16} className="animate-spin" /> Scanning <span className="font-mono">{formatTime(scan.scanTime)}</span>
                </div>
                <button type="button" onClick={scan.cancel} className="flex shrink-0 items-center justify-center rounded-xl border border-rose-500/20 bg-rose-500/10 px-4 py-2.5 text-sm font-bold text-rose-300 transition hover:border-rose-500/30 hover:bg-rose-500/20">
                  Cancel
                </button>
              </div>
            ) : (
              <button
                type="submit"
                disabled={!selectedFile}
                className="primary-action flex w-full items-center justify-center gap-2 px-4 py-2.5 text-sm font-bold disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <Play size={16} fill="currentColor" /> Scan ZIP Archive <ChevronRight size={15} />
              </button>
            )}
          </form>
        ) : (
          <form onSubmit={event => { event.preventDefault(); onScan(); }} className="space-y-4">
            <label className="block text-[11px] font-semibold text-slate-400">
              <span className="mb-1.5 flex items-center justify-between"><span className="flex items-center gap-1.5"><GitFork size={13} className="text-slate-500" /> Repository</span>{selectedRepository?.permission && <span className="rounded border border-emerald-400/15 bg-emerald-400/8 px-1.5 py-0.5 text-[9px] uppercase tracking-wide text-emerald-300">{selectedRepository.permission}</span>}</span>
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
              <span><span className="block text-xs font-semibold text-slate-300">Force fresh rescan</span><span className="mt-1 block text-[10px] leading-4 text-slate-500">Bypass a valid commit cache and run a new scan.</span></span>
              <span className="relative inline-flex shrink-0">
                <input type="checkbox" checked={config.forceRescan} onChange={event => onConfigChange('forceRescan', event.target.checked)} className="peer sr-only" />
                <span className="h-6 w-11 rounded-full border border-slate-600 bg-slate-800 transition peer-checked:border-cyan-400/60 peer-checked:bg-cyan-400/20" />
                <span className="absolute left-1 top-1 h-4 w-4 rounded-full bg-slate-500 shadow transition peer-checked:translate-x-5 peer-checked:bg-[#06b6d4]" />
              </span>
            </label>

            {scan.isScanning ? (
              <div className="flex gap-2">
                <div className="flex flex-1 items-center justify-center gap-2 rounded-xl bg-slate-800 px-4 py-2.5 text-sm font-bold text-slate-400">
                  <RefreshCw size={16} className="animate-spin" /> Scanning <span className="font-mono">{formatTime(scan.scanTime)}</span>
                </div>
                <button type="button" onClick={scan.cancel} className="flex shrink-0 items-center justify-center rounded-xl border border-rose-500/20 bg-rose-500/10 px-4 py-2.5 text-sm font-bold text-rose-300 transition hover:border-rose-500/30 hover:bg-rose-500/20">
                  Cancel
                </button>
              </div>
            ) : (
              <button disabled={!config.repositoryId || !config.branch}
                className="primary-action flex w-full items-center justify-center gap-2 px-4 py-2.5 text-sm font-bold">
                <Play size={16} fill="currentColor" /> Run Scan <ChevronRight size={15} />
              </button>
            )}
          </form>
        )}

        {scanMode === 'github' && !loadingRepositories && repositories.length === 0 && <div className="mt-4 rounded-xl border border-amber-400/20 bg-amber-400/8 p-3.5 text-[11px] leading-5 text-amber-100">
          No installed repositories are available for this GitHub account.
          {installationUrl
            ? <a href={installationUrl} className="mt-2 flex items-center gap-1 font-semibold text-amber-200 underline decoration-amber-300/40 underline-offset-4">Install or configure the GitHub App <ChevronRight size={13} /></a>
            : <p className="mt-2 text-amber-200/65">Ask an administrator to install the AuditAgent GitHub App with repository access.</p>}
        </div>}

        {(scan.isScanning || scan.progress) && <section className="surface-card-muted mt-6 rounded-2xl p-4" aria-label="Scan progress">
          <div className="mb-3 flex items-center justify-between">
            <div><p className="text-xs font-semibold text-slate-200">Scan in progress</p><p className="mt-0.5 text-[10px] text-slate-500">{formatTime(scan.scanTime)} elapsed</p></div>
            <span className="font-mono text-sm font-bold text-cyan-300">{scan.progress?.progress ?? 0}%</span>
          </div>
          <div className="mb-4 h-1.5 overflow-hidden rounded-full bg-slate-800" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow={scan.progress?.progress ?? 0}>
            <div className="h-full rounded-full bg-linear-to-r from-cyan-500 to-cyan-300 transition-all duration-500" style={{ width: `${scan.progress?.progress ?? 0}%` }} />
          </div>
          <div className="space-y-0.5">{SCAN_STEPS.map((step, index) => {
            const completed = index < currentIndex;
            const active = index === currentIndex;
            return <div key={step.id} className={`relative flex items-center gap-2.5 rounded-lg px-2 py-2 text-[11px] ${active ? 'bg-cyan-400/7 text-slate-200' : completed ? 'text-slate-400' : 'text-slate-600'}`}>
              {completed ? <Check size={13} className="text-emerald-400" /> : active ? <CircleDot size={13} className="text-cyan-300" /> : <Terminal size={13} />}
              <span className={active ? 'font-semibold' : ''}>{step.name}</span>
            </div>;
          })}</div>
          {scan.progress?.message && <p className="mt-3 border-t border-slate-700/30 pt-3 text-[10px] leading-4 text-slate-500">{scan.progress.message}</p>}
        </section>}

        {scan.error && <div role="alert" className="mt-4 rounded-xl border border-rose-400/25 bg-rose-400/8 p-3.5 text-[11px] leading-5 text-rose-200">{scan.error}</div>}
      </div>
    </aside>
  );
}
