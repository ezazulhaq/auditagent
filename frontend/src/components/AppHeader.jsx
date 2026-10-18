import { Activity, LogOut, Moon, ShieldCheck, Sun, PlayCircle, BookOpen, ChevronDown, Menu, X } from 'lucide-react';
import { NotificationDropdown } from './NotificationDropdown';
import { useState, useRef, useEffect } from 'react';

export function AppHeader({ metadata, counts, user, onLogout, findings = [], onShowFindings, onShowObservability, theme = 'dark', onToggleTheme, onTourClick, onShowDocs, onToggleSidebar, isSidebarOpen, api }) {
  const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);
  const userMenuRef = useRef(null);

  useEffect(() => {
    function handleClickOutside(event) {
      if (userMenuRef.current && !userMenuRef.current.contains(event.target)) {
        setIsUserMenuOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);
  return (
    <header className="sticky top-0 z-30 h-18 shrink-0 border-b border-[var(--surface-panel-border)] bg-[var(--surface-panel)]/90 px-4 backdrop-blur-xl md:px-6 xl:px-8">
      <div className="mx-auto flex h-full max-w-450 items-center justify-between gap-4">
        <div className="flex min-w-0 items-center gap-3">
          <button
            type="button"
            onClick={onToggleSidebar}
            className="flex h-10 w-10 items-center justify-center rounded-xl border border-slate-700/50 bg-slate-800/50 text-slate-300 transition hover:bg-slate-700/50 lg:hidden"
            aria-label="Toggle Sidebar"
          >
            {isSidebarOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
          <div className="relative flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-cyan-400/25 bg-linear-to-br from-cyan-400/15 to-indigo-500/10 text-cyan-300 shadow-md shadow-cyan-950/20">
            <ShieldCheck size={22} strokeWidth={1.8} />
            <span className="absolute -right-0.5 -top-0.5 h-2.5 w-2.5 rounded-full border-2 border-[var(--surface-panel)] bg-emerald-400" />
          </div>
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <h1 className="truncate text-[15px] font-bold tracking-tight text-white sm:text-base font-sans">AuditAgent</h1>
              <span className="hidden rounded-md border border-cyan-400/20 bg-cyan-400/10 px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-[0.14em] text-cyan-300 sm:inline">Enterprise</span>
            </div>
            <p className="truncate text-[10px] font-medium tracking-wide text-slate-400 sm:text-[11px]">AI security operations workspace</p>
          </div>
        </div>

        <div className="flex min-w-0 items-center gap-2 md:gap-3">
          <div className="hidden items-center gap-2 rounded-xl border border-slate-700/30 bg-slate-900/35 px-3 py-2 lg:flex">
            <Activity size={14} className="text-emerald-400" />
            <div className="max-w-52 truncate text-xs">
              <span className="font-semibold text-slate-200">{metadata?.projectName || 'Workspace ready'}</span>
              <span className="ml-2 text-slate-500">{metadata ? `${metadata.totalFilesScanned ?? 0} files analyzed` : 'Select a repository'}</span>
            </div>
          </div>

          <div className="hidden items-center gap-2 rounded-xl border border-slate-700/30 bg-slate-900/35 px-3 py-2 sm:flex" aria-label="Finding summary">
            <button
              type="button"
              onClick={() => onShowFindings?.('High Severity Findings', findings.filter(f => f.severity === 'HIGH'))}
              className="flex items-center gap-1.5 text-[11px] text-slate-400 hover:text-slate-200 transition focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-cyan-400 rounded-sm"
            >
              <b className="font-mono text-sm font-bold tabular-nums text-rose-400">{counts.high}</b> High
            </button>
            <span className="h-4 w-px bg-slate-700/70" />
            <button
              type="button"
              onClick={() => onShowFindings?.('Medium Severity Findings', findings.filter(f => f.severity === 'MEDIUM'))}
              className="flex items-center gap-1.5 text-[11px] text-slate-400 hover:text-slate-200 transition focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-cyan-400 rounded-sm"
            >
              <b className="font-mono text-sm font-bold tabular-nums text-amber-300">{counts.medium}</b> Medium
            </button>
          </div>

          <div className="ml-0.5 flex items-center gap-2 border-l border-slate-700/40 pl-2 md:ml-1 md:pl-3">
            <button
              type="button"
              onClick={onTourClick}
              className="hidden items-center gap-1.5 rounded-lg border border-cyan-400/20 bg-cyan-400/10 px-3 py-1.5 text-xs font-semibold text-cyan-300 transition hover:bg-cyan-400/20 hover:text-cyan-200 md:flex mr-2"
            >
              <PlayCircle size={14} />
            </button>
            <NotificationDropdown api={api} />
            <div className="relative" ref={userMenuRef}>
              <button
                type="button"
                onClick={() => setIsUserMenuOpen(!isUserMenuOpen)}
                className={`flex items-center gap-2 rounded-xl p-1 pr-2 transition border ${
                  isUserMenuOpen
                    ? 'border-[var(--border-subtle)] bg-[var(--surface-inset)] ring-1 ring-[var(--border-subtle)]'
                    : 'border-transparent hover:border-[var(--border-subtle)] hover:bg-[var(--surface-inset)]/70'
                }`}
                aria-expanded={isUserMenuOpen}
                aria-label="User profile menu"
              >
                {user?.avatarUrl
                  ? <img src={user.avatarUrl} alt="" className="h-8 w-8 rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-inset)] object-cover" />
                  : <span className="flex h-8 w-8 items-center justify-center rounded-lg border border-cyan-500/25 bg-cyan-500/10 text-xs font-bold text-cyan-600 dark:text-cyan-300">{(user?.login || 'U').slice(0, 1).toUpperCase()}</span>}
                <ChevronDown size={14} className={`text-[var(--text-muted)] transition-transform duration-150 ${isUserMenuOpen ? 'rotate-180' : ''}`} />
              </button>

              {isUserMenuOpen && (
                <div className="absolute right-0 mt-2 w-60 rounded-2xl border border-[var(--border-subtle)] bg-[var(--surface-card)] p-1.5 shadow-2xl shadow-slate-900/10 dark:shadow-black/50 backdrop-blur-xl z-50">
                  <div className="px-3.5 py-2.5 rounded-xl border border-[var(--border-subtle)] bg-[var(--surface-inset)]/70 mb-1.5">
                    <p className="truncate text-xs font-bold tracking-tight text-[var(--text-primary)]">{user?.name || user?.login || 'Signed in'}</p>
                    <p className="truncate text-[10px] font-mono text-[var(--text-muted)] mt-0.5">{user?.login ? `@${user.login}` : 'Auditor'}</p>
                  </div>

                  <div className="space-y-0.5">
                    <button
                      type="button"
                      onClick={() => { setIsUserMenuOpen(false); onShowDocs(); }}
                      className="flex w-full items-center gap-2.5 rounded-xl px-3 py-2 text-left text-xs font-medium text-[var(--text-secondary)] transition hover:bg-[var(--surface-inset)] hover:text-[var(--text-primary)]"
                    >
                      <BookOpen size={15} className="text-cyan-600 dark:text-cyan-400 shrink-0" />
                      <span>Documentation</span>
                    </button>

                    <button
                      type="button"
                      onClick={() => { setIsUserMenuOpen(false); onShowObservability(); }}
                      className="flex w-full items-center gap-2.5 rounded-xl px-3 py-2 text-left text-xs font-medium text-[var(--text-secondary)] transition hover:bg-[var(--surface-inset)] hover:text-[var(--text-primary)]"
                    >
                      <Activity size={15} className="text-emerald-600 dark:text-emerald-400 shrink-0" />
                      <span>Token Usage</span>
                    </button>

                    <button
                      type="button"
                      onClick={() => { setIsUserMenuOpen(false); onToggleTheme(); }}
                      className="flex w-full items-center justify-between rounded-xl px-3 py-2 text-left text-xs font-medium text-[var(--text-secondary)] transition hover:bg-[var(--surface-inset)] hover:text-[var(--text-primary)]"
                    >
                      <div className="flex items-center gap-2.5">
                        {theme === 'light' ? <Moon size={15} className="text-amber-500 shrink-0" /> : <Sun size={15} className="text-amber-300 shrink-0" />}
                        <span>{theme === 'light' ? 'Dark Mode' : 'Light Mode'}</span>
                      </div>
                      <span className="text-[10px] uppercase font-mono px-1.5 py-0.5 rounded border border-[var(--border-subtle)] bg-[var(--surface-inset)] text-[var(--text-muted)]">
                        {theme === 'light' ? 'Light' : 'Dark'}
                      </span>
                    </button>
                  </div>

                  <div className="my-1 border-t border-[var(--border-subtle)]" />

                  <button
                    type="button"
                    onClick={() => { setIsUserMenuOpen(false); onLogout(); }}
                    className="flex w-full items-center gap-2.5 rounded-xl px-3 py-2 text-left text-xs font-medium text-rose-500 transition hover:bg-rose-500/10 hover:text-rose-600 dark:text-rose-400 dark:hover:text-rose-300"
                  >
                    <LogOut size={15} className="shrink-0" />
                    <span>Sign out</span>
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </header>
  );
}