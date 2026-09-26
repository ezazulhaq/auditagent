import { useEffect, useState } from 'react';
import { Book, ChevronRight, ChevronLeft, FileText } from 'lucide-react';

export function DocsSidebar({ api, theme, isOpen, onToggle, selectedDoc, onSelectDoc }) {
  const [docs, setDocs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const controller = new AbortController();
    api.getDocs(controller.signal)
      .then(fetchedDocs => {
        setDocs(fetchedDocs);
        if (fetchedDocs.length > 0 && !selectedDoc) {
          onSelectDoc(fetchedDocs[0]);
        }
      })
      .catch(err => {
        if (err.name !== 'AbortError') setError(err.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [api, selectedDoc, onSelectDoc]);

  if (!isOpen) {
    return (
      <aside className="shrink-0 border-b border-slate-700/25 bg-[#081522]/82 h-full lg:w-14 lg:border-b-0 lg:border-r flex flex-col items-center py-5">
        <button onClick={onToggle} className={`flex h-8 w-8 items-center justify-center rounded-lg border border-slate-700/40 bg-slate-900/45 transition ${theme === 'light' ? 'text-slate-400 hover:bg-slate-200 hover:text-slate-200' : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800'}`} title="Expand panel">
          <ChevronRight size={16} />
        </button>
      </aside>
    );
  }

  return (
    <aside className="shrink-0 border-b border-slate-700/25 bg-[#081522]/82 h-full lg:w-88 lg:border-b-0 lg:border-r flex flex-col overflow-hidden">
      <div className="p-5 sm:p-6 lg:p-5 xl:p-6 pb-2">
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="eyebrow">Documentation</p>
            <h2 className="mt-2 flex items-center gap-2 text-base font-semibold text-white"><Book size={18} className="text-cyan-300" /> Guides & References</h2>
          </div>
          {onToggle && (
            <button onClick={onToggle} className={`flex h-8 w-8 items-center justify-center rounded-lg border border-slate-700/40 bg-slate-900/45 transition ${theme === 'light' ? 'text-slate-400 hover:bg-slate-200 hover:text-slate-200' : 'text-slate-500 hover:bg-slate-800 hover:text-slate-300'}`} title="Collapse panel">
              <ChevronLeft size={16} />
            </button>
          )}
        </div>
      </div>
      <div className="flex-1 overflow-y-auto p-5 sm:p-6 lg:p-5 xl:p-6 pt-0 space-y-2 mt-4">
        {loading && <p className="text-xs text-slate-500">Loading docs...</p>}
        {error && <p className="text-xs text-rose-400">Failed to load docs: {error}</p>}
        {!loading && !error && docs.length === 0 && <p className="text-xs text-slate-500">No documentation found.</p>}
        {docs.map(doc => {
          const formatDocName = (filename) => {
            let name = filename.replace(/\.md$/, '');
            name = name.replace(/^\d+-/, '');
            name = name.replaceAll('-', ' ');
            return name.split(' ').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ');
          };
          
          const isSelected = selectedDoc === doc;
          
          return (
          <button
            key={doc}
            onClick={() => onSelectDoc(doc)}
            className={`w-full flex items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors text-left border ${
              isSelected 
                ? theme === 'light' ? 'bg-cyan-50 text-cyan-700 font-semibold border-cyan-200' : 'bg-cyan-400/10 text-cyan-200 font-semibold border-cyan-400/20' 
                : theme === 'light' ? 'text-slate-300 hover:bg-slate-200 hover:text-slate-200 border-transparent' : 'text-slate-400 hover:bg-slate-800 hover:text-slate-200 border-transparent'
            }`}
          >
            <FileText size={14} className={isSelected ? (theme === 'light' ? 'text-cyan-600' : 'text-cyan-300') : (theme === 'light' ? 'text-slate-400' : 'text-slate-500')} />
            <span className="truncate">{formatDocName(doc)}</span>
          </button>
        )})}
      </div>
    </aside>
  );
}

