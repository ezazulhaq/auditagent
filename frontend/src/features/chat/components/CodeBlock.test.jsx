import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { CodeBlock } from './CodeBlock';

describe('CodeBlock', () => {
  it('renders inline code with a code element', () => {
    render(<CodeBlock inline={true}>inline code</CodeBlock>);
    const codeEl = screen.getByText('inline code');
    expect(codeEl.tagName.toLowerCase()).toBe('code');
  });

  it('renders fenced code blocks with a pre element', () => {
    const { container } = render(<CodeBlock language="js">{'const x = 1;'}</CodeBlock>);
    const preEl = container.querySelector('pre');
    expect(preEl).toBeInTheDocument();
  });

  it('shows the copy button and calls clipboard API on click', () => {
    Object.assign(navigator, { clipboard: { writeText: vi.fn() } });
    render(<CodeBlock language="js">{'const x = 1;'}</CodeBlock>);

    const copyBtn = screen.getByLabelText('Copy code');
    expect(copyBtn).toBeInTheDocument();

    fireEvent.click(copyBtn);
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('const x = 1;');
  });

  it('shows "Show more" button for long code blocks (>12 lines)', () => {
    const longCode = Array.from({ length: 15 }, (_, i) => `line ${i}`).join('\n');
    render(<CodeBlock language="js">{longCode}</CodeBlock>);

    const btn = screen.getByText(/Show more/i);
    expect(btn).toBeInTheDocument();
  });

  it('does not show "Show more" button for short code blocks (<= 12 lines)', () => {
    const shortCode = Array.from({ length: 5 }, (_, i) => `line ${i}`).join('\n');
    render(<CodeBlock language="js">{shortCode}</CodeBlock>);

    const btn = screen.queryByText(/Show more/i);
    expect(btn).not.toBeInTheDocument();
  });

  it('shows language badge when language prop is provided', () => {
    render(<CodeBlock language="python">{'print("hello")'}</CodeBlock>);
    const badge = screen.getByText('python');
    expect(badge).toBeInTheDocument();
  });
});
