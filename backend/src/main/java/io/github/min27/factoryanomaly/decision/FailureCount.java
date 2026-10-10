package io.github.min27.factoryanomaly.decision;

/** 엔진별 실패 건수 집계 결과. */
public record FailureCount(String engine, long count) {
}
