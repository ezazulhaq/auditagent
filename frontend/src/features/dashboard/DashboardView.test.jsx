import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { DashboardView } from './DashboardView';

describe('DashboardView hook order', () => {
  it('does not throw when transitioning from null metadata to non-null metadata', () => {
    const counts = { high: 0, medium: 0, low: 0, info: 0 };
    const { rerender } = render(
      <DashboardView metadata={null} findings={[]} counts={counts} />
    );

    // Transitioning from no metadata to metadata present should not crash React hook count
    expect(() => {
      rerender(
        <DashboardView
          metadata={{ projectName: 'Test Repo', totalFilesScanned: 5, totalLocScanned: 100 }}
          findings={[]}
          counts={counts}
        />
      );
    }).not.toThrow();
  });
});

