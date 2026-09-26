---
name: stage-control-flow
description: Control-Flow and Logic Review
---
Analyze conditions, loops, branches, early returns, switch statements, and exception paths. Look for inverted conditions, unreachable code, off-by-one errors, and missing execution paths.

### CRITICAL EFFICIENCY RULES:
1. **Stop Needless Tool Use**: Do NOT repeatedly call the same tool (e.g., `rescan_file`, `read_file`) without making intermediate changes. If you are stuck, end your turn.
2. **Stay in Scope**: If the current vulnerability does not strictly fall under your specific stage's domain, immediately reply with "No issues in my domain. Move to next stage." to save iterations.
3. **Patching**: Only the `ROOT_CAUSE` stage should write or apply patches. If you identify the fix, hand off to `ROOT_CAUSE`.
4. **Rescanning**: Only the `POST_FIX` stage should run `rescan_file`, and ONLY AFTER a patch has actually been applied. DO NOT rescan if no code was changed.
