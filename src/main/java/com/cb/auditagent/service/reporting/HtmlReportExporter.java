package com.cb.auditagent.service.reporting;

import com.cb.auditagent.domain.ScanMetadata;
import com.cb.auditagent.domain.Vulnerability;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Generates interactive HTML security assessment reports.
 * Follows Single Responsibility Principle (SRP) and Open/Closed Principle (OCP).
 */
@Component
public class HtmlReportExporter implements ReportExporter {

    @Override
    public ReportFormat getFormat() {
        return ReportFormat.HTML;
    }

    @Override
    public String exportString(ReportExportContext context) {
        List<Vulnerability> findings = context.findings();
        ScanMetadata metadata = context.metadata();

        String currentDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        SecurityMetricsCalculator.SeverityCounts sc = SecurityMetricsCalculator.computeCounts(findings);
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
                "        .stat-card.info { border-left-color: var(--info); }\n" +
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
