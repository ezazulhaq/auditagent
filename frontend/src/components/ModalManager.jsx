import { memo } from 'react';
import { useApi } from '../context/ApiContext';
import { useAuth } from '../context/AuthContext';
import { useModals } from '../context/ModalContext';
import { useAudit } from '../context/AuditContext';
import { useToast } from './Toast';
import { Modal } from './Modal';
import { ConfirmDialog } from './ConfirmDialog';
import { OnboardingTour } from './OnboardingTour';
import { DocsView } from '../features/docs/DocsView';
import { TokenUsageLayout } from '../features/observability/TokenUsageLayout';
import { UserProfileModal } from '../features/profile/UserProfileModal';
import { LlmOnboardingModal } from '../features/profile/LlmOnboardingModal';
import { FindingDrawer } from '../features/remediation/FindingDrawer';
import { ChatWidget } from '../features/chat/ChatWidget';
import { FindingsView } from '../features/findings/FindingsView';

/**
 * ModalManager centralizes all overlay, dialog, modal, and drawer rendering.
 * Satisfies Single Responsibility Principle (SRP) by decoupling modal presentation
 * from the main App shell and views.
 */
export const ModalManager = memo(function ModalManager({ theme }) {
  const api = useApi();
  const toast = useToast();
  const auth = useAuth();
  const modals = useModals();
  const audit = useAudit();

  const {
    authSession, needsLlmOnboarding, handleLlmOnboardingComplete, logout,
  } = auth;

  const {
    activeModal, closeModal, confirmAction, closeConfirm,
    isTourOpen, closeTour, isViewingDocs, closeDocs,
    isViewingTokenUsage, closeObservability, isProfileModalOpen, closeProfile,
  } = modals;

  const {
    selectedFinding, handleCloseFindingDrawer, isAnalyzing, isPublishing,
    awaitingApproval, approvalPreview, publication, activeRun, graphSteps,
    analyzeFinding, loadApprovalPreview, submitDecision, retry, isUploadScan,
    chat, submitChat, cancelStream, resume, threadId, discard,
    forgetConversation, forgetRepository, runs, handleSelectFinding,
    handleBulkAction, refreshReport, batchState, cancelBatch, isAnalyzingMap,
  } = audit;

  return (
    <>
      {/* Documentation Modal */}
      {isViewingDocs && (
        <DocsView api={api} theme={theme} onClose={closeDocs} />
      )}

      {/* Observability / Token Usage Modal */}
      {isViewingTokenUsage && (
        <TokenUsageLayout api={api} theme={theme} onClose={closeObservability} />
      )}

      {/* Remediation Finding Drawer */}
      <FindingDrawer
        finding={selectedFinding}
        isAnalyzing={isAnalyzing}
        isPublishing={isPublishing}
        awaitingApproval={awaitingApproval}
        approvalPreview={approvalPreview}
        publication={publication}
        activeRun={activeRun}
        graphSteps={graphSteps}
        onClose={handleCloseFindingDrawer}
        onAnalyze={() => analyzeFinding(selectedFinding)}
        onLoadPreview={loadApprovalPreview}
        onDecision={submitDecision}
        onRetryPublish={retry}
        theme={theme}
        isUploadScan={isUploadScan}
      />

      {/* AI Assistant Chat Widget */}
      <ChatWidget
        chat={chat}
        onSubmit={submitChat}
        onCancel={cancelStream}
        activeRun={activeRun}
        publication={publication}
        onResume={() => publication?.approvedAt ? retry() : resume(threadId)}
        onDiscard={discard}
        onRetryPublish={retry}
        onForgetConversation={forgetConversation}
        onForgetRepository={forgetRepository}
      />

      {/* Findings Breakdown Modal */}
      <Modal isOpen={activeModal !== null} onClose={closeModal} title={activeModal?.title || 'Findings'}>
        {activeModal && (
          <FindingsView
            findings={activeModal.findings}
            runs={runs}
            onSelect={(finding) => {
              closeModal();
              handleSelectFinding(finding);
            }}
            onBulkAction={(action, ids) => {
              closeModal();
              handleBulkAction(action, ids);
            }}
            onRefresh={refreshReport}
            batchState={batchState}
            onCancelBatch={cancelBatch}
            isAnalyzingMap={isAnalyzingMap}
          />
        )}
      </Modal>

      {/* General Confirmation Dialog */}
      <ConfirmDialog
        isOpen={confirmAction !== null}
        onClose={closeConfirm}
        onConfirm={confirmAction?.action || (() => {})}
        title={confirmAction?.title}
        message={confirmAction?.message}
        confirmText={confirmAction?.confirmText}
        confirmStyle={confirmAction?.style}
      />

      {/* Onboarding Tour */}
      <OnboardingTour isOpen={isTourOpen} onClose={closeTour} />

      {/* User Profile & LLM Settings Modal */}
      <UserProfileModal
        isOpen={isProfileModalOpen}
        onClose={closeProfile}
        user={authSession?.user}
        api={api}
        toast={toast}
      />

      {/* Required LLM Onboarding Modal */}
      {needsLlmOnboarding && (
        <LlmOnboardingModal
          api={api}
          onComplete={handleLlmOnboardingComplete}
          onLogout={logout}
        />
      )}
    </>
  );
});
