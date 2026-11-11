package com.cb.auditagent.service.reporting;

import com.cb.auditagent.domain.ScanMetadata;
import com.cb.auditagent.domain.Vulnerability;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Generates portable Markdown security audit reports.
 * Follows Single Responsibility Principle (SRP) and Open/Closed Principle (OCP).
 */
@Component
public class MarkdownReportExporter implements ReportExporter {

    private static final DateTimeFormatter REPORT_TIME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    @Override
    public ReportFormat getFormat() {
        return ReportFormat.MARKDOWN;
    }

    @Override
    public String exportString(ReportExportContext context) {
        List<Vulnerability> findings = context.findings();
        ScanMetadata metadata = context.metadata();
        String baseRepoPath = context.baseRepoPath();
        String branch = context.branch();
        String baseSha = context.baseSha();

        SecurityMetricsCalculator.SeverityCounts counts = SecurityMetricsCalculator.computeCounts(findings);
        String riskPosture = SecurityMetricsCalculator.computeRiskPosture(counts);
        StringBuilder report = new StringBuilder();

        report.append("# 🛡️ AuditAgent Enterprise Security Assessment\n\n");
        report.append("> **Classification:** Confidential - repository-scoped security artifact  \n");
        report.append("> **Repository:** ").append(markdownValue(baseRepoPath)).append("  \n");
        report.append("> **Generated:** ").append(REPORT_TIME.format(Instant.now())).append("\n\n");

        report.append("## 📊 Executive Summary\n\n");
        report.append("**Risk posture:** ").append(riskPosture).append(". ");
        report.append("The scan identified **").append(counts.total()).append(" finding(s)** across **")
                .append(metadata.getTotalFilesScanned()).append(" file(s)** and **")
                .append(String.format(Locale.ROOT, "%,d", metadata.getTotalLocScanned()))
                .append(" lines of code**.\n\n");

        if (counts.total() > 0) {
            report.append("```mermaid\n");
            report.append("pie title Findings by Severity\n");
            if (counts.high() > 0)
                report.append("    \"High\" : ").append(counts.high()).append("\n");
            if (counts.medium() > 0)
                report.append("    \"Medium\" : ").append(counts.medium()).append("\n");
            if (counts.low() > 0)
                report.append("    \"Low\" : ").append(counts.low()).append("\n");
            if (counts.info() > 0)
                report.append("    \"Informational\" : ").append(counts.info()).append("\n");
            report.append("```\n\n");
        }

        report.append("| Severity | Findings | Management guidance |\n");
        report.append("| --- | ---: | --- |\n");
        report.append("| 🔴 High | ").append(counts.high()).append(" | Prioritize for immediate remediation |\n");
        report.append("| 🟡 Medium | ").append(counts.medium())
                .append(" | Review and schedule for upcoming sprint |\n");
        report.append("| 🟢 Low | ").append(counts.low()).append(" | Address through normal backlog |\n");
        report.append("| 🔵 Informational | ").append(counts.info())
                .append(" | Track for awareness and best practices |\n");
        report.append("| **Total** | **").append(counts.total()).append("** | |\n\n");

        report.append("## 🎯 Compliance & Impact\n\n");
        report.append(
                "Unresolved security findings represent a continuous risk to the application's integrity, availability, and confidentiality. Addressing these vulnerabilities ensures alignment with industry best practices (e.g., OWASP Top 10) and minimizes the potential attack surface. Proactive remediation demonstrates a commitment to secure software development and protects both user data and organizational reputation.\n\n");

        report.append("## 🔧 Remediation Summary\n\n");
        if (counts.total() == 0) {
            report.append("No remediation actions are currently required.\n\n");
        } else {
            report.append(
                    "To improve the security posture, the development team should focus on the following aggregate steps:\n");
            report.append(
                    "- **High Severity:** Immediately assign to engineers for triage and patching. Do not deploy to production until resolved.\n");
            report.append(
                    "- **Medium Severity:** Log tickets in the issue tracker and allocate time in the next development cycle.\n");
            report.append(
                    "- **Low & Info:** Review periodically and apply proposed fixes when modifying the affected components.\n\n");
        }

        report.append("## 🚀 Next Steps\n\n");
        report.append(
                "1. **Review Detailed Findings:** Examine the specific code locations and vulnerable snippets provided below.\n");
        report.append(
                "2. **Validate Proposed Fixes:** Evaluate the AI-generated remediation suggestions for context appropriateness.\n");
        report.append(
                "3. **Apply & Verify:** Integrate the fixes into the codebase, run regression tests, and trigger a follow-up scan to confirm resolution.\n\n");

        report.append("## 🔍 Assessment Scope\n\n");
        report.append("| Property | Value |\n");
        report.append("| --- | --- |\n");
        report.append("| Project | ").append(markdownValue(metadata.getProjectName())).append(" |\n");
        report.append("| Repository | ").append(markdownValue(baseRepoPath)).append(" |\n");
        report.append("| Branch | ").append(markdownValue(branch)).append(" |\n");
        report.append("| Scanned commit | `").append(markdownCode(baseSha)).append("` |\n");
        report.append("| Scanner | ").append(markdownValue(metadata.getScannerName())).append(" |\n");
        report.append("| Scan started | ").append(markdownValue(metadata.getStartTime())).append(" |\n");
        report.append("| Scan completed | ").append(markdownValue(metadata.getEndTime())).append(" |\n");
        report.append("| Duration | ").append(markdownValue(metadata.getScanDuration())).append(" |\n");
        report.append("| Files scanned | ").append(metadata.getTotalFilesScanned()).append(" |\n");
        report.append("| Lines of code | ").append(String.format(Locale.ROOT, "%,d", metadata.getTotalLocScanned()))
                .append(" |\n\n");

        report.append("## Detailed findings\n\n");
        if (findings.isEmpty()) {
            report.append("No security findings were reported by the configured scanner.\n");
            return report.toString();
        }
        for (Vulnerability finding : findings) {
            report.append("### ").append(markdownText(finding.getId())).append(" - ")
                    .append(markdownText(finding.getVulnType())).append("\n\n");
            report.append("| Attribute | Value |\n| --- | --- |\n");
            report.append("| Severity | ").append(markdownValue(finding.getSeverity())).append(" |\n");
            report.append("| Status | ").append(markdownValue(finding.getStatus())).append(" |\n");
            report.append("| Rule | ").append(markdownValue(finding.getRuleId())).append(" |\n");
            report.append("| Location | `").append(markdownCode(finding.getFilePath())).append(":")
                    .append(finding.getLineNumber()).append("` |\n");
            report.append("| Language | ").append(markdownValue(finding.getLanguage())).append(" |\n\n");
            report.append(markdownText(finding.getDescription())).append("\n\n");
            appendCodeBlock(report, "Vulnerable code", finding.getLanguage(),
                    formatSnippetWithLineNumbers(finding, baseRepoPath));
            if (finding.getProposedFix() != null && !finding.getProposedFix().isBlank()) {
                appendCodeBlock(report, "Proposed remediation", finding.getLanguage(), finding.getProposedFix());
            }
            report.append("---\n\n");
        }
        return report.toString();
    }

    private void appendCodeBlock(StringBuilder report, String heading, String language, String code) {
        if (code == null || code.isBlank())
            return;
        String safeLanguage = Optional.ofNullable(language).orElse("").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_+-]", "");
        String fence = "~~~~";
        while (code.contains(fence))
            fence += "~";
        report.append("#### ").append(heading).append("\n\n");
        report.append(fence).append(safeLanguage).append("\n").append(code.strip())
                .append("\n").append(fence).append("\n\n");
    }

    private String markdownValue(Object value) {
        if (value == null || String.valueOf(value).isBlank())
            return "N/A";
        return escapeMarkdownHtml(String.valueOf(value)).replace("|", "\\|")
                .replace("\r", " ").replace("\n", " ");
    }

    private String markdownText(Object value) {
        if (value == null || String.valueOf(value).isBlank())
            return "N/A";
        return escapeMarkdownHtml(String.valueOf(value)).replace("\r\n", "\n").replace('\r', '\n');
    }

    private String markdownCode(String value) {
        return Optional.ofNullable(value).filter(text -> !text.isBlank()).orElse("N/A")
                .replace("`", "'").replace("|", "\\|");
    }

    private String escapeMarkdownHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String formatSnippetWithLineNumbers(Vulnerability finding, String baseRepoPath) {
        String snippet = finding.getCodeSnippet();
        if (snippet == null || snippet.isBlank()) {
            return "No code snippet available.";
        }

        if (baseRepoPath != null && finding.getFilePath() != null) {
            Path fullPath = Paths.get(baseRepoPath).resolve(finding.getFilePath()).normalize();
            if (Files.exists(fullPath) && Files.isRegularFile(fullPath)) {
                try {
                    List<String> allLines = Files.readAllLines(fullPath);
                    int targetLine = finding.getLineNumber();
                    int start = Math.max(1, targetLine - 3);
                    int end = Math.min(allLines.size(), targetLine + 3);

                    StringBuilder sb = new StringBuilder();
                    for (int i = start; i <= end; i++) {
                        String prefix = (i == targetLine) ? "-> " : "   ";
                        sb.append(prefix).append(String.format(java.util.Locale.ROOT, "%4d | ", i))
                                .append(allLines.get(i - 1)).append("\n");
                    }
                    return sb.toString().stripTrailing();
                } catch (Exception ignored) {
                }
            }
        }

        String[] lines = snippet.split("\\r?\\n");
        int startLine = Math.max(1, finding.getLineNumber() - 10);
        if (lines.length == 1) {
            return "-> " + String.format(java.util.Locale.ROOT, "%4d | ", finding.getLineNumber()) + lines[0];
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            int currentLine = startLine + i;
            String prefix = (currentLine == finding.getLineNumber()) ? "-> " : "   ";
            sb.append(prefix).append(String.format(java.util.Locale.ROOT, "%4d | ", currentLine)).append(lines[i])
                    .append("\n");
        }
        return sb.toString().stripTrailing();
    }
}
