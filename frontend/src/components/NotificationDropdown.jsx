import { Bell, CheckCircle2, AlertCircle, Info, X } from 'lucide-react';
import { useState, useEffect, useRef } from 'react';
import { auditApi } from '../api/auditApi';

export function NotificationDropdown({ api = auditApi }) {
  const [isOpen, setIsOpen] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const dropdownRef = useRef(null);

  const unreadCount = notifications.filter(n => !n.read).length;

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

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleOpen = () => {
    setIsOpen(!isOpen);
    if (!isOpen && unreadCount > 0) {
      api?.markNotificationsRead?.()
        ?.then?.(() => {
          setNotifications(current => current.map(n => ({ ...n, read: true })));
        })
        ?.catch?.(console.error);
    }
  };

  return (
    <div className="relative" ref={dropdownRef}>
      <button
        type="button"
        onClick={handleOpen}
        title="Notifications"
        aria-label="Notifications"
        className="relative ml-0.5 rounded-xl border border-transparent p-2 text-[var(--text-muted)] transition hover:border-[var(--border-subtle)] hover:bg-[var(--surface-inset)]/70 hover:text-[var(--text-primary)]"
      >
        <Bell size={16} />
        {unreadCount > 0 && (
          <span className="absolute top-1.5 right-1.5 flex h-2 w-2 rounded-full bg-rose-500"></span>
        )}
      </button>

      {isOpen && (
        <div className="absolute right-0 mt-2 w-80 sm:w-96 rounded-2xl border border-[var(--border-subtle)] bg-[var(--surface-card)] shadow-2xl shadow-slate-900/10 dark:shadow-black/50 backdrop-blur-xl z-50 overflow-hidden flex flex-col max-h-[70vh]">
          <div className="flex items-center justify-between border-b border-[var(--border-subtle)] px-4 py-3 bg-[var(--surface-inset)]/60">
            <div className="flex items-center gap-2">
              <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--text-primary)]">Notifications</h3>
              {unreadCount > 0 && (
                <span className="rounded-full bg-rose-500/20 px-2 py-0.5 text-[10px] font-semibold text-rose-400 font-mono">
                  {unreadCount} new
                </span>
              )}
            </div>
            <button onClick={() => setIsOpen(false)} aria-label="Close notifications" className="rounded-lg p-1 text-[var(--text-muted)] hover:text-[var(--text-primary)] hover:bg-[var(--surface-inset)] transition">
              <X size={15} />
            </button>
          </div>
          <div className="overflow-y-auto flex-1 p-2 space-y-1">
            {notifications.length === 0 ? (
              <p className="text-center text-xs text-[var(--text-muted)] py-8">No notifications</p>
            ) : (
              notifications.map((n, idx) => (
                <div key={n.id || `${n.message}-${idx}`} className={`flex gap-3 items-start p-2.5 hover:bg-[var(--surface-inset)] rounded-xl transition ${!n.read ? 'bg-[var(--surface-inset)]/40' : ''}`}>
                  {n.type === 'success' && <CheckCircle2 size={16} className="text-emerald-500 shrink-0 mt-0.5" />}
                  {n.type === 'error' && <AlertCircle size={16} className="text-rose-500 shrink-0 mt-0.5" />}
                  {n.type === 'warning' && <AlertCircle size={16} className="text-amber-500 shrink-0 mt-0.5" />}
                  {(!n.type || n.type === 'info') && <Info size={16} className="text-cyan-500 shrink-0 mt-0.5" />}
                  <div className="flex flex-col min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-xs font-medium text-[var(--text-primary)] break-words">{n.message}</span>
                      {!n.read && (
                        <span className="h-1.5 w-1.5 rounded-full bg-cyan-400 shrink-0" title="Unread" />
                      )}
                    </div>
                    <span className="text-[10px] text-[var(--text-muted)] font-mono mt-1">
                      {n.createdAt && !isNaN(new Date(n.createdAt).getTime())
                        ? new Date(n.createdAt).toLocaleString()
                        : 'Just now'}
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
