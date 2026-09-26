import { RotateCcw } from 'lucide-react';

export function RecoveryBanner({ activeRun, onResume, onDiscard, onRetryPublish }) {
  if (!activeRun || !['INTERRUPTED', 'AWAITING_APPROVAL', 'CONFLICTED', 'PUBLISH_FAILED'].includes(activeRun.status)) {
    return null;
  }

  return (
    <div className="border-b border-amber-300/18 bg-amber-300/[0.07] p-3.5 text-xs text-amber-100">
      <div className="flex items-center gap-2 font-semibold">
        <RotateCcw size={13} /> Recovered {activeRun.status.toLowerCase().replaceAll('_', ' ')} run
      </div>
      <div className="mt-1 truncate font-mono text-[9px] text-amber-100/45">{activeRun.runId}</div>
      <div className="mt-3 flex gap-2">
        {activeRun.status === 'INTERRUPTED' && (
          <button onClick={onResume} className="rounded-lg bg-amber-300 px-3 py-1.5 text-[10px] font-bold text-[#2b2207]">Resume</button>
        )}
        {activeRun.status === 'PUBLISH_FAILED' && (
          <button onClick={onRetryPublish} className="rounded-lg bg-amber-300 px-3 py-1.5 text-[10px] font-bold text-[#2b2207]">Retry publishing</button>
        )}
        {activeRun.status !== 'PUBLISH_FAILED' && (
          <button onClick={onDiscard} className="rounded-lg border border-amber-200/25 px-3 py-1.5 text-[10px] font-semibold">Discard</button>
        )}
      </div>
    </div>
  );
}

