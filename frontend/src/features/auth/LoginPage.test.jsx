import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { LoginPage } from './LoginPage';

describe('LoginPage', () => {
  it('places the sign-in panel and GitHub action ahead of capabilities in document order for mobile reachability', () => {
    render(<LoginPage configured={true} error={null} onLogin={vi.fn()} />);

    const authSection = screen.getByRole('region', { name: /Sign in to your workspace/i });
    const overviewSection = screen.getByRole('region', { name: /Find risk\. Verify the fix\. Ship with confidence\./i });
    const loginHeading = screen.getByRole('heading', { level: 1, name: /Sign in to your workspace/i });
    const overviewHeading = screen.getByRole('heading', { level: 2, name: /Find risk\. Verify the fix\. Ship with confidence\./i });
    const signInButton = screen.getByRole('button', { name: /Continue with GitHub/i });

    expect(signInButton).toBeEnabled();
    expect(authSection.compareDocumentPosition(overviewSection) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(loginHeading.compareDocumentPosition(overviewHeading) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('calls onLogin when Continue with GitHub is clicked', () => {
    const onLogin = vi.fn();
    render(<LoginPage configured={true} error={null} onLogin={onLogin} />);

    fireEvent.click(screen.getByRole('button', { name: /Continue with GitHub/i }));
    expect(onLogin).toHaveBeenCalledTimes(1);
  });

  it('disables sign-in and shows configuration alert when GitHub App auth is not configured', () => {
    render(<LoginPage configured={false} error={null} onLogin={vi.fn()} />);

    expect(screen.getByRole('button', { name: /Continue with GitHub/i })).toBeDisabled();
    expect(screen.getByRole('alert')).toHaveTextContent(/GitHub App authentication is not configured/i);
  });

  it('displays authentication errors inside an alert banner', () => {
    render(<LoginPage configured={true} error="OAuth state mismatch." onLogin={vi.fn()} />);

    expect(screen.getByRole('alert')).toHaveTextContent('OAuth state mismatch.');
  });
});
