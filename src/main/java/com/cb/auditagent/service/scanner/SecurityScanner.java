package com.cb.auditagent.service.scanner;

import com.cb.auditagent.service.ScannerService.ScanResult;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Strategy interface for vulnerability and security scanners.
 * Follows Open/Closed Principle (OCP) and Dependency Inversion Principle (DIP).
 */
public interface SecurityScanner {

    /**
     * Unique identifier for the scanner engine (e.g., "semgrep", "trivy", "snyk").
     */
    String getScannerName();

    /**
     * Checks if this scanner supports the specified project workspace or language.
     */
    boolean supports(String targetPath);

    /**
     * Executes a security audit on the target repository workspace with real-time progress callbacks.
     */
    ScanResult scan(String targetRepo, Consumer<Map<String, Object>> progressCallback);

    /**
     * Executes a security audit synchronously without a progress callback.
     */
    default ScanResult scan(String targetRepo) {
        return scan(targetRepo, progress -> {});
    }

    /**
     * Detects project programming languages present in the target workspace.
     */
    List<String> detectLanguages(File workspace);

    /**
     * Detects the programming language for a single file path.
     */
    String detectLanguageForFile(String filename);

    /**
     * Returns the rule configuration paths or IDs for the given programming languages.
     */
    List<String> getRuleConfigs(List<String> languages);
}
