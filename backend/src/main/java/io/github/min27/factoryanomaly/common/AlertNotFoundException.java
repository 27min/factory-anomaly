package io.github.min27.factoryanomaly.common;

public class AlertNotFoundException extends RuntimeException {

    public AlertNotFoundException(long id) {
        super("알람을 찾을 수 없습니다: " + id);
    }
}
