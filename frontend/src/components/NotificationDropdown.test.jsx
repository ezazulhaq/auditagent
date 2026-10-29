import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { NotificationDropdown } from './NotificationDropdown';

describe('NotificationDropdown', () => {
  it('renders unread badge when there are unread notifications and opens dropdown', async () => {
    const mockNotifications = [
      { id: 'n-1', type: 'success', message: 'Analysis completed for VULN-001', read: false, createdAt: '2026-10-29T06:00:00Z' },
      { id: 'n-2', type: 'error', message: 'Analysis failed for VULN-002: syntax error', read: true, createdAt: '2026-10-29T05:00:00Z' },
    ];
    const api = {
      getNotifications: vi.fn().mockResolvedValue(mockNotifications),
      markNotificationsRead: vi.fn().mockResolvedValue({}),
    };

    render(<NotificationDropdown api={api} />);

    // Initially loads notifications
    await waitFor(() => expect(api.getNotifications).toHaveBeenCalled());

    // Bell button exists and has unread badge
    const button = screen.getByRole('button', { name: /Notifications/i });
    expect(button).toBeInTheDocument();
    expect(button.querySelector('.bg-rose-500')).toBeInTheDocument();

    // Click to open
    fireEvent.click(button);

    // Expect dropdown to be open and display notifications
    expect(await screen.findByText('Analysis completed for VULN-001')).toBeInTheDocument();
    expect(screen.getByText('Analysis failed for VULN-002: syntax error')).toBeInTheDocument();

    // markNotificationsRead was called
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

  it('closes dropdown when clicking close button or outside', async () => {
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
  });
});
