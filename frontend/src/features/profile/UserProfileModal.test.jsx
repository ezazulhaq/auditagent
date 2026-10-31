import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { UserProfileModal } from './UserProfileModal';

describe('UserProfileModal', () => {
  const user = {
    login: 'testdev',
    name: 'Test Developer',
    avatarUrl: 'https://example.com/avatar.png',
  };

  const mockApi = {
    getUserLlmConfig: vi.fn().mockResolvedValue({
      configured: true,
      userKeysRequired: true,
      provider: 'OPENROUTER',
      baseUrl: 'https://openrouter.ai/api/v1',
      modelName: 'anthropic/claude-3.5-sonnet',
      maskedApiKey: 'sk-or-••••••••1234',
    }),
  };

  it('renders user information and closes on close button', () => {
    const onClose = vi.fn();
    render(
      <UserProfileModal
        isOpen={true}
        onClose={onClose}
        user={user}
        api={mockApi}
      />
    );

    expect(screen.getByText('Test Developer')).toBeInTheDocument();
    expect(screen.getByText('@testdev')).toBeInTheDocument();
    expect(screen.getByText(/AI Provider & Key Management/i)).toBeInTheDocument();

    const closeBtn = screen.getByLabelText(/Close modal/i);
    fireEvent.click(closeBtn);
    expect(onClose).toHaveBeenCalled();
  });

  it('does not render when isOpen is false', () => {
    const { container } = render(
      <UserProfileModal
        isOpen={false}
        onClose={vi.fn()}
        user={user}
        api={mockApi}
      />
    );
    expect(container.firstChild).toBeNull();
  });
});

