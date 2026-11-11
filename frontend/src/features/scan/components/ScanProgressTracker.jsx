import { memo } from 'react';
import { Check, CircleDot, Terminal } from 'lucide-react';
import { formatTime, SCAN_STEPS } from '../../../utils/auditSelectors';

/**
 * ScanProgressTracker displays real-time scan progress, elapsed time, and step checklist.
 * Adheres to Single Responsibility Principle (SRP).
 */
export const ScanProgressTracker = memo(function ScanProgressTracker({
  scan,
  scanTime,
}) {
  if (!scan.isScanning && !scan.progress) {
    return null;
  }

  const currentIndex = SCAN_STEPS.findIndex(step => step.id === scan.progress?.step);

  return (
    <section className="surface-card-muted mt-6 rounded-2xl p-4" aria-label="Scan progress">
      <div className="mb-3 flex items-center justify-between">
        <div>
          <p className="text-xs font-semibold text-slate-200">Scan in progress</p>
          <p className="mt-0.5 text-[10px] text-slate-500">{formatTime(scanTime)} elapsed</p>
        </div>
        <span className="font-mono text-sm font-bold text-cyan-300">
          {scan.progress?.progress ?? 0}%
        </span>
      </div>

      <div
        className="mb-4 h-1.5 overflow-hidden rounded-full bg-slate-800"
        role="progressbar"
        aria-valuemin="0"
        aria-valuemax="100"
        aria-valuenow={scan.progress?.progress ?? 0}
      >
        <div
          className="h-full rounded-full bg-linear-to-r from-cyan-500 to-cyan-300 transition-all duration-500"
          style={{ width: `${scan.progress?.progress ?? 0}%` }}
        />
      </div>

      <div className="space-y-0.5">
        {SCAN_STEPS.map((step, index) => {
          const completed = index < currentIndex;
          const active = index === currentIndex;
          return (
            <div
              key={step.id}
              className={`relative flex items-center gap-2.5 rounded-lg px-2 py-2 text-[11px] ${
                active ? 'bg-cyan-400/7 text-slate-200' : completed ? 'text-slate-400' : 'text-slate-600'
              }`}
            >
              {completed ? (
                <Check size={13} className="text-emerald-400" />
              ) : active ? (
                <CircleDot size={13} className="text-cyan-300" />
              ) : (
                <Terminal size={13} />
              )}
              <span className={active ? 'font-semibold' : ''}>{step.name}</span>
            </div>
          );
        })}
      </div>

      {scan.progress?.message && (
        <p className="mt-3 border-t border-slate-700/30 pt-3 text-[10px] leading-4 text-slate-500">
          {scan.progress.message}
        </p>
      )}
    </section>
  );
});
