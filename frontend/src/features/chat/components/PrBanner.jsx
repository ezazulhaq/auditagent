import { ExternalLink } from 'lucide-react';

export function PrBanner({ activeRun, publication }) {
  if (activeRun?.status !== 'PR_OPEN' || !publication?.pullRequestUrl) return null;

  return (
    <a href={publication.pullRequestUrl} target="_blank" rel="noreferrer" className="flex items-center justify-between border-b border-cyan-400/18 bg-cyan-400/[0.07] p-3.5 text-xs font-semibold text-cyan-100">
      <span>Pull request #{publication.pullRequestNumber} is open</span>
      <ExternalLink size={14} />
    </a>
  );
}

