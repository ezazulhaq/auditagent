import { memo } from 'react';
import { Globe, Sparkles } from 'lucide-react';
import { PROVIDERS } from '../constants/llmProviders';

/**
 * ProviderSelector renders the grid of supported LLM providers.
 * Adheres to Single Responsibility Principle (SRP).
 */
export const ProviderSelector = memo(function ProviderSelector({
  provider,
  onProviderChange,
}) {
  return (
    <div>
      <label className="block text-xs font-semibold text-slate-300 mb-2">
        LLM Provider
      </label>
      <div className="grid grid-cols-2 md:grid-cols-3 gap-2.5">
        {PROVIDERS.map((p) => {
          const isSelected = provider === p.id;
          return (
            <button
              key={p.id}
              type="button"
              onClick={() => onProviderChange(p.id)}
              className={`flex flex-col items-start p-2.5 rounded-xl border text-left transition ${
                isSelected
                  ? 'border-cyan-500/60 bg-cyan-500/10 ring-1 ring-cyan-500/30 text-white'
                  : 'border-slate-800 bg-slate-900/60 text-slate-400 hover:border-slate-700 hover:text-slate-200'
              }`}
            >
              <div className="flex items-center gap-1.5 font-semibold text-xs text-cyan-400">
                {p.id === 'OPENROUTER' ? <Sparkles size={13} /> : <Globe size={13} />}
                <span className="truncate">{p.label}</span>
              </div>
              <p className="text-[10px] text-slate-400 mt-1 line-clamp-2 leading-relaxed">
                {p.subtitle}
              </p>
            </button>
          );
        })}
      </div>
    </div>
  );
});
