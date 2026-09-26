import { useCallback, useEffect, useRef, useState } from 'react';
import { WELCOME_MESSAGE } from '../session/useMemoryThread';

const now = () => new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

export function useChatState() {
  /** @type {[Array<import('../../types').ChatMessage>, Function]} */
  const [messages, setMessages] = useState([{ role: 'agent', text: WELCOME_MESSAGE }]);
  const [input, setInput] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [isThinking, setIsThinking] = useState(false);
  const [isStreaming, setIsStreaming] = useState(false);
  const openRef = useRef(false);

  useEffect(() => { openRef.current = isOpen; }, [isOpen]);

  const addMessage = useCallback((role, text) => {
    setMessages(previous => [...previous, { role, text, timestamp: now() }]);
    if (role !== 'user' && !openRef.current) setUnreadCount(previous => previous + 1);
  }, []);

  const updateLastMessage = useCallback((text) => {
    setMessages(previous => {
      const copy = [...previous];
      if (copy.length > 0) {
        copy[copy.length - 1] = { ...copy[copy.length - 1], text };
      }
      return copy;
    });
  }, []);

  const restoreMessages = useCallback((restored) => {
    setMessages(restored?.length ? restored : [{ role: 'agent', text: WELCOME_MESSAGE }]);
  }, []);

  const resetMessages = useCallback(() => {
    setMessages([{ role: 'agent', text: WELCOME_MESSAGE }]);
  }, []);

  const removeMessage = useCallback((text) => {
    setMessages(previous => previous.filter(message => message.text !== text));
  }, []);

  const toggle = useCallback(() => {
    setIsOpen(previous => {
      if (!previous) setUnreadCount(0);
      return !previous;
    });
  }, []);

  return {
    messages, input, isOpen, unreadCount, isThinking, isStreaming,
    setInput, setIsOpen, setIsThinking, setIsStreaming, addMessage, updateLastMessage, restoreMessages, resetMessages, removeMessage, toggle,
  };
}
