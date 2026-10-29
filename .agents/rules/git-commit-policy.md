# Git and Commit Policy

- **Do NOT commit any changes until explicit user confirmation is received.**
- The AI agent must never execute `git commit`, `git push`, or modify Git branches/history autonomously.
- Always present the proposed changes, file diffs, and verification or test results to the user first.
- Only proceed with creating a Git commit after the user explicitly reviews the changes and gives confirmation to commit.
- **Pull Request Target Rule**: Any Pull Request must ONLY target and be merged into the `dev` branch. Never target or merge directly into `master` or `main`, `prod`, feature branches, or any other non-base branch.

