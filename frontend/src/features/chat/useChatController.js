import { useCallback, useRef } from 'react';

export function useChatController({ api, chat, repositoryId, branch, threadId, findings,
  onScan, onAnalyze }) {
  const abortRef = useRef(null);

  const cancelStream = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    chat.setIsThinking(false);
    chat.setIsStreaming(false);
    chat.addMessage('system', '*(Generation stopped)*');
  }, [chat]);

  const submitChat = useCallback(async (eventOrMessage) => {
    eventOrMessage?.preventDefault?.();
    const isDirectString = typeof eventOrMessage === 'string';
    const userInput = (isDirectString ? eventOrMessage : chat.input).trim();
    if (!userInput) return;
    chat.addMessage('user', userInput);
    if (!isDirectString) chat.setInput('');

    if (userInput.toLowerCase() === 'scan') {
      await onScan();
      return;
    }

    const fixMatch = /fix\s+(VULN-[A-Z0-9]{6})/i.exec(userInput);
    const idMatch = /^(VULN-[A-Z0-9]{6})$/i.exec(userInput);
    const targetId = (fixMatch?.[1] ?? idMatch?.[1])?.toUpperCase();
    if (targetId) {
      const finding = findings.find(item => item.id === targetId);
      if (finding) await onAnalyze(finding);
      else chat.addMessage('agent', `Could not find ${targetId} in the active report.`);
      return;
    }

    if (!repositoryId || !branch) {
      chat.addMessage('agent', 'Select a GitHub repository and branch before starting a repository conversation.');
      return;
    }

    const controller = new AbortController();
    abortRef.current = controller;
    chat.setIsThinking(true);
    chat.setIsStreaming(false);
    try {
      let responseText = '';
      let startedStreaming = false;
      chat.addMessage('agent', ''); // Placeholder for streaming

      await api.chat({ message: userInput, repositoryId, branch, threadId }, event => {
        if (!startedStreaming) {
          startedStreaming = true;
          chat.setIsThinking(false);
          chat.setIsStreaming(true);
        }
        if (event.type === 'token') {
          responseText += event.content;
          chat.updateLastMessage(responseText);
        } else if (event.response) {
          responseText += event.response;
          chat.updateLastMessage(responseText);
        }
      }, controller.signal);

      chat.setIsThinking(false);
      chat.setIsStreaming(false);
      abortRef.current = null;

      if (responseText.includes('[TRIGGER_SCAN]')) {
        await onScan();
      } else {
        const trigger = /\[TRIGGER_FIX:(VULN-[A-Z0-9]{6})\]/i.exec(responseText);
        const finding = trigger && findings.find(item => item.id === trigger[1].toUpperCase());
        if (finding) await onAnalyze(finding);
      }
    } catch (error) {
      chat.setIsThinking(false);
      chat.setIsStreaming(false);
      abortRef.current = null;
      if (error.name !== 'AbortError') {
        chat.addMessage('agent', `Error reaching assistant: ${error.message}`);
      }
    }
  }, [api, branch, chat, findings, onAnalyze, onScan, repositoryId, threadId]);

  return { submitChat, cancelStream };
}
