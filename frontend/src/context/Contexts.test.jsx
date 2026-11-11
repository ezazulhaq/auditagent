import { renderHook, act } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ApiProvider, useApi } from './ApiContext';
import { AuthProvider, useAuth } from './AuthContext';
import { WorkspaceProvider, useWorkspace } from './WorkspaceContext';
import { ModalProvider, useModals, MODAL_TYPES } from './ModalContext';
import { ToastProvider } from '../components/Toast';

describe('Domain Context Providers', () => {
  it('useApi returns injected API client via ApiProvider', () => {
    const mockApi = { test: true };
    const { result } = renderHook(() => useApi(), {
      wrapper: ({ children }) => <ApiProvider api={mockApi}>{children}</ApiProvider>,
    });
    expect(result.current).toBe(mockApi);
  });

  it('useAuth handles authentication and logout', async () => {
    const mockApi = {
      authSession: vi.fn().mockResolvedValue({
        configured: true,
        authenticated: true,
        user: { id: 'u1', name: 'Alice' },
      }),
      logout: vi.fn().mockResolvedValue({}),
      loginUrl: vi.fn().mockReturnValue('/api/auth/github/login'),
    };

    const wrapper = ({ children }) => (
      <ToastProvider>
        <ApiProvider api={mockApi}>
          <AuthProvider>{children}</AuthProvider>
        </ApiProvider>
      </ToastProvider>
    );

    const { result } = renderHook(() => useAuth(), { wrapper });

    await act(async () => {
      await Promise.resolve();
    });

    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.user?.name).toBe('Alice');

    await act(async () => {
      await result.current.logout();
    });

    expect(result.current.isAuthenticated).toBe(false);
    expect(mockApi.logout).toHaveBeenCalled();
  });

  it('useWorkspace updates config and branches', async () => {
    const mockApi = {
      authSession: vi.fn().mockResolvedValue({ configured: true, authenticated: true }),
      repositories: vi.fn().mockResolvedValue([
        { repositoryId: 10, fullName: 'org/repo', defaultBranch: 'main' },
      ]),
      branches: vi.fn().mockResolvedValue(['main', 'dev']),
    };

    const wrapper = ({ children }) => (
      <ToastProvider>
        <ApiProvider api={mockApi}>
          <AuthProvider>
            <WorkspaceProvider>{children}</WorkspaceProvider>
          </AuthProvider>
        </ApiProvider>
      </ToastProvider>
    );

    const { result } = renderHook(() => useWorkspace(), { wrapper });

    await act(async () => {
      await Promise.resolve();
    });

    act(() => {
      result.current.updateConfig('repositoryId', '10');
    });

    expect(result.current.config.repositoryId).toBe('10');
  });

  it('useModals manages modal visibility and confirmation dialogs', () => {
    const wrapper = ({ children }) => <ModalProvider>{children}</ModalProvider>;
    const { result } = renderHook(() => useModals(), { wrapper });

    expect(result.current.isTourOpen).toBe(false);

    act(() => {
      result.current.openTour();
    });
    expect(result.current.isTourOpen).toBe(true);

    act(() => {
      result.current.closeTour();
    });
    expect(result.current.isTourOpen).toBe(false);

    act(() => {
      result.current.openFindingsModal('High Vulns', [{ id: '1' }]);
    });
    expect(result.current.activeModal?.type).toBe(MODAL_TYPES.FINDINGS);
    expect(result.current.activeModal?.title).toBe('High Vulns');

    act(() => {
      result.current.closeModal();
    });
    expect(result.current.activeModal).toBe(null);

    act(() => {
      result.current.showConfirm({
        title: 'Delete Repo',
        message: 'Are you sure?',
      });
    });
    expect(result.current.confirmAction?.title).toBe('Delete Repo');

    act(() => {
      result.current.closeConfirm();
    });
    expect(result.current.confirmAction).toBe(null);
  });
});
