import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { LlmConfigForm } from './LlmConfigForm';

describe('LlmConfigForm', () => {
  let mockApi;
  let onSuccess;

  beforeEach(() => {
    mockApi = {
      getUserLlmConfig: vi.fn().mockResolvedValue({
        configured: false,
        userKeysRequired: true,
        provider: 'OPENROUTER',
        baseUrl: 'https://openrouter.ai/api/v1',
        modelName: 'anthropic/claude-3.5-sonnet',
        supervisorModelName: null,
        maskedApiKey: null,
      }),
      testUserLlmConfig: vi.fn().mockResolvedValue({
        success: true,
        message: 'Connection successful! Model anthropic/claude-3.5-sonnet responded.',
      }),
      saveUserLlmConfig: vi.fn().mockResolvedValue({
        configured: true,
        userKeysRequired: true,
        provider: 'OPENROUTER',
        baseUrl: 'https://openrouter.ai/api/v1',
        modelName: 'anthropic/claude-3.5-sonnet',
        maskedApiKey: 'sk-or-••••••••1234',
      }),
    };
    onSuccess = vi.fn();
  });

  it('renders provider selection and defaults to OpenRouter', async () => {
    render(<LlmConfigForm api={mockApi} onSuccess={onSuccess} />);
    await waitFor(() => expect(screen.getByText(/OpenRouter \(Default\)/i)).toBeInTheDocument());
    expect(screen.getByText(/OpenAI Compatible/i)).toBeInTheDocument();
  });

  it('switches to OpenAI-compatible and displays base URL input', async () => {
    render(<LlmConfigForm api={mockApi} onSuccess={onSuccess} />);
    await waitFor(() => expect(screen.getByText(/OpenRouter \(Default\)/i)).toBeInTheDocument());

    const openAiBtn = screen.getByText(/OpenAI Compatible/i).closest('button');
    fireEvent.click(openAiBtn);

    expect(screen.getByText(/API Base URL/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText('https://api.openai.com/v1')).toBeInTheDocument();
  });

  it('allows testing connection and shows result', async () => {
    render(<LlmConfigForm api={mockApi} onSuccess={onSuccess} />);
    await waitFor(() => expect(screen.getByText(/OpenRouter \(Default\)/i)).toBeInTheDocument());

    const keyInput = screen.getByPlaceholderText(/sk-or-v1-/i);
    fireEvent.change(keyInput, { target: { value: 'sk-or-v1-my-secret-key' } });

    const testBtn = screen.getByRole('button', { name: /Test Connection/i });
    fireEvent.click(testBtn);

    await waitFor(() => {
      expect(mockApi.testUserLlmConfig).toHaveBeenCalledWith(expect.objectContaining({
        apiKey: 'sk-or-v1-my-secret-key',
        provider: 'OPENROUTER',
      }));
      expect(screen.getByText(/Connection successful!/i)).toBeInTheDocument();
    });
  });

  it('submits and saves configuration', async () => {
    render(<LlmConfigForm api={mockApi} onSuccess={onSuccess} isOnboarding={true} />);
    await waitFor(() => expect(screen.getByText(/OpenRouter \(Default\)/i)).toBeInTheDocument());

    const keyInput = screen.getByPlaceholderText(/sk-or-v1-/i);
    fireEvent.change(keyInput, { target: { value: 'sk-or-v1-valid-key-1234' } });

    const saveBtn = screen.getByRole('button', { name: /Save & Start Auditing/i });
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(mockApi.saveUserLlmConfig).toHaveBeenCalledWith(expect.objectContaining({
        apiKey: 'sk-or-v1-valid-key-1234',
        modelName: 'anthropic/claude-3.5-sonnet',
        provider: 'OPENROUTER',
      }));
      expect(onSuccess).toHaveBeenCalled();
    });
  });

  it('displays saved masked key when already configured', async () => {
    mockApi.getUserLlmConfig.mockResolvedValueOnce({
      configured: true,
      userKeysRequired: true,
      provider: 'OPENROUTER',
      baseUrl: 'https://openrouter.ai/api/v1',
      modelName: 'anthropic/claude-3.5-sonnet',
      maskedApiKey: 'sk-or-v1-••••••••3a8f',
    });

    render(<LlmConfigForm api={mockApi} onSuccess={onSuccess} />);
    await waitFor(() => {
      expect(screen.getByText('sk-or-v1-••••••••3a8f')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Change Key/i })).toBeInTheDocument();
    });
  });
});

