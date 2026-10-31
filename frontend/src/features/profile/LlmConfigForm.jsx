import { useState, useEffect } from 'react';
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
  Sparkles
} from 'lucide-react';

const POPULAR_PRESETS = [
  { id: 'anthropic/claude-3.5-sonnet', label: 'Anthropic Claude 3.5 Sonnet', tag: 'Recommended' },
  { id: 'openai/gpt-4o', label: 'OpenAI GPT-4o', tag: 'High Intelligence' },
  { id: 'openai/gpt-4o-mini', label: 'OpenAI GPT-4o-mini', tag: 'Fast & Lightweight' },
  { id: 'google/gemini-2.5-flash', label: 'Google Gemini 2.5 Flash', tag: 'Ultra Fast' },
  { id: 'z-ai/glm-5.3-flash', label: 'Z-AI GLM 5.3 Flash', tag: 'Balanced' },
  { id: 'deepseek/deepseek-chat', label: 'DeepSeek Chat', tag: 'Cost Effective' },
];

export function LlmConfigForm({ api, onSuccess, isOnboarding = false }) {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState(null); // { success: boolean, message: string }
  const [error, setError] = useState('');

  const [provider, setProvider] = useState('OPENROUTER');
  const [baseUrl, setBaseUrl] = useState('https://openrouter.ai/api/v1');
  const [apiKey, setApiKey] = useState('');
  const [showKey, setShowKey] = useState(false);
  const [existingMaskedKey, setExistingMaskedKey] = useState('');
  const [isReplacingKey, setIsReplacingKey] = useState(false);

  const [selectedPreset, setSelectedPreset] = useState('anthropic/claude-3.5-sonnet');
  const [customModel, setCustomModel] = useState('');
  const [supervisorModel, setSupervisorModel] = useState('');
  const [showAdvanced, setShowAdvanced] = useState(false);

  useEffect(() => {
    let active = true;
    const controller = new AbortController();

    api.getUserLlmConfig(controller.signal)
      .then(config => {
        if (!active) return;
        if (config.configured) {
          setProvider(config.provider || 'OPENROUTER');
          setBaseUrl(config.baseUrl || 'https://openrouter.ai/api/v1');
          setExistingMaskedKey(config.maskedApiKey || '');
          setIsReplacingKey(false);

          const matchingPreset = POPULAR_PRESETS.find(p => p.id === config.modelName);
          if (matchingPreset) {
            setSelectedPreset(config.modelName);
            setCustomModel('');
          } else {
            setSelectedPreset('custom');
            setCustomModel(config.modelName || '');
          }

          setSupervisorModel(config.supervisorModelName || '');
        } else {
          setExistingMaskedKey('');
          setIsReplacingKey(true);
        }
      })
      .catch(err => {
        if (active && err.name !== 'AbortError') {
          setError(err.message || 'Failed to load configuration');
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [api]);

  const activeModelName = selectedPreset === 'custom' ? customModel.trim() : selectedPreset;

  const handleProviderChange = (newProvider) => {
    setProvider(newProvider);
    setTestResult(null);
    setError('');
    if (newProvider === 'OPENROUTER') {
      setBaseUrl('https://openrouter.ai/api/v1');
    } else if (baseUrl === 'https://openrouter.ai/api/v1') {
      setBaseUrl('https://api.openai.com/v1');
    }
  };

  const handleTestConnection = async () => {
    setError('');
    setTestResult(null);

    if (!activeModelName) {
      setError('Please select or specify a model name to test');
      return;
    }

    if (isReplacingKey && !apiKey.trim() && !existingMaskedKey) {
      setError('Please provide an API Key to test');
      return;
    }

    setTesting(true);
    try {
      const payload = {
        provider,
        baseUrl: baseUrl.trim(),
        apiKey: apiKey.trim() || undefined,
        modelName: activeModelName,
      };

      const result = await api.testUserLlmConfig(payload);
      setTestResult(result);
    } catch (err) {
      setTestResult({
        success: false,
        message: err.message || 'Connection test failed',
      });
    } finally {
      setTesting(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!activeModelName) {
      setError('Please select or enter a model name');
      return;
    }

    if (isReplacingKey && !apiKey.trim() && !existingMaskedKey) {
      setError('Please provide an API Key');
      return;
    }

    setSaving(true);
    try {
      const payload = {
        provider,
        baseUrl: baseUrl.trim(),
        apiKey: apiKey.trim() || undefined,
        modelName: activeModelName,
        supervisorModelName: supervisorModel.trim() || undefined,
      };

      const saved = await api.saveUserLlmConfig(payload);
      setTestResult({ success: true, message: 'Settings saved successfully!' });
      if (onSuccess) {
        onSuccess(saved);
      }
    } catch (err) {
      setError(err.message || 'Failed to save configuration');
    } finally {
      setSaving(false);
    }
  };

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

      {/* Provider Selector */}
      <div>
        <label className="block text-xs font-semibold text-slate-300 mb-2">
          LLM Provider
        </label>
        <div className="grid grid-cols-2 gap-2.5">
          <button
            type="button"
            onClick={() => handleProviderChange('OPENROUTER')}
            className={`flex flex-col items-start p-3 rounded-xl border text-left transition ${
              provider === 'OPENROUTER'
                ? 'border-cyan-500/60 bg-cyan-500/10 ring-1 ring-cyan-500/30 text-white'
                : 'border-slate-800 bg-slate-900/60 text-slate-400 hover:border-slate-700 hover:text-slate-200'
            }`}
          >
            <div className="flex items-center gap-2 font-semibold text-xs text-cyan-400">
              <Sparkles size={14} />
              <span>OpenRouter (Default)</span>
            </div>
            <p className="text-[11px] text-slate-400 mt-1 line-clamp-2">
              Access Claude 3.5, GPT-4o, Gemini 2.5 & DeepSeek with one API key.
            </p>
          </button>

          <button
            type="button"
            onClick={() => handleProviderChange('OPENAI_COMPATIBLE')}
            className={`flex flex-col items-start p-3 rounded-xl border text-left transition ${
              provider === 'OPENAI_COMPATIBLE'
                ? 'border-cyan-500/60 bg-cyan-500/10 ring-1 ring-cyan-500/30 text-white'
                : 'border-slate-800 bg-slate-900/60 text-slate-400 hover:border-slate-700 hover:text-slate-200'
            }`}
          >
            <div className="flex items-center gap-2 font-semibold text-xs text-cyan-400">
              <Globe size={14} />
              <span>OpenAI Compatible</span>
            </div>
            <p className="text-[11px] text-slate-400 mt-1 line-clamp-2">
              Custom API URL for OpenAI, Ollama, vLLM, LM Studio or Groq.
            </p>
          </button>
        </div>
      </div>

      {/* Base URL (Editable for OpenAI compatible, prefilled for OpenRouter) */}
      {provider === 'OPENAI_COMPATIBLE' && (
        <div>
          <label className="block text-xs font-semibold text-slate-300 mb-1.5">
            API Base URL
          </label>
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
      )}

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
                placeholder={provider === 'OPENROUTER' ? 'sk-or-v1-...' : 'sk-...'}
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
              setTestResult(null);
            }}
            className="w-full rounded-xl border border-slate-800 bg-slate-950 py-2 px-3 text-xs text-slate-200 focus:border-cyan-500 focus:outline-none focus:ring-1 focus:ring-cyan-500"
          >
            {POPULAR_PRESETS.map(preset => (
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
