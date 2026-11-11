package com.cb.auditagent.service.tool;

/**
 * Role interface for compiling code, running tests, and executing linters.
 * Follows Interface Segregation Principle (ISP).
 */
public interface WorkspaceBuildVerifier {

    /**
     * Attempts to build the project using its detected build system (Maven, Gradle, etc.).
     */
    String compileProject(String repoPath);

    /**
     * Executes the appropriate linter for the workspace languages.
     */
    String runLinter(String repoPath);

    /**
     * Executes test suites, optionally matching a specific test pattern or glob.
     */
    String runTests(String repoPath, String testGlob);
}
