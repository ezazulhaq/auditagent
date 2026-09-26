import { CheckCircle2, GitFork, LockKeyhole, ScanSearch, ShieldCheck, Sparkles } from 'lucide-react';

const capabilities = [
  [ScanSearch, 'Repository-aware scanning', 'Audit the exact GitHub branch and commit you selected.'],
  [Sparkles, 'Verified AI remediation', 'Review evidence and the complete diff before any publication.'],
  [LockKeyhole, 'Controlled pull requests', 'Code changes require an explicit, bound approval decision.'],
];

export function LoginPage({ configured, error, onLogin }) {
  return (
    <main className="app-shell relative flex min-h-screen items-center justify-center overflow-hidden px-5 py-10 text-slate-200 sm:px-8">
      <div className="pointer-events-none absolute inset-0 opacity-40" aria-hidden="true">
        <div className="absolute left-[8%] top-[12%] h-64 w-64 rounded-full bg-cyan-500/10 blur-3xl" />
        <div className="absolute bottom-[4%] right-[5%] h-80 w-80 rounded-full bg-blue-600/10 blur-3xl" />
      </div>

      <section className="relative grid w-full max-w-6xl overflow-hidden rounded-3xl border border-slate-700/35 bg-[#091624]/92 shadow-[0_35px_100px_rgba(0,0,0,0.4)] backdrop-blur-xl lg:grid-cols-[1.15fr_0.85fr]">
        <div className="border-b border-slate-700/30 p-7 sm:p-10 lg:border-b-0 lg:border-r lg:p-14">
          <div className="flex items-center gap-3">
            <span className="flex h-11 w-11 items-center justify-center rounded-xl border border-cyan-300/20 bg-cyan-300/10 text-cyan-300"><ShieldCheck size={24} /></span>
            <div><p className="text-base font-bold text-white">AuditAgent</p><p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-500">Enterprise security workspace</p></div>
          </div>
          <p className="eyebrow mt-12">Secure software delivery</p>
          <h1 className="mt-3 max-w-xl text-3xl font-semibold leading-tight tracking-[-0.03em] text-white sm:text-4xl lg:text-[42px]">Find risk. Verify the fix. Ship with confidence.</h1>
          <p className="mt-5 max-w-xl text-sm leading-7 text-slate-400 sm:text-[15px]">One governed workspace for repository scanning, evidence-led remediation, and review-ready security pull requests.</p>

          <div className="mt-9 grid gap-4 sm:grid-cols-3 lg:grid-cols-1">
            {capabilities.map(([Icon, title, description]) => <div key={title} className="flex gap-3">
              <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-slate-700/50 bg-slate-800/55 text-cyan-300"><Icon size={15} /></span>
              <div><p className="text-xs font-semibold text-slate-200">{title}</p><p className="mt-1 text-[11px] leading-5 text-slate-500">{description}</p></div>
            </div>)}
          </div>
        </div>

        <div className="flex flex-col justify-center p-7 sm:p-10 lg:p-12">
          <div className="mb-8">
            <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-400/20 bg-emerald-400/8 px-2.5 py-1 text-[10px] font-semibold text-emerald-300"><span className="h-1.5 w-1.5 rounded-full bg-emerald-400" /> Protected access</span>
            <h2 className="mt-5 text-2xl font-semibold tracking-tight text-white">Sign in to your workspace</h2>
            <p className="mt-2 text-sm leading-6 text-slate-400">Use your authorized GitHub account to access installed repositories and managed audit history.</p>
          </div>

          {!configured && <div role="alert" className="mb-4 rounded-xl border border-amber-400/25 bg-amber-400/8 p-4 text-xs leading-5 text-amber-100">
            GitHub App authentication is not configured. Set the required GitHub App, encryption, webhook, callback, and managed-workspace secrets on the server.
          </div>}
          {error && <div role="alert" className="mb-4 rounded-xl border border-rose-400/25 bg-rose-400/8 p-4 text-xs leading-5 text-rose-200">{error}</div>}

          <button type="button" onClick={onLogin} disabled={!configured}
            className="primary-action flex w-full items-center justify-center gap-2.5 px-4 py-3 text-sm font-bold">
            <GitFork size={18} /> Continue with GitHub
          </button>

          <div className="mt-6 space-y-3 border-t border-slate-700/30 pt-6">
            <p className="flex items-start gap-2 text-[11px] leading-5 text-slate-500"><CheckCircle2 size={14} className="mt-0.5 shrink-0 text-emerald-400" /> GitHub credentials remain server-side and encrypted.</p>
            <p className="flex items-start gap-2 text-[11px] leading-5 text-slate-500"><CheckCircle2 size={14} className="mt-0.5 shrink-0 text-emerald-400" /> No branch, commit, or pull request is created until you approve a verified diff.</p>
          </div>
        </div>
      </section>
    </main>
  );
}