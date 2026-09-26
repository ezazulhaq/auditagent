package com.cb.auditagent.domain;

public enum AgentRunStatus {
    ACTIVE,
    AWAITING_APPROVAL,
    PUBLISHING,
    PUBLISH_FAILED,
    PR_OPEN,
    PR_MERGED,
    PR_CLOSED,
    APPROVED,
    REJECTED,
    FAILED,
    INTERRUPTED,
    CONFLICTED,
    DISCARDED
}
