package io.github.min27.factoryanomaly.decision;

/** 엔진별 요약 집계에 필요한 판정 값만 담은 투영. */
public record DecisionStat(String engine, boolean anomaly, long latencyUs) {
}
