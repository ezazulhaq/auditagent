/**
 * Utility functions for notification parsing, filtering, and timestamp formatting.
 */

/**
 * Formats a date string or timestamp into a friendly relative time (e.g., 'Just now', '5m ago').
 *
 * @param {string|number|Date} dateInput
 * @param {Date} [now=new Date()]
 * @returns {string}
 */
export function formatRelativeTime(dateInput, now = new Date()) {
  if (!dateInput) return 'Just now';
  const date = new Date(dateInput);
  if (isNaN(date.getTime())) return 'Just now';

  const diffMs = now.getTime() - date.getTime();
  if (diffMs < 0) return 'Just now';

  const diffSeconds = Math.floor(diffMs / 1000);
  if (diffSeconds < 60) {
    return 'Just now';
  }

  const diffMinutes = Math.floor(diffSeconds / 60);
  if (diffMinutes < 60) {
    return `${diffMinutes}m ago`;
  }

  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) {
    return `${diffHours}h ago`;
  }

  const diffDays = Math.floor(diffHours / 24);
  if (diffDays < 7) {
    return `${diffDays}d ago`;
  }

  return date.toLocaleDateString();
}

/**
 * Extracts a vulnerability ID (e.g., 'VULN-000042' or 'VULN-1') from notification message text.
 *
 * @param {string} message
 * @returns {string|null}
 */
export function extractFindingId(message) {
  if (!message || typeof message !== 'string') return null;
  const match = message.match(/\b(VULN-[A-Za-z0-9_-]+)\b/i);
  return match ? match[1].toUpperCase() : null;
}

/**
 * Extracts pull request number and URL (if present) from notification message text.
 *
 * @param {string} message
 * @returns {{ prNumber: string|null, prUrl: string|null }}
 */
export function extractPrInfo(message) {
  if (!message || typeof message !== 'string') return { prNumber: null, prUrl: null };
  const numMatch = message.match(/(?:pull request|PR)\s*#(\d+)/i);
  const urlMatch = message.match(/https?:\/\/[^\s]+pull\/(\d+)/i);

  const prNumber = numMatch ? numMatch[1] : (urlMatch ? urlMatch[1] : null);
  const prUrl = urlMatch ? urlMatch[0] : null;

  return { prNumber, prUrl };
}

/**
 * Filters a list of notifications based on active tab ('all', 'unread', 'alerts').
 *
 * @param {Array} notifications
 * @param {'all'|'unread'|'alerts'} tab
 * @returns {Array}
 */
export function filterNotifications(notifications, tab = 'all') {
  if (!Array.isArray(notifications)) return [];
  switch (tab) {
    case 'unread':
      return notifications.filter(n => !n.read);
    case 'alerts':
      return notifications.filter(n => n.type === 'error' || n.type === 'warning');
    case 'all':
    default:
      return notifications;
  }
}
