package com.cb.auditagent.service.tool;

/**
 * Role interface for re-scanning files or running security analysis on the workspace.
 * Follows Interface Segregation Principle (ISP).
 */
public interface WorkspaceRescanner {

    /**
     * Rescans a single modified file to verify if the vulnerability has been eliminated.
     */
    String rescanFile(String repoPath, String filePath);

    /**
     * Runs full static analysis across the entire repository workspace.
     */
    String runStaticAnalysis(String repoPath);
}
