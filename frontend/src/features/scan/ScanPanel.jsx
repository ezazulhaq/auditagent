import { ChevronLeft, ChevronRight, GitBranch, Layers3, Upload } from 'lucide-react';
import { memo, useState } from 'react';
import { useScanElapsedSeconds } from './useScanElapsedSeconds';
import { UploadDropzone } from './components/UploadDropzone';
import { RepoBranchSelector } from './components/RepoBranchSelector';
import { ScanProgressTracker } from './components/ScanProgressTracker';

/**
 * ScanPanel coordinates audit scope selection, file upload dropzone, and progress tracking.
 * Adheres to Interface Segregation Principle (ISP) and Single Responsibility Principle (SRP).
 */
export const ScanPanel = memo(function ScanPanel({
  config,
  onConfigChange,
  onScan,
  onUploadScan,
  scan,
  repositories = [],
  branches = [],
  loadingRepositories,
  installationUrl,
  isOpen = true,
  onToggle,
  scanMode: externalScanMode,
  onScanModeChange,
}) {
  const internalElapsed = useScanElapsedSeconds(scan?.startedAt, scan?.finishedAt);
  const scanTime = scan?.scanTime !== undefined ? scan.scanTime : internalElapsed;
  const [internalScanMode, setInternalScanMode] = useState('github');
  const scanMode = externalScanMode !== undefined ? externalScanMode : internalScanMode;

  const toggleMode = () => {
    const next = scanMode === 'github' ? 'upload' : 'github';
    if (onScanModeChange) {
      onScanModeChange(next);
    } else {
      setInternalScanMode(next);
    }
  };

  if (!isOpen) {
    return (
      <aside className="hidden lg:flex shrink-0 border-b border-[var(--surface-panel-border)] bg-[var(--surface-panel)]/90 lg:h-[calc(100vh-72px)] lg:w-14 lg:border-b-0 lg:border-r flex-col items-center py-5">
        <button
          onClick={onToggle}
          className="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-700/40 bg-slate-900/45 text-slate-400 hover:text-slate-200 hover:bg-slate-800 transition"
          title="Expand panel"
        >
          <ChevronRight size={16} />
        </button>
      </aside>
    );
  }

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
              <button
                onClick={onToggle}
                className="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-700/40 bg-slate-900/45 text-slate-500 hover:bg-slate-800 hover:text-slate-300 transition"
                title="Collapse panel"
              >
                <ChevronLeft size={16} />
              </button>
            )}
          </div>
        </div>

        {scanMode === 'upload' ? (
          <UploadDropzone
            onUploadScan={onUploadScan}
            scan={scan}
            scanTime={scanTime}
          />
        ) : (
          <RepoBranchSelector
            config={config}
            onConfigChange={onConfigChange}
            onScan={onScan}
            scan={scan}
            scanTime={scanTime}
            repositories={repositories}
            branches={branches}
            loadingRepositories={loadingRepositories}
          />
        )}

        {scanMode === 'github' && !loadingRepositories && repositories.length === 0 && (
          <div className="mt-4 rounded-xl border border-amber-400/20 bg-amber-400/8 p-3.5 text-[11px] leading-5 text-amber-100">
            No installed repositories are available for this GitHub account.
            {installationUrl ? (
              <a
                href={installationUrl}
                className="mt-2 flex items-center gap-1 font-semibold text-amber-200 underline decoration-amber-300/40 underline-offset-4"
              >
                Install or configure the GitHub App <ChevronRight size={13} />
              </a>
            ) : (
              <p className="mt-2 text-amber-200/65">
                Ask an administrator to install the AuditAgent GitHub App with repository access.
              </p>
            )}
          </div>
        )}

        <ScanProgressTracker
          scan={scan}
          scanTime={scanTime}
        />

        {scan.error && (
          <div role="alert" className="mt-4 rounded-xl border border-rose-400/25 bg-rose-400/8 p-3.5 text-[11px] leading-5 text-rose-200">
            {scan.error}
          </div>
        )}
      </div>
    </aside>
  );
});
