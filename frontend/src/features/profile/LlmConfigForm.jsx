import {
  Key,
  Cpu,
  Globe,
  CheckCircle2,
  AlertCircle,
  Eye,
  EyeOff,
  ChevronDown,
  ChevronUp,
  Loader2,
  ExternalLink,
  ShieldCheck,
} from 'lucide-react';
import { useLlmConfigForm } from './hooks/useLlmConfigForm';
import { ProviderSelector } from './components/ProviderSelector';
import { SystemOneSettings } from './components/SystemOneSettings';

/**
 * LlmConfigForm manages user LLM provider credentials and model parameters.
 * Refactored to strictly adhere to Single Responsibility Principle (SRP).
 */
export function LlmConfigForm({ api, onSuccess, isOnboarding = false }) {
  const {
    loading,
    saving,
    testing,
    testingJev,
    testResult,
    jevTestResult,
    error,
    provider,
    baseUrl,
    setBaseUrl,
    apiKey,
    setApiKey,
    showKey,
    setShowKey,
    existingMaskedKey,
    isReplacingKey,
    setIsReplacingKey,
    selectedPreset,
    setSelectedPreset,
    customModel,
    setCustomModel,
    supervisorModel,
    setSupervisorModel,
    showAdvanced,
    setShowAdvanced,
    jevEnabled,
    setJevEnabled,
    jevModel,
    setJevModel,
    useCustomJevCredentials,
    setUseCustomJevCredentials,
    jevBaseUrl,
    setJevBaseUrl,
    jevApiKey,
    setJevApiKey,
    showJevKey,
    setShowJevKey,
    existingMaskedJevKey,
    isReplacingJevKey,
    setIsReplacingJevKey,
    activeModelName,
    currentPresets,
    currentProviderMeta,
    handleProviderChange,
    handleTestConnection,
    handleTestJevConnection,
    handleSubmit,
  } = useLlmConfigForm(api, onSuccess);

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center p-8 text-center text-slate-400">
        <Loader2 className="h-8 w-8 animate-spin text-cyan-400 mb-2" />
        <p className="text-xs">Loading LLM credentials configuration...</p>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-5">
      {error && (
        <div className="flex items-start gap-2.5 rounded-xl border border-rose-500/30 bg-rose-500/10 p-3 text-xs text-rose-300">
          <AlertCircle size={16} className="shrink-0 mt-0.5 text-rose-400" />
          <div className="flex-1 font-medium">{error}</div>
        </div>
      )}

      {/* Provider Selector Grid */}
      <ProviderSelector
        provider={provider}
        onProviderChange={handleProviderChange}
      />

      {/* Base URL */}
      <div>
        <div className="flex items-center justify-between mb-1.5">
          <label className="block text-xs font-semibold text-slate-300">
            API Base URL
          </label>
          <span className="text-[10px] text-slate-500 font-mono">
            {provider === 'OPENROUTER' ? 'OpenRouter Gateway' : 'OpenAI-Compatible Endpoint'}
          </span>
        </div>
        <div className="relative">
          <Globe size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
          <input
            type="url"
            required
            value={baseUrl}
            onChange={(e) => setBaseUrl(e.target.value)}
            placeholder="https://api.openai.com/v1"
            className="w-full rounded-xl border border-slate-800 bg-slate-950/80 py-2 pl-9 pr-3 text-xs text-slate-200 placeholder-slate-600 focus:border-cyan-500 focus:outline-none focus:ring-1 focus:ring-cyan-500 font-mono"
          />
        </div>
        <p className="mt-1 text-[11px] text-slate-500">
          Endpoint URL conforming to OpenAI API specifications.
        </p>
      </div>

      {/* API Key */}
      <div>
        <div className="flex items-center justify-between mb-1.5">
          <label className="block text-xs font-semibold text-slate-300">
            API Key
          </label>
          {provider === 'OPENROUTER' && (
            <a
              href="https://openrouter.ai/keys"
              target="_blank"
              rel="noreferrer"
              className="flex items-center gap-1 text-[11px] text-cyan-400 hover:underline"
            >
              Get OpenRouter key <ExternalLink size={10} />
            </a>
          )}
        </div>

        {existingMaskedKey && !isReplacingKey ? (
          <div className="flex items-center justify-between rounded-xl border border-slate-800 bg-slate-900/60 px-3.5 py-2.5">
            <div className="flex items-center gap-2">
              <ShieldCheck size={16} className="text-emerald-400" />
              <div>
                <p className="text-xs font-mono font-medium text-slate-200">{existingMaskedKey}</p>
                <p className="text-[10px] text-slate-400">Encrypted AES-GCM credential active</p>
              </div>
            </div>
            <button
              type="button"
              onClick={() => { setIsReplacingKey(true); setApiKey(''); }}
              className="rounded-lg border border-slate-700 bg-slate-800 px-2.5 py-1 text-[11px] font-semibold text-slate-300 hover:bg-slate-700 hover:text-white transition"
            >
              Change Key
            </button>
          </div>
        ) : (
          <div className="space-y-1.5">
            <div className="relative">
              <Key size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
              <input
                type={showKey ? 'text' : 'password'}
                required={!existingMaskedKey}
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder={currentProviderMeta.keyPlaceholder || 'sk-...'}
                className="w-full rounded-xl border border-slate-800 bg-slate-950/80 py-2 pl-9 pr-10 text-xs text-slate-200 placeholder-slate-600 focus:border-cyan-500 focus:outline-none focus:ring-1 focus:ring-cyan-500 font-mono"
              />
              <button
                type="button"
                onClick={() => setShowKey(!showKey)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300"
                aria-label={showKey ? 'Hide key' : 'Show key'}
              >
                {showKey ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>
            {existingMaskedKey && isReplacingKey && (
              <div className="flex items-center justify-between text-[11px] text-slate-400">
                <span>Leave blank to keep existing key</span>
                <button
                  type="button"
                  onClick={() => { setIsReplacingKey(false); setApiKey(''); }}
                  className="text-cyan-400 hover:underline"
                >
                  Cancel change
                </button>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Primary Model Selection */}
      <div>
        <label className="block text-xs font-semibold text-slate-300 mb-1.5">
          Primary Model
        </label>
        <div className="space-y-2">
          <select
            value={selectedPreset}
            onChange={(e) => {
              setSelectedPreset(e.target.value);
            }}
            className="w-full rounded-xl border border-slate-800 bg-slate-950 py-2 px-3 text-xs text-slate-200 focus:border-cyan-500 focus:outline-none focus:ring-1 focus:ring-cyan-500"
          >
            {currentPresets.map(preset => (
              <option key={preset.id} value={preset.id}>
                {preset.label} ({preset.id})
              </option>
            ))}
            <option value="custom">Custom Model (specify ID)...</option>
          </select>

          {selectedPreset === 'custom' && (
            <div className="relative">
              <Cpu size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
              <input
                type="text"
                required
                value={customModel}
                onChange={(e) => setCustomModel(e.target.value)}
                placeholder="e.g. meta-llama/llama-3.3-70b-instruct"
                className="w-full rounded-xl border border-slate-800 bg-slate-950/80 py-2 pl-9 pr-3 text-xs text-slate-200 placeholder-slate-600 focus:border-cyan-500 focus:outline-none focus:ring-1 focus:ring-cyan-500 font-mono"
              />
            </div>
          )}
        </div>
        <p className="mt-1 text-[11px] text-slate-500">
          Used across vulnerability triage, intelligent chat, and multi-stage autonomous remediation.
        </p>
      </div>

      {/* SystemOne Fast Decision Engine (TypeSafe Jev) */}
      <SystemOneSettings
        jevEnabled={jevEnabled}
        onJevEnabledChange={setJevEnabled}
        jevModel={jevModel}
        onJevModelChange={setJevModel}
        useCustomJevCredentials={useCustomJevCredentials}
        onUseCustomJevCredentialsChange={setUseCustomJevCredentials}
        jevBaseUrl={jevBaseUrl}
        onJevBaseUrlChange={setJevBaseUrl}
        existingMaskedJevKey={existingMaskedJevKey}
        isReplacingJevKey={isReplacingJevKey}
        onStartReplacingJevKey={() => { setIsReplacingJevKey(true); setJevApiKey(''); }}
        jevApiKey={jevApiKey}
        onJevApiKeyChange={setJevApiKey}
        showJevKey={showJevKey}
        onToggleShowJevKey={() => setShowJevKey(!showJevKey)}
        onTestJevConnection={handleTestJevConnection}
        testingJev={testingJev}
        saving={saving}
        jevTestResult={jevTestResult}
      />

      {/* Collapsible Advanced: Supervisor Model */}
      <div className="rounded-xl border border-slate-800/80 bg-slate-900/30 overflow-hidden">
        <button
          type="button"
          onClick={() => setShowAdvanced(!showAdvanced)}
          className="flex w-full items-center justify-between px-3.5 py-2.5 text-left text-xs font-medium text-slate-400 hover:text-slate-200 transition"
        >
          <span>Advanced Orchestration Settings</span>
          {showAdvanced ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
        </button>

        {showAdvanced && (
          <div className="p-3.5 pt-0 border-t border-slate-800/60 space-y-2 mt-2">
            <label className="block text-[11px] font-semibold text-slate-400">
              Supervisor Model (Optional)
            </label>
            <input
              type="text"
              value={supervisorModel}
              onChange={(e) => setSupervisorModel(e.target.value)}
              placeholder={`Defaults to primary model (${activeModelName || 'same as above'})`}
              className="w-full rounded-xl border border-slate-800 bg-slate-950/80 py-1.5 px-3 text-xs text-slate-200 placeholder-slate-600 focus:border-cyan-500 focus:outline-none font-mono"
            />
            <p className="text-[10px] text-slate-500">
              Specialized supervisor router agent in the 15-stage remediation graph. Leave empty to use primary model.
            </p>
          </div>
        )}
      </div>

      {/* Connection Test Result Feedback */}
      {testResult && (
        <div
          className={`flex items-start gap-2.5 rounded-xl border p-3 text-xs transition ${
            testResult.success
              ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300'
              : 'border-rose-500/30 bg-rose-500/10 text-rose-300'
          }`}
        >
          {testResult.success ? (
            <CheckCircle2 size={16} className="shrink-0 mt-0.5 text-emerald-400" />
          ) : (
            <AlertCircle size={16} className="shrink-0 mt-0.5 text-rose-400" />
          )}
          <div className="flex-1 font-medium">{testResult.message}</div>
        </div>
      )}

      {/* Actions */}
      <div className="flex items-center justify-end gap-2.5 pt-2 border-t border-slate-800">
        <button
          type="button"
          onClick={handleTestConnection}
          disabled={testing || saving}
          className="flex items-center gap-1.5 rounded-xl border border-slate-700 bg-slate-800/80 px-3.5 py-2 text-xs font-semibold text-slate-200 hover:bg-slate-700 hover:text-white transition disabled:opacity-50"
        >
          {testing && <Loader2 size={13} className="animate-spin text-cyan-400" />}
          <span>{testing ? 'Testing...' : 'Test Connection'}</span>
        </button>

        <button
          type="submit"
          disabled={saving || testing}
          className="flex items-center gap-1.5 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 px-4 py-2 text-xs font-semibold text-white shadow-lg shadow-cyan-500/20 hover:from-cyan-400 hover:to-blue-500 transition disabled:opacity-50"
        >
          {saving && <Loader2 size={13} className="animate-spin text-white" />}
          <span>{saving ? 'Saving...' : (isOnboarding ? 'Save & Start Auditing' : 'Save Credentials')}</span>
        </button>
      </div>
    </form>
  );
}
