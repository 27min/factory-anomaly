package io.github.min27.factoryanomaly.decision;

import java.util.Objects;

/**
 * 판정 엔진의 공통 응답. Jev의 질문 3종(예/아니오, 점수, 분류) 형태를 따른다.
 *
 * @param anomaly    이상 여부. {@code category != NORMAL}과 항상 일치한다
 * @param severity   심각도 0~100
 * @param category   대표 고장 유형 1개. 여러 유형이 해당되면 엔진이 하나를 고른다 (D-011)
 * @param confidence 판정 확신도 0~1. 결정적인 룰 엔진은 1.0
 */
public record DecisionResult(
        boolean anomaly,
        double severity,
        FailureType category,
        double confidence
) {
    public DecisionResult {
        Objects.requireNonNull(category, "category");
        if (!(severity >= 0 && severity <= 100)) {
            throw new IllegalArgumentException("severity must be 0~100: " + severity);
        }
        if (!(confidence >= 0 && confidence <= 1)) {
            throw new IllegalArgumentException("confidence must be 0~1: " + confidence);
        }
        if (anomaly == (category == FailureType.NORMAL)) {
            throw new IllegalArgumentException("anomaly=" + anomaly + " conflicts with category=" + category);
        }
    }
}
