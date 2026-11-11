import { useRef, useState, memo } from 'react';
import { UploadCloud, FileArchive, X, Layers3, RefreshCw, Play, ChevronRight } from 'lucide-react';
import { formatTime } from '../../../utils/auditSelectors';

function formatFileSize(bytes) {
  if (!bytes) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
}

/**
 * UploadDropzone handles ZIP archive drag-and-drop, validation, and submission.
 * Adheres to Single Responsibility Principle (SRP).
 */
export const UploadDropzone = memo(function UploadDropzone({
  onUploadScan,
  scan,
  scanTime,
}) {
  const [selectedFile, setSelectedFile] = useState(null);
  const [uploadProjectName, setUploadProjectName] = useState('');
  const [isDragging, setIsDragging] = useState(false);
  const [fileError, setFileError] = useState('');
  const fileInputRef = useRef(null);

  const handleFileSelect = (file) => {
    if (!file) return;
    setFileError('');
    if (!file.name.toLowerCase().endsWith('.zip')) {
      setFileError('Only .zip files are supported.');
      return;
    }
    if (file.size > 50 * 1024 * 1024) {
      setFileError('File size exceeds the 50MB limit.');
      return;
    }
    setSelectedFile(file);
    if (!uploadProjectName) {
      const derived = file.name.replace(/\.zip$/i, '').replace(/[^a-zA-Z0-9-_.]/g, '-');
      setUploadProjectName(derived);
    }
  };

  const clearFile = () => {
    setSelectedFile(null);
    setUploadProjectName('');
    setFileError('');
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      handleFileSelect(e.dataTransfer.files[0]);
    }
  };

  const handleUploadSubmit = (e) => {
    e.preventDefault();
    if (!selectedFile || scan.isScanning) return;
    if (onUploadScan) {
      onUploadScan(selectedFile, uploadProjectName);
    }
  };

  return (
    <form onSubmit={handleUploadSubmit} className="space-y-4">
      {!selectedFile ? (
        <div
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
          onClick={() => fileInputRef.current?.click()}
          className={`flex cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed p-6 text-center transition ${
            isDragging
              ? 'border-cyan-400 bg-cyan-950/20'
              : 'border-slate-700/60 bg-slate-900/30 hover:border-slate-600 hover:bg-slate-900/50'
          }`}
        >
          <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-xl border border-cyan-500/20 bg-cyan-500/10 text-cyan-400">
            <UploadCloud size={20} />
          </div>
          <p className="text-xs font-semibold text-slate-200">
            Click to browse or drop ZIP archive
          </p>
          <p className="mt-1 text-[10px] text-slate-500">
            Max archive size: 50MB
          </p>
          <input
            ref={fileInputRef}
            type="file"
            accept=".zip,application/zip,application/x-zip-compressed"
            className="hidden"
            onChange={e => handleFileSelect(e.target.files?.[0])}
          />
        </div>
      ) : (
        <div className="flex items-center justify-between rounded-xl border border-slate-700/60 bg-slate-900/45 p-3.5">
          <div className="flex items-center gap-3 min-w-0">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-cyan-500/20 bg-cyan-500/10 text-cyan-400">
              <FileArchive size={18} />
            </div>
            <div className="min-w-0">
              <p className="truncate text-xs font-semibold text-slate-200" title={selectedFile.name}>
                {selectedFile.name}
              </p>
              <p className="text-[10px] text-slate-500">
                {formatFileSize(selectedFile.size)}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={clearFile}
            disabled={scan.isScanning}
            className="ml-2 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-800 hover:text-slate-200 disabled:opacity-50"
            title="Remove file"
          >
            <X size={15} />
          </button>
        </div>
      )}

      {fileError && (
        <div className="rounded-xl border border-rose-500/20 bg-rose-500/10 p-3 text-[11px] text-rose-300">
          {fileError}
        </div>
      )}

      <label className="block text-[11px] font-semibold text-slate-400">
        <span className="mb-1.5 flex items-center gap-1.5"><Layers3 size={13} className="text-slate-500" /> Project name</span>
        <input
          type="text"
          value={uploadProjectName}
          onChange={e => setUploadProjectName(e.target.value)}
          placeholder="e.g. my-service"
          className="w-full rounded-xl border border-slate-700/60 bg-slate-900/60 px-3.5 py-2.5 text-xs text-slate-200 placeholder-slate-600 focus:border-cyan-400 focus:outline-none focus:ring-1 focus:ring-cyan-400"
        />
      </label>

      {scan.isScanning ? (
        <div className="flex gap-2">
          <div className="flex flex-1 items-center justify-center gap-2 rounded-xl bg-slate-800 px-4 py-2.5 text-sm font-bold text-slate-400">
            <RefreshCw size={16} className="animate-spin" /> Scanning <span className="font-mono">{formatTime(scanTime)}</span>
          </div>
          <button type="button" onClick={scan.cancel} className="flex shrink-0 items-center justify-center rounded-xl border border-rose-500/20 bg-rose-500/10 px-4 py-2.5 text-sm font-bold text-rose-300 transition hover:border-rose-500/30 hover:bg-rose-500/20">
            Cancel
          </button>
        </div>
      ) : (
        <button
          type="submit"
          disabled={!selectedFile}
          className="primary-action flex w-full items-center justify-center gap-2 px-4 py-2.5 text-sm font-bold disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <Play size={16} fill="currentColor" /> Scan ZIP Archive <ChevronRight size={15} />
        </button>
      )}
    </form>
  );
});
