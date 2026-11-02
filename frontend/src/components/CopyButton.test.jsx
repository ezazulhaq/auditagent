import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CopyButton } from './CopyButton';

describe('CopyButton', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders copy button and copies text to clipboard on click', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText } });

    render(<CopyButton text="VULN-000042" />);

    const button = screen.getByRole('button', { name: /Copy to clipboard/i });
    expect(button).toBeInTheDocument();
    expect(button).toHaveAttribute('title', 'Copy to clipboard');

    fireEvent.click(button);

    expect(writeText).toHaveBeenCalledWith('VULN-000042');
    expect(await screen.findByRole('button', { name: /Copied to clipboard/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Copied to clipboard/i })).toHaveAttribute('title', 'Copied!');
  });

  it('respects custom title, ariaLabel, and size variant', () => {
    render(
      <CopyButton
        text="VULN-123456"
        size="xs"
        title="Copy finding ID to clipboard"
        ariaLabel="Copy finding ID to clipboard"
      />
    );

    const button = screen.getByRole('button', { name: /Copy finding ID to clipboard/i });
    expect(button).toBeInTheDocument();
    expect(button).toHaveAttribute('title', 'Copy finding ID to clipboard');
  });

  it('handles clipboard write failure gracefully', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
    const writeText = vi.fn().mockRejectedValue(new Error('Clipboard permission denied'));
    Object.assign(navigator, { clipboard: { writeText } });

    render(<CopyButton text="test-id" />);

    const button = screen.getByRole('button', { name: /Copy to clipboard/i });
    fireEvent.click(button);

    expect(writeText).toHaveBeenCalledWith('test-id');
    await vi.waitFor(() => {
      expect(consoleError).toHaveBeenCalledWith('Failed to copy text', expect.any(Error));
    });
  });

  it('stops event propagation on click', () => {
    const parentClick = vi.fn();
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText } });

    render(
      <div onClick={parentClick}>
        <CopyButton text="nested-copy" />
      </div>
    );

    fireEvent.click(screen.getByRole('button', { name: /Copy to clipboard/i }));
    expect(parentClick).not.toHaveBeenCalled();
    expect(writeText).toHaveBeenCalledWith('nested-copy');
  });
});
