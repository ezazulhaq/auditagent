package com.cb.auditagent.graph.agent;

import java.util.Locale;
import java.util.Map;

/**
 * Shared classifier for remediation failure indicators and explicit false-positive
 * determinations across supervisor, worker, and approval-readiness nodes.
 */
public final class RemediationOutcomeClassifier {

    private RemediationOutcomeClassifier() {
    }

    public static boolean containsFailureIndicators(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("aborted")
                || lower.contains("max iterations")
                || lower.contains("maximum iterations")
                || lower.contains("consecutive empty responses")
                || lower.contains("consecutive same tool")
                || lower.contains("timed out")
                || lower.contains("timeout")
                || lower.contains("remediation failed");
    }

    public static boolean containsExplicitFalsePositiveAssertion(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("not a false positive")
                || lower.contains("not a false-positive")
                || lower.contains("is not a false positive")
                || lower.contains("isn't a false positive")) {
            return false;
        }
        return lower.contains("false positive")
                || lower.contains("false-positive")
                || lower.contains("not a vulnerability")
                || lower.contains("not a security vulnerability")
                || lower.contains("no vulnerability")
                || lower.contains("invalid finding");
    }

    public static boolean hasExplicitFalsePositiveInOutputs(Map<?, ?> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            return false;
        }
        for (Object value : outputs.values()) {
            if (value != null && containsExplicitFalsePositiveAssertion(String.valueOf(value))) {
                return true;
            }
        }
        return false;
    }
}
