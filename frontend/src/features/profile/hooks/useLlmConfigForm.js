import { useState, useEffect, useCallback } from 'react';
import { PROVIDERS, PROVIDER_PRESETS } from '../constants/llmProviders';

/**
 * Custom hook encapsulating LLM configuration fetching, form state, connection tests, and persistence.
 * Adheres to Single Responsibility Principle (SRP).
 */
export function useLlmConfigForm(api, onSuccess) {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testingJev, setTestingJev] = useState(false);
  const [testResult, setTestResult] = useState(null);
  const [jevTestResult, setJevTestResult] = useState(null);
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

  const handleProviderChange = useCallback((newProvider) => {
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
  }, []);

  const handleTestConnection = useCallback(async () => {
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
  }, [activeModelName, apiKey, api, baseUrl, existingMaskedKey, isReplacingKey, provider]);

  const handleTestJevConnection = useCallback(async () => {
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
  }, [api, apiKey, baseUrl, jevApiKey, jevBaseUrl, jevModel, useCustomJevCredentials]);

  const handleSubmit = useCallback(async (e) => {
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
  }, [
    activeModelName,
    api,
    apiKey,
    baseUrl,
    existingMaskedKey,
    isReplacingKey,
    jevApiKey,
    jevBaseUrl,
    jevEnabled,
    jevModel,
    onSuccess,
    provider,
    supervisorModel,
    useCustomJevCredentials,
  ]);

  return {
    loading,
    saving,
    testing,
    testingJev,
    testResult,
    jevTestResult,
    setJevTestResult,
    setTestResult,
    error,
    setError,
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
  };
}
