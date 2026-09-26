import { ArrowDown } from 'lucide-react';

export function NewMessagesPill({ onClick }) {
  return (
    <button
      onClick={onClick}
      className="animate-pill-bounce absolute bottom-2 inset-x-0 mx-auto w-fit z-10 flex items-center gap-1.5 rounded-full border border-cyan-400/30 bg-[#091624]/95 px-3 py-1.5 text-[10px] font-medium text-cyan-200 shadow-lg backdrop-blur-sm transition hover:bg-cyan-400/15 hover:text-cyan-100"
      aria-label="Scroll to new messages"
    >
      <ArrowDown size={12} /> New messages
    </button>
  );
}

