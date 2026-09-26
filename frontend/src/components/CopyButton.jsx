import { Check, Copy } from 'lucide-react';
import { useState } from 'react';

export function CopyButton({ text, className = '' }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (error) {
      console.error('Failed to copy text', error);
    }
  };

  return (
    <button
      onClick={handleCopy}
      title="Copy to clipboard"
      className={`flex items-center justify-center rounded-lg border border-slate-600/50 bg-slate-800/80 p-1.5 text-slate-400 backdrop-blur-sm transition hover:bg-slate-700 hover:text-slate-200 ${className}`}
    >
      {copied ? <Check size={14} className="text-emerald-400" /> : <Copy size={14} />}
    </button>
  );
}
