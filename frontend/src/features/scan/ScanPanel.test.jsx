import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ScanPanel } from './ScanPanel';

const config = { repositoryId: '42', branch: 'main', scannerName: 'semgrep', forceRescan: true };

describe('ScanPanel', () => {
  it('renders progress and explicit protocol errors', () => {
    render(<ScanPanel config={config} repositories={[{ repositoryId: 42, fullName: 'octo/repo', permission: 'WRITE' }]}
      branches={['main']} onConfigChange={vi.fn()} onScan={vi.fn()}
      scan={{ isScanning: true, scanTime: 4, progress: { step: 'run_semgrep', progress: 60, message: 'Analyzing' }, error: 'Stream ended without completion' }} />);
    expect(screen.getByText('60%')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Stream ended without completion');
  });
});
