import { Check, Copy } from 'lucide-react';
import { useState } from 'react';

const SIZE_VARIANTS = {
  xs: {
    btn: 'rounded p-1 border border-slate-700/50 bg-slate-800/40 text-slate-400 hover:border-slate-500 hover:bg-slate-700/60 hover:text-slate-200',
    iconSize: 11,
  },
  sm: {
    btn: 'rounded-md p-1 border border-slate-600/50 bg-slate-800/60 text-slate-400 hover:bg-slate-700 hover:text-slate-200',
    iconSize: 12,
  },
  md: {
    btn: 'rounded-lg border border-slate-600/50 bg-slate-800/80 p-1.5 text-slate-400 backdrop-blur-sm hover:bg-slate-700 hover:text-slate-200',
    iconSize: 14,
  },
};

export function CopyButton({
  text,
  className = '',
  title = 'Copy to clipboard',
  ariaLabel,
  iconSize,
  size = 'md',
}) {
  const [copied, setCopied] = useState(false);
  const variant = SIZE_VARIANTS[size] || SIZE_VARIANTS.md;
  const resolvedIconSize = iconSize ?? variant.iconSize;

  const handleCopy = async (e) => {
    e?.stopPropagation?.();
    try {
      await navigator?.clipboard?.writeText?.(text ?? '');
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (error) {
      console.error('Failed to copy text', error);
    }
  };

  const resolvedTitle = copied ? 'Copied!' : title;
  const resolvedAriaLabel = copied ? 'Copied to clipboard' : (ariaLabel || title);

  return (
    <button
      type="button"
      onClick={handleCopy}
      title={resolvedTitle}
      aria-label={resolvedAriaLabel}
      className={`flex items-center justify-center transition ${variant.btn} ${className}`}
    >
      {copied ? <Check size={resolvedIconSize} className="text-emerald-400" /> : <Copy size={resolvedIconSize} />}
    </button>
  );
}
