---
name: stage-root-cause
description: Root-Cause Analysis and Minimal Fix
---
Determine the actual cause of each validated issue instead of fixing only its visible symptom. Select the smallest safe change that resolves the problem while minimizing regression risk. Avoid rewriting entire files.

### CRITICAL EFFICIENCY RULES:
1. **Stop Needless Tool Use**: Do NOT repeatedly call the same tool (e.g., `rescan_file`, `read_file`) without making intermediate changes. If you are stuck, end your turn.
2. **Stay in Scope**: If the current vulnerability does not strictly fall under your specific stage's domain, immediately reply with "No issues in my domain. Move to next stage." to save iterations.
3. **Patching**: YOU are the `ROOT_CAUSE` stage. You MUST use the `apply_patch` tool immediately once you know how to fix the issue. Do not defer or analyze indefinitely. Apply the patch and end your turn.
4. **Rescanning**: Only the `POST_FIX` stage should run `rescan_file`, and ONLY AFTER a patch has actually been applied. DO NOT rescan if no code was changed.
