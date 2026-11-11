package com.cb.auditagent.service.reporting;

import com.cb.auditagent.domain.Severity;
import com.cb.auditagent.domain.Vulnerability;

import java.util.List;

/**
 * Calculates security assessment metrics, severity distribution, and risk posture.
 * Follows Single Responsibility Principle (SRP).
 */
public class SecurityMetricsCalculator {

    public record SeverityCounts(long high, long medium, long low, long info, long total) {
        public static SeverityCounts from(List<Vulnerability> findings) {
            if (findings == null || findings.isEmpty()) {
                return new SeverityCounts(0, 0, 0, 0, 0);
            }
            return new SeverityCounts(
                    findings.stream().filter(v -> v.getSeverity() == Severity.HIGH).count(),
                    findings.stream().filter(v -> v.getSeverity() == Severity.MEDIUM).count(),
                    findings.stream().filter(v -> v.getSeverity() == Severity.LOW).count(),
                    findings.stream().filter(v -> v.getSeverity() == Severity.INFO).count(),
                    findings.size()
            );
        }
    }

    public static SeverityCounts computeCounts(List<Vulnerability> findings) {
        return SeverityCounts.from(findings);
    }

    public static String computeRiskPosture(SeverityCounts counts) {
        if (counts.high() > 0) {
            return "🔴 Immediate action recommended";
        } else if (counts.medium() > 0) {
            return "🟡 Review and prioritize";
        } else {
            return "🟢 No elevated findings detected";
        }
    }
}
