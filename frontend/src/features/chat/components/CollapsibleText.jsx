import { useState } from 'react';

const CHAR_THRESHOLD = 300;

/**
 * Wraps long prose text with a "Read more" / "Read less" toggle.
 * Content exceeding CHAR_THRESHOLD is truncated with a gradient fade.
 */
export function CollapsibleText({ text, children }) {
  const [expanded, setExpanded] = useState(false);
  const content = text || '';

  if (content.length <= CHAR_THRESHOLD) return children || content;

  return (
    <div className="relative">
      <div className={expanded ? '' : 'max-h-20 overflow-hidden'}>
        {children || content}
        {!expanded && (
          <div className="pointer-events-none absolute inset-x-0 bottom-0 h-10 bg-gradient-to-t from-slate-800/90 to-transparent" />
        )}
      </div>
      <button
        onClick={() => setExpanded(previous => !previous)}
        className="mt-1 text-[10px] font-medium text-cyan-400 transition hover:text-cyan-300"
      >
        {expanded ? 'Read less ▴' : 'Read more ▾'}
      </button>
    </div>
  );
}

