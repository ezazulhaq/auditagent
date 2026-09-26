---
name: github-pr-manager
description: Creates a new local branch, commits changes, and opens a GitHub Pull Request.
input_schema:
  type: object
  properties:
    repo_name:
      type: string
    branch_name:
      type: string
    commit_message:
      type: string
    pr_title:
      type: string
    pr_body:
      type: string
  required:
    - repo_name
    - branch_name
    - commit_message
    - pr_title
    - pr_body
---
# GitHub PR Manager Playbook

You use this tool to deploy an approved code fix.

## Workflow Rule
You are STRICTLY FORBIDDEN from calling `github-pr-manager` unless the `human-gateway` skill was called and returned "APPROVED".

### CRITICAL EFFICIENCY RULES:
1. **Stop Needless Tool Use**: Do NOT repeatedly call the same tool (e.g., `rescan_file`, `read_file`) without making intermediate changes. If you are stuck, end your turn.
2. **Stay in Scope**: If the current vulnerability does not strictly fall under your specific stage's domain, immediately reply with "No issues in my domain. Move to next stage." to save iterations.
3. **Patching**: Only the `ROOT_CAUSE` stage should write or apply patches. If you identify the fix, hand off to `ROOT_CAUSE`.
4. **Rescanning**: Only the `POST_FIX` stage should run `rescan_file`, and ONLY AFTER a patch has actually been applied. DO NOT rescan if no code was changed.
