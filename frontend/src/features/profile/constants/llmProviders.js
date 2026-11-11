export const PROVIDERS = [
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

export const PROVIDER_PRESETS = {
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
