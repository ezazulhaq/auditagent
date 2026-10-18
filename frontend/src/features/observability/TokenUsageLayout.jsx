import { useEffect, useState } from 'react';
import { ArrowLeft, Activity } from 'lucide-react';

export function TokenUsageLayout({ api, theme, onClose }) {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let active = true;
    const controller = new AbortController();

    async function fetchTokens() {
      try {
        const json = await api.observabilityTokens(controller.signal);
        if (active) setData(json);
      } catch (err) {
        if (err.name !== 'AbortError' && active) {
          setError(err.message);
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    fetchTokens();
    return () => {
      active = false;
      controller.abort();
    };
  }, [api]);

  return (
    <div className="flex h-full w-full flex-col bg-[var(--surface-canvas)] absolute inset-0 z-40">
      <div className="flex h-14 shrink-0 items-center gap-3 border-b border-[var(--surface-panel-border)] bg-[var(--surface-panel)]/90 px-4 backdrop-blur">
        <button
          onClick={onClose}
          className={`flex items-center gap-2 rounded-lg px-3 py-1.5 text-sm font-medium transition ${theme === 'light' ? 'text-slate-700 hover:bg-slate-200 hover:text-slate-900' : 'text-slate-300 hover:bg-slate-800 hover:text-white'}`}
        >
          <ArrowLeft size={16} />
          Back to Workspace
        </button>
        <span className="text-slate-600">|</span>
        <h2 className="flex items-center gap-2 text-sm font-semibold text-cyan-400 font-sans">
          <Activity size={16} />
          Token Usage
        </h2>
      </div>
      
      <div className="flex-1 min-w-0 overflow-auto bg-[var(--surface-canvas)]">
        <div className="mx-auto max-w-5xl p-6 lg:p-10">
          <div className="surface-card rounded-2xl p-6 shadow-xl">
            <div className="mb-6 border-b border-[var(--border-subtle)] pb-5">
              <h1 className="text-xl font-bold tracking-tight text-white font-sans">Model Usage</h1>
              <p className="mt-1 text-sm text-slate-400">Track token consumption across repositories.</p>
            </div>
            
            {loading ? (
              <div className="py-12 text-center text-slate-400 font-sans">Loading token usage...</div>
            ) : error ? (
              <div className="py-12 text-center text-rose-400 font-sans">Error: {error}</div>
            ) : data.length === 0 ? (
              <div className="py-12 text-center text-slate-400 font-sans">No token usage data found.</div>
            ) : (
              <div className="overflow-x-auto rounded-xl border border-[var(--border-subtle)]">
                <table className="w-full text-left text-sm text-slate-300">
                  <thead className="bg-[var(--surface-inset)]/60 text-xs uppercase text-slate-400 border-b border-[var(--border-subtle)]">
                    <tr>
                      <th className="px-5 py-4 font-semibold font-sans">Repository</th>
                      <th className="px-5 py-4 font-semibold font-sans">Model</th>
                      <th className="px-5 py-4 font-semibold font-sans text-right">Calls</th>
                      <th className="px-5 py-4 font-semibold font-sans text-right">Tokens</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[var(--border-subtle)]">
                    {data.map((row, idx) => (
                      <tr key={idx} className="transition-colors hover:bg-slate-800/30">
                        <td className="px-5 py-4 max-w-62.5 truncate font-mono text-xs" title={row.repoPath}>{row.repoPath || 'Unknown'}</td>
                        <td className="px-5 py-4 font-mono text-xs text-slate-300">{row.modelName}</td>
                        <td className="px-5 py-4 text-right tabular-nums font-mono text-xs font-medium text-slate-200">{row.callCount}</td>
                        <td className="px-5 py-4 text-right tabular-nums font-mono text-xs font-semibold text-cyan-300">
                          {row.totalTokens.toLocaleString()}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
