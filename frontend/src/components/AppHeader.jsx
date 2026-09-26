import { Activity, LogOut, Moon, ShieldCheck, Sun, PlayCircle, BookOpen, ChevronDown, Menu, X } from 'lucide-react';
import { NotificationDropdown } from './NotificationDropdown';
import { useState, useRef, useEffect } from 'react';

export function AppHeader({ metadata, counts, user, onLogout, findings = [], onShowFindings, onShowObservability, theme = 'dark', onToggleTheme, onTourClick, onShowDocs, onToggleSidebar, isSidebarOpen }) {
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
    <header className="sticky top-0 z-30 h-18 shrink-0 border-b border-slate-700/25 bg-[#071321]/88 px-4 backdrop-blur-xl md:px-6 xl:px-8">
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
          <div className="relative flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-cyan-300/20 bg-linear-to-br from-cyan-300/16 to-blue-500/8 text-cyan-300 shadow-lg shadow-cyan-950/30">
            <ShieldCheck size={22} strokeWidth={1.8} />
            <span className="absolute -right-0.5 -top-0.5 h-2.5 w-2.5 rounded-full border-2 border-[#071321] bg-emerald-400" />
          </div>
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <h1 className="truncate text-[15px] font-bold tracking-tight text-white sm:text-base">AuditAgent</h1>
              <span className="hidden rounded-md border border-slate-700/70 bg-slate-800/45 px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-[0.12em] text-slate-400 sm:inline">Enterprise</span>
            </div>
            <p className="truncate text-[10px] font-medium tracking-wide text-slate-500 sm:text-[11px]">AI security operations workspace</p>
          </div>
        </div>

        <div className="flex min-w-0 items-center gap-2 md:gap-3">
          <div className="hidden items-center gap-2 rounded-xl border border-slate-700/30 bg-slate-900/35 px-3 py-2 lg:flex">
            <Activity size={14} className="text-emerald-400" />
            <div className="max-w-52 truncate text-xs">
              <span className="font-semibold text-slate-300">{metadata?.projectName || 'Workspace ready'}</span>
              <span className="ml-2 text-slate-600">{metadata ? `${metadata.totalFilesScanned ?? 0} files analyzed` : 'Select a repository'}</span>
            </div>
          </div>

          <div className="hidden items-center gap-2 rounded-xl border border-slate-700/30 bg-slate-900/35 px-3 py-2 sm:flex" aria-label="Finding summary">
            <button
              type="button"
              onClick={() => onShowFindings?.('High Severity Findings', findings.filter(f => f.severity === 'HIGH'))}
              className="flex items-center gap-1 text-[11px] text-slate-500 hover:text-slate-300 transition focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-cyan-400 rounded-sm"
            >
              <b className="text-sm text-rose-400">{counts.high}</b> High
            </button>
            <span className="h-4 w-px bg-slate-700/70" />
            <button
              type="button"
              onClick={() => onShowFindings?.('Medium Severity Findings', findings.filter(f => f.severity === 'MEDIUM'))}
              className="flex items-center gap-1 text-[11px] text-slate-500 hover:text-slate-300 transition focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-cyan-400 rounded-sm"
            >
              <b className="text-sm text-amber-300">{counts.medium}</b> Medium
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
            <NotificationDropdown />
            <div className="relative" ref={userMenuRef}>
              <button
                type="button"
                onClick={() => setIsUserMenuOpen(!isUserMenuOpen)}
                className="flex items-center gap-2 rounded-xl p-1 pr-2 transition hover:bg-slate-800/70"
              >
                {user?.avatarUrl
                  ? <img src={user.avatarUrl} alt="" className="h-8 w-8 rounded-lg border border-slate-600/60 bg-slate-800 object-cover" />
                  : <span className="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-700 bg-slate-800 text-xs font-bold text-cyan-200">{(user?.login || 'U').slice(0, 1).toUpperCase()}</span>}
                <ChevronDown size={14} className="text-slate-400 ml-1" />
              </button>

              {isUserMenuOpen && (
                <div className="absolute right-0 mt-2 w-56 rounded-xl border border-slate-700 bg-[#0a1421] py-2 shadow-2xl z-50">
                  <div className="px-4 py-2 border-b border-slate-700/50 mb-1">
                    <p className="truncate text-sm font-semibold text-white">{user?.name || user?.login || 'Signed in'}</p>
                    <p className="truncate text-xs text-slate-400">{user?.login || 'User'}</p>
                  </div>

                  <button
                    type="button"
                    onClick={() => { setIsUserMenuOpen(false); onShowDocs(); }}
                    className="flex w-full items-center gap-3 px-4 py-2 text-left text-sm text-slate-300 transition hover:bg-slate-400/10"
                  >
                    <BookOpen size={16} className="text-cyan-400" />
                    Documentation
                  </button>

                  <button
                    type="button"
                    onClick={() => { setIsUserMenuOpen(false); onShowObservability(); }}
                    className="flex w-full items-center gap-3 px-4 py-2 text-left text-sm text-slate-300 transition hover:bg-slate-400/10"
                  >
                    <Activity size={16} className="text-emerald-400" />
                    Token Usage
                  </button>

                  <button
                    type="button"
                    onClick={() => { setIsUserMenuOpen(false); onToggleTheme(); }}
                    className="flex w-full items-center gap-3 px-4 py-2 text-left text-sm text-slate-300 transition hover:bg-slate-400/10"
                  >
                    {theme === 'light' ? <Moon size={16} className="text-amber-500" /> : <Sun size={16} className="text-amber-300" />}
                    {theme === 'light' ? 'Dark Mode' : 'Light Mode'}
                  </button>

                  <div className="my-1 border-t border-slate-700/50" />

                  <button
                    type="button"
                    onClick={() => { setIsUserMenuOpen(false); onLogout(); }}
                    className="flex w-full items-center gap-3 px-4 py-2 text-left text-sm text-rose-400 transition hover:bg-rose-500/10"
                  >
                    <LogOut size={16} />
                    Sign out
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