import { agentTools, createAgent, registerTools } from '@ui-sdk/react/agent';

/**
 * AuditAgent UI Agent Bridge
 * Enables AI agents to read, inspect, and safely invoke actions across
 * AuditAgent's interface using @ui-sdk/react primitives.
 */
export const agent = createAgent({
  allow: (step) => {
    // When a step has a confirmation requirement (data-agent-confirm),
    // in browser environment check if user permits or require explicit confirmation.
    if (step.confirm) {
      if (typeof window !== 'undefined' && window.confirm) {
        return window.confirm(`AI Action Confirmation: ${step.confirm}`);
      }
      return false;
    }
    return true;
  },
});

/**
 * Initializes WebMCP tools registration with browser model context if available.
 * Returns an unregister cleanup function.
 */
export function initWebMcp(customAllow) {
  if (typeof document !== 'undefined' && document.modelContext) {
    return registerTools(agent, {
      allow: customAllow || (() => true),
    });
  }
  return () => {};
}

export { agentTools };
