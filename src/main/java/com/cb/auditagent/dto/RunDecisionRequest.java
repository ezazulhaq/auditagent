package com.cb.auditagent.dto;

public class RunDecisionRequest {
    private String vulnId;
    private String decision;
    private String approvalDigest;
    private String reason;

    public String getVulnId() {
        return vulnId;
    }

    public void setVulnId(String vulnId) {
        this.vulnId = vulnId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getApprovalDigest() {
        return approvalDigest;
    }

    public void setApprovalDigest(String approvalDigest) {
        this.approvalDigest = approvalDigest;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
