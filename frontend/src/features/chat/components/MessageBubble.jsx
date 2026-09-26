import { Bot } from 'lucide-react';
import { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { CodeBlock } from './CodeBlock';
import { CollapsibleText } from './CollapsibleText';

const PROSE_THRESHOLD = 300;

/**
 * Renders a single chat message with role-based styling, entrance animation,
 * syntax-highlighted code blocks, collapsible long content, and hover timestamps.
 */
export function MessageBubble({ message }) {
  const { role, text, timestamp } = message;
  const [hovered, setHovered] = useState(false);
  const isUser = role === 'user';
  const isSystem = role === 'system';

  const bubbleClasses = isUser
    ? 'rounded-br-md bg-gradient-to-br from-cyan-300 to-cyan-400 font-medium text-[#05212a]'
    : isSystem
      ? 'rounded-bl-md border-l-2 border-l-amber-400/40 border border-slate-700/30 bg-slate-900/40 text-slate-400 text-[11px]'
      : 'rounded-bl-md border border-slate-700/25 bg-slate-800/75 text-slate-200';

  const markdownComponents = {
    code({ inline, className, children, ...props }) {
      const match = /language-(\w+)/.exec(className || '');
      return (
        <CodeBlock language={match?.[1]} inline={inline} {...props}>
          {children}
        </CodeBlock>
      );
    },
  };

  const content = (
    <div className="space-y-1.5 [&_ul]:list-disc [&_ul]:pl-4 [&_ol]:list-decimal [&_ol]:pl-4 [&_a]:underline [&_p]:whitespace-pre-wrap">
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={markdownComponents}>
        {text}
      </ReactMarkdown>
    </div>
  );

  const needsCollapse = !isUser && text && text.length > PROSE_THRESHOLD && !text.includes('```');

  return (
    <div
      className={`animate-message-enter flex ${isUser ? 'justify-end' : 'justify-start'}`}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      {/* Agent avatar */}
      {!isUser && !isSystem && (
        <div className="mr-2 mt-1 flex h-6 w-6 shrink-0 items-center justify-center rounded-lg border border-cyan-400/15 bg-cyan-400/[0.07] text-cyan-300">
          <Bot size={13} />
        </div>
      )}

      <div className={`max-w-[88%] overflow-hidden rounded-2xl px-3.5 py-2.5 text-xs leading-5 shadow-sm ${bubbleClasses}`}>
        {needsCollapse ? <CollapsibleText text={text}>{content}</CollapsibleText> : content}

        {/* Timestamp — visible on hover */}
        {timestamp && (
          <span className={`mt-1.5 block text-[9px] transition-opacity duration-150 ${hovered ? 'opacity-45' : 'opacity-0'}`}>
            {timestamp}
          </span>
        )}
      </div>
    </div>
  );
}

