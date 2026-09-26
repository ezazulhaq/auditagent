package com.cb.auditagent.domain;

public class ScanMetadata {
    private String projectName = "Unknown";
    private String scannerName = "Anonymous";
    private String startTime;
    private String endTime;
    private String scanDuration;
    private String reportCreationTime;
    private int totalFilesScanned = 0;
    private int totalLocScanned = 0;

    public ScanMetadata() {
    }

    // Getters and Setters
    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getScannerName() {
        return scannerName;
    }

    public void setScannerName(String scannerName) {
        this.scannerName = scannerName;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getScanDuration() {
        return scanDuration;
    }

    public void setScanDuration(String scanDuration) {
        this.scanDuration = scanDuration;
    }

    public String getReportCreationTime() {
        return reportCreationTime;
    }

    public void setReportCreationTime(String reportCreationTime) {
        this.reportCreationTime = reportCreationTime;
    }

    public int getTotalFilesScanned() {
        return totalFilesScanned;
    }

    public void setTotalFilesScanned(int totalFilesScanned) {
        this.totalFilesScanned = totalFilesScanned;
    }

    public int getTotalLocScanned() {
        return totalLocScanned;
    }

    public void setTotalLocScanned(int totalLocScanned) {
        this.totalLocScanned = totalLocScanned;
    }
}
