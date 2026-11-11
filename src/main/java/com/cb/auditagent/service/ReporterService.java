package com.cb.auditagent.service;

import com.cb.auditagent.domain.ScanMetadata;
import com.cb.auditagent.domain.Vulnerability;
import com.cb.auditagent.service.reporting.HtmlReportExporter;
import com.cb.auditagent.service.reporting.MarkdownReportExporter;
import com.cb.auditagent.service.reporting.PdfReportExporter;
import com.cb.auditagent.service.reporting.ReportExportContext;
import com.cb.auditagent.service.reporting.ReportGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * High-level reporting service coordinating audit assessment document generation.
 * Follows Single Responsibility Principle (SRP) by delegating format-specific
 * rendering to dedicated ReportExporter strategies (Markdown, HTML, PDF).
 */
@Service
public class ReporterService implements ReportGenerator {

    private final MarkdownReportExporter markdownExporter;
    private final HtmlReportExporter htmlExporter;
    private final PdfReportExporter pdfExporter;

    public ReporterService() {
        this(new MarkdownReportExporter(), new HtmlReportExporter(), new PdfReportExporter());
    }

    @Autowired
    public ReporterService(
            MarkdownReportExporter markdownExporter,
            HtmlReportExporter htmlExporter,
            PdfReportExporter pdfExporter) {
        this.markdownExporter = markdownExporter != null ? markdownExporter : new MarkdownReportExporter();
        this.htmlExporter = htmlExporter != null ? htmlExporter : new HtmlReportExporter();
        this.pdfExporter = pdfExporter != null ? pdfExporter : new PdfReportExporter();
    }

    @Override
    public String generateMarkdown(List<Vulnerability> findings, ScanMetadata metadata, String baseRepoPath) {
        return generateMarkdown(findings, metadata, baseRepoPath, null, null);
    }

    @Override
    public String generateMarkdown(List<Vulnerability> rawFindings, ScanMetadata metadata, String baseRepoPath,
            String branch, String baseSha) {
        ReportExportContext context = ReportExportContext.of(rawFindings, metadata, baseRepoPath, branch, baseSha);
        return markdownExporter.exportString(context);
    }

    @Override
    public String generateHtml(List<Vulnerability> findings, ScanMetadata metadata, String baseRepoPath) {
        ReportExportContext context = ReportExportContext.of(findings, metadata, baseRepoPath);
        return htmlExporter.exportString(context);
    }

    @Override
    public byte[] generatePdf(List<Vulnerability> findings, ScanMetadata metadata, String repository,
            String branch, String baseSha) {
        ReportExportContext context = ReportExportContext.of(findings, metadata, repository, branch, baseSha);
        return pdfExporter.exportBytes(context);
    }
}
