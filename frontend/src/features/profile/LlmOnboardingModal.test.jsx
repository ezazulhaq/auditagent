import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { LlmOnboardingModal } from './LlmOnboardingModal';

describe('LlmOnboardingModal', () => {
  it('renders onboarding dialog with instructions and logout option', async () => {
    const mockApi = {
      getUserLlmConfig: vi.fn().mockResolvedValue({
        configured: false,
        userKeysRequired: true,
        provider: 'OPENROUTER',
        baseUrl: 'https://openrouter.ai/api/v1',
        modelName: 'anthropic/claude-3.5-sonnet',
        maskedApiKey: null,
      }),
    };
    const onLogout = vi.fn();
    const onComplete = vi.fn();

    render(
      <LlmOnboardingModal
        api={mockApi}
        onComplete={onComplete}
        onLogout={onLogout}
      />
    );

    expect(screen.getByText(/Connect AI Provider Credentials/i)).toBeInTheDocument();
    expect(screen.getByText(/AuditAgent requires an API key/i)).toBeInTheDocument();

    const logoutBtn = screen.getByRole('button', { name: /Log out/i });
    fireEvent.click(logoutBtn);
    expect(onLogout).toHaveBeenCalled();
  });
});
