import { useState, useMemo } from 'react';
import { Columns, Rows, FileDiff } from 'lucide-react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { CopyButton } from './CopyButton';

/**
 * Parses a unified diff string into structured file hunks for side-by-side display.
 */
export function parseDiffToSideBySide(diffText) {
  if (!diffText) return [];
  const lines = diffText.split('\n');
  const files = [];
  let currentFile = null;
  let leftLineNum = 0;
  let rightLineNum = 0;

  let i = 0;
  while (i < lines.length) {
    const line = lines[i];

    if (line.startsWith('diff --git')) {
      currentFile = { header: line, hunks: [] };
      files.push(currentFile);
      i++;
      continue;
    }

    if (line.startsWith('--- ') || line.startsWith('+++ ') || line.startsWith('index ')) {
      i++;
      continue;
    }

    if (line.startsWith('@@')) {
      const match = line.match(/@@\s+-(\d+)(?:,\d+)?\s+\+(\d+)(?:,\d+)?\s+@@/);
      if (match) {
        leftLineNum = parseInt(match[1], 10);
        rightLineNum = parseInt(match[2], 10);
      }
      if (!currentFile) {
        currentFile = { header: 'Diff', hunks: [] };
        files.push(currentFile);
      }
      const hunk = { header: line, rows: [] };
      currentFile.hunks.push(hunk);
      i++;

      const pendingRemoved = [];
      const pendingAdded = [];

      const flushPending = () => {
        const max = Math.max(pendingRemoved.length, pendingAdded.length);
        for (let j = 0; j < max; j++) {
          const rem = pendingRemoved[j];
          const add = pendingAdded[j];
          hunk.rows.push({
            left: rem !== undefined ? { lineNum: leftLineNum++, content: rem, type: 'removed' } : { lineNum: '', content: '', type: 'empty' },
            right: add !== undefined ? { lineNum: rightLineNum++, content: add, type: 'added' } : { lineNum: '', content: '', type: 'empty' },
          });
        }
        pendingRemoved.length = 0;
        pendingAdded.length = 0;
      };

      while (i < lines.length && !lines[i].startsWith('@@') && !lines[i].startsWith('diff --git')) {
        const l = lines[i];
        if (l.startsWith('-')) {
          pendingRemoved.push(l.slice(1));
        } else if (l.startsWith('+')) {
          pendingAdded.push(l.slice(1));
        } else {
          flushPending();
          const content = l.startsWith(' ') ? l.slice(1) : l;
          hunk.rows.push({
            left: { lineNum: leftLineNum++, content, type: 'normal' },
            right: { lineNum: rightLineNum++, content, type: 'normal' },
          });
        }
        i++;
      }
      flushPending();
      continue;
    }
    i++;
  }

  return files;
}

export function DiffViewer({ diffText, initialMode = 'side-by-side' }) {
  const [viewMode, setViewMode] = useState(initialMode);

  const parsedFiles = useMemo(() => {
    if (viewMode === 'side-by-side') {
      return parseDiffToSideBySide(diffText);
    }
    return [];
  }, [diffText, viewMode]);

  if (!diffText) return null;

  return (
    <div className="rounded-xl border border-slate-700/35 overflow-hidden bg-[#050d18]">
      {/* Header controls */}
      <div className="flex items-center justify-between border-b border-slate-700/35 bg-slate-900/60 px-3 py-2">
        <div className="flex items-center gap-2">
          <FileDiff size={13} className="text-cyan-300" />
          <span className="text-[10px] font-bold uppercase tracking-[0.12em] text-slate-400">
            {viewMode === 'side-by-side' ? 'Side-by-side diff' : 'Unified diff'}
          </span>
        </div>

        <div className="flex items-center gap-3">
          <div className="flex items-center rounded-lg border border-slate-700/50 bg-slate-950/60 p-0.5">
            <button
              onClick={() => setViewMode('side-by-side')}
              className={`flex items-center gap-1.5 rounded-md px-2 py-1 text-[10px] font-semibold transition ${
                viewMode === 'side-by-side'
                  ? 'bg-cyan-400/15 text-cyan-200'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
              title="Side-by-side diff view"
            >
              <Columns size={12} />
              <span className="hidden sm:inline">Side-by-side</span>
            </button>
            <button
              onClick={() => setViewMode('unified')}
              className={`flex items-center gap-1.5 rounded-md px-2 py-1 text-[10px] font-semibold transition ${
                viewMode === 'unified'
                  ? 'bg-cyan-400/15 text-cyan-200'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
              title="Unified diff view"
            >
              <Rows size={12} />
              <span className="hidden sm:inline">Unified</span>
            </button>
          </div>

          <CopyButton text={diffText} />
        </div>
      </div>

      {/* Content View */}
      {viewMode === 'unified' ? (
        <div className="relative overflow-hidden">
          <SyntaxHighlighter
            language="diff"
            style={vscDarkPlus}
            customStyle={{
              margin: 0,
              padding: '1rem',
              background: '#050d18',
              fontSize: '11px',
              lineHeight: '1.25rem',
              maxHeight: '26rem',
            }}
          >
            {diffText}
          </SyntaxHighlighter>
        </div>
      ) : (
        <div className="max-h-[26rem] overflow-auto font-mono text-[11px] leading-5">
          {parsedFiles.length === 0 ? (
            <div className="p-4 text-center text-xs text-slate-500">No diff available</div>
          ) : (
            parsedFiles.map((file, fileIdx) => (
              <div key={fileIdx} className="border-b border-slate-800 last:border-b-0">
                {file.hunks.map((hunk, hunkIdx) => (
                  <div key={hunkIdx}>
                    {/* Hunk Header */}
                    <div className="bg-slate-900/80 px-3 py-1 text-[10px] font-semibold text-cyan-400/80 border-y border-slate-800/60 sticky top-0 z-10">
                      {hunk.header}
                    </div>

                    {/* Side-by-side table rows */}
                    <table className="w-full border-collapse table-fixed">
                      <colgroup>
                        <col className="w-10" />
                        <col className="w-[calc(50%-2.5rem)]" />
                        <col className="w-10" />
                        <col className="w-[calc(50%-2.5rem)]" />
                      </colgroup>
                      <tbody>
                        {hunk.rows.map((row, rowIdx) => {
                          const isLeftRemoved = row.left.type === 'removed';
                          const isRightAdded = row.right.type === 'added';
                          const isLeftEmpty = row.left.type === 'empty';
                          const isRightEmpty = row.right.type === 'empty';

                          return (
                            <tr key={rowIdx} className="hover:bg-slate-800/30">
                              {/* Left Line Number */}
                              <td
                                className={`select-none text-right pr-2 font-mono text-[10px] border-r border-slate-800/60 ${
                                  isLeftRemoved
                                    ? 'bg-rose-950/60 text-rose-400 font-semibold'
                                    : isLeftEmpty
                                    ? 'bg-slate-950/40 text-transparent'
                                    : 'bg-slate-900/40 text-slate-600'
                                }`}
                              >
                                {row.left.lineNum}
                              </td>
                              {/* Left Code Content */}
                              <td
                                className={`px-2 py-0.5 whitespace-pre overflow-x-auto border-r border-slate-800/60 ${
                                  isLeftRemoved
                                    ? 'bg-rose-950/30 text-rose-200'
                                    : isLeftEmpty
                                    ? 'bg-slate-950/20'
                                    : 'text-slate-300'
                                }`}
                              >
                                {isLeftRemoved && <span className="inline-block w-4 text-rose-400 select-none">-</span>}
                                {row.left.content}
                              </td>

                              {/* Right Line Number */}
                              <td
                                className={`select-none text-right pr-2 font-mono text-[10px] border-r border-slate-800/60 ${
                                  isRightAdded
                                    ? 'bg-emerald-950/60 text-emerald-400 font-semibold'
                                    : isRightEmpty
                                    ? 'bg-slate-950/40 text-transparent'
                                    : 'bg-slate-900/40 text-slate-600'
                                }`}
                              >
                                {row.right.lineNum}
                              </td>
                              {/* Right Code Content */}
                              <td
                                className={`px-2 py-0.5 whitespace-pre overflow-x-auto ${
                                  isRightAdded
                                    ? 'bg-emerald-950/30 text-emerald-200'
                                    : isRightEmpty
                                    ? 'bg-slate-950/20'
                                    : 'text-slate-300'
                                }`}
                              >
                                {isRightAdded && <span className="inline-block w-4 text-emerald-400 select-none">+</span>}
                                {row.right.content}
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                ))}
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
