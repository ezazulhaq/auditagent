export function SkeletonApp() {
  return (
    <div className="flex min-h-screen flex-col bg-[#070b14] pointer-events-none">
      {/* Header Skeleton */}
      <header className="flex h-[72px] shrink-0 items-center justify-between border-b border-slate-700/30 bg-[#0b1a2b]/95 px-4 sm:px-6">
        <div className="flex items-center gap-6">
          <div className="h-6 w-32 animate-pulse rounded-md bg-slate-800/80" />
          <div className="hidden h-5 w-48 animate-pulse rounded-md bg-slate-800/50 lg:block" />
        </div>
        <div className="flex items-center gap-4">
          <div className="h-8 w-24 animate-pulse rounded-lg bg-slate-800/80" />
          <div className="h-8 w-8 animate-pulse rounded-full bg-slate-800/80" />
        </div>
      </header>

      <div className="mx-auto flex w-full max-w-[1800px] flex-1 flex-col lg:flex-row">
        {/* Sidebar Skeleton */}
        <aside className="shrink-0 border-b border-slate-700/25 bg-[#081522]/82 p-5 sm:p-6 lg:p-5 xl:p-6 lg:h-[calc(100vh-72px)] lg:w-[352px] lg:border-b-0 lg:border-r">
          <div className="mb-6 h-6 w-32 animate-pulse rounded-md bg-slate-800/80" />
          <div className="space-y-4">
            <div className="h-[60px] w-full animate-pulse rounded-xl bg-slate-800/60" />
            <div className="h-[60px] w-full animate-pulse rounded-xl bg-slate-800/60" />
            <div className="h-[60px] w-full animate-pulse rounded-xl bg-slate-800/60" />
            <div className="h-[60px] w-full animate-pulse rounded-xl bg-slate-800/60" />
            <div className="mt-6 h-[44px] w-full animate-pulse rounded-xl bg-cyan-900/20 border border-cyan-900/30" />
          </div>
        </aside>

        {/* Main Content Skeleton */}
        <main className="flex-1 p-4 sm:p-6 lg:p-8">
          <div className="mb-8 flex items-center gap-2 border-b border-slate-700/30 pb-4">
            <div className="h-8 w-24 animate-pulse rounded-lg bg-slate-800/80" />
            <div className="h-8 w-24 animate-pulse rounded-lg bg-slate-800/80" />
            <div className="h-8 w-24 animate-pulse rounded-lg bg-slate-800/80" />
          </div>
          
          <div className="mb-6 h-8 w-48 animate-pulse rounded-md bg-slate-800/80" />
          
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-6">
            {[1, 2, 3, 4, 5, 6].map(i => (
              <div key={i} className="h-[140px] w-full animate-pulse rounded-2xl bg-slate-800/40 border border-slate-700/30" />
            ))}
          </div>

          <div className="mt-8 grid gap-4 lg:grid-cols-2">
             <div className="h-64 w-full animate-pulse rounded-2xl bg-slate-800/30 border border-slate-700/30" />
             <div className="h-64 w-full animate-pulse rounded-2xl bg-slate-800/30 border border-slate-700/30" />
          </div>
        </main>
      </div>
    </div>
  );
}
