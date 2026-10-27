# Pull Request Target & Merge Policy

- **Target `master` or `main` ONLY**: Any Pull Request (PR) must ONLY target and be merged into the `master` or `main` branch.
- **Prohibited Target Branches**: Never create a PR targeting or attempt to merge into `dev` or `prod`, feature branches, staging branches, or any non-primary branch.
- **PR Creation Flag**: When opening a PR via the GitHub CLI (`gh pr create`), the base flag must strictly and explicitly be `--base master` (or `--base main` if `main` is the primary branch).
- **Pre-Merge Validation**: Before executing `gh pr merge`, always verify that the PR base branch is `master` or `main`. Never merge a PR whose base branch is anything else.
- **Secondary Branch Synchronization**: Parallel long-lived branches (such as `dev`) must only receive updates via fast-forward merge from `master` or `main` after the PR has already been merged into `master`/`main`, never as direct PR merge targets.
