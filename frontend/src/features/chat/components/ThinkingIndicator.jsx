export function ThinkingIndicator() {
  return (
    <div className="animate-message-enter flex justify-start">
      <div className="flex items-center gap-1.5 rounded-2xl rounded-bl-md border border-slate-700/25 bg-slate-800/75 px-4 py-3 shadow-sm">
        <span className="typing-dot h-2 w-2 rounded-full bg-cyan-300/70" style={{ animationDelay: '0ms' }} />
        <span className="typing-dot h-2 w-2 rounded-full bg-cyan-300/70" style={{ animationDelay: '160ms' }} />
        <span className="typing-dot h-2 w-2 rounded-full bg-cyan-300/70" style={{ animationDelay: '320ms' }} />
      </div>
    </div>
  );
}

