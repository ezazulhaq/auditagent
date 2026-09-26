import { Sparkles } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ChatFab } from './components/ChatFab';
import { ChatHeader } from './components/ChatHeader';
import { ChatInput } from './components/ChatInput';
import { MessageBubble } from './components/MessageBubble';
import { NewMessagesPill } from './components/NewMessagesPill';
import { PrBanner } from './components/PrBanner';
import { RecoveryBanner } from './components/RecoveryBanner';
import { ThinkingIndicator } from './components/ThinkingIndicator';

/**
 * Orchestrates the chat panel UI by composing sub-components.
 * Handles smart scroll anchoring — auto-scrolls only when the user is near
 * the bottom, and shows a "New messages" pill otherwise.
 */
export function ChatWidget({ chat, onSubmit, onCancel, activeRun, publication, onResume, onDiscard, onRetryPublish,
  onForgetConversation, onForgetRepository }) {
  const scrollRef = useRef(null);
  const endRef = useRef(null);
  const [isNearBottom, setIsNearBottom] = useState(true);
  const [showNewMessagesPill, setShowNewMessagesPill] = useState(false);
  const { isOpen, toggle } = chat;

  // Track scroll position to determine if user is near bottom
  const handleScroll = useCallback(() => {
    const container = scrollRef.current;
    if (!container) return;
    const threshold = 80; // px from bottom
    const atBottom = container.scrollHeight - container.scrollTop - container.clientHeight < threshold;
    setIsNearBottom(atBottom);
    if (atBottom) setShowNewMessagesPill(false);
  }, []);

  // Auto-scroll when new messages arrive (only if near bottom)
  useEffect(() => {
    if (isNearBottom) {
      endRef.current?.scrollIntoView?.({ behavior: 'smooth' });
    } else if (chat.messages.length > 0) {
      setShowNewMessagesPill(true);
    }
  }, [chat.messages, chat.isThinking, isNearBottom]);

  // Reset scroll state when chat opens
  useEffect(() => {
    if (isOpen) {
      setIsNearBottom(true);
      setShowNewMessagesPill(false);
      // Scroll to bottom after the opening animation
      requestAnimationFrame(() => endRef.current?.scrollIntoView?.({ behavior: 'instant' }));
    }
  }, [isOpen]);

  // Close on Escape
  useEffect(() => {
    if (!isOpen) return undefined;
    const closeOnEscape = event => { if (event.key === 'Escape') toggle(); };
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [isOpen, toggle]);

  const scrollToBottom = useCallback(() => {
    endRef.current?.scrollIntoView?.({ behavior: 'smooth' });
    setShowNewMessagesPill(false);
    setIsNearBottom(true);
  }, []);

  return <div className="pointer-events-none fixed inset-x-0 bottom-0 z-50 flex flex-col items-end sm:inset-x-auto sm:bottom-5 sm:right-5">
    <section aria-label="Security agent chat" className={`pointer-events-auto flex origin-bottom-right flex-col overflow-hidden bg-[#091624]/98 shadow-[0_30px_100px_rgba(0,0,0,0.52)] backdrop-blur-xl transition duration-200 fixed inset-0 z-50 h-[100dvh] w-full rounded-none sm:static sm:z-auto sm:mb-4 sm:h-[min(680px,calc(100vh-8rem))] sm:w-[420px] sm:rounded-2xl sm:border sm:border-slate-700/40 ${chat.isOpen ? 'translate-y-0 scale-100 opacity-100' : 'pointer-events-none translate-y-3 scale-[0.98] opacity-0'}`}>
      <ChatHeader
        onForgetRepository={onForgetRepository}
        onForgetConversation={onForgetConversation}
        onClose={toggle}
      />

      <RecoveryBanner activeRun={activeRun} onResume={onResume} onDiscard={onDiscard} onRetryPublish={onRetryPublish} />
      <PrBanner activeRun={activeRun} publication={publication} />

      <div className="relative flex-1 overflow-hidden">
        <div ref={scrollRef} onScroll={handleScroll} className="h-full space-y-4 overflow-y-auto p-4">
          {chat.messages.length === 0 && <div className="flex h-full min-h-56 flex-col items-center justify-center px-6 text-center">
            <span className="flex h-12 w-12 items-center justify-center rounded-2xl border border-cyan-400/15 bg-cyan-400/[0.07] text-cyan-300"><Sparkles size={21} /></span>
            <p className="mt-4 text-sm font-semibold text-slate-200">How can I help with this audit?</p>
            <p className="mt-2 text-[11px] leading-5 text-slate-500">Ask about findings, remediation guidance, or start a scan with a direct command.</p>
            <div className="mt-6 flex flex-wrap justify-center gap-2">
              {['Scan repository', 'Fix highest severity', 'Explain this finding'].map(action => (
                <button key={action} onClick={() => onSubmit(action)} className="rounded-full border border-slate-700/50 bg-slate-800/40 px-3 py-1.5 text-[10px] font-medium text-slate-300 transition hover:border-cyan-500/30 hover:bg-cyan-500/10 hover:text-cyan-100">{action}</button>
              ))}
            </div>
          </div>}

          {chat.messages.map((message, index) => (
            <MessageBubble key={`${message.timestamp || 'message'}-${index}`} message={message} />
          ))}

          {chat.isThinking && <ThinkingIndicator />}
          <div ref={endRef} />
        </div>

        {showNewMessagesPill && <NewMessagesPill onClick={scrollToBottom} />}
      </div>

      <ChatInput
        input={chat.input}
        setInput={chat.setInput}
        onSubmit={onSubmit}
        isStreaming={chat.isStreaming}
        onCancel={onCancel}
      />
    </section>

    <div className={chat.isOpen ? 'hidden sm:block' : 'block'}>
      <ChatFab isOpen={chat.isOpen} onToggle={chat.toggle} unreadCount={chat.unreadCount} />
    </div>
  </div>;
}