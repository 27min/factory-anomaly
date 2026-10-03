package io.github.min27.factoryanomaly.decision;

/** 엔진이 판정을 내지 못한 이유 (D-017). */
public enum FailureReason {
    /** 응답 대기 시간 초과 */
    TIMEOUT,
    /** 연결 실패 (서버 다운, 연결 시간 초과 포함) */
    CONNECTION,
    /** 4xx / 5xx 응답 */
    HTTP_ERROR,
    /** 응답 형식이 맞지 않거나 판정 불변식을 어김 */
    INVALID_RESPONSE,
    /** 위로 분류되지 않은 예외 (엔진 버그 등) */
    UNEXPECTED
}
