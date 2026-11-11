package com.cb.auditagent.service.reporting;

import com.cb.auditagent.service.PdfReportRenderer;
import org.springframework.stereotype.Component;

/**
 * Generates publication-ready PDF security audit reports.
 * Follows Single Responsibility Principle (SRP) and Open/Closed Principle (OCP).
 */
@Component
public class PdfReportExporter implements ReportExporter {

    private final PdfReportRenderer renderer;

    public PdfReportExporter() {
        this(new PdfReportRenderer());
    }

    public PdfReportExporter(PdfReportRenderer renderer) {
        this.renderer = renderer != null ? renderer : new PdfReportRenderer();
    }

    @Override
    public ReportFormat getFormat() {
        return ReportFormat.PDF;
    }

    @Override
    public String exportString(ReportExportContext context) {
        throw new UnsupportedOperationException("PDF reports must be retrieved as binary bytes via exportBytes()");
    }

    @Override
    public byte[] exportBytes(ReportExportContext context) {
        return renderer.render(
                context.findings(),
                context.metadata(),
                context.baseRepoPath(),
                context.branch(),
                context.baseSha()
        );
    }
}
