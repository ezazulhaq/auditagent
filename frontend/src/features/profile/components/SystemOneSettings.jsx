import { memo } from 'react';
import { Zap, Cpu, Globe, Key, Eye, EyeOff, Loader2, CheckCircle2, AlertCircle } from 'lucide-react';

/**
 * SystemOneSettings renders the TypeSafe Jev Non-Autoregressive Decision Engine configuration.
 * Adheres to Single Responsibility Principle (SRP).
 */
export const SystemOneSettings = memo(function SystemOneSettings({
  jevEnabled,
  onJevEnabledChange,
  jevModel,
  onJevModelChange,
  useCustomJevCredentials,
  onUseCustomJevCredentialsChange,
  jevBaseUrl,
  onJevBaseUrlChange,
  existingMaskedJevKey,
  isReplacingJevKey,
  onStartReplacingJevKey,
  jevApiKey,
  onJevApiKeyChange,
  showJevKey,
  onToggleShowJevKey,
  onTestJevConnection,
  testingJev,
  saving,
  jevTestResult,
}) {
  return (
    <div className="rounded-2xl border border-cyan-500/30 bg-slate-900/60 p-4 space-y-3.5">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2.5">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg border border-cyan-500/40 bg-cyan-500/10 text-cyan-400">
            <Zap size={16} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h3 className="text-xs font-bold text-white tracking-tight">SystemOne Decision Engine (TypeSafe Jev)</h3>
              <span className="rounded-full bg-cyan-500/10 border border-cyan-500/30 px-2 py-0.5 text-[10px] font-semibold text-cyan-300">
                ~98% Token Reduction
              </span>
            </div>
            <p className="text-[11px] text-slate-400 mt-0.5">
              Non-autoregressive model for sub-2s finding triage and supervisor routing.
            </p>
          </div>
        </div>

        <label className="relative inline-flex items-center cursor-pointer">
          <input
            type="checkbox"
            checked={jevEnabled}
            onChange={(e) => onJevEnabledChange(e.target.checked)}
            className="sr-only peer"
          />
          <div className="w-9 h-5 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-cyan-500" />
        </label>
      </div>

      {jevEnabled && (
        <div className="space-y-3 pt-2 border-t border-slate-800/80">
          <div>
            <label className="block text-[11px] font-semibold text-slate-300 mb-1">
              SystemOne Model ID
            </label>
            <div className="relative">
              <Cpu size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
              <input
                type="text"
                value={jevModel}
                onChange={(e) => onJevModelChange(e.target.value)}
                placeholder="typesafe/jev-1.14"
                className="w-full rounded-xl border border-slate-800 bg-slate-950/80 py-1.5 pl-8 pr-3 text-xs text-slate-200 placeholder-slate-600 focus:border-cyan-500 focus:outline-none font-mono"
              />
            </div>
            <p className="mt-1 text-[10px] text-slate-500">
              Defaults to <code className="text-cyan-400 font-mono">typesafe/jev-1.14</code> via OpenRouter <code className="font-mono">/api/v1/systemone</code>.
            </p>
          </div>

          <div className="space-y-1.5">
            <label className="block text-[11px] font-semibold text-slate-300">
              Credentials &amp; Endpoint
            </label>
            <div className="flex flex-col sm:flex-row gap-2 text-xs">
              <label className="flex items-center gap-1.5 text-slate-300 cursor-pointer">
                <input
                  type="radio"
                  name="jevCreds"
                  checked={!useCustomJevCredentials}
                  onChange={() => onUseCustomJevCredentialsChange(false)}
                  className="text-cyan-500 focus:ring-cyan-500 bg-slate-950 border-slate-700"
                />
                <span>Inherit Primary Key &amp; URL</span>
              </label>
              <label className="flex items-center gap-1.5 text-slate-300 cursor-pointer">
                <input
                  type="radio"
                  name="jevCreds"
                  checked={useCustomJevCredentials}
                  onChange={() => onUseCustomJevCredentialsChange(true)}
                  className="text-cyan-500 focus:ring-cyan-500 bg-slate-950 border-slate-700"
                />
                <span>Custom SystemOne Endpoint / Key</span>
              </label>
            </div>
          </div>

          {useCustomJevCredentials && (
            <div className="space-y-2.5 p-3 rounded-xl border border-slate-800 bg-slate-950/60">
              <div>
                <label className="block text-[10px] font-semibold text-slate-400 mb-1">
                  SystemOne Base URL
                </label>
                <div className="relative">
                  <Globe size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-500" />
                  <input
                    type="url"
                    value={jevBaseUrl}
                    onChange={(e) => onJevBaseUrlChange(e.target.value)}
                    placeholder="https://openrouter.ai/api/v1"
                    className="w-full rounded-lg border border-slate-800 bg-slate-900 py-1.5 pl-7 pr-3 text-xs text-slate-200 placeholder-slate-600 focus:border-cyan-500 focus:outline-none font-mono"
                  />
                </div>
              </div>

              <div>
                <label className="block text-[10px] font-semibold text-slate-400 mb-1">
                  Dedicated SystemOne API Key
                </label>
                {existingMaskedJevKey && !isReplacingJevKey ? (
                  <div className="flex items-center justify-between rounded-lg border border-slate-800 bg-slate-900 px-3 py-1.5">
                    <span className="text-xs font-mono text-slate-200">{existingMaskedJevKey}</span>
                    <button
                      type="button"
                      onClick={onStartReplacingJevKey}
                      className="text-[10px] text-cyan-400 hover:underline"
                    >
                      Change
                    </button>
                  </div>
                ) : (
                  <div className="relative">
                    <Key size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-500" />
                    <input
                      type={showJevKey ? 'text' : 'password'}
                      value={jevApiKey}
                      onChange={(e) => onJevApiKeyChange(e.target.value)}
                      placeholder="Dedicated API key..."
                      className="w-full rounded-lg border border-slate-800 bg-slate-900 py-1.5 pl-7 pr-8 text-xs text-slate-200 placeholder-slate-600 focus:border-cyan-500 focus:outline-none font-mono"
                    />
                    <button
                      type="button"
                      onClick={onToggleShowJevKey}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300"
                    >
                      {showJevKey ? <EyeOff size={13} /> : <Eye size={13} />}
                    </button>
                  </div>
                )}
              </div>
            </div>
          )}

          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pt-1">
            <button
              type="button"
              onClick={onTestJevConnection}
              disabled={testingJev || saving}
              className="flex items-center justify-center gap-1.5 rounded-lg border border-cyan-500/40 bg-cyan-500/10 px-3 py-1.5 text-[11px] font-semibold text-cyan-300 hover:bg-cyan-500/20 transition disabled:opacity-50"
            >
              {testingJev ? <Loader2 size={12} className="animate-spin text-cyan-400" /> : <Zap size={12} />}
              <span>{testingJev ? 'Testing SystemOne...' : 'Test SystemOne Connection'}</span>
            </button>

            {jevTestResult && (
              <div className="flex items-center gap-1.5 text-[11px] font-medium">
                {jevTestResult.success ? (
                  <span className="flex items-center gap-1 text-emerald-400">
                    <CheckCircle2 size={13} /> {jevTestResult.message}
                  </span>
                ) : (
                  <span className="flex items-center gap-1 text-rose-400">
                    <AlertCircle size={13} /> {jevTestResult.message}
                  </span>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
});
