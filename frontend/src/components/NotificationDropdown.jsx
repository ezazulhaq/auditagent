import { Bell, CheckCircle2, AlertCircle, Info, X } from 'lucide-react';
import { useState, useEffect, useRef } from 'react';
import { auditApi } from '../api/auditApi';

export function NotificationDropdown() {
  const [isOpen, setIsOpen] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const dropdownRef = useRef(null);

  const unreadCount = notifications.filter(n => !n.read).length;

  const fetchNotifications = async () => {
    try {
      const data = await auditApi.getNotifications();
      setNotifications(data);
    } catch (e) {
      console.error('Failed to fetch notifications', e);
    }
  };

  useEffect(() => {
    fetchNotifications();

    const handleNew = () => {
      fetchNotifications();
    };

    window.addEventListener('notification-added', handleNew);
    return () => window.removeEventListener('notification-added', handleNew);
  }, []);

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
      auditApi.markNotificationsRead().then(() => {
        setNotifications(current => current.map(n => ({ ...n, read: true })));
      }).catch(console.error);
    }
  };

  return (
    <div className="relative" ref={dropdownRef}>
      <button
        type="button"
        onClick={handleOpen}
        title="Notifications"
        aria-label="Notifications"
        className="relative ml-0.5 rounded-lg border border-transparent p-2 text-slate-500 transition hover:border-slate-700/60 hover:bg-slate-800/70 hover:text-white"
      >
        <Bell size={16} />
        {unreadCount > 0 && (
          <span className="absolute top-1.5 right-1.5 flex h-2 w-2 rounded-full bg-rose-500"></span>
        )}
      </button>

      {isOpen && (
        <div className="absolute right-0 mt-2 w-80 sm:w-96 rounded-xl border border-slate-700/60 bg-[#0a1421] shadow-2xl z-50 overflow-hidden flex flex-col max-h-[70vh]">
          <div className="flex items-center justify-between border-b border-slate-700/60 px-4 py-3 bg-slate-800/30">
            <h3 className="text-sm font-semibold text-slate-200">Notifications</h3>
            <button onClick={() => setIsOpen(false)} className="text-slate-500 hover:text-slate-300">
              <X size={16} />
            </button>
          </div>
          <div className="overflow-y-auto flex-1 p-2 space-y-1">
            {notifications.length === 0 ? (
              <p className="text-center text-xs text-slate-500 py-6">No notifications</p>
            ) : (
              notifications.map(n => (
                <div key={n.id} className="flex gap-3 items-start p-3 hover:bg-slate-800/50 rounded-lg transition">
                  {n.type === 'success' && <CheckCircle2 size={16} className="text-emerald-400 shrink-0 mt-0.5" />}
                  {n.type === 'error' && <AlertCircle size={16} className="text-rose-400 shrink-0 mt-0.5" />}
                  {n.type === 'warning' && <AlertCircle size={16} className="text-amber-400 shrink-0 mt-0.5" />}
                  {n.type === 'info' && <Info size={16} className="text-cyan-400 shrink-0 mt-0.5" />}
                  <div className="flex flex-col min-w-0">
                    <span className="text-sm text-slate-200 break-words">{n.message}</span>
                    <span className="text-[10px] text-slate-500 mt-1">
                      {new Date(n.createdAt).toLocaleString()}
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
