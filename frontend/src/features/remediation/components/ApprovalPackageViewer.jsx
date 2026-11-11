import { memo } from 'react';
import { ShieldCheck, FileDiff, Check } from 'lucide-react';
import { DiffViewer } from '../../../components/DiffViewer';

/**
 * ApprovalPackageViewer renders verified fix details, changed files, verification evidence, and the diff.
 * Adheres to Single Responsibility Principle (SRP).
 */
export const ApprovalPackageViewer = memo(function ApprovalPackageViewer({ approvalPreview }) {
  if (!approvalPreview) return null;

  return (
    <section className="overflow-hidden rounded-2xl border border-cyan-400/22 bg-cyan-400/[0.035]">
      <header className="border-b border-cyan-400/15 bg-cyan-400/4.5 p-4 sm:p-5">
        <div className="flex items-start gap-3">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-cyan-400/20 bg-cyan-400/10 text-cyan-300">
            <ShieldCheck size={18} />
          </span>
          <div>
            <h3 className="text-sm font-semibold text-cyan-100">Verified approval package</h3>
            <p className="mt-1 text-[11px] leading-5 text-slate-400">
              Review the exact verified change. Approval authorizes one bot branch, commit, push, and ready-for-review pull request.
            </p>
          </div>
        </div>
      </header>

      <div className="space-y-5 p-4 sm:p-5">
        <dl className="grid gap-3 text-xs sm:grid-cols-2">
          <div className="rounded-xl border border-slate-700/30 bg-slate-950/25 p-3">
            <dt className="text-[9px] font-bold uppercase tracking-wider text-slate-600">Repository / base</dt>
            <dd className="mt-1.5 break-all text-slate-300">{approvalPreview.repository} · {approvalPreview.baseBranch}</dd>
          </div>
          <div className="rounded-xl border border-slate-700/30 bg-slate-950/25 p-3">
            <dt className="text-[9px] font-bold uppercase tracking-wider text-slate-600">Pinned commit</dt>
            <dd className="mt-1.5 break-all font-mono text-[11px] text-slate-300">{approvalPreview.baseSha}</dd>
          </div>
          <div className="rounded-xl border border-slate-700/30 bg-slate-950/25 p-3 sm:col-span-2">
            <dt className="text-[9px] font-bold uppercase tracking-wider text-slate-600">Planned branch</dt>
            <dd className="mt-1.5 break-all font-mono text-[11px] text-slate-300">{approvalPreview.branchName}</dd>
          </div>
        </dl>

        <div>
          <h4 className="text-[9px] font-bold uppercase tracking-[0.12em] text-slate-600">Changed files</h4>
          <ul className="mt-2 space-y-1.5">
            {approvalPreview.changedFiles?.map(file => (
              <li key={file} className="flex items-start gap-2 break-all rounded-lg border border-slate-700/25 bg-slate-950/20 px-3 py-2 font-mono text-[10px] text-slate-300">
                <FileDiff size={12} className="mt-0.5 shrink-0 text-cyan-300" />
                {file}
              </li>
            ))}
          </ul>
        </div>

        <div>
          <h4 className="text-[9px] font-bold uppercase tracking-[0.12em] text-slate-600">Verification evidence</h4>
          <div className="mt-2 grid gap-2 sm:grid-cols-2">
            {Object.entries(approvalPreview.verification ?? {}).map(([name, value]) => (
              <div key={name} className="rounded-xl border border-emerald-400/12 bg-emerald-400/[0.035] p-3 text-xs">
                <span className="flex items-center gap-1.5 font-semibold text-emerald-300">
                  <Check size={12} /> {name}
                </span>
                <p className="mt-1.5 wrap-break-word text-[10px] leading-4 text-slate-500">
                  {String(value)}
                </p>
              </div>
            ))}
          </div>
        </div>

        <div data-testid="approval-diff">
          <DiffViewer diffText={approvalPreview.diff} initialMode="side-by-side" />
        </div>
      </div>
    </section>
  );
});
