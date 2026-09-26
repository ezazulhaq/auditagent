import { useState } from 'react';
import { ArrowLeft } from 'lucide-react';
import { DocsSidebar } from './DocsSidebar';
import { DocsMainPanel } from './DocsMainPanel';

export function DocsView({ api, theme, onClose }) {
  const [selectedDoc, setSelectedDoc] = useState(null);
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);

  return (
    <div className="flex h-full w-full flex-col bg-[#071321] absolute inset-0 z-40">
      <div className="flex h-14 items-center gap-3 border-b border-slate-700/50 bg-slate-900/50 px-4">
        <button
          onClick={onClose}
          className={`flex items-center gap-2 rounded-lg px-3 py-1.5 text-sm font-medium transition ${theme === 'light' ? 'text-slate-200 hover:bg-slate-200 hover:text-slate-900' : 'text-slate-300 hover:bg-slate-800 hover:text-white'}`}
        >
          <ArrowLeft size={16} />
          Back to Workspace
        </button>
        <span className="text-slate-500">|</span>
        <h2 className="text-sm font-semibold text-cyan-400">Documentation</h2>
      </div>
      <div className="flex flex-1 min-h-0">
        <DocsSidebar 
          api={api} 
          theme={theme}
          isOpen={isSidebarOpen} 
          onToggle={() => setIsSidebarOpen(!isSidebarOpen)} 
          selectedDoc={selectedDoc} 
          onSelectDoc={setSelectedDoc} 
        />
        <main className="flex-1 min-w-0 overflow-auto bg-[#071321]">
          <DocsMainPanel api={api} selectedDoc={selectedDoc} theme={theme} />
        </main>
      </div>
    </div>
  );
}
