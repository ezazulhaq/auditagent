import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { TokenUsageLayout } from './TokenUsageLayout';

describe('TokenUsageLayout', () => {
  it('renders loading indicator initially and then displays token usage data with repoName', async () => {
    const mockData = [
      {
        repoName: 'octocat/auditagent',
        repoPath: 'octocat/auditagent',
        modelName: 'gpt-4o',
        callCount: 5,
        totalTokens: 12500,
      },
      {
        repoName: 'octocat/demo-repo',
        repoPath: 'octocat/demo-repo',
        modelName: 'claude-3-7-sonnet',
        callCount: 2,
        totalTokens: 3400,
      },
    ];

    const api = {
      observabilityTokens: vi.fn().mockResolvedValue(mockData),
    };

    render(<TokenUsageLayout api={api} theme="dark" onClose={vi.fn()} />);

    expect(screen.getByText(/Loading token usage.../i)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('octocat/auditagent')).toBeInTheDocument();
    });

    expect(screen.getByText('octocat/demo-repo')).toBeInTheDocument();
    expect(screen.getByText('gpt-4o')).toBeInTheDocument();
    expect(screen.getByText('12,500')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
  });

  it('renders empty message when no usage data is found', async () => {
    const api = {
      observabilityTokens: vi.fn().mockResolvedValue([]),
    };

    render(<TokenUsageLayout api={api} theme="dark" onClose={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByText(/No token usage data found/i)).toBeInTheDocument();
    });
  });

  it('renders error message when fetch fails', async () => {
    const api = {
      observabilityTokens: vi.fn().mockRejectedValue(new Error('Network error')),
    };

    render(<TokenUsageLayout api={api} theme="dark" onClose={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByText(/Error: Network error/i)).toBeInTheDocument();
    });
  });

  it('calls onClose when clicking Back to Workspace', async () => {
    const onClose = vi.fn();
    const api = {
      observabilityTokens: vi.fn().mockResolvedValue([]),
    };

    render(<TokenUsageLayout api={api} theme="dark" onClose={onClose} />);

    fireEvent.click(screen.getByRole('button', { name: /Back to Workspace/i }));
    expect(onClose).toHaveBeenCalled();
  });
});
