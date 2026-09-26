package com.cb.auditagent.dto;

public class ScanRequest {
    private Long repositoryId;
    private String branch;
    private String scannerName = "semgrep";
    private boolean forceRescan = false;
    private String threadId;

    public ScanRequest() {
    }

    public Long getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(Long repositoryId) {
        this.repositoryId = repositoryId;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getScannerName() {
        return scannerName;
    }

    public void setScannerName(String scannerName) {
        this.scannerName = scannerName;
    }

    public boolean isForceRescan() {
        return forceRescan;
    }

    public void setForceRescan(boolean forceRescan) {
        this.forceRescan = forceRescan;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }
}
