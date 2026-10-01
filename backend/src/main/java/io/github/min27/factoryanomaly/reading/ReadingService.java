package io.github.min27.factoryanomaly.reading;

import io.github.min27.factoryanomaly.common.ReadingNotFoundException;
import io.github.min27.factoryanomaly.common.UnknownEquipmentException;
import io.github.min27.factoryanomaly.decision.Decision;
import io.github.min27.factoryanomaly.decision.DecisionService;
import io.github.min27.factoryanomaly.equipment.Equipment;
import io.github.min27.factoryanomaly.equipment.EquipmentRepository;
import io.github.min27.factoryanomaly.state.SensorState;
import io.github.min27.factoryanomaly.state.SensorValues;
import io.github.min27.factoryanomaly.state.StateBuilder;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReadingService {

    private final EquipmentRepository equipmentRepository;
    private final SensorReadingRepository readingRepository;
    private final StateBuilder stateBuilder;
    private final DecisionService decisionService;
    private final Clock clock;

    /**
     * 측정값을 먼저 저장(save 자체 트랜잭션으로 커밋)한 뒤 판정한다.
     * 전체를 하나의 트랜잭션으로 묶지 않으므로 엔진이 실패해도 측정값은 남고,
     * 외부 엔진을 호출하는 동안 DB 커넥션을 잡고 있지 않는다 (D-013).
     */
    public ReadingResponse ingest(ReadingRequest request) {
        Equipment equipment = equipmentRepository.findByCode(request.equipmentCode())
                .orElseThrow(() -> new UnknownEquipmentException(request.equipmentCode()));

        SensorState state = stateBuilder.build(request.toSensorValues());
        SensorValues v = state.values();

        SensorReading saved = readingRepository.save(SensorReading.builder()
                .equipment(equipment)
                .productType(v.productType())
                .airTemp(v.airTemp()).processTemp(v.processTemp())
                .rotSpeed(v.rotSpeed()).torque(v.torque()).toolWear(v.toolWear())
                .tempDiff(state.tempDiff()).power(state.power()).wearTorque(state.wearTorque())
                .labels(request.labels())
                .sourceUdi(request.sourceUdi())
                .receivedAt(Instant.now(clock))
                .build());

        List<Decision> decisions = decisionService.decide(saved, state);
        return ReadingResponse.from(saved, decisions);
    }

    @Transactional(readOnly = true)
    public ReadingResponse get(long id) {
        SensorReading reading = readingRepository.findById(id)
                .orElseThrow(() -> new ReadingNotFoundException(id));
        return ReadingResponse.from(reading, decisionService.findByReading(id));
    }
}
