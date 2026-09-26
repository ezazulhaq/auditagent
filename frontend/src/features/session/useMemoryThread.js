import { useCallback, useRef, useState } from 'react';

export const WELCOME_MESSAGE = 'Security Auditor Agent ready. Select a GitHub repository and branch to begin.';

const restoredMessages = (messages = []) => messages.map(message => ({
  role: message.role === 'USER' ? 'user' : message.role === 'AI' ? 'agent' : 'system',
  text: message.content,
  timestamp: message.createdAt
    ? new Date(message.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    : undefined,
}));

export function useMemoryThread({ api, onRestore }) {
  const [threadId, setThreadId] = useState('');
  const [isRestoring, setIsRestoring] = useState(false);
  const requestRef = useRef(0);
  const abortRef = useRef(null);
  const threadRef = useRef({ id: '', key: '' });

  const restoreThread = useCallback(async (repositoryId = null, branch = null, externalSignal) => {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    if (externalSignal) {
      if (externalSignal.aborted) controller.abort();
      else externalSignal.addEventListener('abort', () => controller.abort(), { once: true });
    }
    const requestId = ++requestRef.current;
    setIsRestoring(true);
    try {
      const data = await api.restoreThread(repositoryId, branch, controller.signal);
      if (requestId !== requestRef.current) return null;
      const key = repositoryId ? `${repositoryId}:${branch}` : 'general';
      threadRef.current = { id: data.threadId, key };
      setThreadId(data.threadId);
      onRestore?.({ ...data, messages: restoredMessages(data.messages) });
      return data.threadId;
    } finally {
      if (requestId === requestRef.current) setIsRestoring(false);
    }
  }, [api, onRestore]);

  const ensureThread = useCallback(async (repositoryId, branch, signal) => {
    const key = repositoryId ? `${repositoryId}:${branch}` : 'general';
    if (threadRef.current.id && threadRef.current.key === key) return threadRef.current.id;
    return restoreThread(repositoryId, branch, signal);
  }, [restoreThread]);

  const forgetThread = useCallback(async (repositoryId, branch) => {
    if (!threadRef.current.id) return;
    await api.forgetThread(threadRef.current.id);
    threadRef.current = { id: '', key: '' };
    setThreadId('');
    await restoreThread(repositoryId || null, branch || null);
  }, [api, restoreThread]);

  return { threadId, isRestoring, ensureThread, restoreThread, forgetThread };
}
