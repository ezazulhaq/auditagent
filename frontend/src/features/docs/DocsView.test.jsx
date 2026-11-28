import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { DocsView } from './DocsView';

describe('DocsView', () => {
  it('fetches documentation once, selects the first document, and switches without re-fetching the list', async () => {
    const api = {
      getDocs: vi.fn().mockResolvedValue(['01-product-and-business-guide.md', 'README.md']),
      getDocContent: vi.fn().mockImplementation(async (filename) => {
        if (filename === '01-product-and-business-guide.md') return '# Product Guide\nWelcome!';
        if (filename === 'README.md') return '# Index\nOverview';
        return '';
      }),
    };

    render(<DocsView api={api} theme="dark" onClose={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByText('Product And Business Guide')).toBeInTheDocument();
      expect(screen.getByText('Welcome!')).toBeInTheDocument();
    });

    expect(api.getDocs).toHaveBeenCalledTimes(1);
    expect(api.getDocContent).toHaveBeenCalledWith('01-product-and-business-guide.md', expect.any(AbortSignal));

    fireEvent.click(screen.getByRole('button', { name: /README/i }));

    await waitFor(() => {
      expect(screen.getByText('Overview')).toBeInTheDocument();
    });

    expect(api.getDocs).toHaveBeenCalledTimes(1);
    expect(api.getDocContent).toHaveBeenCalledWith('README.md', expect.any(AbortSignal));
  });

  it('displays empty state message when no documentation files exist', async () => {
    const api = {
      getDocs: vi.fn().mockResolvedValue([]),
      getDocContent: vi.fn(),
    };

    render(<DocsView api={api} theme="dark" onClose={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByText('No documentation found.')).toBeInTheDocument();
    });

    expect(api.getDocContent).not.toHaveBeenCalled();
  });

  it('displays error state when fetching docs fails', async () => {
    const api = {
      getDocs: vi.fn().mockRejectedValue(new Error('Service unavailable')),
      getDocContent: vi.fn(),
    };

    render(<DocsView api={api} theme="dark" onClose={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByText(/Failed to load docs: Service unavailable/i)).toBeInTheDocument();
    });
  });
});
