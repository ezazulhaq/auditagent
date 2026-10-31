# Git and Commit Policy

- **Mandatory "Step 0" Pre-Flight Reflex**: Before creating, modifying, or deleting any workspace files, inspect the current branch (`git branch --show-current`). If on `dev`, `master`, or `main`, stop immediately and check out an isolated feature branch (`git checkout -b <type>/<name>`). Never author or stage changes on protected branches.
- **Automated Branch Guardrail**: `.githooks/pre-commit` rejects any direct commit attempts to `dev`, `master`, or `main`.
- **Do NOT commit any changes until explicit user confirmation is received.**
- The AI agent must never execute `git commit`, `git push`, or modify Git branches/history autonomously.
- Always present the proposed changes, file diffs, and verification or test results to the user first.
- Only proceed with creating a Git commit after the user explicitly reviews the changes and gives confirmation to commit.
- **Pull Request Target Rule**: Any Pull Request must ONLY target and be merged into the `dev` branch (`--base dev`). Never target or merge directly into `master` or `main`, `prod`, feature branches, or any other non-base branch.
