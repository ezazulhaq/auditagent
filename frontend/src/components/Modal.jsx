import { X } from 'lucide-react';
import {
  Dialog,
  DialogBackdrop,
  DialogClose,
  DialogContent,
  DialogTitle,
} from '@ui-sdk/react/dialog';

export function Modal({ isOpen, onClose, title, children }) {
  if (!isOpen) return null;

  return (
    <Dialog open={Boolean(isOpen)} onOpenChange={(open) => { if (!open) onClose?.(); }}>
      <DialogBackdrop className="fixed inset-0 z-50 bg-[#07101b]/80 backdrop-blur-sm animate-in fade-in duration-200" />
      <DialogContent className="fixed inset-0 z-50 flex flex-col w-full max-w-6xl max-h-[90vh] my-auto mx-auto p-0 bg-slate-900 border border-slate-700/50 rounded-2xl shadow-2xl overflow-hidden">
        <header className="flex items-center justify-between px-5 py-4 border-b border-slate-700/50 bg-slate-950/50 shrink-0">
          <DialogTitle className="text-lg font-semibold text-white">{title}</DialogTitle>
          <DialogClose
            aria-label="Close"
            className="p-1.5 text-slate-400 rounded-lg hover:bg-slate-800 hover:text-slate-200 transition cursor-pointer"
          >
            <X size={18} />
          </DialogClose>
        </header>
        <div className="flex-1 overflow-auto">
          {children}
        </div>
      </DialogContent>
    </Dialog>
  );
}
