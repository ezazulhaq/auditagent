---
name: generate-release-notes
description: >-
  Generates a markdown release note in the release_notes directory based on the git commit log history of the current branch. Use this skill when the user asks to generate or draft release notes.
---

# Generate Release Notes

Use this skill to automate the drafting of release notes based on the git commit history.

## Steps

1.  **Determine the Version Name:**
    *   Check if the user specified a version name (e.g., `v2.0.3` or `2.0.3`) in their request.
    *   **CRITICAL:** If the user did *not* specify the version name, you MUST ask them for it and wait for their response before proceeding.

2.  **Retrieve Commit History:**
    *   Use the `run_command` tool to get the recent commit history of the current branch.
    *   *Tip:* You can use `git log --oneline -n 20` or, if comparing against the main branch, `git log origin/master..HEAD --oneline` to find the relevant changes.

3.  **Draft the Content:**
    *   Synthesize the commit log into a well-formatted markdown release note. 
    *   Group changes logically (e.g., `🚀 Features`, `🐞 Bug Fixes`, `⚙️ Configuration`, `📚 Documentation`).
    *   Use clear, user-facing language, expanding on brief commit messages where context is obvious.

4.  **Save the File:**
    *   Use the `write_to_file` tool to save the generated markdown content.
    *   The file MUST be saved in the project's `release_notes/` directory.
    *   The filename should be the version name (e.g., `release_notes/2.0.3.md`).

5.  **Notify the User:**
    *   Provide the user with a markdown link to the newly created file (e.g., `[release_notes/2.0.3.md](file:///path/to/repo/release_notes/2.0.3.md)`) so they can review and edit it.
