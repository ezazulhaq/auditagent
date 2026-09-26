package com.cb.auditagent.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import com.cb.auditagent.domain.ScanMetadata;
import com.cb.auditagent.domain.Severity;
import com.cb.auditagent.domain.Vulnerability;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class PdfReportRenderer {
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN = 48;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2);
    private static final float[] NAVY = { 0.035f, 0.090f, 0.150f };
    private static final float[] CYAN = { 0.040f, 0.710f, 0.820f };
    private static final float[] TEXT = { 0.090f, 0.125f, 0.180f };
    private static final float[] MUTED = { 0.360f, 0.420f, 0.500f };
    private static final float[] SURFACE = { 0.955f, 0.970f, 0.985f };

    static {
        if (System.getProperty("pdfbox.fontcache", "").isBlank()) {
            Path cacheDirectory = Path.of(System.getProperty("java.io.tmpdir"), "auditagent-pdfbox-font-cache");
            try {
                Files.createDirectories(cacheDirectory);
                System.setProperty("pdfbox.fontcache", cacheDirectory.toString());
            } catch (IOException ignored) {
                System.setProperty("pdfbox.fontcache", System.getProperty("java.io.tmpdir"));
            }
        }
    }

    private static final DateTimeFormatter GENERATED_AT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    byte[] render(List<Vulnerability> rawFindings, ScanMetadata metadata, String repository,
            String branch, String baseSha) {
        List<Vulnerability> findings = rawFindings == null ? List.of() : rawFindings;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDDocumentInformation information = document.getDocumentInformation();
            information.setTitle("AuditAgent Security Assessment - " + printable(repository));
            information.setAuthor("AuditAgent");
            information.setSubject("Repository security scan report");
            information.setCreator("AuditAgent Enterprise Reporting");

            try (Writer writer = new Writer(document)) {
                writer.newPage();
                writer.cover(metadata, repository, branch, baseSha);
                writer.executiveSummary(findings, metadata);
                writer.findings(findings, repository);
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not generate the PDF report", e);
        }
    }

    private static final class Writer implements AutoCloseable {
        private final PDDocument document;
        private PDPageContentStream content;
        private float y;
        private int pageNumber;
        private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final PDFont mono = new PDType1Font(Standard14Fonts.FontName.COURIER);

        private Writer(PDDocument document) {
            this.document = document;
        }

        private void newPage() throws IOException {
            closePage();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            pageNumber++;
            content = new PDPageContentStream(document, page);
            fill(0, PAGE_HEIGHT - 36, PAGE_WIDTH, 36, NAVY);
            text(MARGIN, PAGE_HEIGHT - 23, "AUDITAGENT  /  SECURITY ASSURANCE", bold, 8.5f, 1, 1, 1);
            text(PAGE_WIDTH - MARGIN - 52, 24, "PAGE " + pageNumber, bold, 7.5f, MUTED);
            text(MARGIN, 24, "CONFIDENTIAL - REPOSITORY-SCOPED", regular, 7.5f, MUTED);
            y = PAGE_HEIGHT - 66;
        }

        private void cover(ScanMetadata metadata, String repository, String branch, String baseSha) throws IOException {
            label("ENTERPRISE SECURITY ASSESSMENT");
            paragraph("Security posture, prioritized findings, and remediation context", bold, 22, NAVY, 28);
            y -= 5;
            paragraph(printable(repository), bold, 14, TEXT, 18);
            paragraph("Branch " + printable(branch) + "  |  Commit " + shortSha(baseSha), regular, 9.5f, MUTED, 14);
            y -= 18;

            fill(MARGIN, y - 116, CONTENT_WIDTH, 116, SURFACE);
            keyValue("Report generated", GENERATED_AT.format(Instant.now()), MARGIN + 16, y - 24);
            keyValue("Scan engine", value(metadata.getScannerName()), MARGIN + 16, y - 48);
            keyValue("Scan completed", value(metadata.getEndTime()), MARGIN + 16, y - 72);
            keyValue("Scan duration", value(metadata.getScanDuration()), MARGIN + 16, y - 96);
            y -= 143;
        }

        private void executiveSummary(List<Vulnerability> findings, ScanMetadata metadata) throws IOException {
            section("Executive summary");
            Counts counts = Counts.from(findings);
            String posture = counts.high > 0 ? "Immediate action recommended"
                    : counts.medium > 0 ? "Review and prioritize" : "No elevated findings detected";
            paragraph(posture + ". The scan identified " + counts.total + " finding(s) across "
                    + metadata.getTotalFilesScanned() + " file(s) and "
                    + String.format(Locale.ROOT, "%,d", metadata.getTotalLocScanned()) + " lines of code.",
                    regular, 10, TEXT, 15);
            y -= 8;
            metrics(counts);
            y -= 12;

            section("Compliance & Impact");
            paragraph(
                    "Unresolved security findings represent a continuous risk to the application's integrity, availability, and confidentiality. Addressing these vulnerabilities ensures alignment with industry best practices (e.g., OWASP Top 10) and minimizes the potential attack surface. Proactive remediation demonstrates a commitment to secure software development and protects both user data and organizational reputation.",
                    regular, 9, TEXT, 13);
            y -= 12;

            section("Remediation Summary");
            if (counts.total == 0) {
                paragraph("No remediation actions are currently required.", regular, 9, TEXT, 13);
            } else {
                paragraph(
                        "To improve the security posture, the development team should focus on the following aggregate steps:",
                        regular, 9, TEXT, 13);
                y -= 4;
                paragraph(
                        "- High Severity: Immediately assign to engineers for triage and patching. Do not deploy to production until resolved.",
                        regular, 9, TEXT, 13);
                paragraph(
                        "- Medium Severity: Log tickets in the issue tracker and allocate time in the next development cycle.",
                        regular, 9, TEXT, 13);
                paragraph(
                        "- Low & Info: Review periodically and apply proposed fixes when modifying the affected components.",
                        regular, 9, TEXT, 13);
            }
            y -= 12;

            section("Next Steps");
            paragraph(
                    "1. Review Detailed Findings: Examine the specific code locations and vulnerable snippets provided below.",
                    regular, 9, TEXT, 13);
            paragraph(
                    "2. Validate Proposed Fixes: Evaluate the AI-generated remediation suggestions for context appropriateness.",
                    regular, 9, TEXT, 13);
            paragraph(
                    "3. Apply & Verify: Integrate the fixes into the codebase, run regression tests, and trigger a follow-up scan to confirm resolution.",
                    regular, 9, TEXT, 13);
            y -= 12;

            section("Assessment scope");
            keyValue("Project", value(metadata.getProjectName()), MARGIN, y - 4);
            keyValue("Files scanned", Integer.toString(metadata.getTotalFilesScanned()), MARGIN, y - 28);
            keyValue("Lines of code", String.format(Locale.ROOT, "%,d", metadata.getTotalLocScanned()), MARGIN, y - 52);
            keyValue("Scan window", value(metadata.getStartTime()) + " to " + value(metadata.getEndTime()), MARGIN,
                    y - 76);
            y -= 102;
        }

        private void metrics(Counts counts) throws IOException {
            float gap = 8;
            float width = (CONTENT_WIDTH - (gap * 3)) / 4;
            metric(MARGIN, width, "HIGH", counts.high, new float[] { 0.78f, 0.12f, 0.16f });
            metric(MARGIN + width + gap, width, "MEDIUM", counts.medium, new float[] { 0.82f, 0.48f, 0.05f });
            metric(MARGIN + (width + gap) * 2, width, "LOW", counts.low, new float[] { 0.08f, 0.55f, 0.36f });
            metric(MARGIN + (width + gap) * 3, width, "INFO", counts.info, new float[] { 0.05f, 0.48f, 0.78f });
            y -= 66;
        }

        private void metric(float x, float width, String label, long count, float[] accent) throws IOException {
            fill(x, y - 58, width, 58, SURFACE);
            fill(x, y - 58, 3, 58, accent);
            text(x + 12, y - 22, label, bold, 7.5f, MUTED);
            text(x + 12, y - 45, Long.toString(count), bold, 18, TEXT);
        }

        private void findings(List<Vulnerability> findings, String repository) throws IOException {
            ensure(100);
            section("Detailed findings");
            if (findings.isEmpty()) {
                paragraph("No security findings were reported by the configured scanner.", regular, 10, TEXT, 15);
                return;
            }
            for (Vulnerability finding : findings)
                finding(finding, repository);
        }

        private void finding(Vulnerability finding, String repository) throws IOException {
            ensure(175);
            float[] accent = severityColor(finding.getSeverity());
            fill(MARGIN, y - 30, CONTENT_WIDTH, 30, NAVY);
            fill(MARGIN, y - 30, 4, 30, accent);
            text(MARGIN + 12, y - 20, fit(printable(finding.getId()) + "  /  "
                    + printable(finding.getVulnType()), bold, 10, CONTENT_WIDTH - 130), bold, 10, 1, 1, 1);
            text(PAGE_WIDTH - MARGIN - 72, y - 20, printable(severity(finding)), bold, 8.5f, accent);
            y -= 47;
            paragraph("Location: " + printable(finding.getFilePath()) + ":" + finding.getLineNumber(),
                    bold, 8.5f, TEXT, 12);
            paragraph("Status: " + printable(finding.getStatus()) + "  |  Rule: "
                    + value(finding.getRuleId()), regular, 8.5f, MUTED, 12);
            paragraph(value(finding.getDescription()), regular, 9.5f, TEXT, 14);
            y -= 5;
            codeBlock(formatSnippetWithLineNumbers(finding, repository));
            y -= 16;
        }

        private void codeBlock(String snippet) throws IOException {
            if (snippet == null || snippet.isBlank())
                return;
            List<String> lines = new ArrayList<>();
            String[] source = printable(snippet).split("\\R", -1);
            int visible = Math.min(source.length, 14);
            for (int i = 0; i < visible; i++)
                lines.add(fit(source[i], mono, 7.2f, CONTENT_WIDTH - 22));
            if (source.length > visible)
                lines.add("... snippet truncated in PDF; download Markdown for full text ...");
            float height = 18 + (lines.size() * 10);
            ensure(height + 10);
            fill(MARGIN, y - height, CONTENT_WIDTH, height, SURFACE);
            float lineY = y - 16;
            for (String line : lines) {
                text(MARGIN + 11, lineY, line, mono, 7.2f, TEXT);
                lineY -= 10;
            }
            y -= height;
        }

        private void section(String title) throws IOException {
            ensure(45);
            label(title.toUpperCase(Locale.ROOT));
            y -= 8;
        }

        private void label(String value) throws IOException {
            text(MARGIN, y, printable(value), bold, 8, CYAN);
            y -= 16;
        }

        private void paragraph(String value, PDFont font, float size, float[] color, float leading) throws IOException {
            for (String line : wrap(printable(value), font, size, CONTENT_WIDTH)) {
                ensure(leading + 5);
                text(MARGIN, y, line, font, size, color);
                y -= leading;
            }
        }

        private void keyValue(String key, String value, float x, float lineY) throws IOException {
            text(x, lineY, printable(key).toUpperCase(Locale.ROOT), bold, 7.2f, MUTED);
            text(x + 112, lineY, fit(printable(value), regular, 8.5f, CONTENT_WIDTH - 128), regular, 8.5f, TEXT);
        }

        private void ensure(float required) throws IOException {
            if (y - required < 46)
                newPage();
        }

        private void fill(float x, float y, float width, float height, float[] color) throws IOException {
            content.setNonStrokingColor(color[0], color[1], color[2]);
            content.addRect(x, y, width, height);
            content.fill();
        }

        private void text(float x, float y, String value, PDFont font, float size, float[] color) throws IOException {
            text(x, y, value, font, size, color[0], color[1], color[2]);
        }

        private void text(float x, float y, String value, PDFont font, float size,
                float red, float green, float blue) throws IOException {
            content.beginText();
            content.setFont(font, size);
            content.setNonStrokingColor(red, green, blue);
            content.newLineAtOffset(x, y);
            content.showText(printable(value));
            content.endText();
        }

        private void closePage() throws IOException {
            if (content != null) {
                content.close();
                content = null;
            }
        }

        @Override
        public void close() throws IOException {
            closePage();
        }
    }

    private static List<String> wrap(String value, PDFont font, float size, float width) throws IOException {
        if (value == null || value.isBlank())
            return List.of("");
        List<String> lines = new ArrayList<>();
        for (String paragraph : value.split("\\R", -1)) {
            String current = "";
            for (String word : paragraph.split("\\s+")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (!current.isEmpty() && textWidth(candidate, font, size) > width) {
                    lines.add(current);
                    current = fit(word, font, size, width);
                } else
                    current = candidate;
            }
            lines.add(current);
        }
        return lines;
    }

    private static String formatSnippetWithLineNumbers(Vulnerability finding, String baseRepoPath) {
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

    private static String fit(String value, PDFont font, float size, float width) throws IOException {
        String safe = printable(value);
        if (textWidth(safe, font, size) <= width)
            return safe;
        String suffix = "...";
        int end = safe.length();
        while (end > 0 && textWidth(safe.substring(0, end) + suffix, font, size) > width)
            end--;
        return safe.substring(0, end) + suffix;
    }

    private static float textWidth(String value, PDFont font, float size) throws IOException {
        return font.getStringWidth(printable(value)) / 1000f * size;
    }

    private static String printable(Object value) {
        if (value == null)
            return "N/A";
        String normalized = Normalizer.normalize(String.valueOf(value), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "").replace('\t', ' ');
        StringBuilder safe = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(codePoint -> safe.append((codePoint >= 32 && codePoint <= 126)
                || (codePoint >= 160 && codePoint <= 255)
                        ? (char) codePoint
                        : codePoint == '\n' || codePoint == '\r' ? (char) codePoint : '?'));
        return safe.toString();
    }

    private static String value(String value) {
        return Optional.ofNullable(value).filter(text -> !text.isBlank()).orElse("N/A");
    }

    private static String shortSha(String sha) {
        String safe = value(sha);
        return safe.length() > 12 ? safe.substring(0, 12) : safe;
    }

    private static String severity(Vulnerability finding) {
        return finding.getSeverity() == null ? "UNKNOWN" : finding.getSeverity().name();
    }

    private static float[] severityColor(Severity severity) {
        if (severity == Severity.HIGH)
            return new float[] { 0.98f, 0.35f, 0.38f };
        if (severity == Severity.MEDIUM)
            return new float[] { 0.98f, 0.70f, 0.20f };
        if (severity == Severity.LOW)
            return new float[] { 0.20f, 0.75f, 0.48f };
        return new float[] { 0.25f, 0.68f, 0.95f };
    }

    private record Counts(long high, long medium, long low, long info, long total) {
        private static Counts from(List<Vulnerability> findings) {
            return new Counts(
                    findings.stream().filter(v -> v.getSeverity() == Severity.HIGH).count(),
                    findings.stream().filter(v -> v.getSeverity() == Severity.MEDIUM).count(),
                    findings.stream().filter(v -> v.getSeverity() == Severity.LOW).count(),
                    findings.stream().filter(v -> v.getSeverity() == Severity.INFO).count(), findings.size());
        }
    }
}
