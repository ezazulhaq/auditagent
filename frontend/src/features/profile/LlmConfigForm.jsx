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
  Sparkles,
  Zap,
} from 'lucide-react';

const PROVIDERS = [
  {
    id: 'OPENROUTER',
    label: 'OpenRouter (Default)',
    subtitle: 'Claude, GPT-4o, Gemini & DeepSeek with one API key',
    defaultUrl: 'https://openrouter.ai/api/v1',
    keyPlaceholder: 'sk-or-v1-...',
  },
  {
    id: 'OPENAI',
    label: 'OpenAI Direct',
    subtitle: 'Official OpenAI endpoint (GPT-4o, o3-mini)',
    defaultUrl: 'https://api.openai.com/v1',
    keyPlaceholder: 'sk-...',
  },
  {
    id: 'GROQ',
    label: 'Groq Cloud',
    subtitle: 'Ultra-low latency LPU inference (Llama 3.3)',
    defaultUrl: 'https://api.groq.com/openai/v1',
    keyPlaceholder: 'gsk_...',
  },
  {
    id: 'DEEPSEEK',
    label: 'DeepSeek Direct',
    subtitle: 'DeepSeek V3 & R1 reasoning models',
    defaultUrl: 'https://api.deepseek.com/v1',
    keyPlaceholder: 'sk-...',
  },
  {
    id: 'OLLAMA',
    label: 'Ollama / Local',
    subtitle: 'Self-hosted local models on your network',
    defaultUrl: 'http://localhost:11434/v1',
    keyPlaceholder: 'ollama (or optional)',
  },
  {
    id: 'OPENAI_COMPATIBLE',
    label: 'OpenAI Compatible',
    subtitle: 'Custom API URL for vLLM, LM Studio, or LiteLLM',
    defaultUrl: 'https://api.openai.com/v1',
    keyPlaceholder: 'sk-...',
  },
];

const PROVIDER_PRESETS = {
  OPENROUTER: [
    { id: 'openai/gpt-6-luna', label: 'OpenAI GPT-6 Luna', tag: 'Recommended' },
    { id: 'openai/gpt-4o', label: 'OpenAI GPT-4o', tag: 'High Intelligence' },
    { id: 'openai/gpt-4o-mini', label: 'OpenAI GPT-4o-mini', tag: 'Fast & Lightweight' },
    { id: 'anthropic/claude-sonnet-5.5', label: 'Anthropic Claude Sonnet 5.5', tag: 'Advanced' },
    { id: 'anthropic/claude-haiku-5.5', label: 'Anthropic Claude Haiku 5.5', tag: 'Fast' },
    { id: 'google/gemini-2.5-flash', label: 'Google Gemini 2.5 Flash', tag: 'Ultra Fast' },
    { id: 'z-ai/glm-5.3-flash', label: 'Z-AI GLM 5.3 Flash', tag: 'Balanced' },
    { id: 'deepseek/deepseek-chat', label: 'DeepSeek Chat', tag: 'Cost Effective' },
  ],
  OPENAI: [
    { id: 'gpt-4o', label: 'GPT-4o', tag: 'Flagship' },
    { id: 'gpt-4o-mini', label: 'GPT-4o-mini', tag: 'Fast & Lightweight' },
    { id: 'o3-mini', label: 'o3-mini', tag: 'Reasoning' },
    { id: 'gpt-4-turbo', label: 'GPT-4 Turbo', tag: 'Legacy' },
  ],
  GROQ: [
    { id: 'llama-3.3-70b-versatile', label: 'Llama 3.3 70B Versatile', tag: 'Ultra Fast LPU' },
    { id: 'llama-3.1-8b-instant', label: 'Llama 3.1 8B Instant', tag: 'Instant' },
    { id: 'mixtral-8x7b-32768', label: 'Mixtral 8x7B', tag: 'Balanced' },
  ],
  DEEPSEEK: [
    { id: 'deepseek-chat', label: 'DeepSeek Chat (V3)', tag: 'Cost Effective' },
    { id: 'deepseek-reasoner', label: 'DeepSeek Reasoner (R1)', tag: 'Reasoning' },
  ],
  OLLAMA: [
    { id: 'llama3.2', label: 'Llama 3.2', tag: 'Local Default' },
    { id: 'qwen2.5-coder', label: 'Qwen 2.5 Coder', tag: 'Coding' },
    { id: 'deepseek-r1', label: 'DeepSeek R1', tag: 'Reasoning' },
  ],
  OPENAI_COMPATIBLE: [
    { id: 'openai/gpt-6-luna', label: 'OpenAI GPT-6 Luna', tag: 'Recommended' },
    { id: 'openai/gpt-4o', label: 'OpenAI GPT-4o', tag: 'High Intelligence' },
    { id: 'openai/gpt-4o-mini', label: 'OpenAI GPT-4o-mini', tag: 'Fast & Lightweight' },
  ],
};

export function LlmConfigForm({ api, onSuccess, isOnboarding = false }) {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testingJev, setTestingJev] = useState(false);
  const [testResult, setTestResult] = useState(null); // { success: boolean, message: string }
  const [jevTestResult, setJevTestResult] = useState(null); // { success: boolean, message: string }
  const [error, setError] = useState('');

  const [provider, setProvider] = useState('OPENROUTER');
  const [baseUrl, setBaseUrl] = useState('https://openrouter.ai/api/v1');
  const [apiKey, setApiKey] = useState('');
  const [showKey, setShowKey] = useState(false);
  const [existingMaskedKey, setExistingMaskedKey] = useState('');
  const [isReplacingKey, setIsReplacingKey] = useState(false);

  const [selectedPreset, setSelectedPreset] = useState('openai/gpt-6-luna');
  const [customModel, setCustomModel] = useState('');
  const [supervisorModel, setSupervisorModel] = useState('');
  const [showAdvanced, setShowAdvanced] = useState(false);

  // SystemOne (TypeSafe Jev) state
  const [jevEnabled, setJevEnabled] = useState(true);
  const [jevModel, setJevModel] = useState('typesafe/jev-1.14');
  const [useCustomJevCredentials, setUseCustomJevCredentials] = useState(false);
  const [jevBaseUrl, setJevBaseUrl] = useState('https://openrouter.ai/api/v1');
  const [jevApiKey, setJevApiKey] = useState('');
  const [showJevKey, setShowJevKey] = useState(false);
  const [existingMaskedJevKey, setExistingMaskedJevKey] = useState('');
  const [isReplacingJevKey, setIsReplacingJevKey] = useState(false);

  useEffect(() => {
    let active = true;
    const controller = new AbortController();

    api.getUserLlmConfig(controller.signal)
      .then(config => {
        if (!active) return;
        if (config.configured) {
          const prov = config.provider || 'OPENROUTER';
          setProvider(prov);
          setBaseUrl(config.baseUrl || 'https://openrouter.ai/api/v1');
          setExistingMaskedKey(config.maskedApiKey || '');
          setIsReplacingKey(false);

          const presets = PROVIDER_PRESETS[prov] || PROVIDER_PRESETS.OPENROUTER;
          const matchingPreset = presets.find(p => p.id === config.modelName);
          if (matchingPreset) {
            setSelectedPreset(config.modelName);
            setCustomModel('');
          } else {
            setSelectedPreset('custom');
            setCustomModel(config.modelName || '');
          }

          setSupervisorModel(config.supervisorModelName || '');

          // SystemOne settings
          if (config.jevEnabled !== undefined) {
            setJevEnabled(config.jevEnabled);
          } else if (config.systemJevEnabled !== undefined) {
            setJevEnabled(config.systemJevEnabled);
          }
          if (config.jevModelName) {
            setJevModel(config.jevModelName);
          } else if (config.defaultJevModelName) {
            setJevModel(config.defaultJevModelName);
          }
          if (config.jevBaseUrl) {
            setJevBaseUrl(config.jevBaseUrl);
          }
          if (config.maskedJevApiKey) {
            setExistingMaskedJevKey(config.maskedJevApiKey);
            setUseCustomJevCredentials(true);
            setIsReplacingJevKey(false);
          } else if (config.jevBaseUrl && config.jevBaseUrl !== 'https://openrouter.ai/api/v1') {
            setUseCustomJevCredentials(true);
          }
        } else {
          setExistingMaskedKey('');
          setIsReplacingKey(true);
          if (config.defaultJevModelName) {
            setJevModel(config.defaultJevModelName);
          }
          if (config.systemJevEnabled !== undefined) {
            setJevEnabled(config.systemJevEnabled);
          }
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
  const currentPresets = PROVIDER_PRESETS[provider] || PROVIDER_PRESETS.OPENROUTER;
  const currentProviderMeta = PROVIDERS.find(p => p.id === provider) || PROVIDERS[0];

  const handleProviderChange = (newProvider) => {
    setProvider(newProvider);
    setTestResult(null);
    setError('');
    const meta = PROVIDERS.find(p => p.id === newProvider);
    if (meta) {
      setBaseUrl(meta.defaultUrl);
      const newPresets = PROVIDER_PRESETS[newProvider] || PROVIDER_PRESETS.OPENROUTER;
      if (newPresets && newPresets.length > 0) {
        setSelectedPreset(newPresets[0].id);
        setCustomModel('');
      } else {
        setSelectedPreset('custom');
      }
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

  const handleTestJevConnection = async () => {
    setJevTestResult(null);
    setTestingJev(true);
    try {
      const payload = {
        modelName: jevModel.trim() || 'typesafe/jev-1.14',
        baseUrl: (useCustomJevCredentials && jevBaseUrl.trim() ? jevBaseUrl.trim() : baseUrl.trim()) || undefined,
        apiKey: (useCustomJevCredentials && jevApiKey.trim() ? jevApiKey.trim() : (apiKey.trim() || undefined)),
      };
      const result = await api.testUserJevConfig(payload);
      setJevTestResult(result);
    } catch (err) {
      setJevTestResult({
        success: false,
        message: err.message || 'SystemOne connection test failed',
      });
    } finally {
      setTestingJev(false);
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
        jevEnabled,
        jevModelName: jevModel.trim() || undefined,
        jevBaseUrl: useCustomJevCredentials && jevBaseUrl.trim() ? jevBaseUrl.trim() : undefined,
        jevApiKey: useCustomJevCredentials && jevApiKey.trim() ? jevApiKey.trim() : undefined,
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

      {/* Provider Selector Grid */}
      <div>
        <label className="block text-xs font-semibold text-slate-300 mb-2">
          LLM Provider
        </label>
        <div className="grid grid-cols-2 md:grid-cols-3 gap-2.5">
          {PROVIDERS.map((p) => {
            const isSelected = provider === p.id;
            return (
              <button
                key={p.id}
                type="button"
                onClick={() => handleProviderChange(p.id)}
                className={`flex flex-col items-start p-2.5 rounded-xl border text-left transition ${
                  isSelected
                    ? 'border-cyan-500/60 bg-cyan-500/10 ring-1 ring-cyan-500/30 text-white'
                    : 'border-slate-800 bg-slate-900/60 text-slate-400 hover:border-slate-700 hover:text-slate-200'
                }`}
              >
                <div className="flex items-center gap-1.5 font-semibold text-xs text-cyan-400">
                  {p.id === 'OPENROUTER' ? <Sparkles size={13} /> : <Globe size={13} />}
                  <span className="truncate">{p.label}</span>
                </div>
                <p className="text-[10px] text-slate-400 mt-1 line-clamp-2 leading-relaxed">
                  {p.subtitle}
                </p>
              </button>
            );
          })}
        </div>
      </div>

      {/* Base URL (Editable for OpenAI compatible, Groq, DeepSeek, Ollama, prefilled) */}
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
              setTestResult(null);
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

          {/* Toggle */}
          <label className="relative inline-flex items-center cursor-pointer">
            <input
              type="checkbox"
              checked={jevEnabled}
              onChange={(e) => setJevEnabled(e.target.checked)}
              className="sr-only peer"
            />
            <div className="w-9 h-5 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-cyan-500"></div>
          </label>
        </div>

        {jevEnabled && (
          <div className="space-y-3 pt-2 border-t border-slate-800/80">
            {/* Model Name */}
            <div>
              <label className="block text-[11px] font-semibold text-slate-300 mb-1">
                SystemOne Model ID
              </label>
              <div className="relative">
                <Cpu size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
                <input
                  type="text"
                  value={jevModel}
                  onChange={(e) => { setJevModel(e.target.value); setJevTestResult(null); }}
                  placeholder="typesafe/jev-1.14"
                  className="w-full rounded-xl border border-slate-800 bg-slate-950/80 py-1.5 pl-8 pr-3 text-xs text-slate-200 placeholder-slate-600 focus:border-cyan-500 focus:outline-none font-mono"
                />
              </div>
              <p className="mt-1 text-[10px] text-slate-500">
                Defaults to <code className="text-cyan-400 font-mono">typesafe/jev-1.14</code> via OpenRouter <code className="font-mono">/api/v1/systemone</code>.
              </p>
            </div>

            {/* Credentials source choice */}
            <div className="space-y-1.5">
              <label className="block text-[11px] font-semibold text-slate-300">
                Credentials & Endpoint
              </label>
              <div className="flex flex-col sm:flex-row gap-2 text-xs">
                <label className="flex items-center gap-1.5 text-slate-300 cursor-pointer">
                  <input
                    type="radio"
                    name="jevCreds"
                    checked={!useCustomJevCredentials}
                    onChange={() => { setUseCustomJevCredentials(false); setJevTestResult(null); }}
                    className="text-cyan-500 focus:ring-cyan-500 bg-slate-950 border-slate-700"
                  />
                  <span>Inherit Primary Key & URL</span>
                </label>
                <label className="flex items-center gap-1.5 text-slate-300 cursor-pointer">
                  <input
                    type="radio"
                    name="jevCreds"
                    checked={useCustomJevCredentials}
                    onChange={() => { setUseCustomJevCredentials(true); setJevTestResult(null); }}
                    className="text-cyan-500 focus:ring-cyan-500 bg-slate-950 border-slate-700"
                  />
                  <span>Custom SystemOne Endpoint / Key</span>
                </label>
              </div>
            </div>

            {useCustomJevCredentials && (
              <div className="space-y-2.5 p-3 rounded-xl border border-slate-800 bg-slate-950/60">
                {/* Custom Jev URL */}
                <div>
                  <label className="block text-[10px] font-semibold text-slate-400 mb-1">
                    SystemOne Base URL
                  </label>
                  <div className="relative">
                    <Globe size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-500" />
                    <input
                      type="url"
                      value={jevBaseUrl}
                      onChange={(e) => setJevBaseUrl(e.target.value)}
                      placeholder="https://openrouter.ai/api/v1"
                      className="w-full rounded-lg border border-slate-800 bg-slate-900 py-1.5 pl-7 pr-3 text-xs text-slate-200 placeholder-slate-600 focus:border-cyan-500 focus:outline-none font-mono"
                    />
                  </div>
                </div>

                {/* Custom Jev Key */}
                <div>
                  <label className="block text-[10px] font-semibold text-slate-400 mb-1">
                    Dedicated SystemOne API Key
                  </label>
                  {existingMaskedJevKey && !isReplacingJevKey ? (
                    <div className="flex items-center justify-between rounded-lg border border-slate-800 bg-slate-900 px-3 py-1.5">
                      <span className="text-xs font-mono text-slate-200">{existingMaskedJevKey}</span>
                      <button
                        type="button"
                        onClick={() => { setIsReplacingJevKey(true); setJevApiKey(''); }}
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
                        onChange={(e) => setJevApiKey(e.target.value)}
                        placeholder="Dedicated API key..."
                        className="w-full rounded-lg border border-slate-800 bg-slate-900 py-1.5 pl-7 pr-8 text-xs text-slate-200 placeholder-slate-600 focus:border-cyan-500 focus:outline-none font-mono"
                      />
                      <button
                        type="button"
                        onClick={() => setShowJevKey(!showJevKey)}
                        className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300"
                      >
                        {showJevKey ? <EyeOff size={13} /> : <Eye size={13} />}
                      </button>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* SystemOne Test Connection Button & Result */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pt-1">
              <button
                type="button"
                onClick={handleTestJevConnection}
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
