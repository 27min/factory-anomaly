package io.github.min27.factoryanomaly.reading;

import io.github.min27.factoryanomaly.decision.Decision;
import io.github.min27.factoryanomaly.decision.DecisionResponse;
import java.time.Instant;
import java.util.List;

public record ReadingResponse(
        long id,
        String equipmentCode,
        ProductType productType,
        double tempDiff,
        double power,
        double wearTorque,
        Instant receivedAt,
        List<DecisionResponse> decisions
) {
    static ReadingResponse from(SensorReading r, List<Decision> decisions) {
        return new ReadingResponse(
                r.getId(), r.getEquipment().getCode(), r.getProductType(),
                r.getTempDiff(), r.getPower(), r.getWearTorque(), r.getReceivedAt(),
                decisions.stream().map(DecisionResponse::from).toList());
    }
}
