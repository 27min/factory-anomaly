package io.github.min27.factoryanomaly.common;

/** 허용되지 않는 알람 상태 변경 (예: 이미 해결된 알람을 확인). */
public class InvalidAlertTransitionException extends RuntimeException {

    public InvalidAlertTransitionException(Long id, String from, String to) {
        super("알람 " + id + "의 상태를 " + from + "에서 " + to + "(으)로 바꿀 수 없습니다");
    }
}
