package io.github.min27.factoryanomaly.alert;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.min27.factoryanomaly.decision.Decision;
import io.github.min27.factoryanomaly.decision.DecisionRepository;
import io.github.min27.factoryanomaly.decision.FailureType;
import io.github.min27.factoryanomaly.equipment.Equipment;
import io.github.min27.factoryanomaly.equipment.EquipmentRepository;
import io.github.min27.factoryanomaly.reading.ProductType;
import io.github.min27.factoryanomaly.reading.SensorReading;
import io.github.min27.factoryanomaly.reading.SensorReadingRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알람을 읽어 둔 채 해결하는 동안 다른 요청이 발생 횟수를 늘려도, 해결이 그 횟수를 옛 값으로 덮어쓰지 않는지 확인한다 (D-022 @DynamicUpdate).
 * 두 트랜잭션이 각각 커밋되어야 하므로 @Transactional 없이, 테스트 전용 설비를 만들고 끝나면 지운다.
 */
@SpringBootTest
class AlertConcurrentUpdateIntegrationTest {

    @Autowired EquipmentRepository equipmentRepository;
    @Autowired SensorReadingRepository readingRepository;
    @Autowired DecisionRepository decisionRepository;
    @Autowired AlertRepository alertRepository;
    @Autowired PlatformTransactionManager transactionManager;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private Equipment equipment;
    private SensorReading reading;
    private Decision decision;
    private Alert alert;

    @BeforeEach
    void setUp() {
        equipment = equipmentRepository.save(new Equipment("EQ-TEST", "동시성 테스트", now));
        reading = readingRepository.save(SensorReading.builder()
                .equipment(equipment).productType(ProductType.L)
                .airTemp(300).processTemp(305).rotSpeed(1300).torque(40).toolWear(10)
                .tempDiff(5).power(5445.4).wearTorque(400)
                .receivedAt(now).build());
        decision = decisionRepository.save(Decision.builder()
                .reading(reading).engine("rule").anomaly(true).severity(90).category(FailureType.HDF)
                .confidence(1).latencyUs(10).decidedAt(now).build());
        alert = alertRepository.save(Alert.open(decision, equipment, now));
    }

    @AfterEach
    void cleanUp() {
        alertRepository.deleteById(alert.getId());
        decisionRepository.deleteById(decision.getId());
        readingRepository.deleteById(reading.getId());
        equipmentRepository.deleteById(equipment.getId());
    }

    @Test
    void 해결은_그_사이에_늘어난_발생_횟수를_덮어쓰지_않는다() {
        TransactionTemplate resolving = new TransactionTemplate(transactionManager);
        TransactionTemplate merging = new TransactionTemplate(transactionManager);
        merging.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        resolving.executeWithoutResult(s -> {
            Alert loaded = alertRepository.findById(alert.getId()).orElseThrow(); // 횟수 1인 상태로 읽음

            // 그 사이 다른 요청이 같은 알람에 발생을 합치고 먼저 커밋한다 (횟수 2)
            merging.executeWithoutResult(s2 -> alertRepository.recordOccurrence(
                    equipment.getId(), FailureType.HDF, AlertStatus.UNRESOLVED, now.plusSeconds(1)));

            loaded.resolve(now.plusSeconds(2));
        });

        Alert result = alertRepository.findById(alert.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(result.getOccurrenceCount()).isEqualTo(2);
        assertThat(result.getLastOccurredAt()).isEqualTo(now.plusSeconds(1));
    }
}
