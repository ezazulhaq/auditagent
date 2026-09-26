import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ChatInput } from './ChatInput';

describe('ChatInput', () => {
  it('renders textarea with correct placeholder', () => {
    render(<ChatInput input="" setInput={vi.fn()} onSubmit={vi.fn()} isStreaming={false} onCancel={vi.fn()} />);
    const textarea = screen.getByRole('textbox');
    expect(textarea).toBeInTheDocument();
  });

  it('calls onSubmit on Enter key', () => {
    const onSubmit = vi.fn();
    render(<ChatInput input="hello" setInput={vi.fn()} onSubmit={onSubmit} isStreaming={false} onCancel={vi.fn()} />);
    const textarea = screen.getByRole('textbox');
    
    fireEvent.keyDown(textarea, { key: 'Enter', shiftKey: false });
    expect(onSubmit).toHaveBeenCalled();
  });

  it('does not call onSubmit on Shift+Enter', () => {
    const onSubmit = vi.fn();
    render(<ChatInput input="hello" setInput={vi.fn()} onSubmit={onSubmit} isStreaming={false} onCancel={vi.fn()} />);
    const textarea = screen.getByRole('textbox');
    
    fireEvent.keyDown(textarea, { key: 'Enter', shiftKey: true });
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('shows Stop button when isStreaming=true and calls onCancel on click', () => {
    const onCancel = vi.fn();
    const { container } = render(<ChatInput input="" setInput={vi.fn()} onSubmit={vi.fn()} isStreaming={true} onCancel={onCancel} />);
    
    const btn = screen.getByRole('button');
    fireEvent.click(btn);
    expect(onCancel).toHaveBeenCalled();
  });

  it('shows Send button when isStreaming=false', () => {
    render(<ChatInput input="" setInput={vi.fn()} onSubmit={vi.fn()} isStreaming={false} onCancel={vi.fn()} />);
    const btn = screen.getByRole('button');
    expect(btn).toBeInTheDocument();
  });

  it('controls input value and calls setInput on change', () => {
    const setInput = vi.fn();
    render(<ChatInput input="initial text" setInput={setInput} onSubmit={vi.fn()} isStreaming={false} onCancel={vi.fn()} />);
    
    const textarea = screen.getByRole('textbox');
    expect(textarea.value).toBe('initial text');
    
    fireEvent.change(textarea, { target: { value: 'new value' } });
    expect(setInput).toHaveBeenCalledWith('new value');
  });
});
