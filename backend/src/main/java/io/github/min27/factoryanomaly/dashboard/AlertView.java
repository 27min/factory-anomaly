package io.github.min27.factoryanomaly.dashboard;

import io.github.min27.factoryanomaly.alert.Alert;
import io.github.min27.factoryanomaly.alert.AlertStatus;
import io.github.min27.factoryanomaly.decision.FailureType;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record AlertView(
        long id,
        String equipmentCode,
        FailureType category,
        double severity,
        AlertStatus status,
        int occurrenceCount,
        ZonedDateTime createdAt,
        ZonedDateTime lastOccurredAt,
        ZonedDateTime acknowledgedAt
) {
    static AlertView from(Alert a, ZoneId zone) {
        return new AlertView(a.getId(), a.getEquipment().getCode(), a.getCategory(), a.getSeverity(), a.getStatus(),
                a.getOccurrenceCount(), a.getCreatedAt().atZone(zone), a.getLastOccurredAt().atZone(zone),
                a.getAcknowledgedAt() == null ? null : a.getAcknowledgedAt().atZone(zone));
    }

    public boolean acknowledged() {
        return status == AlertStatus.ACKNOWLEDGED;
    }
}
