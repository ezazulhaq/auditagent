import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { NotificationDropdown } from './NotificationDropdown';

describe('NotificationDropdown', () => {
  it('renders unread badge when there are unread notifications, opens dropdown, and marks all read on explicit click', async () => {
    const mockNotifications = [
      { id: 'n-1', type: 'success', message: 'Analysis completed for VULN-001', read: false, createdAt: '2026-10-29T06:00:00Z' },
      { id: 'n-2', type: 'error', message: 'Analysis failed for VULN-002: syntax error', read: true, createdAt: '2026-10-29T05:00:00Z' },
    ];
    const api = {
      getNotifications: vi.fn().mockResolvedValue(mockNotifications),
      markNotificationsRead: vi.fn().mockResolvedValue({}),
      markNotificationRead: vi.fn().mockResolvedValue({}),
    };

    render(<NotificationDropdown api={api} />);

    // Initially loads notifications
    await waitFor(() => expect(api.getNotifications).toHaveBeenCalled());

    // Bell button exists and has unread count badge
    const button = screen.getByRole('button', { name: /Notifications/i });
    expect(button).toBeInTheDocument();
    expect(button.querySelector('.bg-rose-500')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument(); // 1 unread

    // Click to open
    fireEvent.click(button);

    // Expect dropdown to be open and display notifications
    expect(await screen.findByText('Analysis completed for VULN-001')).toBeInTheDocument();
    expect(screen.getByText('Analysis failed for VULN-002: syntax error')).toBeInTheDocument();

    // Opening does NOT auto-mark as read (preserving unread state)
    expect(api.markNotificationsRead).not.toHaveBeenCalled();

    // Click "Mark read" button
    const markReadBtn = screen.getByRole('button', { name: /Mark all as read/i });
    fireEvent.click(markReadBtn);

    expect(api.markNotificationsRead).toHaveBeenCalledTimes(1);
  });

  it('displays empty state when no notifications exist', async () => {
    const api = {
      getNotifications: vi.fn().mockResolvedValue([]),
      markNotificationsRead: vi.fn().mockResolvedValue({}),
    };

    render(<NotificationDropdown api={api} />);
    await waitFor(() => expect(api.getNotifications).toHaveBeenCalled());

    const button = screen.getByRole('button', { name: /Notifications/i });
    expect(button.querySelector('.bg-rose-500')).toBeNull();

    fireEvent.click(button);
    expect(await screen.findByText('No notifications')).toBeInTheDocument();
  });

  it('updates notifications list when notification-added event is dispatched', async () => {
    const initialList = [
      { id: 'n-1', type: 'info', message: 'Initial scan completed', read: true, createdAt: '2026-10-29T04:00:00Z' },
    ];
    const updatedList = [
      { id: 'n-2', type: 'success', message: 'Remediation verified for VULN-003', read: false, createdAt: '2026-10-29T07:00:00Z' },
      ...initialList,
    ];
    const api = {
      getNotifications: vi.fn()
        .mockResolvedValueOnce(initialList)
        .mockResolvedValueOnce(updatedList),
      markNotificationsRead: vi.fn().mockResolvedValue({}),
    };

    render(<NotificationDropdown api={api} />);
    await waitFor(() => expect(api.getNotifications).toHaveBeenCalledTimes(1));

    // Dispatch custom event
    window.dispatchEvent(new CustomEvent('notification-added', {
      detail: { type: 'success', message: 'Remediation verified for VULN-003' }
    }));

    // Expect getNotifications to be called again
    await waitFor(() => expect(api.getNotifications).toHaveBeenCalledTimes(2));

    const button = screen.getByRole('button', { name: /Notifications/i });
    fireEvent.click(button);

    expect(await screen.findByText('Remediation verified for VULN-003')).toBeInTheDocument();
  });

  it('filters notifications by All, Unread, and Alerts tabs', async () => {
    const mockNotifications = [
      { id: 'n-1', type: 'info', message: 'Repository indexed', read: true, createdAt: '2026-10-29T04:00:00Z' },
      { id: 'n-2', type: 'success', message: 'Fix applied for VULN-101', read: false, createdAt: '2026-10-29T05:00:00Z' },
      { id: 'n-3', type: 'error', message: 'Scan failed on branch staging', read: false, createdAt: '2026-10-29T06:00:00Z' },
    ];
    const api = {
      getNotifications: vi.fn().mockResolvedValue(mockNotifications),
      markNotificationsRead: vi.fn().mockResolvedValue({}),
    };

    render(<NotificationDropdown api={api} />);
    await waitFor(() => expect(api.getNotifications).toHaveBeenCalled());

    fireEvent.click(screen.getByRole('button', { name: /Notifications/i }));

    // Default 'All' tab shows all 3
    expect(screen.getByText('Repository indexed')).toBeInTheDocument();
    expect(screen.getByText('Fix applied for VULN-101')).toBeInTheDocument();
    expect(screen.getByText('Scan failed on branch staging')).toBeInTheDocument();

    // Switch to 'Unread' tab
    fireEvent.click(screen.getByRole('button', { name: /^Unread/i }));
    expect(screen.queryByText('Repository indexed')).toBeNull();
    expect(screen.getByText('Fix applied for VULN-101')).toBeInTheDocument();
    expect(screen.getByText('Scan failed on branch staging')).toBeInTheDocument();

    // Switch to 'Alerts' tab
    fireEvent.click(screen.getByRole('button', { name: /^Alerts/i }));
    expect(screen.queryByText('Repository indexed')).toBeNull();
    expect(screen.queryByText('Fix applied for VULN-101')).toBeNull();
    expect(screen.getByText('Scan failed on branch staging')).toBeInTheDocument();
  });

  it('triggers onSelectFinding and marks notification read on item click', async () => {
    const mockNotifications = [
      { id: 'n-1', type: 'success', message: 'Analysis completed for VULN-000042', read: false, createdAt: '2026-10-29T06:00:00Z' },
    ];
    const findings = [{ id: 'VULN-000042', vulnType: 'SQL Injection' }];
    const onSelectFinding = vi.fn();
    const api = {
      getNotifications: vi.fn().mockResolvedValue(mockNotifications),
      markNotificationRead: vi.fn().mockResolvedValue({}),
      markNotificationsRead: vi.fn().mockResolvedValue({}),
    };

    render(
      <NotificationDropdown
        api={api}
        findings={findings}
        onSelectFinding={onSelectFinding}
      />
    );
    await waitFor(() => expect(api.getNotifications).toHaveBeenCalled());

    fireEvent.click(screen.getByRole('button', { name: /Notifications/i }));

    const item = await screen.findByText('Analysis completed for VULN-000042');
    fireEvent.click(item);

    expect(onSelectFinding).toHaveBeenCalledWith(expect.objectContaining({ id: 'VULN-000042' }));
    expect(api.markNotificationRead).toHaveBeenCalledWith('n-1');
  });

  it('supports inline Clear all confirmation flow', async () => {
    const mockNotifications = [
      { id: 'n-1', type: 'info', message: 'Old notification', read: true, createdAt: '2026-10-29T04:00:00Z' },
    ];
    const api = {
      getNotifications: vi.fn().mockResolvedValue(mockNotifications),
      clearNotifications: vi.fn().mockResolvedValue({}),
    };

    render(<NotificationDropdown api={api} />);
    await waitFor(() => expect(api.getNotifications).toHaveBeenCalled());

    fireEvent.click(screen.getByRole('button', { name: /Notifications/i }));

    const clearBtn = screen.getByRole('button', { name: /Clear all notifications/i });
    fireEvent.click(clearBtn);

    // Inline prompt appears
    expect(screen.getByText('Clear all notifications?')).toBeInTheDocument();
    const confirmBtn = screen.getByRole('button', { name: /^Clear$/i });
    fireEvent.click(confirmBtn);

    await waitFor(() => expect(api.clearNotifications).toHaveBeenCalledTimes(1));
    expect(await screen.findByText('No notifications')).toBeInTheDocument();
  });

  it('closes dropdown when clicking close button, clicking outside, or pressing Escape', async () => {
    const api = {
      getNotifications: vi.fn().mockResolvedValue([
        { id: 'n-1', type: 'info', message: 'Ready', read: true, createdAt: '2026-10-29T04:00:00Z' }
      ]),
      markNotificationsRead: vi.fn().mockResolvedValue({}),
    };

    render(
      <div>
        <div data-testid="outside">Outside</div>
        <NotificationDropdown api={api} />
      </div>
    );
    await waitFor(() => expect(api.getNotifications).toHaveBeenCalled());

    const button = screen.getByRole('button', { name: /Notifications/i });
    fireEvent.click(button);
    expect(await screen.findByText('Ready')).toBeInTheDocument();

    const closeBtn = screen.getByRole('button', { name: /Close notifications/i });
    fireEvent.click(closeBtn);
    expect(screen.queryByText('Ready')).toBeNull();

    // Reopen and click outside
    fireEvent.click(button);
    expect(await screen.findByText('Ready')).toBeInTheDocument();
    fireEvent.mouseDown(screen.getByTestId('outside'));
    expect(screen.queryByText('Ready')).toBeNull();

    // Reopen and press Escape
    fireEvent.click(button);
    expect(await screen.findByText('Ready')).toBeInTheDocument();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByText('Ready')).toBeNull();
  });
});
