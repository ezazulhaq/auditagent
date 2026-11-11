import { createContext, useContext, useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useApi } from './ApiContext';
import { useAuth } from './AuthContext';
import { useToast } from '../components/Toast';

export const WorkspaceContext = createContext(null);

const initialConfig = {
  repositoryId: '',
  branch: '',
  scannerName: 'semgrep',
  forceRescan: false,
};

/**
 * WorkspaceProvider manages repository discovery, branch selection, and scan target configuration.
 * Adheres to Single Responsibility Principle (SRP).
 */
export function WorkspaceProvider({ children }) {
  const api = useApi();
  const { isAuthenticated } = useAuth();
  const toast = useToast();

  const [config, setConfig] = useState(initialConfig);
  const [repositories, setRepositories] = useState([]);
  const [branches, setBranches] = useState([]);
  const [loadingRepositories, setLoadingRepositories] = useState(true);
  const repositoryKeyRef = useRef('');

  // Fetch installed repositories when authenticated
  useEffect(() => {
    if (!isAuthenticated) return undefined;
    const controller = new AbortController();
    api.repositories(controller.signal)
      .then(setRepositories)
      .catch(error => {
        if (error.name !== 'AbortError') {
          toast.error(`Unable to load installed repositories: ${error.message}`);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoadingRepositories(false);
        }
      });
    return () => controller.abort();
  }, [api, isAuthenticated, toast]);

  // Fetch branches when repository selection changes
  useEffect(() => {
    if (!config.repositoryId) {
      return undefined;
    }
    const controller = new AbortController();
    api.branches(config.repositoryId, controller.signal)
      .then(items => {
        setBranches(items);
        const repository = repositories.find(item => String(item.repositoryId) === String(config.repositoryId));
        const preferred = items.includes(repository?.defaultBranch) ? repository.defaultBranch : items[0] ?? '';
        setConfig(previous => previous.repositoryId === config.repositoryId ? { ...previous, branch: preferred } : previous);
      })
      .catch(error => {
        if (error.name !== 'AbortError') {
          toast.error(`Unable to load branches: ${error.message}`);
        }
      });
    return () => controller.abort();
  }, [api, config.repositoryId, repositories, toast]);

  const updateConfig = useCallback((name, value) => {
    if (name === 'repositoryId') {
      repositoryKeyRef.current = '';
      setBranches([]);
    }
    if (name === 'branch') {
      repositoryKeyRef.current = '';
    }
    setConfig(previous => ({
      ...previous,
      [name]: value,
      ...(name === 'repositoryId' ? { branch: '' } : {})
    }));
  }, []);

  const selectedRepository = useMemo(() => {
    return repositories.find(item => String(item.repositoryId) === String(config.repositoryId)) ?? null;
  }, [repositories, config.repositoryId]);

  const value = useMemo(() => ({
    config,
    setConfig,
    updateConfig,
    repositories,
    branches,
    loadingRepositories,
    selectedRepository,
    repositoryKeyRef,
  }), [
    config,
    updateConfig,
    repositories,
    branches,
    loadingRepositories,
    selectedRepository,
  ]);

  return (
    <WorkspaceContext.Provider value={value}>
      {children}
    </WorkspaceContext.Provider>
  );
}

/**
 * Hook to consume workspace, repository, and branch configuration.
 */
export function useWorkspace() {
  const context = useContext(WorkspaceContext);
  if (!context) {
    throw new Error('useWorkspace must be used within a WorkspaceProvider');
  }
  return context;
}
