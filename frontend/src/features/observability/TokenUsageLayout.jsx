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
    <div className="flex h-full w-full flex-col bg-[#071321] absolute inset-0 z-40">
      <div className="flex h-14 shrink-0 items-center gap-3 border-b border-slate-700/50 bg-slate-900/50 px-4">
        <button
          onClick={onClose}
          className={`flex items-center gap-2 rounded-lg px-3 py-1.5 text-sm font-medium transition ${theme === 'light' ? 'text-slate-200 hover:bg-slate-200 hover:text-slate-900' : 'text-slate-300 hover:bg-slate-800 hover:text-white'}`}
        >
          <ArrowLeft size={16} />
          Back to Workspace
        </button>
        <span className="text-slate-500">|</span>
        <h2 className="flex items-center gap-2 text-sm font-semibold text-cyan-400">
          <Activity size={16} />
          Token Usage
        </h2>
      </div>
      
      <div className="flex-1 min-w-0 overflow-auto bg-[#071321]">
        <div className="mx-auto max-w-5xl p-6 lg:p-10">
          <div className="surface-card rounded-2xl p-6 shadow-xl">
            <div className="mb-6 border-b border-slate-700/40 pb-5">
              <h1 className="text-xl font-semibold tracking-tight text-white">Model Usage</h1>
              <p className="mt-1 text-sm text-slate-400">Track token consumption across repositories.</p>
            </div>
            
            {loading ? (
              <div className="py-12 text-center text-slate-400">Loading token usage...</div>
            ) : error ? (
              <div className="py-12 text-center text-rose-400">Error: {error}</div>
            ) : data.length === 0 ? (
              <div className="py-12 text-center text-slate-400">No token usage data found.</div>
            ) : (
              <div className="overflow-x-auto rounded-xl border border-slate-700/40">
                <table className="w-full text-left text-sm text-slate-300">
                  <thead className="bg-slate-800/50 text-xs uppercase text-slate-400 border-b border-slate-700/50">
                    <tr>
                      <th className="px-5 py-4 font-semibold">Repository</th>
                      <th className="px-5 py-4 font-semibold">Model</th>
                      <th className="px-5 py-4 font-semibold text-right">Calls</th>
                      <th className="px-5 py-4 font-semibold text-right">Tokens</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-700/50">
                    {data.map((row, idx) => (
                      <tr key={idx} className="transition-colors hover:bg-slate-800/30">
                        <td className="px-5 py-4 max-w-62.5 truncate" title={row.repoPath}>{row.repoPath || 'Unknown'}</td>
                        <td className="px-5 py-4">{row.modelName}</td>
                        <td className="px-5 py-4 text-right tabular-nums font-medium">{row.callCount}</td>
                        <td className="px-5 py-4 text-right tabular-nums font-semibold text-cyan-300">
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
