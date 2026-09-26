import { Download, ExternalLink, FileCode2, FileText, LoaderCircle, LockKeyhole, RefreshCw } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';

const buttonClass = 'flex min-h-10 items-center justify-center gap-2 rounded-lg border px-3 text-[11px] font-semibold transition disabled:cursor-not-allowed disabled:opacity-50';

function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export function ReportView({ api, repositoryId, branch, reportAvailable, projectName }) {
  const [pdfArtifact, setPdfArtifact] = useState(null);
  const [pdfUrl, setPdfUrl] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [downloading, setDownloading] = useState('');
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    if (!reportAvailable || !repositoryId || !branch) {
      return undefined;
    }
    const controller = new AbortController();
    let objectUrl = '';
    api.reportArtifact(repositoryId, branch, 'pdf', controller.signal).then(artifact => {
      objectUrl = URL.createObjectURL(artifact.blob);
      setPdfArtifact(artifact); setPdfUrl(objectUrl);
    }).catch(requestError => {
      if (requestError.name !== 'AbortError') setError({ message: requestError.message || 'Unable to load the PDF report.', retryPdf: true });
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => {
      controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [api, branch, reloadToken, reportAvailable, repositoryId]);

  const download = useCallback(async format => {
    setDownloading(format); setError(null);
    try {
      const artifact = format === 'pdf' && pdfArtifact
        ? pdfArtifact : await api.reportArtifact(repositoryId, branch, format);
      saveBlob(artifact.blob, artifact.filename);
    } catch (requestError) {
      setError({ message: requestError.message || `Unable to download the ${format.toUpperCase()} report.`, retryPdf: false });
    } finally { setDownloading(''); }
  }, [api, branch, pdfArtifact, repositoryId]);

  const retryPdf = useCallback(() => {
    setLoading(true); setError(null); setPdfArtifact(null); setPdfUrl('');
    setReloadToken(value => value + 1);
  }, []);

  if (!reportAvailable) return <div className="flex min-h-[560px] items-center justify-center p-8 text-center"><div><span className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl border border-slate-700/35 bg-slate-900/45 text-slate-600"><FileText size={28} /></span><p className="mt-5 text-sm font-semibold text-slate-300">No report generated yet</p><p className="mt-2 text-xs text-slate-600">Run a security scan to create the enterprise assessment.</p></div></div>;

  return <div className="flex h-full min-h-[660px] flex-col p-3 sm:p-5 xl:p-7">
    <section className="surface-card flex min-h-0 flex-1 flex-col overflow-hidden rounded-2xl">
      <header className="flex flex-col gap-4 border-b border-slate-700/25 bg-slate-950/20 px-4 py-4 lg:flex-row lg:items-center lg:justify-between lg:px-5">
        <div className="flex min-w-0 items-center gap-3">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-cyan-400/20 bg-cyan-400/8 text-cyan-300"><FileText size={16} /></span>
          <div className="min-w-0"><p className="truncate text-xs font-semibold text-slate-100">{projectName || 'Repository'} security assessment</p><p className="mt-1 flex items-center gap-1 text-[9px] text-slate-500"><LockKeyhole size={9} /> Authenticated, repository-scoped report</p></div>
        </div>
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-3 lg:flex">
          <button type="button" disabled={!pdfUrl} onClick={() => window.open(pdfUrl, '_blank', 'noopener,noreferrer')} className={`${buttonClass} border-slate-600/45 bg-slate-900/45 text-slate-300 hover:border-cyan-400/35 hover:text-cyan-200`}><ExternalLink size={14} /> Open PDF</button>
          <button type="button" disabled={loading || Boolean(downloading)} onClick={() => download('pdf')} className={`${buttonClass} border-cyan-400/30 bg-cyan-400/10 text-cyan-100 hover:bg-cyan-400/15`}>{downloading === 'pdf' ? <LoaderCircle className="animate-spin" size={14} /> : <Download size={14} />} Download PDF</button>
          <button type="button" disabled={Boolean(downloading)} onClick={() => download('markdown')} className={`${buttonClass} border-slate-600/45 bg-slate-900/45 text-slate-300 hover:border-violet-400/35 hover:text-violet-200`}>{downloading === 'markdown' ? <LoaderCircle className="animate-spin" size={14} /> : <FileCode2 size={14} />} Download Markdown</button>
        </div>
      </header>

      {error && <div role="alert" className="flex flex-col gap-3 border-b border-rose-400/20 bg-rose-400/8 px-4 py-3 text-xs text-rose-200 sm:flex-row sm:items-center sm:justify-between"><span>{error.message}</span>{error.retryPdf && <button type="button" onClick={retryPdf} className="flex min-h-9 items-center justify-center gap-2 rounded-lg border border-rose-300/25 px-3 font-semibold hover:bg-rose-300/10"><RefreshCw size={13} /> Retry PDF</button>}</div>}

      <div className="relative min-h-[580px] flex-1 bg-slate-900/40">
        {loading && <div role="status" className="absolute inset-0 z-10 flex items-center justify-center bg-[#07101b]/90"><div className="text-center"><LoaderCircle className="mx-auto animate-spin text-cyan-300" size={28} /><p className="mt-4 text-xs font-semibold text-slate-300">Preparing enterprise PDF</p><p className="mt-1 text-[10px] text-slate-600">Formatting the latest persisted scan result</p></div></div>}
        {!loading && pdfUrl && <iframe title="AuditAgent PDF Report" src={pdfUrl} className="h-full min-h-[580px] w-full border-0 bg-white" />}
        {!loading && !pdfUrl && !error && <div className="flex min-h-[580px] items-center justify-center text-xs text-slate-500">The PDF report is unavailable.</div>}
      </div>
    </section>
  </div>;
}
