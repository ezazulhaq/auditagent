package com.cb.auditagent.domain;

import java.util.List;
import java.util.Map;

public record ApprovalPreview(String runId, String vulnerabilityId, String repository,
        String baseBranch, String baseSha, String branchName,
        List<String> changedFiles, String diff,
        Map<String, String> verification, String summary,
        String approvalDigest) implements java.io.Serializable {
}
