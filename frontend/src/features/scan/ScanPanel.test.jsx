import { fireEvent, render, screen } from '@testing-library/react';
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

  it('switches to manual upload mode when toggle button is clicked', () => {
    const handleScanModeChange = vi.fn();

    const { rerender } = render(
      <ScanPanel
        config={config}
        repositories={[{ repositoryId: 42, fullName: 'octo/repo', permission: 'WRITE' }]}
        branches={['main']}
        onConfigChange={vi.fn()}
        onScan={vi.fn()}
        onUploadScan={vi.fn()}
        scan={{ isScanning: false, scanTime: 0 }}
        scanMode="github"
        onScanModeChange={handleScanModeChange}
      />
    );

    expect(screen.getByText('Scan configuration')).toBeInTheDocument();

    const toggleButton = screen.getByTitle('Switch to manual ZIP upload');
    fireEvent.click(toggleButton);

    expect(handleScanModeChange).toHaveBeenCalledWith('upload');

    rerender(
      <ScanPanel
        config={config}
        repositories={[{ repositoryId: 42, fullName: 'octo/repo', permission: 'WRITE' }]}
        branches={['main']}
        onConfigChange={vi.fn()}
        onScan={vi.fn()}
        onUploadScan={vi.fn()}
        scan={{ isScanning: false, scanTime: 0 }}
        scanMode="upload"
        onScanModeChange={handleScanModeChange}
      />
    );

    expect(screen.getByText('Manual project upload')).toBeInTheDocument();
    expect(screen.getByText('Click to browse or drop ZIP archive')).toBeInTheDocument();
  });

  it('allows file selection and triggers onUploadScan in upload mode', () => {
    const handleUploadScan = vi.fn();

    render(
      <ScanPanel
        config={config}
        repositories={[]}
        branches={[]}
        onConfigChange={vi.fn()}
        onScan={vi.fn()}
        onUploadScan={handleUploadScan}
        scan={{ isScanning: false, scanTime: 0 }}
        scanMode="upload"
      />
    );

    const file = new File(['fake zip content'], 'my-app.zip', { type: 'application/zip' });
    const dropzone = screen.getByText('Click to browse or drop ZIP archive').closest('div');
    const input = dropzone.querySelector('input[type="file"]');

    fireEvent.change(input, { target: { files: [file] } });

    expect(screen.getByText('my-app.zip')).toBeInTheDocument();
    expect(screen.getByDisplayValue('my-app')).toBeInTheDocument();

    const scanBtn = screen.getByRole('button', { name: /scan zip archive/i });
    expect(scanBtn).not.toBeDisabled();

    fireEvent.click(scanBtn);
    expect(handleUploadScan).toHaveBeenCalledWith(file, 'my-app');
  });
});
