import { memo } from 'react';
import { AlertTriangle, Check } from 'lucide-react';
import { timelineSteps } from '../constants/remediationConstants';

/**
 * RemediationTimeline displays the multi-stage progress of a finding from detection to merge.
 * Adheres to Single Responsibility Principle (SRP).
 */
export const RemediationTimeline = memo(function RemediationTimeline({ currentStatus }) {
  if (currentStatus === 'IGNORED') return null;

  let activeIndex = 0;
  if (timelineSteps[4].states.includes(currentStatus)) activeIndex = 4;
  else if (timelineSteps[3].states.includes(currentStatus)) activeIndex = 3;
  else if (timelineSteps[2].states.includes(currentStatus)) activeIndex = 2;
  else if (timelineSteps[1].states.includes(currentStatus) || currentStatus === 'PATCH_FAILED') activeIndex = 1;

  return (
    <section className="rounded-2xl border border-slate-700/35 bg-slate-900/25 p-4 sm:p-5" aria-label="Remediation timeline">
      <h3 className="mb-4 text-[10px] font-bold uppercase tracking-[0.14em] text-slate-500">Remediation Progress</h3>
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between relative">
        <div className="hidden sm:block absolute top-2.5 left-2.5 right-2.5 h-px bg-slate-700/50 z-0" />
        {timelineSteps.map((step, index) => {
          const isComplete = index < activeIndex || (index === 4 && activeIndex === 4);
          const isActive = index === activeIndex && !isComplete;
          const isFailed = (currentStatus === 'PATCH_FAILED' && index === 1) || (currentStatus === 'PUBLISH_FAILED' && index === 3);

          let icon = <div className="h-1.5 w-1.5 rounded-full bg-slate-600" />;
          if (isComplete) icon = <Check size={10} className="text-emerald-400" />;
          else if (isFailed) icon = <AlertTriangle size={10} className="text-rose-400" />;
          else if (isActive && currentStatus !== 'DETECTED') icon = <div className="h-1.5 w-1.5 rounded-full bg-cyan-400 animate-pulse" />;
          else if (isActive && currentStatus === 'DETECTED') icon = <div className="h-1.5 w-1.5 rounded-full bg-cyan-400" />;

          return (
            <div key={step.label} className="relative z-10 flex sm:flex-col items-center sm:items-center gap-3 sm:gap-2 pb-5 sm:pb-0 last:pb-0">
              <div className="sm:hidden absolute left-2.5 top-5 h-full w-px -translate-x-1/2 bg-slate-700/50 -z-10" />
              <div className={`relative flex h-5 w-5 shrink-0 items-center justify-center rounded-full mt-0.5 sm:mt-0 ${isComplete ? 'bg-emerald-400/10 ring-4 ring-slate-900/80' : isFailed ? 'bg-rose-400/10 ring-4 ring-slate-900/80' : isActive ? 'bg-cyan-400/10 ring-4 ring-slate-900/80' : 'bg-slate-800/50 ring-4 ring-slate-900/80'}`}>
                {icon}
              </div>
              <div className="flex flex-col sm:items-center sm:text-center mt-0.5 sm:mt-1">
                <span className={`text-[11px] font-semibold ${isComplete ? 'text-emerald-300' : isFailed ? 'text-rose-300' : isActive ? 'text-white' : 'text-slate-500'}`}>
                  {step.label}
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
});
