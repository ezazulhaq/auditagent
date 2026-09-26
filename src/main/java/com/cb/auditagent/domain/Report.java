package com.cb.auditagent.domain;

import java.util.ArrayList;
import java.util.List;

public class Report {
    private String repoPath;
    private String mdReport;
    private String htmlReport;
    private List<Vulnerability> findings = new ArrayList<>();
    private ScanMetadata metadata;

    public Report() {
    }

    public Report(String repoPath, String mdReport, String htmlReport, List<Vulnerability> findings,
            ScanMetadata metadata) {
        this.repoPath = repoPath;
        this.mdReport = mdReport;
        this.htmlReport = htmlReport;
        this.findings = findings;
        this.metadata = metadata;
    }

    // Getters and Setters
    public String getRepoPath() {
        return repoPath;
    }

    public void setRepoPath(String repoPath) {
        this.repoPath = repoPath;
    }

    public String getMdReport() {
        return mdReport;
    }

    public void setMdReport(String mdReport) {
        this.mdReport = mdReport;
    }

    public String getHtmlReport() {
        return htmlReport;
    }

    public void setHtmlReport(String htmlReport) {
        this.htmlReport = htmlReport;
    }

    public List<Vulnerability> getFindings() {
        return findings;
    }

    public void setFindings(List<Vulnerability> findings) {
        this.findings = findings;
    }

    public ScanMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(ScanMetadata metadata) {
        this.metadata = metadata;
    }
}
