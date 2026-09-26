import { useState, useEffect } from 'react';
import { Check, Copy } from 'lucide-react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';

const LINE_THRESHOLD = 12;

export function CodeBlock({ language, inline, children }) {
  const [copied, setCopied] = useState(false);
  const [expanded, setExpanded] = useState(false);

  useEffect(() => {
    if (copied) {
      const timeout = setTimeout(() => setCopied(false), 2000);
      return () => clearTimeout(timeout);
    }
  }, [copied]);

  if (inline) {
    return (
      <code className="rounded bg-black/20 px-1 py-0.5 text-[11px]">
        {children}
      </code>
    );
  }

  const codeString = String(children).replace(/\n$/, '');
  const lines = codeString.split('\n').length;
  const isCollapsible = lines > LINE_THRESHOLD;

  const handleCopy = () => {
    navigator.clipboard.writeText(codeString);
    setCopied(true);
  };

  return (
    <div className="my-2 flex flex-col">
      <div className="group relative overflow-hidden rounded-lg">
        {language && (
          <div className="absolute left-0 top-0 z-10 rounded-br-lg bg-slate-700/60 px-2 py-0.5 text-[9px] font-medium text-slate-400">
            {language}
          </div>
        )}
        
        <button
          onClick={handleCopy}
          className="absolute right-1 top-1 z-10 rounded-md p-1.5 text-slate-400 opacity-0 transition hover:bg-slate-700 hover:text-white group-hover:opacity-100"
          aria-label="Copy code"
        >
          {copied ? <Check size={14} /> : <Copy size={14} />}
        </button>

        <div className={isCollapsible && !expanded ? 'max-h-72 overflow-hidden relative' : 'relative'}>
          <SyntaxHighlighter
            language={language || 'text'}
            style={vscDarkPlus}
            customStyle={{
              margin: 0,
              padding: '0.75rem 1rem',
              fontSize: '0.72rem',
              lineHeight: '1.45',
              borderRadius: 0,
              background: '#1e1e1e',
            }}
            wrapLongLines={true}
          >
            {codeString}
          </SyntaxHighlighter>
          
          {isCollapsible && !expanded && (
            <div className="pointer-events-none absolute inset-x-0 bottom-0 h-10 bg-gradient-to-t from-[#1e1e1e] to-transparent" />
          )}
        </div>
      </div>
      
      {isCollapsible && (
        <button
          onClick={() => setExpanded(!expanded)}
          className="mt-1 self-start text-[10px] font-medium text-cyan-400 hover:text-cyan-300"
        >
          {expanded ? 'Show less ▴' : 'Show more ▾'}
        </button>
      )}
    </div>
  );
}
