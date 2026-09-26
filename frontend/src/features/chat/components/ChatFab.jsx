import { MessageSquare, X } from 'lucide-react';

export function ChatFab({ isOpen, onToggle, unreadCount }) {
  return (
    <button onClick={onToggle} aria-label="Toggle security chat" className={`pointer-events-auto relative mr-4 mb-4 flex h-14 items-center justify-center gap-2 rounded-2xl bg-gradient-to-br from-cyan-300 to-cyan-400 px-4 font-semibold text-[#05212a] shadow-xl shadow-cyan-950/35 transition hover:-translate-y-0.5 hover:brightness-105 sm:mr-0 sm:mb-0 ${isOpen ? 'w-14 px-0' : 'w-auto'}`}>
      {isOpen ? <X size={22} /> : <MessageSquare size={20} />}
      {!isOpen && unreadCount > 0 && (
        <span className="absolute -right-1.5 -top-1.5 flex h-5 min-w-5 items-center justify-center rounded-full border-2 border-[#071321] bg-rose-500 px-1 text-[9px] font-bold text-white">{unreadCount}</span>
      )}
    </button>
  );
}

