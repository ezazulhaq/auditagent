import { useEffect } from 'react';
import { X, Sparkles } from 'lucide-react';
import { LlmConfigForm } from './LlmConfigForm';

export function UserProfileModal({ isOpen, onClose, user, api, toast }) {
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && isOpen) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const handleSuccess = () => {
    if (toast?.success) {
      toast.success('AI Provider credentials updated successfully');
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-in fade-in duration-150"
      onClick={onClose}
    >
      <div
        className="relative w-full max-w-xl max-h-[90vh] overflow-y-auto rounded-2xl border border-slate-800 bg-slate-900 shadow-2xl p-6 md:p-7 space-y-6"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-slate-800">
          <div className="flex items-center gap-3">
            {user?.avatarUrl ? (
              <img
                src={user.avatarUrl}
                alt=""
                className="h-10 w-10 rounded-xl border border-slate-700 object-cover"
              />
            ) : (
              <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-cyan-500/30 bg-cyan-500/10 text-cyan-400 font-bold text-sm">
                {(user?.login || 'U').slice(0, 1).toUpperCase()}
              </div>
            )}
            <div>
              <h2 className="text-sm font-bold text-white tracking-tight">
                {user?.name || user?.login || 'User Profile'}
              </h2>
              <p className="text-xs font-mono text-slate-400">
                {user?.login ? `@${user.login}` : 'Auditor'}
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-800 hover:text-slate-200 transition"
            aria-label="Close modal"
          >
            <X size={18} />
          </button>
        </div>

        {/* Section info */}
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-cyan-400 mb-1">
            <Sparkles size={14} />
            <span>AI Provider & Key Management</span>
          </div>
          <p className="text-xs text-slate-400">
            Configure your OpenRouter or OpenAI-compatible API credentials to power intelligent code scans, triage chat, and auto-remediation.
          </p>
        </div>

        {/* Credentials Form */}
        <LlmConfigForm
          api={api}
          isOnboarding={false}
          onSuccess={handleSuccess}
        />
      </div>
    </div>
  );
}
