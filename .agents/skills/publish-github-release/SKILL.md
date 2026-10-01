---
name: publish-github-release
description: >-
  Publishes a release note from a markdown file to the GitHub repository as a GitHub Release.
  Use this skill when the user asks to publish or upload a release note to GitHub.
---

# Publish GitHub Release

Use this skill to publish a release note (markdown file) to the GitHub repository as a GitHub Release.

## Prerequisites
- The environment variable `GH_TOKEN` or `GITHUB_TOKEN` must be set in your terminal environment.

## Steps

1.  Identify the path to the markdown file containing the release notes and the desired version tag (e.g., `v2.0.1`).
    *   *Note: Ensure the tag starts with `v` if that is the repository's convention.*
2.  Run the publisher script using the `run_command` tool.
    *   **CRITICAL**: You MUST set `BypassSandbox: true` because the script needs network access to `api.github.com`.
    *   Command to run:
        ```bash
        bash .agents/skills/publish-github-release/scripts/publish.sh <path_to_markdown_file> <tag_name>
        ```
3.  The script will automatically detect the repository from `git remote -v` and either create a new release or update an existing one if the tag already exists.
4.  Report the `html_url` returned by the script to the user.
