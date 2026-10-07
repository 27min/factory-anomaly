package io.github.min27.factoryanomaly.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.min27.factoryanomaly.decision.Decision;
import io.github.min27.factoryanomaly.decision.DecisionRepository;
import io.github.min27.factoryanomaly.decision.FailureType;
import io.github.min27.factoryanomaly.equipment.Equipment;
import io.github.min27.factoryanomaly.equipment.EquipmentRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집 → 룰 판정 → 알람 생성·합치기까지 실제 SQL Server로 확인한다. 각 테스트는 롤백된다.
 * 시뮬레이터가 남긴 미해결 알람이 있어도 결과가 같도록, 테스트 트랜잭션 안에서 대상 설비의 알람을 먼저 해결 처리한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AlertIntegrationTest {

    // AI4I UDI 70: 룰 엔진 PWF·OSF 동시 충족 → 대표 유형 PWF, 심각도 90
    static final String PWF = """
            {"equipmentCode":"EQ-05","productType":"L",
             "airTemp":298.9,"processTemp":309.0,"rotSpeed":1410,"torque":65.7,"toolWear":191}
            """;
    // 공구마모 210분, 나머지 정상: 룰 엔진 TWF 경고 (심각도 40)
    static final String TWF_WARNING = """
            {"equipmentCode":"EQ-05","productType":"L",
             "airTemp":300,"processTemp":310,"rotSpeed":1500,"torque":40,"toolWear":210}
            """;

    @Autowired MockMvcTester mvc;
    @Autowired AlertRepository alertRepository;
    @Autowired DecisionRepository decisionRepository;
    @Autowired EquipmentRepository equipmentRepository;
    @Autowired JdbcTemplate jdbc;

    private Equipment eq;

    @BeforeEach
    void resolveExistingAlerts() {
        eq = equipmentRepository.findByCode("EQ-05").orElseThrow();
        jdbc.update("UPDATE alert SET status = 'RESOLVED' WHERE equipment_id = ?", eq.getId());
    }

    private long post(String body) {
        var result = mvc.post().uri("/api/readings").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String location = result.getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    private List<Alert> unresolved(FailureType category) {
        return alertRepository.findByEquipmentIdAndCategoryAndStatusIn(eq.getId(), category, AlertStatus.UNRESOLVED)
                .stream().toList();
    }

    @Test
    void 고장_조건이면_알람을_만들고_반복_발생은_같은_알람에_합친다() {
        long first = post(PWF);
        post(PWF);
        post(PWF);

        assertThat(unresolved(FailureType.PWF)).singleElement().satisfies(a -> {
            assertThat(a.getDecision().getReading().getId()).isEqualTo(first);
            assertThat(a.getSeverity()).isEqualTo(90);
            assertThat(a.getStatus()).isEqualTo(AlertStatus.OPEN);
            assertThat(a.getOccurrenceCount()).isEqualTo(3);
            assertThat(a.getLastOccurredAt()).isAfterOrEqualTo(a.getCreatedAt());
        });
    }

    @Test
    void TWF_경고는_판정으로만_남고_알람은_만들지_않는다() {
        long id = post(TWF_WARNING);

        assertThat(decisionRepository.findByReadingIdOrderByEngine(id)).singleElement().satisfies(d -> {
            assertThat(d.isAnomaly()).isTrue();
            assertThat(d.getCategory()).isEqualTo(FailureType.TWF);
            assertThat(d.getSeverity()).isEqualTo(40);
        });
        assertThat(unresolved(FailureType.TWF)).isEmpty();
    }

    @Test
    void 해결된_알람_뒤의_발생은_새_알람이_된다() {
        post(PWF);
        jdbc.update("UPDATE alert SET status = 'RESOLVED' WHERE equipment_id = ?", eq.getId());

        long again = post(PWF);

        assertThat(unresolved(FailureType.PWF)).singleElement().satisfies(a -> {
            assertThat(a.getDecision().getReading().getId()).isEqualTo(again);
            assertThat(a.getOccurrenceCount()).isEqualTo(1);
        });
    }

    @Test
    void 같은_설비_유형의_미해결_알람은_DB가_하나만_허용한다() {
        post(PWF);
        long second = post(PWF); // 서비스는 첫 알람에 합쳤다
        Decision secondDecision = decisionRepository.findByReadingIdOrderByEngine(second).get(0);

        // 서비스를 거치지 않고 두 번째 미해결 알람을 직접 넣으면 uq_alert_unresolved에 막힌다
        Alert duplicate = Alert.open(secondDecision, eq, Instant.now());
        assertThatThrownBy(() -> alertRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_alert_unresolved");
    }
}
