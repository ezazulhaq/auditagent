package com.cb.auditagent.service.tool;

/**
 * Role interface for inspecting repository code and searching files.
 * Follows Interface Segregation Principle (ISP).
 */
public interface WorkspaceInspector {

    /**
     * Reads file content between startLine and endLine (1-indexed, inclusive).
     */
    String readFile(String repoPath, String filePath, int startLine, int endLine);

    /**
     * Searches the codebase for a text query, optionally filtered by a file glob.
     */
    String searchCodebase(String repoPath, String query, String fileGlob);

    /**
     * Computes the SHA-256 hash of a file within the workspace.
     */
    String hashFile(String repoPath, String filePath);
}
