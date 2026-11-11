import { useState, useEffect, memo } from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { CopyButton } from '../../../components/CopyButton';

/**
 * VulnerableCodeViewer displays the highlighted vulnerable code snippet with copy support.
 * Adheres to Single Responsibility Principle (SRP).
 */
export const VulnerableCodeViewer = memo(function VulnerableCodeViewer({ snippet, language, lineNumber }) {
  const [highlighted, setHighlighted] = useState(false);

  useEffect(() => {
    const frame = requestAnimationFrame(() => {
      setHighlighted(true);
    });
    return () => cancelAnimationFrame(frame);
  }, [snippet, language]);

  return (
    <div className="relative group overflow-hidden rounded-2xl border border-slate-700/35 shadow-inner bg-[#050d18]">
      <CopyButton
        text={snippet}
        className="absolute right-2 top-2 z-10 opacity-0 transition-opacity group-hover:opacity-100 focus-within:opacity-100"
      />
      {highlighted ? (
        <SyntaxHighlighter
          language={language?.toLowerCase() || 'javascript'}
          style={vscDarkPlus}
          showLineNumbers={true}
          startingLineNumber={Math.max(1, lineNumber - 10)}
          wrapLines={true}
          lineProps={lineNum => ({
            style: {
              display: 'block',
              backgroundColor: lineNum === lineNumber ? 'rgba(239, 68, 68, 0.2)' : 'transparent',
            }
          })}
          customStyle={{
            margin: 0,
            padding: '1rem',
            paddingTop: '2rem',
            background: '#050d18',
            fontSize: '0.75rem',
            lineHeight: '1.25rem'
          }}
        >
          {snippet}
        </SyntaxHighlighter>
      ) : (
        <pre className="m-0 p-4 pt-8 text-xs leading-5 font-mono text-slate-300 overflow-x-auto whitespace-pre bg-[#050d18]">
          <code>{snippet}</code>
        </pre>
      )}
    </div>
  );
});
