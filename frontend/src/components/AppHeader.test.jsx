import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { AppHeader } from './AppHeader';
import packageJson from '../../package.json';

describe('AppHeader', () => {
  const defaultProps = {
    metadata: { projectName: 'AuditAgent Project', totalFilesScanned: 42 },
    counts: { high: 2, medium: 5 },
    user: { name: 'Security Auditor', login: 'auditor1', avatarUrl: '' },
    onLogout: vi.fn(),
    onShowFindings: vi.fn(),
    onShowObservability: vi.fn(),
    onToggleTheme: vi.fn(),
    onTourClick: vi.fn(),
    onShowDocs: vi.fn(),
    onToggleSidebar: vi.fn(),
    isSidebarOpen: false,
    onOpenProfileSettings: vi.fn(),
    api: {
      getNotifications: vi.fn().mockResolvedValue([]),
    },
  };

  it('renders application branding and metadata', () => {
    render(<AppHeader {...defaultProps} />);
    expect(screen.getByText('AuditAgent')).toBeInTheDocument();
    expect(screen.getByText('AuditAgent Project')).toBeInTheDocument();
    expect(screen.getByText('42 files analyzed')).toBeInTheDocument();
  });

  it('displays the package version beside the Sign out button in the user dropdown menu', () => {
    render(<AppHeader {...defaultProps} />);

    // Open user menu
    const menuButton = screen.getByRole('button', { name: /User profile menu/i });
    fireEvent.click(menuButton);

    // Verify user menu is displayed
    expect(screen.getByText('Security Auditor')).toBeInTheDocument();
    expect(screen.getByText('@auditor1')).toBeInTheDocument();

    // Verify Sign out button and package.json version
    const signOutButton = screen.getByRole('button', { name: /Sign out/i });
    expect(signOutButton).toBeInTheDocument();

    const expectedVersion = `v${packageJson.version}`;
    const versionBadge = screen.getByText(expectedVersion);
    expect(versionBadge).toBeInTheDocument();
  });

  it('calls onLogout and closes menu when Sign out is clicked', () => {
    const onLogout = vi.fn();
    render(<AppHeader {...defaultProps} onLogout={onLogout} />);

    // Open user menu
    const menuButton = screen.getByRole('button', { name: /User profile menu/i });
    fireEvent.click(menuButton);

    const signOutButton = screen.getByRole('button', { name: /Sign out/i });
    fireEvent.click(signOutButton);

    expect(onLogout).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('button', { name: /Sign out/i })).not.toBeInTheDocument();
  });
});
