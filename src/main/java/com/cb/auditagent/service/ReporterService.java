package com.cb.auditagent.service;

import org.springframework.stereotype.Service;

import com.cb.auditagent.domain.ScanMetadata;
import com.cb.auditagent.domain.Severity;
import com.cb.auditagent.domain.Vulnerability;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ReporterService {

    private static final DateTimeFormatter REPORT_TIME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    public String generateMarkdown(List<Vulnerability> findings, ScanMetadata metadata, String baseRepoPath) {
        return generateMarkdown(findings, metadata, baseRepoPath, null, null);
    }

    public String generateMarkdown(List<Vulnerability> rawFindings, ScanMetadata metadata, String baseRepoPath,
            String branch, String baseSha) {
        List<Vulnerability> findings = rawFindings == null ? List.of() : rawFindings;
        SeverityCounts counts = SeverityCounts.from(findings);
        String riskPosture = counts.high() > 0 ? "🔴 Immediate action recommended"
                : counts.medium() > 0 ? "🟡 Review and prioritize" : "🟢 No elevated findings detected";
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

    public byte[] generatePdf(List<Vulnerability> findings, ScanMetadata metadata, String repository,
            String branch, String baseSha) {
        return new PdfReportRenderer().render(findings, metadata, repository, branch, baseSha);
    }

    @SuppressWarnings("unused")
    private String generateLegacyMarkdown(List<Vulnerability> findings, ScanMetadata metadata, String baseRepoPath) {
        StringBuilder sb = new StringBuilder();
        String currentDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        sb.append("# 🛡️ Audit Report: ").append(metadata.getProjectName()).append("\n\n");

        sb.append("### 📝 Project Information\n");
        sb.append("| Property | Value |\n");
        sb.append("| :--- | :--- |\n");
        sb.append("| **Project Name** | ").append(metadata.getProjectName()).append(" |\n");
        sb.append("| **Scanner** | ").append(metadata.getScannerName()).append(" |\n");
        sb.append("| **Date** | ").append(new SimpleDateFormat("yyyy-MM-dd").format(new Date())).append(" |\n");
        sb.append("| **Report Creation Time** | ").append(currentDate).append(" |\n\n");

        sb.append("### ⏱️ Scan Performance\n");
        sb.append("| Metric | Value |\n");
        sb.append("| :--- | :--- |\n");
        sb.append("| **Start Time** | ").append(Optional.ofNullable(metadata.getStartTime()).orElse("N/A"))
                .append(" |\n");
        sb.append("| **End Time** | ").append(Optional.ofNullable(metadata.getEndTime()).orElse("N/A")).append(" |\n");
        sb.append("| **Scan Duration** | ").append(Optional.ofNullable(metadata.getScanDuration()).orElse("N/A"))
                .append(" |\n\n");

        sb.append("### 📊 Scan Scope\n");
        sb.append("| Metric | Value |\n");
        sb.append("| :--- | :--- |\n");
        sb.append("| **Lines of Code Scanned** | ").append(String.format("%,d", metadata.getTotalLocScanned()))
                .append(" |\n");
        sb.append("| **Files Scanned** | ").append(metadata.getTotalFilesScanned()).append(" |\n\n");

        SeverityCounts sc = SeverityCounts.from(findings);
        long high = sc.high();
        long medium = sc.medium();
        long low = sc.low();
        long info = sc.info();
        long total = sc.total();

        sb.append("## 📈 Vulnerability Summary\n\n");
        if (total > 0) {
            sb.append("```mermaid\n");
            sb.append("pie title Findings by Severity\n");
            if (high > 0)
                sb.append("    \"High\" : ").append(high).append("\n");
            if (medium > 0)
                sb.append("    \"Medium\" : ").append(medium).append("\n");
            if (low > 0)
                sb.append("    \"Low\" : ").append(low).append("\n");
            if (info > 0)
                sb.append("    \"Info\" : ").append(info).append("\n");
            sb.append("```\n\n");
        }

        sb.append("### 🔴 Vulnerability wise Category (Severity)\n");
        sb.append("| Severity | Count | Status |\n");
        sb.append("| :--- | :--- | :--- |\n");
        sb.append(String.format("| 🔴 **High** | %d | %s | %n", high, high > 0 ? "❌ Action Required" : "✅ Clear"));
        sb.append(String.format("| 🟡 **Medium** | %d | %s | %n", medium,
                medium > 0 ? "⚠️ Review Recommended" : "✅ Clear"));
        sb.append(String.format("| 🟢 **Low** | %d | %s | %n", low, low > 0 ? "ℹ️ Info" : "✅ Clear"));
        sb.append(String.format("| 🔵 **Info** | %d | %s | %n", info, info > 0 ? "ℹ️ Note" : "✅ Clear"));
        sb.append(String.format("| **Total Vulnerabilities** | **%d** | |%n%n", total));

        sb.append("---\n\n");
        sb.append("### 🛡️ Findings Details\n\n");
        for (Vulnerability v : findings) {
            sb.append("#### ").append(v.getId()).append(" - ").append(v.getVulnType()).append("\n");
            sb.append("- **Severity:** ").append(v.getSeverity().name()).append("\n");
            sb.append("- **Status:** ").append(v.getStatus().name()).append("\n");
            sb.append("- **File Path:** `").append(v.getFilePath()).append(":").append(v.getLineNumber()).append("`\n");
            sb.append("- **Description:** ").append(v.getDescription()).append("\n");
            sb.append("- **Language:** ").append(v.getLanguage()).append("\n");
            sb.append("- **Snippet:**\n```").append(v.getLanguage().toLowerCase()).append("\n")
                    .append(v.getCodeSnippet()).append("\n```\n");
            if (v.getProposedFix() != null && !v.getProposedFix().isEmpty()) {
                sb.append("- **Proposed Fix:**\n```").append(v.getLanguage().toLowerCase()).append("\n")
                        .append(v.getProposedFix()).append("\n```\n");
            }
            sb.append("\n---\n\n");
        }

        return sb.toString();
    }

    public String generateHtml(List<Vulnerability> findings, ScanMetadata metadata, String baseRepoPath) {
        String currentDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        SeverityCounts sc = SeverityCounts.from(findings);
        long high = sc.high();
        long medium = sc.medium();
        long low = sc.low();
        long info = sc.info();

        StringBuilder rows = new StringBuilder();
        for (Vulnerability v : findings) {
            rows.append(String.format(
                    "<tr class=\"vuln-row\" data-severity=\"%s\">\n" +
                            "    <td><strong>%s</strong></td>\n" +
                            "    <td><span class=\"sev-badge %s\">%s</span></td>\n" +
                            "    <td>%s</td>\n" +
                            "    <td>%s</td>\n" +
                            "    <td>%d</td>\n" +
                            "    <td><span class=\"status-badge %s\">%s</span></td>\n" +
                            "</tr>\n",
                    escapeHtml(v.getSeverity().name()),
                    escapeHtml(v.getId()),
                    escapeHtml(v.getSeverity().name().toLowerCase()), escapeHtml(v.getSeverity().name()),
                    escapeHtml(v.getVulnType()),
                    escapeHtml(v.getFilePath()),
                    v.getLineNumber(),
                    escapeHtml(v.getStatus().name().toLowerCase()), escapeHtml(v.getStatus().name())));
        }

        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>Security Audit Report - " + escapeHtml(metadata.getProjectName()) + "</title>\n" +
                "    <script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>\n" +
                "    <style>\n" +
                "        :root {\n" +
                "            --primary: #2563eb;\n" +
                "            --danger: #dc2626;\n" +
                "            --warning: #f59e0b;\n" +
                "            --success: #16a34a;\n" +
                "            --info: #0ea5e9;\n" +
                "            --bg: #f8fafc;\n" +
                "            --card: #ffffff;\n" +
                "            --text: #1e293b;\n" +
                "        }\n" +
                "        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: var(--bg); color: var(--text); margin: 0; padding: 20px; }\n"
                +
                "        .container { max-width: 1200px; margin: 0 auto; }\n" +
                "        .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 30px; border-bottom: 2px solid #e2e8f0; padding-bottom: 20px; }\n"
                +
                "        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; margin-bottom: 30px; }\n"
                +
                "        .stat-card { background: var(--card); padding: 20px; border-radius: 12px; box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.1); border-left: 5px solid var(--primary); }\n"
                +
                "        .stat-card.high { border-left-color: var(--danger); }\n" +
                "        .stat-card.medium { border-left-color: var(--warning); }\n" +
                "        .stat-card.low { border-left-color: var(--success); }\n" +
                "        .stat-value { font-size: 24px; font-weight: bold; margin: 10px 0; }\n" +
                "        .stat-label { color: #64748b; font-size: 14px; text-transform: uppercase; }\n" +
                "        .charts-container { display: grid; grid-template-columns: 1fr 2fr; gap: 30px; margin-bottom: 40px; }\n"
                +
                "        .card { background: var(--card); padding: 25px; border-radius: 12px; box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.1); }\n"
                +
                "        table { width: 100%; border-collapse: collapse; margin-top: 20px; }\n" +
                "        th { text-align: left; background: #f1f5f9; padding: 12px; font-weight: 600; border-bottom: 2px solid #e2e8f0; }\n"
                +
                "        td { padding: 12px; border-bottom: 1px solid #e2e8f0; font-size: 14px; }\n" +
                "        .sev-badge { padding: 4px 8px; border-radius: 4px; font-weight: 600; font-size: 12px; }\n" +
                "        .sev-badge.high { background: #fee2e2; color: #991b1b; }\n" +
                "        .sev-badge.medium { background: #fef3c7; color: #92400e; }\n" +
                "        .sev-badge.low { background: #dcfce7; color: #166534; }\n" +
                "        .sev-badge.info { background: #e0f2fe; color: #0369a1; }\n" +
                "        .status-badge { padding: 4px 8px; border-radius: 4px; font-weight: 600; font-size: 11px; text-transform: uppercase; }\n"
                +
                "        .status-badge.detected { background: #f1f5f9; color: #475569; }\n" +
                "        .status-badge.analyzing, .status-badge.generating_fix { background: #e0e7ff; color: #3730a3; }\n"
                +
                "        .status-badge.verifying { background: #fef08a; color: #854d0e; }\n" +
                "        .status-badge.awaiting_approval { background: #fef3c7; color: #b45309; }\n" +
                "        .status-badge.patch_failed { background: #fecaca; color: #991b1b; }\n" +
                "        .status-badge.fixed { background: #d1fae5; color: #065f46; }\n" +
                "        .status-badge.ignored { background: #ffedd5; color: #9a3412; }\n" +
                "        pre { background: #f1f5f9; padding: 15px; border-radius: 8px; overflow-x: auto; font-size: 13px; }\n"
                +
                "        .filter-box { margin-bottom: 20px; display: flex; gap: 10px; }\n" +
                "        input, select { padding: 8px 12px; border: 1px solid #cbd5e1; border-radius: 6px; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"header\">\n" +
                "            <div>\n" +
                "                <h1>🛡️ Security Audit Report</h1>\n" +
                "                <p>Project: <strong>" + escapeHtml(metadata.getProjectName()) + "</strong> | Date: "
                + currentDate + "</p>\n" +
                "            </div>\n" +
                "            <div style=\"text-align: right\">\n" +
                "                <p>Scanner: " + escapeHtml(metadata.getScannerName()) + "</p>\n" +
                "                <p>Duration: " + Optional.ofNullable(metadata.getScanDuration()).orElse("N/A")
                + "</p>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"stats-grid\">\n" +
                "            <div class=\"stat-card high\">\n" +
                "                <div class=" + '"' + "stat-label" + '"' + ">High Risk</div>\n" +
                "                <div class=" + '"' + "stat-value" + '"' + ">" + high + "</div>\n" +
                "            </div>\n" +
                "            <div class=\"stat-card medium\">\n" +
                "                <div class=" + '"' + "stat-label" + '"' + ">Medium Risk</div>\n" +
                "                <div class=" + '"' + "stat-value" + '"' + ">" + medium + "</div>\n" +
                "            </div>\n" +
                "            <div class=\"stat-card low\">\n" +
                "                <div class=" + '"' + "stat-label" + '"' + ">Low Risk</div>\n" +
                "                <div class=" + '"' + "stat-value" + '"' + ">" + low + "</div>\n" +
                "            </div>\n" +
                "            <div class=\"stat-card\">\n" +
                "                <div class=" + '"' + "stat-label" + '"' + ">Files Scanned</div>\n" +
                "                <div class=" + '"' + "stat-value" + '"' + ">" + metadata.getTotalFilesScanned()
                + "</div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"charts-container\">\n" +
                "            <div class=\"card\">\n" +
                "                <h3>Severity Distribution</h3>\n" +
                "                <canvas id=\"severityChart\"></canvas>\n" +
                "            </div>\n" +
                "            <div class=\"card\">\n" +
                "                <h3>Scan Details</h3>\n" +
                "                <p><strong>Total LOC:</strong> " + String.format("%,d", metadata.getTotalLocScanned())
                + "</p>\n" +
                "                <p><strong>Report ID:</strong> "
                + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + "</p>\n" +
                "                <p><strong>Status:</strong> Completed</p>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"card\">\n" +
                "            <h3>Vulnerability Findings</h3>\n" +
                "            <div class=\"filter-box\">\n" +
                "                <input type=\"text\" id=\"searchInput\" placeholder=\"Search by file or type...\" onkeyup=\"filterTable()\">\n"
                +
                "                <select id=\"severityFilter\" onchange=\"filterTable()\">\n" +
                "                    <option value=\"\">All Severities</option>\n" +
                "                    <option value=\"HIGH\">High</option>\n" +
                "                    <option value=\"MEDIUM\">Medium</option>\n" +
                "                    <option value=\"LOW\">Low</option>\n" +
                "                </select>\n" +
                "            </div>\n" +
                "            <div style=\"overflow-x: auto; width: 100%;\">\n" +
                "                <table id=\"vulnTable\">\n" +
                "                    <thead>\n" +
                "                        <tr>\n" +
                "                            <th>ID</th>\n" +
                "                            <th>Severity</th>\n" +
                "                            <th>Type</th>\n" +
                "                            <th>File Path</th>\n" +
                "                            <th>Line</th>\n" +
                "                            <th>Status</th>\n" +
                "                        </tr>\n" +
                "                    </thead>\n" +
                "                    <tbody>\n" +
                rows.toString() +
                "                    </tbody>\n" +
                "                </table>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <script>\n" +
                "        const ctx = document.getElementById('severityChart').getContext('2d');\n" +
                "        new Chart(ctx, {\n" +
                "            type: 'doughnut',\n" +
                "            data: {\n" +
                "                labels: ['High', 'Medium', 'Low', 'Info'],\n" +
                "                datasets: [{\n" +
                "                    data: [" + high + ", " + medium + ", " + low + ", " + info + "],\n" +
                "                    backgroundColor: ['#dc2626', '#f59e0b', '#16a34a', '#0ea5e9']\n" +
                "                }]\n" +
                "            },\n" +
                "            options: { responsive: true, plugins: { legend: { position: 'bottom' } } }\n" +
                "        });\n" +
                "\n" +
                "        function filterTable() {\n" +
                "            const input = document.getElementById(\"searchInput\").value.toUpperCase();\n" +
                "            const sevFilter = document.getElementById(\"severityFilter\").value;\n" +
                "            const rows = document.getElementsByClassName(\"vuln-row\");\n" +
                "\n" +
                "            for (let i = 0; i < rows.length; i++) {\n" +
                "                const text = rows[i].textContent.toUpperCase();\n" +
                "                const sev = rows[i].getAttribute(\"data-severity\");\n" +
                "                const matchesSearch = text.includes(input);\n" +
                "                const matchesSev = sevFilter === \"\" || sev === sevFilter;\n" +
                "                rows[i].style.display = (matchesSearch && matchesSev) ? \"\" : \"none\";\n" +
                "            }\n" +
                "        }\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }

    // ── Helper: Code Snippet Formatting ────────────────────────────────────

    private String formatSnippetWithLineNumbers(Vulnerability finding, String baseRepoPath) {
        String snippet = finding.getCodeSnippet();
        if (snippet == null || snippet.isEmpty() || snippet.startsWith("Source code not available")) {
            return snippet;
        }

        java.io.File targetFile = new java.io.File(baseRepoPath, finding.getFilePath());
        if (targetFile.exists() && targetFile.isFile()) {
            try {
                java.util.List<String> fileLines = java.nio.file.Files.readAllLines(targetFile.toPath(),
                        java.nio.charset.StandardCharsets.UTF_8);
                int exactLineIdx = finding.getLineNumber() - 1;
                if (exactLineIdx >= 0 && exactLineIdx < fileLines.size()) {
                    int startIdx = Math.max(0, exactLineIdx - 10);
                    int endIdx = Math.min(exactLineIdx + 11, fileLines.size());

                    StringBuilder sb = new StringBuilder();
                    for (int i = startIdx; i < endIdx; i++) {
                        int currentLine = i + 1;
                        String prefix = (currentLine == finding.getLineNumber()) ? "-> " : "   ";
                        sb.append(prefix).append(String.format(java.util.Locale.ROOT, "%4d | ", currentLine))
                                .append(fileLines.get(i)).append("\n");
                    }
                    return sb.toString().stripTrailing();
                }
            } catch (Exception e) {
                // Ignore and fallback
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

    // ── Helper: Severity counting ──────────────────────────────────────────

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

    private record SeverityCounts(long high, long medium, long low, long info, long total) {
        static SeverityCounts from(List<Vulnerability> findings) {
            return new SeverityCounts(
                    findings.stream().filter(v -> v.getSeverity() == Severity.HIGH).count(),
                    findings.stream().filter(v -> v.getSeverity() == Severity.MEDIUM).count(),
                    findings.stream().filter(v -> v.getSeverity() == Severity.LOW).count(),
                    findings.stream().filter(v -> v.getSeverity() == Severity.INFO).count(),
                    findings.size());
        }
    }

    // ── Helper: HTML escaping to prevent XSS ──────────────────────────────

    private String escapeHtml(String input) {
        if (input == null)
            return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
