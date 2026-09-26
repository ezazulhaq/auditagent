import { useEffect, useState } from 'react';

export function elapsedScanSeconds(startedAt, finishedAt, now = Date.now()) {
  if (!Number.isFinite(startedAt)) return 0;
  const end = Number.isFinite(finishedAt) ? finishedAt : now;
  return Math.max(0, Math.floor((end - startedAt) / 1000));
}

export function useScanElapsedSeconds(startedAt, finishedAt) {
  const [now, setNow] = useState(() => Date.now());
  const running = Number.isFinite(startedAt) && !Number.isFinite(finishedAt);

  useEffect(() => {
    if (!running) return undefined;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [running, startedAt]);

  return elapsedScanSeconds(startedAt, finishedAt, now);
}
