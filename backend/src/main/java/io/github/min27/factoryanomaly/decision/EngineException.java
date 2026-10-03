package io.github.min27.factoryanomaly.decision;

import lombok.Getter;

/** 엔진이 실패 이유를 분류해서 던지는 예외. 분류되지 않은 예외는 {@link FailureReason#UNEXPECTED}로 기록된다. */
@Getter
public class EngineException extends RuntimeException {

    private final FailureReason reason;

    public EngineException(FailureReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }
}
