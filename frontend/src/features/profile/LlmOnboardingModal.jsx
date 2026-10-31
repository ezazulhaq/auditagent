import { KeyRound, Shield, LogOut } from 'lucide-react';
import { LlmConfigForm } from './LlmConfigForm';

export function LlmOnboardingModal({ api, onComplete, onLogout }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md animate-in fade-in duration-200">
      <div className="relative w-full max-w-xl rounded-2xl border border-cyan-500/30 bg-slate-900 shadow-2xl shadow-cyan-950/50 p-6 md:p-7 overflow-hidden">
        {/* Decorative background glow */}
        <div className="absolute -top-24 -right-24 w-48 h-48 bg-cyan-500/10 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute -bottom-24 -left-24 w-48 h-48 bg-blue-500/10 rounded-full blur-3xl pointer-events-none" />

        {/* Modal Header */}
        <div className="flex items-start justify-between gap-4 mb-5">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-xl border border-cyan-400/30 bg-cyan-500/10 text-cyan-400">
              <KeyRound size={22} />
            </div>
            <div>
              <h2 className="text-base font-bold text-white tracking-tight">
                Connect AI Provider Credentials
              </h2>
              <p className="text-xs text-slate-400 mt-0.5">
                AuditAgent requires an API key to enable autonomous vulnerability fixing and chat triage.
              </p>
            </div>
          </div>

          {onLogout && (
            <button
              type="button"
              onClick={onLogout}
              className="flex items-center gap-1.5 rounded-lg border border-slate-800 bg-slate-850 px-2.5 py-1 text-xs text-slate-400 hover:text-slate-200 hover:border-slate-700 transition"
              title="Sign out of GitHub session"
            >
              <LogOut size={13} />
              <span>Log out</span>
            </button>
          )}
        </div>

        {/* Info callout */}
        <div className="mb-5 flex items-center gap-2.5 rounded-xl border border-cyan-500/20 bg-cyan-500/5 px-3.5 py-2.5 text-xs text-cyan-200/90">
          <Shield size={16} className="text-cyan-400 shrink-0" />
          <span>Your credentials are encrypted at rest with AES-GCM and never shared or logged.</span>
        </div>

        {/* Form */}
        <LlmConfigForm
          api={api}
          isOnboarding={true}
          onSuccess={onComplete}
        />
      </div>
    </div>
  );
}
