import { memo, useMemo } from 'react';
import { useApi } from '../../context/ApiContext';
import { useAudit } from '../../context/AuditContext';
import { useWorkspace } from '../../context/WorkspaceContext';
import { useModals } from '../../context/ModalContext';
import { WorkspaceTabs } from '../report/WorkspaceTabs';
import { WORKSPACE_TABS } from './workspaceTabs';

/**
 * WorkspaceContainer renders the top tab bar and the active tab content
 * driven dynamically by the WORKSPACE_TABS registry (OCP).
 */
export const WorkspaceContainer = memo(function WorkspaceContainer() {
  const api = useApi();
  const audit = useAudit();
  const workspace = useWorkspace();
  const modals = useModals();

  const { activeTab, handleTabChange, findings, issues } = audit;

  const currentTab = useMemo(() => {
    return WORKSPACE_TABS.find(t => t.id === activeTab) ?? WORKSPACE_TABS[0];
  }, [activeTab]);

  return (
    <main className="workspace-surface min-w-0 flex-1 lg:flex lg:min-h-0 lg:flex-col">
      <WorkspaceTabs
        activeTab={activeTab}
        onChange={handleTabChange}
        findingCount={findings.length}
        issueCount={issues.length}
      />
      <div className="min-h-0 flex-1 overflow-auto scroll-smooth">
        {currentTab?.renderView({ audit, workspace, modals, api })}
      </div>
    </main>
  );
});
