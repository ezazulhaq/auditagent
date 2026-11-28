import { CheckCircle2, GitFork, LockKeyhole, ScanSearch, ShieldCheck, Sparkles } from 'lucide-react';

const capabilities = [
  [ScanSearch, 'Repository-aware scanning', 'Audit the exact GitHub branch and commit you selected.'],
  [Sparkles, 'Verified AI remediation', 'Review evidence and the complete diff before any publication.'],
  [LockKeyhole, 'Controlled pull requests', 'Code changes require an explicit, bound approval decision.'],
];

export function LoginPage({ configured, error, onLogin }) {
  return (
    <main className="app-shell relative flex min-h-dvh flex-col justify-center px-4 py-5 text-slate-200 sm:px-8 sm:py-10">
      <div className="pointer-events-none absolute inset-0 overflow-hidden opacity-40" aria-hidden="true">
        <div className="absolute left-[8%] top-[12%] h-64 w-64 rounded-full bg-cyan-500/10 blur-3xl" />
        <div className="absolute bottom-[4%] right-[5%] h-80 w-80 rounded-full bg-blue-600/10 blur-3xl" />
      </div>

      <section className="relative mx-auto my-auto grid w-full max-w-6xl overflow-hidden rounded-2xl border border-[var(--border-subtle)] bg-[var(--surface-card)] shadow-[0_35px_100px_rgba(0,0,0,0.4)] backdrop-blur-xl sm:rounded-3xl lg:grid-cols-[1.15fr_0.85fr] lg:grid-rows-[auto_1fr]">
        <div className="border-b border-[var(--border-subtle)] px-5 py-4 sm:px-10 sm:py-7 lg:col-start-1 lg:row-start-1 lg:border-b-0 lg:border-r lg:px-14 lg:pt-14 lg:pb-0">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex min-w-0 items-center gap-3">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-cyan-400/25 bg-cyan-400/10 text-cyan-300 sm:h-11 sm:w-11">
                <ShieldCheck size={22} className="sm:h-6 sm:w-6" aria-hidden="true" />
              </span>
              <div className="min-w-0">
                <p className="text-base font-bold text-white font-sans">AuditAgent</p>
                <p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-500">Enterprise security workspace</p>
              </div>
            </div>
            <span className="inline-flex shrink-0 items-center gap-1.5 rounded-full border border-emerald-400/20 bg-emerald-400/8 px-2.5 py-1 text-[10px] font-semibold text-emerald-300 lg:hidden">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" /> Protected access
            </span>
          </div>
        </div>

        <section
          aria-labelledby="login-heading"
          className="flex flex-col justify-center p-5 sm:p-10 lg:col-start-2 lg:row-start-1 lg:row-span-2 lg:p-12"
        >
          <div className="mb-5 sm:mb-8">
            <span className="hidden items-center gap-1.5 rounded-full border border-emerald-400/20 bg-emerald-400/8 px-2.5 py-1 text-[10px] font-semibold text-emerald-300 lg:inline-flex">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" /> Protected access
            </span>
            <h1 id="login-heading" className="text-balance text-xl font-bold tracking-tight text-white font-sans sm:text-2xl lg:mt-5">
              Sign in to your workspace
            </h1>
            <p className="mt-1.5 text-pretty text-sm leading-6 text-slate-400 sm:mt-2">
              Use your authorized GitHub account to access installed repositories and managed audit history.
            </p>
          </div>

          {!configured && <div role="alert" className="mb-4 rounded-xl border border-amber-400/25 bg-amber-400/8 p-4 text-xs leading-5 text-amber-100">
            GitHub App authentication is not configured. Set the required GitHub App, encryption, webhook, callback, and managed-workspace secrets on the server.
          </div>}
          {error && <div role="alert" className="mb-4 rounded-xl border border-rose-400/25 bg-rose-400/8 p-4 text-xs leading-5 text-rose-200">{error}</div>}

          <button type="button" onClick={onLogin} disabled={!configured}
            className="primary-action flex min-h-12 w-full items-center justify-center gap-2.5 px-4 py-3 text-sm font-bold">
            <GitFork size={18} aria-hidden="true" /> Continue with GitHub
          </button>

          <div className="mt-5 space-y-2.5 border-t border-slate-700/30 pt-5 sm:mt-6 sm:space-y-3 sm:pt-6">
            <p className="flex items-start gap-2 text-[11px] leading-5 text-slate-500"><CheckCircle2 size={14} className="mt-0.5 shrink-0 text-emerald-400" aria-hidden="true" /> GitHub credentials remain server-side and encrypted.</p>
            <p className="flex items-start gap-2 text-[11px] leading-5 text-slate-500"><CheckCircle2 size={14} className="mt-0.5 shrink-0 text-emerald-400" aria-hidden="true" /> No branch, commit, or pull request is created until you approve a verified diff.</p>
          </div>
        </section>

        <section
          aria-labelledby="login-overview-heading"
          className="border-t border-[var(--border-subtle)] p-5 sm:p-10 lg:col-start-1 lg:row-start-2 lg:border-t-0 lg:border-r lg:px-14 lg:pb-14 lg:pt-12"
        >
          <p className="eyebrow">Secure software delivery</p>
          <h2 id="login-overview-heading" className="mt-2.5 max-w-xl text-balance text-2xl font-bold leading-tight tracking-tight text-white sm:mt-3 sm:text-4xl lg:text-[42px] font-sans">
            Find risk. Verify the fix. Ship with confidence.
          </h2>
          <p className="mt-3 max-w-xl text-pretty text-sm leading-6 text-slate-400 sm:mt-5 sm:text-[15px] sm:leading-7">
            One governed workspace for repository scanning, evidence-led remediation, and review-ready security pull requests.
          </p>

          <div className="mt-6 grid gap-4 sm:mt-8 sm:grid-cols-3 lg:mt-9 lg:grid-cols-1">
            {capabilities.map(([Icon, title, description]) => <div key={title} className="flex gap-3">
              <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-inset)]/60 text-cyan-300"><Icon size={15} aria-hidden="true" /></span>
              <div className="min-w-0"><p className="text-xs font-semibold text-slate-200">{title}</p><p className="mt-1 text-[11px] leading-5 text-slate-500">{description}</p></div>
            </div>)}
          </div>
        </section>
      </section>
    </main>
  );
}