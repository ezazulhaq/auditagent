import { BarChart3, CircleDot, FileText, ShieldAlert } from 'lucide-react';
import { DashboardView } from '../dashboard/DashboardView';
import { FindingsView } from '../findings/FindingsView';
import { IssuesView } from '../issues/IssuesView';
import { ReportView } from '../report/ReportView';

/**
 * Declarative Workspace Tab Registry.
 * Implements Open/Closed Principle (OCP): new tabs can be added by registering an entry
 * without modifying App.jsx or WorkspaceTabs.jsx.
 */
export const WORKSPACE_TABS = [
  {
    id: 'dashboard',
    label: 'Dashboard',
    icon: BarChart3,
    getBadge: () => null,
    renderView: ({ audit, workspace, modals }) => {
      const {
        metadata, findings, counts, scanHistory, result, isUploadScan,
        handleSelectFinding, clearScanHistory,
      } = audit;
      const { selectedRepository } = workspace;
      const { openFindingsModal } = modals;

      const repositoryLabel = isUploadScan
        ? `${result?.projectName || 'Project'} (Manual Upload)`
        : (result?.repository || selectedRepository?.fullName || '');

      return (
        <DashboardView
          metadata={metadata}
          findings={findings}
          counts={counts}
          scanHistory={scanHistory}
          repositoryLabel={repositoryLabel}
          onSelect={handleSelectFinding}
          onShowFindings={openFindingsModal}
          onClearHistory={clearScanHistory}
        />
      );
    },
  },
  {
    id: 'findings',
    label: 'Findings',
    icon: ShieldAlert,
    getBadge: ({ findings }) => findings.length,
    renderView: ({ audit }) => {
      const {
        findings, runs, handleSelectFinding, handleBulkAction, refreshReport,
        batchState, cancelBatch, isAnalyzingMap, isUploadScan,
      } = audit;

      return (
        <FindingsView
          findings={findings}
          runs={runs}
          onSelect={handleSelectFinding}
          onBulkAction={handleBulkAction}
          onRefresh={refreshReport}
          batchState={batchState}
          onCancelBatch={cancelBatch}
          isAnalyzingMap={isAnalyzingMap}
          isUploadScan={isUploadScan}
        />
      );
    },
  },
  {
    id: 'issues',
    label: 'Issues',
    icon: CircleDot,
    getBadge: ({ issues }) => issues?.length ?? 0,
    renderView: ({ audit, workspace }) => {
      const {
        issues, loadingIssues, loadIssues, findings, handleSelectFinding,
      } = audit;
      const { selectedRepository } = workspace;

      return (
        <IssuesView
          issues={issues}
          loading={loadingIssues}
          onRefresh={loadIssues}
          onSelectFinding={id => {
            const f = findings.find(x => x.id === id);
            if (f) handleSelectFinding(f);
          }}
          repository={selectedRepository}
        />
      );
    },
  },
  {
    id: 'report',
    label: 'Audit Report',
    icon: FileText,
    getBadge: () => null,
    renderView: ({ audit, workspace, api }) => {
      const { result, metadata, isUploadScan } = audit;
      const { config, selectedRepository } = workspace;

      return (
        <ReportView
          key={isUploadScan ? `upload:${result?.uploadId}` : `${config.repositoryId}:${config.branch}`}
          api={api}
          repositoryId={config.repositoryId}
          branch={config.branch}
          uploadId={isUploadScan ? result?.uploadId : null}
          reportAvailable={result?.reportAvailable ?? Boolean(result?.htmlReport)}
          projectName={metadata?.projectName || result?.projectName || selectedRepository?.name}
        />
      );
    },
  },
];
