package io.github.min27.factoryanomaly.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.min27.factoryanomaly.alert.Alert;
import io.github.min27.factoryanomaly.alert.AlertRepository;
import io.github.min27.factoryanomaly.alert.AlertStatus;
import io.github.min27.factoryanomaly.decision.Decision;
import io.github.min27.factoryanomaly.decision.DecisionRepository;
import io.github.min27.factoryanomaly.decision.FailureType;
import io.github.min27.factoryanomaly.equipment.Equipment;
import io.github.min27.factoryanomaly.equipment.EquipmentRepository;
import io.github.min27.factoryanomaly.reading.FailureLabels;
import io.github.min27.factoryanomaly.reading.ProductType;
import io.github.min27.factoryanomaly.reading.SensorReading;
import io.github.min27.factoryanomaly.reading.SensorReadingRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * 엔티티와 Flyway 스키마가 맞물려 저장/조회되는지 확인한다.
 * 실제 SQL Server(docker compose)를 사용하며, 각 테스트는 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersistenceMappingTest {

    @Autowired TestEntityManager em;
    @Autowired EquipmentRepository equipmentRepository;
    @Autowired SensorReadingRepository readingRepository;
    @Autowired DecisionRepository decisionRepository;
    @Autowired AlertRepository alertRepository;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Test
    void 설비_5대가_미리_등록되어_있다() {
        assertThat(equipmentRepository.count()).isEqualTo(5);
        assertThat(equipmentRepository.findByCode("EQ-01")).isPresent();
        assertThat(equipmentRepository.findByCode("EQ-99")).isEmpty();
    }

    @Test
    void 측정값_판정_알람을_저장하고_다시_읽는다() {
        Equipment eq = equipmentRepository.findByCode("EQ-01").orElseThrow();

        // AI4I 첫 행 (udi 1) + 02_features.ipynb에서 검증한 파생변수 값
        SensorReading reading = readingRepository.save(SensorReading.builder()
                .equipment(eq)
                .productType(ProductType.M)
                .airTemp(298.1).processTemp(308.6).rotSpeed(1551).torque(42.8).toolWear(0)
                .tempDiff(10.5).power(6951.5906).wearTorque(0.0)
                .labels(new FailureLabels(false, false, false, false, false, false))
                .sourceUdi(1)
                .receivedAt(now)
                .build());

        Decision decision = decisionRepository.save(Decision.builder()
                .reading(reading).engine("rule")
                .anomaly(true).severity(90).category(FailureType.OSF).confidence(1.0)
                .latencyMs(3).decidedAt(now)
                .build());

        Alert alert = alertRepository.save(Alert.open(decision, eq, now));

        em.flush();
        em.clear(); // 1차 캐시를 비워 실제 DB에서 다시 읽는다

        SensorReading r = readingRepository.findById(reading.getId()).orElseThrow();
        assertThat(r.getProductType()).isEqualTo(ProductType.M);
        assertThat(r.getRotSpeed()).isEqualTo(1551);
        assertThat(r.getPower()).isEqualTo(6951.5906);
        assertThat(r.getLabels()).isEqualTo(new FailureLabels(false, false, false, false, false, false));
        assertThat(r.getReceivedAt()).isEqualTo(now);
        assertThat(r.getEquipment().getCode()).isEqualTo("EQ-01");

        Decision d = decisionRepository.findById(decision.getId()).orElseThrow();
        assertThat(d.getCategory()).isEqualTo(FailureType.OSF);
        assertThat(d.getReading().getId()).isEqualTo(reading.getId());

        Alert a = alertRepository.findById(alert.getId()).orElseThrow();
        assertThat(a.getStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(a.getSeverity()).isEqualTo(90);
    }

    @Test
    void 정답_라벨이_없는_측정값도_저장된다() {
        Equipment eq = equipmentRepository.findByCode("EQ-02").orElseThrow();

        SensorReading reading = readingRepository.save(SensorReading.builder()
                .equipment(eq).productType(ProductType.L)
                .airTemp(300).processTemp(310).rotSpeed(1500).torque(40).toolWear(100)
                .tempDiff(10).power(6283.2).wearTorque(4000)
                .receivedAt(now)
                .build());
        em.flush();
        em.clear();

        SensorReading r = readingRepository.findById(reading.getId()).orElseThrow();
        assertThat(r.getLabels()).isNull();
        assertThat(r.getSourceUdi()).isNull();
    }
}
