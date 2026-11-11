package com.cb.auditagent.service.reporting;

import com.cb.auditagent.domain.ScanMetadata;
import com.cb.auditagent.domain.Vulnerability;

import java.util.Collections;
import java.util.List;

/**
 * Encapsulates parameters for report generation across all export strategies.
 */
public record ReportExportContext(
        List<Vulnerability> findings,
        ScanMetadata metadata,
        String baseRepoPath,
        String branch,
        String baseSha
) {
    public ReportExportContext {
        findings = findings == null ? Collections.emptyList() : findings;
    }

    public static ReportExportContext of(List<Vulnerability> findings, ScanMetadata metadata, String baseRepoPath) {
        return new ReportExportContext(findings, metadata, baseRepoPath, null, null);
    }

    public static ReportExportContext of(List<Vulnerability> findings, ScanMetadata metadata, String baseRepoPath,
                                         String branch, String baseSha) {
        return new ReportExportContext(findings, metadata, baseRepoPath, branch, baseSha);
    }
}
