package com.cb.auditagent.service.tool;

/**
 * Role interface for modifying and rolling back files in the workspace.
 * Follows Interface Segregation Principle (ISP).
 */
public interface WorkspacePatcher {

    /**
     * Applies a precise code replacement patch with automated backup.
     */
    String applyPatch(String repoPath, String filePath, String originalCode, String replacementCode);

    /**
     * Restores a file to its state prior to the last patch modification.
     */
    String rollbackFile(String repoPath, String filePath);
}
