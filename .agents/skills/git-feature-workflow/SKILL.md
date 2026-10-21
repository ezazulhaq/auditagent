---
name: git-feature-workflow
description: Branch isolation, grouped logical commits, push approval, and pull request creation workflow when working on master or main branches.
---

# Git Feature Workflow

This skill guides the standard Git workflow whenever you are asked to make code changes while on the `master` or `main` branch. It ensures changes are isolated in feature branches, committed in logical groups, and reviewed/approved before pushing and creating pull requests.

---

## 1. Branch Detection & Isolation (Pre-Flight)

Before writing or editing code files:

1. **Inspect current branch**:
   ```bash
   git branch --show-current
   ```
2. **If currently on `master` or `main`**:
   - Do **NOT** modify or commit files directly on `master` or `main`.
   - Determine the nature of the change:
     - New feature / capability: `feat/<kebab-case-name>`
     - Bug fix / defect resolution: `fix/<kebab-case-name>`
     - Performance / refactor: `refactor/<kebab-case-name>`
     - Maintenance / chores / tooling: `chore/<kebab-case-name>`
   - Create and check out the new branch:
     ```bash
     git checkout -b <branch-name>
     ```
   - Inform the user that a dedicated feature branch has been created.
3. **If already on a dedicated feature branch**:
   - Continue working on the current branch (unless the user explicitly requests starting a separate branch).

---

## 2. Implementation, Verification & Grouped Commits

1. **Implement and verify**:
   - Make the required code changes.
   - Run tests and builds to ensure zero regressions:
     - Backend: `mvn test`
     - Frontend: `npm --prefix frontend run test:run` and `npm --prefix frontend run build`
   - Review and update corresponding documentation in `doc/*.md` per the project's documentation maintenance policy.
2. **Group changes logically by module/component**:
   - Do **not** create one giant single commit for unrelated parts of the codebase.
   - Stage and commit files in coherent groups, for example:
     - **Backend / Core**: Services, entities, repositories, controllers, backend unit tests, and architecture/API docs.
     - **Frontend / UI**: React components, hooks, API client, UI tests, static assets, and user/feature docs.
     - **Infrastructure / Config**: Dockerfiles, CI workflows, build configurations, and operations docs.
3. **Commit review & execution**:
   - Present the grouped changes and test results to the user.
   - Confirm explicit permission before running `git commit` (per commit policy).
   - Use standard conventional commit format:
     ```bash
     git commit -m "<type>(<scope>): <summary>

     - Detailed bullet point 1
     - Detailed bullet point 2"
     ```

---

## 3. Push Approval & Pull Request Creation

1. **Request Push Approval**:
   - Once all grouped commits are created, do **NOT** push autonomously.
   - Ask the user explicitly for confirmation to push the branch to remote:
     > *"All changes are committed locally on branch `<branch-name>`. May I push this branch to `origin` and open a Pull Request against `master`?"*
2. **Push to Remote (Upon Approval)**:
   - Once the user gives confirmation:
     ```bash
     git push -u origin <branch-name>
     ```
3. **Create the Pull Request**:
   - Open the PR using the GitHub CLI (`gh`):
     ```bash
     gh pr create --base master --head <branch-name> --title "<type>(<scope>): <concise title>" --body "<Detailed Markdown summary>"
     ```
   - The PR body should include:
     - **Overview / Objective**: What problem this PR solves or feature it adds.
     - **Key Changes**: Bulleted breakdown of backend, frontend, configuration, and documentation changes.
     - **Verification**: Evidence of passed unit/integration test suites and build output.
4. **Report Back**:
   - Share the PR URL and status with the user.

