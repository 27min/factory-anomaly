package io.github.min27.factoryanomaly.reading;

import io.github.min27.factoryanomaly.common.ReadingNotFoundException;
import io.github.min27.factoryanomaly.common.UnknownEquipmentException;
import io.github.min27.factoryanomaly.equipment.Equipment;
import io.github.min27.factoryanomaly.equipment.EquipmentRepository;
import io.github.min27.factoryanomaly.state.SensorState;
import io.github.min27.factoryanomaly.state.SensorValues;
import io.github.min27.factoryanomaly.state.StateBuilder;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReadingService {

    private final EquipmentRepository equipmentRepository;
    private final SensorReadingRepository readingRepository;
    private final StateBuilder stateBuilder;
    private final Clock clock;

    @Transactional
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

        return ReadingResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ReadingResponse get(long id) {
        return readingRepository.findById(id)
                .map(ReadingResponse::from)
                .orElseThrow(() -> new ReadingNotFoundException(id));
    }
}
