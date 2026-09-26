import React from 'react';
import { CheckCircle, Circle, Play, AlertCircle } from 'lucide-react';

export function GraphTimeline({ steps = [] }) {
  if (!steps || steps.length === 0) return null;

  return (
    <div className="flex flex-col space-y-2 p-4 bg-slate-800/50 rounded-lg border border-slate-700/50">
      <h3 className="text-sm font-semibold text-slate-200 mb-2">Agent Timeline</h3>
      <div className="relative">
        {/* Timeline connecting line */}
        <div className="absolute left-3 top-3 bottom-3 w-0.5 bg-slate-700" />
        
        <ul className="space-y-4">
          {steps.map((step, index) => {
            const isComplete = step.status === 'completed';
            const isActive = step.status === 'started' || step.status === 'running';
            const isError = step.status === 'error';

            return (
              <li key={`${step.node}-${index}`} className="relative pl-8">
                {/* Icon */}
                <div className="absolute left-0 top-0 bg-slate-900 rounded-full p-1 border-2 border-transparent">
                  {isComplete ? (
                    <CheckCircle className="w-4 h-4 text-emerald-400" />
                  ) : isActive ? (
                    <Play className="w-4 h-4 text-cyan-400 fill-cyan-400 animate-pulse" />
                  ) : isError ? (
                    <AlertCircle className="w-4 h-4 text-rose-400" />
                  ) : (
                    <Circle className="w-4 h-4 text-slate-500" />
                  )}
                </div>

                {/* Content */}
                <div className="flex flex-col">
                  <span className={`text-sm font-medium ${isActive ? 'text-cyan-400' : 'text-slate-300'}`}>
                    {step.node}
                  </span>
                  {step.duration_ms && (
                    <span className="text-xs text-slate-400">
                      {step.duration_ms}ms
                    </span>
                  )}
                  {step.iteration !== undefined && (
                    <span className="text-xs text-slate-400">
                      Iteration {step.iteration}
                    </span>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      </div>
    </div>
  );
}
