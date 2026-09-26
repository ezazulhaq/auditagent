import { useEffect } from 'react';

export function useKeyboardShortcuts({ onSearch, onClose, onTabChange, onChatToggle }) {
  useEffect(() => {
    const handleKeyDown = (event) => {
      // Don't trigger shortcuts if user is typing in an input or textarea
      const activeElement = document.activeElement;
      const isInput =
        activeElement.tagName === 'INPUT' ||
        activeElement.tagName === 'TEXTAREA' ||
        activeElement.isContentEditable;

      // Escape allows closing modals/drawers even if in an input
      if (event.key === 'Escape') {
        if (onClose) {
          onClose(event);
        }
        return;
      }

      // Check Ctrl+K or Cmd+K for search/focus
      if ((event.ctrlKey || event.metaKey) && event.key === 'k') {
        event.preventDefault();
        if (onSearch) onSearch();
        return;
      }

      // Check Ctrl+J or Cmd+J for chat toggle
      if ((event.ctrlKey || event.metaKey) && event.key === 'j') {
        event.preventDefault();
        if (onChatToggle) onChatToggle();
        return;
      }

      // 1, 2, 3 for tabs (only when not typing in an input)
      if (!isInput && !event.ctrlKey && !event.metaKey && !event.altKey) {
        if (event.key === '1' && onTabChange) {
          event.preventDefault();
          onTabChange('dashboard');
        }
        if (event.key === '2' && onTabChange) {
          event.preventDefault();
          onTabChange('findings');
        }
        if (event.key === '3' && onTabChange) {
          event.preventDefault();
          onTabChange('report');
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onSearch, onClose, onTabChange, onChatToggle]);
}
