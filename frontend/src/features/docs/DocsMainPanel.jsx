import { useEffect, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';

export function DocsMainPanel({ api, selectedDoc, theme }) {
  const [docState, setDocState] = useState({ doc: null, content: '', error: null });

  useEffect(() => {
    if (!selectedDoc) return;
    const controller = new AbortController();
    api.getDocContent(selectedDoc, controller.signal)
      .then(text => {
        if (!controller.signal.aborted) {
          setDocState({ doc: selectedDoc, content: text, error: null });
        }
      })
      .catch(err => {
        if (err.name !== 'AbortError' && !controller.signal.aborted) {
          setDocState({ doc: selectedDoc, content: '', error: err.message });
        }
      });
    return () => controller.abort();
  }, [api, selectedDoc]);

  if (!selectedDoc) {
    return <div className="p-8 text-slate-500">Select a document from the sidebar to view it here.</div>;
  }

  if (docState.doc !== selectedDoc) {
    return <div className="p-8 text-slate-400">Loading document...</div>;
  }

  if (docState.error) {
    return <div className="p-8 text-rose-400">Failed to load document: {docState.error}</div>;
  }

  const content = docState.content;

  return (
    <div className={`mx-auto max-w-4xl p-6 lg:p-10 prose prose-slate prose-a:text-cyan-400 hover:prose-a:text-cyan-300 ${theme === 'dark' ? 'prose-invert prose-headings:text-slate-100' : 'prose-headings:text-slate-900 text-slate-800'}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          code({_node, inline, className, children, ...props}) {
            const match = /language-(\w+)/.exec(className || '')
            return !inline && match ? (
              <SyntaxHighlighter
                {...props}
                children={String(children).replace(/\n$/, '')}
                style={vscDarkPlus}
                language={match[1]}
                PreTag="div"
              />
            ) : (
              <code {...props} className={className}>
                {children}
              </code>
            )
          }
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}

