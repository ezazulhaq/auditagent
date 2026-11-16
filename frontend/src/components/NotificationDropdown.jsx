import {
  Bell,
  CheckCircle2,
  AlertCircle,
  AlertTriangle,
  Info,
  X,
  CheckCheck,
  Trash2,
  ExternalLink,
  ShieldAlert,
  GitPullRequest
} from 'lucide-react';
import { useState, useEffect, useRef, useMemo } from 'react';
import { auditApi } from '../api/auditApi';
import {
  formatRelativeTime,
  extractFindingId,
  extractPrInfo,
  filterNotifications
} from '../utils/notificationUtils';

export function NotificationDropdown({
  api = auditApi,
  findings = [],
  onSelectFinding,
}) {
  const [isOpen, setIsOpen] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [activeTab, setActiveTab] = useState('all'); // 'all' | 'unread' | 'alerts'
  const [isConfirmingClear, setIsConfirmingClear] = useState(false);
  const dropdownRef = useRef(null);

  const unreadCount = useMemo(() => {
    return notifications.filter(n => !n.read).length;
  }, [notifications]);

  const alertsCount = useMemo(() => {
    return notifications.filter(n => n.type === 'error' || n.type === 'warning').length;
  }, [notifications]);

  const filteredNotifications = useMemo(() => {
    return filterNotifications(notifications, activeTab);
  }, [notifications, activeTab]);

  useEffect(() => {
    let ignore = false;

    const load = () => {
      api?.getNotifications?.()
        ?.then?.(data => {
          if (!ignore && Array.isArray(data)) {
            setNotifications(data);
          }
        })
        ?.catch?.(e => {
          console.error('Failed to fetch notifications', e);
        });
    };

    load();

    const handleNew = (event) => {
      if (event?.detail?.message) {
        setNotifications(current => {
          if (current.some(n => n.message === event.detail.message && (!n.id || String(n.id).startsWith('temp-')))) {
            return current;
          }
          return [
            {
              id: event.detail.id || `temp-${Date.now()}-${Math.random()}`,
              type: event.detail.type || 'info',
              message: event.detail.message,
              read: false,
              createdAt: new Date().toISOString(),
            },
            ...current,
          ];
        });
      }
      load();
    };

    window.addEventListener('notification-added', handleNew);
    return () => {
      ignore = true;
      window.removeEventListener('notification-added', handleNew);
    };
  }, [api]);

  // Click outside and Escape key handler
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setIsOpen(false);
        setIsConfirmingClear(false);
      }
    };

    const handleKeyDown = (event) => {
      if (event.key === 'Escape' && isOpen) {
        setIsOpen(false);
        setIsConfirmingClear(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen]);

  const handleToggleOpen = () => {
    setIsOpen(prev => !prev);
    setIsConfirmingClear(false);
  };

  const handleMarkAllRead = () => {
    if (unreadCount === 0) return;
    api?.markNotificationsRead?.()
      ?.then?.(() => {
        setNotifications(current => current.map(n => ({ ...n, read: true })));
      })
      ?.catch?.(console.error);
  };

  const handleMarkSingleRead = (id) => {
    if (!id) return;
    setNotifications(current => current.map(n => n.id === id ? { ...n, read: true } : n));
    if (api?.markNotificationRead) {
      api.markNotificationRead(id).catch(console.error);
    } else if (api?.markNotificationsRead) {
      // Fallback
      api.markNotificationsRead().catch(console.error);
    }
  };

  const handleClearAll = () => {
    api?.clearNotifications?.()
      ?.then?.(() => {
        setNotifications([]);
        setIsConfirmingClear(false);
      })
      ?.catch?.(err => {
        console.error('Failed to clear notifications', err);
        setIsConfirmingClear(false);
      });
  };

  const handleNotificationClick = (item) => {
    if (!item.read) {
      handleMarkSingleRead(item.id);
    }

    const findingId = extractFindingId(item.message);
    if (findingId && typeof onSelectFinding === 'function') {
      const match = findings?.find?.(f => f?.id?.toUpperCase() === findingId) || { id: findingId };
      onSelectFinding(match);
      setIsOpen(false);
    }
  };

  return (
    <div className="relative" ref={dropdownRef}>
      <button
        type="button"
        onClick={handleToggleOpen}
        title="Notifications"
        aria-label="Notifications"
        aria-expanded={isOpen}
        className="relative ml-0.5 rounded-xl border border-transparent p-2 text-[var(--text-muted)] transition hover:border-[var(--border-subtle)] hover:bg-[var(--surface-inset)]/70 hover:text-[var(--text-primary)]"
      >
        <Bell size={16} />
        {unreadCount > 0 && (
          <span
            className="absolute -top-1 -right-1 flex min-w-4.5 h-4.5 items-center justify-center rounded-full bg-rose-500 px-1 text-[10px] font-bold text-white shadow-sm ring-2 ring-[var(--surface-panel)] animate-in fade-in zoom-in"
            title={`${unreadCount} unread notification${unreadCount > 1 ? 's' : ''}`}
          >
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <div
          role="region"
          aria-label="Notifications panel"
          className="absolute right-0 mt-2 w-84 sm:w-100 rounded-2xl border border-[var(--border-subtle)] bg-[var(--surface-card)] shadow-2xl shadow-slate-900/15 dark:shadow-black/60 backdrop-blur-xl z-50 overflow-hidden flex flex-col max-h-[75vh]"
        >
          {/* Header */}
          <div className="flex items-center justify-between border-b border-[var(--border-subtle)] px-4 py-3 bg-[var(--surface-inset)]/60">
            {isConfirmingClear ? (
              <div className="flex items-center justify-between w-full gap-2 animate-in fade-in duration-150">
                <span className="text-xs font-semibold text-rose-400">Clear all notifications?</span>
                <div className="flex items-center gap-1.5">
                  <button
                    type="button"
                    onClick={handleClearAll}
                    className="rounded-lg bg-rose-500/20 px-2.5 py-1 text-xs font-semibold text-rose-400 hover:bg-rose-500/30 transition"
                  >
                    Clear
                  </button>
                  <button
                    type="button"
                    onClick={() => setIsConfirmingClear(false)}
                    className="rounded-lg px-2 py-1 text-xs text-[var(--text-muted)] hover:text-[var(--text-primary)] transition"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              <>
                <div className="flex items-center gap-2">
                  <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--text-primary)]">Notifications</h3>
                  {unreadCount > 0 && (
                    <span className="rounded-full bg-rose-500/20 px-2 py-0.5 text-[10px] font-semibold text-rose-400 font-mono">
                      {unreadCount} new
                    </span>
                  )}
                </div>
                <div className="flex items-center gap-1">
                  {unreadCount > 0 && (
                    <button
                      type="button"
                      onClick={handleMarkAllRead}
                      title="Mark all as read"
                      aria-label="Mark all as read"
                      className="flex items-center gap-1 rounded-lg px-2 py-1 text-[11px] font-medium text-[var(--text-muted)] hover:text-cyan-400 hover:bg-[var(--surface-inset)] transition"
                    >
                      <CheckCheck size={14} />
                      <span className="hidden sm:inline">Mark read</span>
                    </button>
                  )}
                  {notifications.length > 0 && (
                    <button
                      type="button"
                      onClick={() => setIsConfirmingClear(true)}
                      title="Clear all notifications"
                      aria-label="Clear all notifications"
                      className="rounded-lg p-1 text-[var(--text-muted)] hover:text-rose-400 hover:bg-[var(--surface-inset)] transition"
                    >
                      <Trash2 size={14} />
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() => setIsOpen(false)}
                    aria-label="Close notifications"
                    className="rounded-lg p-1 text-[var(--text-muted)] hover:text-[var(--text-primary)] hover:bg-[var(--surface-inset)] transition"
                  >
                    <X size={15} />
                  </button>
                </div>
              </>
            )}
          </div>

          {/* Segmented Filter Tabs */}
          <div className="flex border-b border-[var(--border-subtle)] bg-[var(--surface-inset)]/30 px-3 py-1.5 gap-1">
            <button
              type="button"
              onClick={() => setActiveTab('all')}
              className={`flex-1 flex items-center justify-center gap-1.5 rounded-lg py-1 text-[11px] font-semibold transition ${
                activeTab === 'all'
                  ? 'bg-[var(--surface-card)] text-[var(--text-primary)] shadow-xs font-bold'
                  : 'text-[var(--text-muted)] hover:text-[var(--text-primary)]'
              }`}
            >
              All
              <span className="text-[10px] font-mono opacity-70">({notifications.length})</span>
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('unread')}
              className={`flex-1 flex items-center justify-center gap-1.5 rounded-lg py-1 text-[11px] font-semibold transition ${
                activeTab === 'unread'
                  ? 'bg-[var(--surface-card)] text-[var(--text-primary)] shadow-xs font-bold'
                  : 'text-[var(--text-muted)] hover:text-[var(--text-primary)]'
              }`}
            >
              Unread
              {unreadCount > 0 && (
                <span className="rounded-full bg-rose-500/20 px-1.5 text-[10px] font-bold text-rose-400">
                  {unreadCount}
                </span>
              )}
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('alerts')}
              className={`flex-1 flex items-center justify-center gap-1.5 rounded-lg py-1 text-[11px] font-semibold transition ${
                activeTab === 'alerts'
                  ? 'bg-[var(--surface-card)] text-[var(--text-primary)] shadow-xs font-bold'
                  : 'text-[var(--text-muted)] hover:text-[var(--text-primary)]'
              }`}
            >
              Alerts
              {alertsCount > 0 && (
                <span className="rounded-full bg-amber-500/20 px-1.5 text-[10px] font-bold text-amber-400">
                  {alertsCount}
                </span>
              )}
            </button>
          </div>

          {/* Notifications List */}
          <div className="overflow-y-auto flex-1 p-2 space-y-1">
            {filteredNotifications.length === 0 ? (
              <div className="text-center py-10 px-4">
                <p className="text-xs font-medium text-[var(--text-primary)] mb-1">
                  {activeTab === 'unread'
                    ? 'All caught up!'
                    : activeTab === 'alerts'
                    ? 'No alerts recorded'
                    : 'No notifications'}
                </p>
                <p className="text-[11px] text-[var(--text-muted)]">
                  {activeTab === 'unread'
                    ? 'You have reviewed all incoming notifications.'
                    : activeTab === 'alerts'
                    ? 'No scan errors or security warnings at this time.'
                    : 'Scan events, PRs, and AI remediation updates will appear here.'}
                </p>
              </div>
            ) : (
              filteredNotifications.map((n, idx) => {
                const findingId = extractFindingId(n.message);
                const { prNumber, prUrl } = extractPrInfo(n.message);

                return (
                  <div
                    key={n.id || `${n.message}-${idx}`}
                    onClick={() => handleNotificationClick(n)}
                    className={`group relative flex gap-3 items-start p-2.5 rounded-xl transition cursor-pointer border ${
                      !n.read
                        ? 'bg-[var(--surface-inset)]/70 border-cyan-500/20 shadow-xs'
                        : 'bg-transparent border-transparent hover:bg-[var(--surface-inset)]/40 hover:border-[var(--border-subtle)]'
                    }`}
                  >
                    {/* Status Type Icon */}
                    <div className="mt-0.5 shrink-0">
                      {n.type === 'success' && <CheckCircle2 size={16} className="text-emerald-400" />}
                      {n.type === 'error' && <AlertCircle size={16} className="text-rose-400" />}
                      {n.type === 'warning' && <AlertTriangle size={16} className="text-amber-400" />}
                      {(!n.type || n.type === 'info') && <Info size={16} className="text-cyan-400" />}
                    </div>

                    {/* Content */}
                    <div className="flex flex-col min-w-0 flex-1">
                      <div className="flex items-start justify-between gap-2">
                        <span className={`text-xs break-words leading-relaxed ${!n.read ? 'font-semibold text-[var(--text-primary)]' : 'text-[var(--text-muted)]'}`}>
                          {n.message}
                        </span>
                        {!n.read && (
                          <span
                            className="h-2 w-2 rounded-full bg-cyan-400 shrink-0 mt-1 shadow-xs shadow-cyan-400/50"
                            title="Unread"
                          />
                        )}
                      </div>

                      {/* Deep-linking metadata chips */}
                      {(findingId || prNumber) && (
                        <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                          {findingId && (
                            <span
                              className="inline-flex items-center gap-1 rounded-md bg-cyan-500/10 border border-cyan-500/30 px-1.5 py-0.5 text-[10px] font-mono font-medium text-cyan-300 hover:bg-cyan-500/20 transition"
                              title="Click to view finding"
                            >
                              <ShieldAlert size={11} />
                              {findingId}
                            </span>
                          )}
                          {prNumber && (
                            <a
                              href={prUrl || '#'}
                              target={prUrl ? '_blank' : '_self'}
                              rel={prUrl ? 'noopener noreferrer' : undefined}
                              onClick={(e) => {
                                if (prUrl) e.stopPropagation();
                              }}
                              className="inline-flex items-center gap-1 rounded-md bg-indigo-500/10 border border-indigo-500/30 px-1.5 py-0.5 text-[10px] font-mono font-medium text-indigo-300 hover:bg-indigo-500/20 transition"
                              title="Pull Request"
                            >
                              <GitPullRequest size={11} />
                              PR #{prNumber}
                              {prUrl && <ExternalLink size={10} />}
                            </a>
                          )}
                        </div>
                      )}

                      {/* Timestamp */}
                      <span
                        className="text-[10px] text-[var(--text-muted)] font-mono mt-1 opacity-80"
                        title={n.createdAt && !isNaN(new Date(n.createdAt).getTime()) ? new Date(n.createdAt).toLocaleString() : ''}
                      >
                        {formatRelativeTime(n.createdAt)}
                      </span>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      )}
    </div>
  );
}
