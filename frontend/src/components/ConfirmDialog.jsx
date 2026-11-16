import { AlertTriangle } from 'lucide-react';
import {
  AlertDialog,
  AlertDialogBackdrop,
  AlertDialogClose,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogTitle,
} from '@ui-sdk/react/alert-dialog';

export function ConfirmDialog({
  isOpen,
  onClose,
  onConfirm,
  title,
  message,
  confirmText = 'Confirm',
  confirmStyle = 'danger',
}) {
  if (!isOpen) return null;

  return (
    <AlertDialog open={Boolean(isOpen)} onOpenChange={(open) => { if (!open) onClose?.(); }}>
      <AlertDialogBackdrop className="fixed inset-0 z-50 bg-[#07101b]/80 backdrop-blur-sm animate-in fade-in duration-200" />
      <AlertDialogContent className="fixed inset-0 z-50 flex flex-col w-full max-w-lg my-auto mx-auto p-6 bg-slate-900 border border-slate-700/50 rounded-2xl shadow-2xl">
        <div className="flex items-start gap-4 mb-6">
          <div
            className={`p-3 rounded-full shrink-0 ${
              confirmStyle === 'danger' ? 'bg-rose-500/10 text-rose-400' : 'bg-cyan-500/10 text-cyan-400'
            }`}
          >
            <AlertTriangle size={24} />
          </div>
          <div>
            <AlertDialogTitle className="text-base font-semibold text-white mb-1.5">{title}</AlertDialogTitle>
            <AlertDialogDescription className="text-sm text-slate-300 leading-relaxed">{message}</AlertDialogDescription>
          </div>
        </div>
        <div className="flex justify-end gap-3 pt-4 border-t border-slate-700/50">
          <AlertDialogClose
            onClick={onClose}
            className="px-4 py-2 rounded-xl text-sm font-semibold text-slate-300 hover:bg-slate-800 hover:text-white transition cursor-pointer"
          >
            Cancel
          </AlertDialogClose>
          <AlertDialogClose
            value="confirm"
            data-agent-confirm={`Confirm ${title}`}
            onClick={() => {
              onConfirm?.();
              onClose?.();
            }}
            className={`px-4 py-2 rounded-xl text-sm font-semibold text-white transition shadow-lg cursor-pointer ${
              confirmStyle === 'danger'
                ? 'bg-rose-500 hover:bg-rose-600 shadow-rose-900/20'
                : 'bg-cyan-500 hover:bg-cyan-600 shadow-cyan-900/20'
            }`}
          >
            {confirmText}
          </AlertDialogClose>
        </div>
      </AlertDialogContent>
    </AlertDialog>
  );
}
