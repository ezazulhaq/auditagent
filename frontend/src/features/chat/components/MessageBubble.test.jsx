import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MessageBubble } from './MessageBubble';

describe('MessageBubble', () => {
  it('renders user messages right-aligned with cyan gradient classes', () => {
    const { container } = render(<MessageBubble message={{ role: 'user', text: 'Hello', timestamp: '10:30 AM' }} />);
    expect(container.innerHTML).toMatch(/cyan/i);
    expect(container.innerHTML).toMatch(/justify-end|ml-auto|right/i);
  });

  it('renders agent messages with a Bot avatar icon', () => {
    const { container } = render(<MessageBubble message={{ role: 'agent', text: 'Agent msg', timestamp: '10:30 AM' }} />);
    const svgEl = container.querySelector('svg');
    expect(svgEl).toBeInTheDocument();
  });

  it('renders system messages with a left-accent border style', () => {
    const { container } = render(<MessageBubble message={{ role: 'system', text: 'System msg', timestamp: '10:30 AM' }} />);
    expect(container.innerHTML).toMatch(/border-l-2/i);
  });

  it('renders markdown content', () => {
    const { container } = render(<MessageBubble message={{ role: 'agent', text: '**bold text**', timestamp: '10:30 AM' }} />);
    expect(container.innerHTML).toMatch(/<strong>bold text<\/strong>/i);
  });

  it('hides timestamp by default and makes it visible on hover', () => {
    render(<MessageBubble message={{ role: 'user', text: 'Hello', timestamp: '10:30 AM' }} />);
    const timeEl = screen.getByText('10:30 AM');
    expect(timeEl.className).toMatch(/opacity-0/);
  });
});
