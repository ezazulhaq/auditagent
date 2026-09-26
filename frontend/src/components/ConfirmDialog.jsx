import { Modal } from './Modal';
import { AlertTriangle } from 'lucide-react';

export function ConfirmDialog({ isOpen, onClose, onConfirm, title, message, confirmText = 'Confirm', confirmStyle = 'danger' }) {
  return (
    <Modal isOpen={isOpen} onClose={onClose} title={title}>
      <div className="p-6">
        <div className="flex items-start gap-4 mb-6">
          <div className={`p-3 rounded-full shrink-0 ${confirmStyle === 'danger' ? 'bg-rose-500/10 text-rose-400' : 'bg-cyan-500/10 text-cyan-400'}`}>
            <AlertTriangle size={24} />
          </div>
          <div>
            <p className="text-sm text-slate-300 leading-relaxed">{message}</p>
          </div>
        </div>
        <div className="flex justify-end gap-3 pt-4 border-t border-slate-700/50">
          <button onClick={onClose} className="px-4 py-2 rounded-xl text-sm font-semibold text-slate-300 hover:bg-slate-800 hover:text-white transition">Cancel</button>
          <button onClick={() => { onConfirm(); onClose(); }} className={`px-4 py-2 rounded-xl text-sm font-semibold text-white transition shadow-lg ${confirmStyle === 'danger' ? 'bg-rose-500 hover:bg-rose-600 shadow-rose-900/20' : 'bg-cyan-500 hover:bg-cyan-600 shadow-cyan-900/20'}`}>
            {confirmText}
          </button>
        </div>
      </div>
    </Modal>
  );
}
