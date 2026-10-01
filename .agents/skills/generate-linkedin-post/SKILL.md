---
name: generate-linkedin-post
description: >-
  Generates a professional LinkedIn post based on a release note from the release_notes directory.
  Use this skill when the user asks to create a LinkedIn post, social media update, or announcement for a release.
---

# Generate LinkedIn Post

Use this skill to create engaging LinkedIn announcements for project releases based on the existing release notes.

## Steps

1.  **Determine the Version:**
    *   Check if the user specified a version name (e.g., `v2.0.1` or `2.0.1`) in their request.
    *   **CRITICAL:** If the user did *not* specify the version name, you MUST ask them for it and wait for their response before proceeding.

2.  **Read the Release Note:**
    *   Once you have the version name, use the `view_file` tool or `cat` command to read the corresponding markdown file in the `release_notes/` directory (e.g., `release_notes/2.0.1.md`).
    *   If the file does not exist, inform the user and stop.

3.  **Draft the LinkedIn Post:**
    *   Synthesize the release note content into a professional and engaging LinkedIn post.
    *   **Tone:** Enthusiastic, professional, and accessible.
    *   **Format:**
        *   Start with a strong hook or announcement statement (e.g., "We're excited to announce the release of AuditAgent v2.0.1! 🎉").
        *   Summarize the 1-3 most impactful features or architectural changes in bullet points. Avoid overly dense technical jargon unless it's a core selling point.
        *   Include a call to action (CTA) at the end (e.g., "Check out the full release notes on our GitHub repository!").
        *   Include 3-5 relevant hashtags (e.g., `#AuditAgent #Release #CyberSecurity #OpenSource`).

4.  **Present the Post:**
    *   Output the generated LinkedIn post directly to the user in a clear, copy-pasteable markdown code block or directly in the chat response.
