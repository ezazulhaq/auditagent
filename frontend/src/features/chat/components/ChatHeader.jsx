import { Bot, LockKeyhole, RotateCcw, Trash2, X } from 'lucide-react';

export function ChatHeader({ onForgetRepository, onForgetConversation, onClose }) {
  return (
    <header className="flex shrink-0 items-center justify-between border-b border-slate-700/30 bg-[#0b1a2b]/95 p-4">
      <div className="flex items-center gap-3">
        <span className="relative flex h-9 w-9 items-center justify-center rounded-xl border border-cyan-400/20 bg-cyan-400/10 text-cyan-300">
          <Bot size={18} />
          <span className="absolute -right-0.5 -top-0.5 h-2 w-2 rounded-full border-2 border-[#0b1a2b] bg-emerald-400" />
        </span>
        <div>
          <h2 className="text-sm font-semibold text-white">Security Agent</h2>
          <p className="mt-0.5 flex items-center gap-1 text-[9px] font-medium text-emerald-400">
            <LockKeyhole size={9} /> Durable memory active
          </p>
        </div>
      </div>
      <div className="flex items-center gap-0.5">
        <button onClick={onForgetRepository} title="Forget repository remediation memory" aria-label="Forget remediation memory" className="rounded-lg p-2 text-slate-500 transition hover:bg-slate-800 hover:text-white">
          <RotateCcw size={15} />
        </button>
        <button onClick={onForgetConversation} title="Forget conversation" aria-label="Forget conversation" className="rounded-lg p-2 text-slate-500 transition hover:bg-slate-800 hover:text-rose-300">
          <Trash2 size={15} />
        </button>
        <button onClick={onClose} aria-label="Close chat" className="rounded-lg p-2 text-slate-500 transition hover:bg-slate-800 hover:text-white">
          <X size={17} />
        </button>
      </div>
    </header>
  );
}

