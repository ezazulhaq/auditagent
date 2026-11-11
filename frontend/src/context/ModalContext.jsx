import { createContext, useContext, useState, useCallback, useMemo } from 'react';

export const ModalContext = createContext(null);

export const MODAL_TYPES = {
  FINDINGS: 'findings',
  DOCS: 'docs',
  OBSERVABILITY: 'observability',
  PROFILE: 'profile',
  TOUR: 'tour',
};

/**
 * ModalProvider centralizes modal, drawer, sidebar, and confirm dialog state.
 * Implements Open/Closed Principle (OCP) and Interface Segregation Principle (ISP).
 */
export function ModalProvider({ children }) {
  const [activeModal, setActiveModal] = useState(null); // { type, title, findings, props }
  const [confirmAction, setConfirmAction] = useState(null); // { title, message, confirmText, style, action }
  const [isTourOpen, setIsTourOpen] = useState(false);
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);
  const [isViewingDocs, setIsViewingDocs] = useState(false);
  const [isViewingTokenUsage, setIsViewingTokenUsage] = useState(false);
  const [isProfileModalOpen, setIsProfileModalOpen] = useState(false);

  // Findings modal helper
  const openFindingsModal = useCallback((title, modalFindings) => {
    setActiveModal({ type: MODAL_TYPES.FINDINGS, title, findings: modalFindings });
  }, []);

  const closeModal = useCallback(() => {
    setActiveModal(null);
  }, []);

  // Confirm dialog helper
  const showConfirm = useCallback((config) => {
    setConfirmAction(config);
  }, []);

  const closeConfirm = useCallback(() => {
    setConfirmAction(null);
  }, []);

  // Dedicated modal open/close helpers
  const openTour = useCallback(() => setIsTourOpen(true), []);
  const closeTour = useCallback(() => setIsTourOpen(false), []);

  const openDocs = useCallback(() => setIsViewingDocs(true), []);
  const closeDocs = useCallback(() => setIsViewingDocs(false), []);

  const openObservability = useCallback(() => setIsViewingTokenUsage(true), []);
  const closeObservability = useCallback(() => setIsViewingTokenUsage(false), []);

  const openProfile = useCallback(() => setIsProfileModalOpen(true), []);
  const closeProfile = useCallback(() => setIsProfileModalOpen(false), []);

  const toggleSidebar = useCallback(() => setIsSidebarOpen(prev => !prev), []);
  const closeSidebar = useCallback(() => setIsSidebarOpen(false), []);

  /**
   * Escape key hierarchy handler: closes the top-most active overlay.
   */
  const handleTopLevelClose = useCallback((additionalCloseHandlers = {}) => {
    if (confirmAction !== null) {
      setConfirmAction(null);
      return true;
    }
    if (activeModal !== null) {
      setActiveModal(null);
      return true;
    }
    if (isProfileModalOpen) {
      setIsProfileModalOpen(false);
      return true;
    }
    if (isViewingDocs) {
      setIsViewingDocs(false);
      return true;
    }
    if (isViewingTokenUsage) {
      setIsViewingTokenUsage(false);
      return true;
    }
    if (isTourOpen) {
      setIsTourOpen(false);
      return true;
    }
    if (additionalCloseHandlers.onCloseFindingDrawer && additionalCloseHandlers.isFindingDrawerOpen) {
      additionalCloseHandlers.onCloseFindingDrawer();
      return true;
    }
    if (additionalCloseHandlers.onCloseChat && additionalCloseHandlers.isChatOpen) {
      additionalCloseHandlers.onCloseChat();
      return true;
    }
    return false;
  }, [
    confirmAction,
    activeModal,
    isProfileModalOpen,
    isViewingDocs,
    isViewingTokenUsage,
    isTourOpen,
  ]);

  const value = useMemo(() => ({
    activeModal,
    openFindingsModal,
    closeModal,
    confirmAction,
    showConfirm,
    closeConfirm,
    isTourOpen,
    openTour,
    closeTour,
    isSidebarOpen,
    toggleSidebar,
    closeSidebar,
    isViewingDocs,
    openDocs,
    closeDocs,
    isViewingTokenUsage,
    openObservability,
    closeObservability,
    isProfileModalOpen,
    openProfile,
    closeProfile,
    handleTopLevelClose,
  }), [
    activeModal,
    openFindingsModal,
    closeModal,
    confirmAction,
    showConfirm,
    closeConfirm,
    isTourOpen,
    openTour,
    closeTour,
    isSidebarOpen,
    toggleSidebar,
    closeSidebar,
    isViewingDocs,
    openDocs,
    closeDocs,
    isViewingTokenUsage,
    openObservability,
    closeObservability,
    isProfileModalOpen,
    openProfile,
    closeProfile,
    handleTopLevelClose,
  ]);

  return (
    <ModalContext.Provider value={value}>
      {children}
    </ModalContext.Provider>
  );
}

/**
 * Hook to consume modal and dialog state.
 */
export function useModals() {
  const context = useContext(ModalContext);
  if (!context) {
    throw new Error('useModals must be used within a ModalProvider');
  }
  return context;
}
