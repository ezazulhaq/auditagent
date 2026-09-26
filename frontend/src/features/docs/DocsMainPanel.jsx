import { useEffect, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';

export function DocsMainPanel({ api, selectedDoc, theme }) {
  const [content, setContent] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!selectedDoc) {
      setContent('');
      return;
    }
    setLoading(true);
    setError(null);
    const controller = new AbortController();
    api.getDocContent(selectedDoc, controller.signal)
      .then(text => setContent(text))
      .catch(err => {
        if (err.name !== 'AbortError') setError(err.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [api, selectedDoc]);

  if (loading) {
    return <div className="p-8 text-slate-400">Loading document...</div>;
  }

  if (error) {
    return <div className="p-8 text-rose-400">Failed to load document: {error}</div>;
  }

  if (!selectedDoc) {
    return <div className="p-8 text-slate-500">Select a document from the sidebar to view it here.</div>;
  }

  return (
    <div className={`mx-auto max-w-4xl p-6 lg:p-10 prose prose-slate prose-a:text-cyan-400 hover:prose-a:text-cyan-300 ${theme === 'dark' ? 'prose-invert prose-headings:text-slate-100' : 'prose-headings:text-slate-900 text-slate-800'}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          code({node, inline, className, children, ...props}) {
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

