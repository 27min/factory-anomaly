package io.github.min27.factoryanomaly.dashboard;

import io.github.min27.factoryanomaly.decision.FailureType;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * @param lastReceivedAt 최신 측정값 수신 시각 (없으면 null)
 * @param latestCategory 최신 측정값에 대한 대표 엔진의 판정 유형 (판정이 없으면 null)
 * @param alerts         이 설비의 미해결 알람
 */
public record EquipmentStatusView(
        String code,
        String name,
        EquipmentState state,
        ZonedDateTime lastReceivedAt,
        FailureType latestCategory,
        Double latestSeverity,
        List<AlertView> alerts
) {
}
