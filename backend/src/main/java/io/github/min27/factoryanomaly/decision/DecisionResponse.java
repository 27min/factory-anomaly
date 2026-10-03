package io.github.min27.factoryanomaly.decision;

import java.time.Instant;

public record DecisionResponse(
        String engine,
        boolean anomaly,
        double severity,
        FailureType category,
        double confidence,
        long latencyUs,
        Instant decidedAt
) {
    public static DecisionResponse from(Decision d) {
        return new DecisionResponse(
                d.getEngine(), d.isAnomaly(), d.getSeverity(), d.getCategory(),
                d.getConfidence(), d.getLatencyUs(), d.getDecidedAt());
    }
}
