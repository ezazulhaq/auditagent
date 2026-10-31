---
name: git-feature-workflow
description: Branch isolation, grouped logical commits, push approval, pull request creation, and versioned release note generation and publishing workflow when working on dev branch.
---

# Git Feature Workflow

This skill guides the standard Git workflow whenever you are asked to make code changes while on the `dev` branch. It ensures changes are isolated in feature branches, committed in logical groups, reviewed/approved before pushing, submitted via pull requests, and followed through to versioned release note generation and GitHub release publication.

---

## 0. Mandatory "Step 0" Pre-Flight Branch Reflex (Non-Negotiable)

**CRITICAL OPERATIONAL REFLEX**: Before creating, editing, deleting, or modifying ANY workspace file (code, test, configuration, skill, rule, or script) and before executing implementation commands, every AI assistant MUST execute the Step 0 Pre-Flight Reflex:

1. **Inspect current branch**:
   ```bash
   git branch --show-current
   ```
2. **If currently on `dev`, `master`, or `main`**:
   - **STOP IMMEDIATELY**. Under no circumstances should code be written, edited, staged, or committed directly on `dev`, `master`, or `main`.
   - If currently on `master` or `main`, check out `dev` first and pull latest (`git checkout dev && git pull origin dev`).
   - Determine the nature of the change:
     - New feature / capability: `feat/<kebab-case-name>`
     - Bug fix / defect resolution: `fix/<kebab-case-name>`
     - Performance / refactor: `refactor/<kebab-case-name>`
     - Maintenance / chores / tooling / governance: `chore/<kebab-case-name>`
   - Create and check out the new branch off `dev`:
     ```bash
     git checkout -b <branch-name>
     ```
   - **Inform the user explicitly**:
     > *"Step 0 Pre-Flight Check: Currently on `dev`. Isolated dedicated feature branch `<branch-name>` created before making any changes."*
3. **If already on a dedicated feature branch**:
   - Verify that the active feature branch matches the current task, and continue working on the branch.
4. **Automated Enforcement**:
   - `.githooks/pre-commit` enforces this rule mechanically. Any direct commit attempt on `dev`, `master`, or `main` will be hard-blocked with an exit code 1.

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
     > *"All changes are committed locally on branch `<branch-name>`. May I push this branch to `origin` and open a Pull Request against `dev`?"*
2. **Push to Remote (Upon Approval)**:
   - Once the user gives confirmation:
     ```bash
     git push -u origin <branch-name>
     ```
3. **Create the Pull Request**:
   - Open the PR using the GitHub CLI (`gh`):
     ```bash
     gh pr create --base dev --head <branch-name> --title "<type>(<scope>): <concise title>" --body "<Detailed Markdown summary>"
     ```
   - The PR body should include:
     - **Overview / Objective**: What problem this PR solves or feature it adds.
     - **Key Changes**: Bulleted breakdown of backend, frontend, configuration, and documentation changes.
     - **Verification**: Evidence of passed unit/integration test suites and build output.
4. **Report Back**:
   - Share the PR URL and status with the user.

---

## 4. Release Note Generation & Version Synchronization

Immediately after opening the PR, proceed to prepare the versioned release notes for the changes delivered in the branch:

1. **Determine Target Release Version**:
   - If the user explicitly requested a version (e.g., `2.1.2`), use that version.
   - If no version was specified, compute the next semantic version based on changes:
     - **Patch (`x.y.Z`)**: Bug fixes, minor documentation updates, developer skills/tooling, or non-breaking maintenance.
     - **Minor (`x.Y.0`)**: New features, new endpoints, new workflows, or significant capabilities.
     - **Major (`X.0.0`)**: Breaking API, database, or architecture changes.
     - If uncertain, confirm the target version with the user.
2. **Synchronize Project Version Descriptors (Mandatory Parity)**:
   - When generating a release note for a user-specified (or computed) version, **always maintain the exact same version string across all project descriptors**:
     - **Backend descriptor (`pom.xml`)**:
       ```xml
       <version>X.Y.Z</version>
       ```
     - **Frontend descriptor (`frontend/package.json`)**:
       ```json
       "version": "X.Y.Z"
       ```
     - **Frontend lockfile (`frontend/package-lock.json`)**:
       ```json
       "version": "X.Y.Z"
       ```
   - **Zero-Drift Rule**: Never create or publish a release note without verifying that `pom.xml`, `frontend/package.json`, and `frontend/package-lock.json` are synchronized to the exact target version.
   - **Pre-Commit Verification**: Run an explicit parity check before committing:
     ```bash
     # Verify pom.xml
     grep -m 1 -E "<version>[0-9]+\.[0-9]+\.[0-9]+</version>" pom.xml

     # Verify frontend/package.json and frontend/package-lock.json
     node -e 'console.log("package.json:", require("./frontend/package.json").version)'
     node -e 'console.log("package-lock.json:", require("./frontend/package-lock.json").version)'
     ```
3. **Generate Release Note File**:
   - Create `release_notes/<version>.md` (e.g. `release_notes/2.1.2.md`) following the standard template:
     ```markdown
     # AuditAgent v<version> Release Notes

     **Release Date:** <Month Day, Year>

     <Executive summary highlighting the core focus of this release.>

     ---

     ## 🚀 New Features & Enhancements
     <Bulleted feature breakdown with key technical details.>

     ---

     ## 📚 Documentation & Catalog Synchronization
     <Updates made to doc/*.md and catalogs.>

     ---

     ## 📦 Versioning & Verification
     <Version synchronization across pom.xml and frontend/package.json, along with test verification summary.>
     ```
4. **Documentation Guard Compliance**:
   - Ensure any new release notes or files are registered in `doc/15-source-inventory-and-glossary.md` so that `.githooks/pre-commit` passes without requiring bypass flags.
5. **Commit and Push to the Open PR**:
   - Stage and commit the version bump and release notes on the active feature branch:
     ```bash
     git add release_notes/<version>.md pom.xml frontend/package.json frontend/package-lock.json doc/
     git commit -m "docs(release): add v<version> release notes and synchronize version in pom.xml and package.json"
     git push origin <branch-name>
     ```
   - Pushing automatically updates the existing Pull Request.

---

## 5. Pull Request Merge & GitHub Release Publication

Once the user approves merging and publishing the release:

1. **Merge the Pull Request**:
   - Use the GitHub CLI to merge into `dev`:
     ```bash
     gh pr merge <pr-number> --merge
     ```
   - Verify that the PR transitioned to merged status:
     ```bash
     gh pr view <pr-number> --json state,mergedAt
     ```
2. **Update Local Tracking Branch**:
   - Switch back to `dev` and pull the latest merged state:
     ```bash
     git checkout dev && git pull origin dev
     ```
3. **Publish GitHub Release**:
   - Create and publish the GitHub release with tag `v<version>` pointing to `dev`:
     ```bash
     gh release create v<version> --target dev --title "v<version>" --notes-file release_notes/<version>.md
     ```
   - Fetch the newly created tag to your local git repository:
     ```bash
     git fetch --tags origin
     ```
4. **Synchronize Long-Lived Branches (`master`)**:
   - If the repository maintains a parallel `master` branch, fast-forward it to keep branches synchronized:
     ```bash
     git checkout master && git merge --ff-only dev && git push origin master && git checkout dev
     ```
5. **Present Release Handoff**:
   - Share the published GitHub release URL (`https://github.com/<owner>/<repo>/releases/tag/v<version>`), a concise summary of the highlights, and confirmation of clean repository state.

