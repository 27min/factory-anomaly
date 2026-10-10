package io.github.min27.factoryanomaly.dashboard;

/**
 * 설비 카드의 상태. 미해결 알람이 우선이고, 없으면 대표 엔진의 최신 판정을 본다 (D-022).
 * 데이터가 시계열이 아니라 최신 판정만 보면 매초 바뀌므로, 사람이 해결할 때까지 알람 상태를 유지한다.
 */
public enum EquipmentState {
    ALERT("알람"),        // 미해결 알람 있음
    WARNING("경고"),      // 최신 판정이 이상이지만 알람 조건 미만 (예: 룰 TWF 경고)
    NORMAL("정상"),
    UNKNOWN("판정 없음"), // 최신 측정값에 대표 엔진의 판정이 없음 (엔진 실패)
    NO_DATA("수신 없음");

    private final String label;

    EquipmentState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String cssClass() {
        return name().toLowerCase().replace('_', '-');
    }
}
