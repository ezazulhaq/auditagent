package com.cb.auditagent.dto;

import java.util.List;

public class BatchAnalyzeRequest {
    private Long repositoryId;
    private String branch;
    private String threadId;
    private List<String> vulnIds;

    public BatchAnalyzeRequest() {
    }

    public BatchAnalyzeRequest(Long repositoryId, String branch, String threadId, List<String> vulnIds) {
        this.repositoryId = repositoryId;
        this.branch = branch;
        this.threadId = threadId;
        this.vulnIds = vulnIds;
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

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public List<String> getVulnIds() {
        return vulnIds;
    }

    public void setVulnIds(List<String> vulnIds) {
        this.vulnIds = vulnIds;
    }
}

