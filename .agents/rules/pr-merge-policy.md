# Pull Request Target & Merge Policy

- **Mandatory "Step 0" Pre-Flight Reflex**: All development work begins on an isolated branch off `dev`. Direct commits to `dev`, `master`, or `main` are strictly blocked.
- **Target `dev` ONLY**: Any Pull Request (PR) must ONLY target and be merged into the `dev` branch.
- **Prohibited Target Branches**: Never create a PR targeting or attempt to merge directly into `master` or `main`, `prod`, feature branches, staging branches, or any non-base branch.
- **PR Creation Flag**: When opening a PR via the GitHub CLI (`gh pr create`), the base flag must strictly and explicitly be `--base dev`.
- **Pre-Merge Validation**: Before executing `gh pr merge`, always verify that the PR base branch is `dev`. Never merge a PR whose base branch is anything else.
- **Tracking / Production Branch Synchronization**: Parallel long-lived tracking branches (such as `master` or `main`) must only receive updates via fast-forward merge from `dev` after the PR has already been merged into `dev`, never as direct PR merge targets.
