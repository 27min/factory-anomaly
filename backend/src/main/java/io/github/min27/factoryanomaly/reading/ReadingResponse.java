package io.github.min27.factoryanomaly.reading;

import java.time.Instant;

public record ReadingResponse(
        long id,
        String equipmentCode,
        ProductType productType,
        double tempDiff,
        double power,
        double wearTorque,
        Instant receivedAt
) {
    static ReadingResponse from(SensorReading r) {
        return new ReadingResponse(
                r.getId(), r.getEquipment().getCode(), r.getProductType(),
                r.getTempDiff(), r.getPower(), r.getWearTorque(), r.getReceivedAt());
    }
}
