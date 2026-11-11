package com.cb.auditagent.service.reporting;

/**
 * Strategy interface for exporting audit assessment reports into distinct file formats.
 * Follows Open/Closed Principle (OCP) and Single Responsibility Principle (SRP).
 */
public interface ReportExporter {

    /**
     * The report format this exporter generates.
     */
    ReportFormat getFormat();

    /**
     * Exports the assessment report as a formatted string (for Markdown and HTML).
     */
    String exportString(ReportExportContext context);

    /**
     * Exports the assessment report as binary bytes (for PDF).
     */
    default byte[] exportBytes(ReportExportContext context) {
        return exportString(context).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
