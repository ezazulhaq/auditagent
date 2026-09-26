import { CheckCircle2, AlertCircle, Info, X } from 'lucide-react';
import { createContext, useContext, useState, useCallback } from 'react';
import { auditApi } from '../api/auditApi';

const ToastContext = createContext(null);

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const addToast = useCallback((message, type = 'info', duration = 5000) => {
    const id = Date.now().toString() + Math.random().toString(36).substring(2, 9);
    setToasts(current => [...current, { id, message, type }]);
    
    if (duration > 0) {
      setTimeout(() => {
        setToasts(current => current.filter(t => t.id !== id));
      }, duration);
    }
  }, []);

  const removeToast = useCallback((id) => {
    setToasts(current => current.filter(t => t.id !== id));
  }, []);

  const success = useCallback((message, duration) => addToast(message, 'success', duration), [addToast]);
  const error = useCallback((message, duration) => addToast(message, 'error', duration), [addToast]);
  const warning = useCallback((message, duration) => addToast(message, 'warning', duration), [addToast]);
  const info = useCallback((message, duration) => addToast(message, 'info', duration), [addToast]);

  return (
    <ToastContext.Provider value={{ addToast, removeToast, success, error, warning, info }}>
      {children}
      <div className="fixed bottom-5 left-1/2 -translate-x-1/2 z-[100] flex flex-col gap-2 pointer-events-none">
        {toasts.map(toast => (
          <div key={toast.id} className="pointer-events-auto flex items-center gap-3 bg-slate-900 border border-slate-700/50 rounded-xl px-4 py-3 shadow-2xl animate-in slide-in-from-bottom-5 fade-in duration-300 max-w-[90vw] md:max-w-[400px]">
            {toast.type === 'success' && <CheckCircle2 size={18} className="text-emerald-400 shrink-0" />}
            {toast.type === 'error' && <AlertCircle size={18} className="text-rose-400 shrink-0" />}
            {toast.type === 'warning' && <AlertCircle size={18} className="text-amber-400 shrink-0" />}
            {toast.type === 'info' && <Info size={18} className="text-cyan-400 shrink-0" />}
            <span className="text-sm font-medium text-slate-200 flex-1">{toast.message}</span>
            <button onClick={() => removeToast(toast.id)} className="ml-2 shrink-0 text-slate-500 hover:text-slate-300 transition">
              <X size={16} />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) throw new Error('useToast must be used within a ToastProvider');
  return context;
}
