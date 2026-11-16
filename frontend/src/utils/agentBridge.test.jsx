import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { agent, agentTools, initWebMcp } from './agentBridge';
import { WorkspaceTabs } from '../features/report/WorkspaceTabs';
import { CustomSelect } from '../components/CustomSelect';
import { ConfirmDialog } from '../components/ConfirmDialog';

describe('AuditAgent UI Agent Bridge & Modernized Primitives', () => {
  it('exposes standard agentTools metadata', () => {
    expect(agentTools).toBeDefined();
    expect(agentTools.length).toBeGreaterThanOrEqual(1);
    const names = agentTools.map(t => t.name);
    expect(names).toContain('snapshot');
  });

  it('registers WebMCP tools when document.modelContext exists', async () => {
    const mockTools = new Map();
    document.modelContext = {
      registerTool: vi.fn(async (tool) => {
        mockTools.set(tool.name, tool);
      }),
    };

    const unregister = initWebMcp(() => true);
    expect(document.modelContext.registerTool).toHaveBeenCalled();
    expect(mockTools.has('snapshot')).toBe(true);

    if (typeof unregister === 'function') {
      unregister();
    }
    delete document.modelContext;
  });

  it('allows agent to inspect and snapshot WorkspaceTabs with accessible names', () => {
    const handleChange = vi.fn();
    render(
      <WorkspaceTabs
        activeTab="dashboard"
        onChange={handleChange}
        findingCount={5}
        issueCount={2}
      />
    );

    const snapshot = agent.snapshot();
    expect(Array.isArray(snapshot)).toBe(true);

    const tabsEntry = snapshot.find(entry => entry.component === 'tabs');
    expect(tabsEntry).toBeDefined();
    expect(tabsEntry.label).toBe('Workspace views');
  });

  it('renders CustomSelect with accessible select primitive', () => {
    const handleChange = vi.fn();
    const options = [
      { value: 'main', label: 'main branch' },
      { value: 'dev', label: 'dev branch' },
    ];

    render(
      <CustomSelect
        value="main"
        onChange={handleChange}
        options={options}
        placeholder="Select branch"
      />
    );

    const snapshot = agent.snapshot();
    const selectEntry = snapshot.find(entry => entry.component === 'select');
    expect(selectEntry).toBeDefined();
    expect(selectEntry.value).toBe('main');
  });

  it('includes data-agent-confirm on ConfirmDialog action button', () => {
    render(
      <ConfirmDialog
        isOpen={true}
        onClose={vi.fn()}
        onConfirm={vi.fn()}
        title="Delete finding"
        message="Are you sure you want to delete this finding?"
        confirmText="Delete"
        confirmStyle="danger"
      />
    );

    const confirmBtn = screen.getByRole('button', { name: 'Delete' });
    expect(confirmBtn).toHaveAttribute('data-agent-confirm', 'Confirm Delete finding');
  });
});
