export const timelineSteps = [
  { label: 'Detected', states: ['DETECTED'] },
  { label: 'Analyzing & fixing', states: ['ANALYZING', 'GENERATING_FIX', 'VERIFYING'] },
  { label: 'Verified & awaiting approval', states: ['AWAITING_APPROVAL'] },
  { label: 'Pull request created', states: ['PR_OPEN', 'PUBLISHED', 'PUBLISH_FAILED'] },
  { label: 'Fix merged', states: ['FIXED'] }
];
