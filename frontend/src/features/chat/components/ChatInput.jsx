import { useRef, useEffect } from 'react';
import { Square, Send } from 'lucide-react';

export function ChatInput({ input, setInput, onSubmit, isStreaming, onCancel }) {
  const textareaRef = useRef(null);

  const handleInput = (e) => {
    setInput(e.target.value);
  };

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = `${textareaRef.current.scrollHeight}px`;
    }
  }, [input]);

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      onSubmit(e);
    }
  };

  return (
    <form 
      onSubmit={onSubmit}
      className="shrink-0 border-t border-slate-700/30 bg-[#081421] p-3"
    >
      <div className="flex items-end gap-2 rounded-xl border border-slate-600/45 bg-slate-950/35 p-1.5 transition focus-within:border-cyan-400/55 focus-within:ring-2 focus-within:ring-cyan-400/8">
        <textarea
          ref={textareaRef}
          rows={1}
          value={input}
          onChange={handleInput}
          onKeyDown={handleKeyDown}
          aria-label="Chat message"
          placeholder="Ask about findings or type scan…"
          className="min-h-9 max-h-32 min-w-0 flex-1 resize-none bg-transparent px-2.5 py-2 text-xs text-white outline-none placeholder:text-slate-600"
        />
        {isStreaming ? (
          <button
            type="button"
            onClick={onCancel}
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-rose-500 text-white transition hover:bg-rose-400"
          >
            <Square size={14} />
          </button>
        ) : (
          <button
            type="submit"
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-cyan-300 text-[#04212a] transition hover:bg-cyan-200"
          >
            <Send size={15} />
          </button>
        )}
      </div>
      <p className="mt-2 text-center text-[9px] text-slate-600">
        Enter to send · Shift+Enter for newline
      </p>
    </form>
  );
}
