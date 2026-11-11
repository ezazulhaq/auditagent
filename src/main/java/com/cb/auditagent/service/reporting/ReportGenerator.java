package com.cb.auditagent.service.reporting;

import com.cb.auditagent.domain.ScanMetadata;
import com.cb.auditagent.domain.Vulnerability;

import java.util.List;

/**
 * High-level interface for generating security audit reports.
 * Follows Dependency Inversion Principle (DIP).
 */
public interface ReportGenerator {

    String generateMarkdown(List<Vulnerability> findings, ScanMetadata metadata, String baseRepoPath);

    String generateMarkdown(List<Vulnerability> findings, ScanMetadata metadata, String baseRepoPath,
                            String branch, String baseSha);

    String generateHtml(List<Vulnerability> findings, ScanMetadata metadata, String baseRepoPath);

    byte[] generatePdf(List<Vulnerability> findings, ScanMetadata metadata, String repository,
                       String branch, String baseSha);
}
