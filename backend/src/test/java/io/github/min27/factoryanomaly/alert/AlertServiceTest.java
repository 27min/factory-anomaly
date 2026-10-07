package io.github.min27.factoryanomaly.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.github.min27.factoryanomaly.decision.Decision;
import io.github.min27.factoryanomaly.decision.EngineProperties;
import io.github.min27.factoryanomaly.decision.FailureType;
import io.github.min27.factoryanomaly.equipment.Equipment;
import io.github.min27.factoryanomaly.reading.SensorReading;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

class AlertServiceTest {

    static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");

    private final AlertRepository repository = mock(AlertRepository.class);
    private final Equipment equipment = new Equipment("EQ-01", "설비 1", NOW);
    private final SensorReading reading = SensorReading.builder().equipment(equipment).build();

    private AlertService service(String... activeEngines) {
        return new AlertService(repository, new AlertProperties(50),
                new EngineProperties(List.of(activeEngines), "rule"),
                mock(PlatformTransactionManager.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AlertService service() {
        return service("rule");
    }

    private Decision decision(String engine, boolean anomaly, double severity, FailureType category) {
        return Decision.builder().reading(reading).engine(engine)
                .anomaly(anomaly).severity(severity).category(category).confidence(1)
                .latencyUs(10).decidedAt(NOW).build();
    }

    private Decision rule(boolean anomaly, double severity, FailureType category) {
        return decision("rule", anomaly, severity, category);
    }

    @ParameterizedTest(name = "anomaly={0}, severity={1} → 알람 {2}")
    @CsvSource({
            "true,  90,   true",  // 룰 고장 조건
            "true,  50,   true",  // 하한 경계: 이상
            "true,  49.9, false", // 하한 경계: 미만
            "true,  40,   false", // 룰 TWF 경고
            "false, 94.7, false", // ML 정상 판정이지만 심각도가 높은 경우
    })
    void 이상_판정이고_심각도가_하한_이상일_때만_알람(boolean anomaly, double severity, boolean expected) {
        FailureType category = anomaly ? FailureType.OSF : FailureType.NORMAL;
        assertThat(service().meetsCondition(rule(anomaly, severity, category))).isEqualTo(expected);
    }

    @Test
    void 미해결_알람이_없으면_새_알람을_만든다() {
        given(repository.recordOccurrence(any(), any(), any(), any())).willReturn(0);

        AlertService.Outcome outcome = service().raiseIfNeeded(List.of(rule(true, 90, FailureType.HDF)));

        assertThat(outcome).isEqualTo(AlertService.Outcome.CREATED);
        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(repository).saveAndFlush(captor.capture());
        Alert alert = captor.getValue();
        assertThat(alert.getEquipment()).isSameAs(equipment);
        assertThat(alert.getCategory()).isEqualTo(FailureType.HDF);
        assertThat(alert.getSeverity()).isEqualTo(90);
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(alert.getOccurrenceCount()).isEqualTo(1);
        assertThat(alert.getCreatedAt()).isEqualTo(NOW);
        assertThat(alert.getLastOccurredAt()).isEqualTo(NOW);
    }

    @Test
    void 같은_설비_유형의_미해결_알람이_있으면_합친다() {
        given(repository.recordOccurrence(any(), eq(FailureType.PWF), eq(AlertStatus.UNRESOLVED), eq(NOW)))
                .willReturn(1);

        AlertService.Outcome outcome = service().raiseIfNeeded(List.of(rule(true, 90, FailureType.PWF)));

        assertThat(outcome).isEqualTo(AlertService.Outcome.MERGED);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void 조건에_맞지_않으면_DB를_건드리지_않는다() {
        AlertService.Outcome outcome = service().raiseIfNeeded(List.of(rule(true, 40, FailureType.TWF)));

        assertThat(outcome).isEqualTo(AlertService.Outcome.NONE);
        verify(repository, never()).recordOccurrence(any(), any(), any(), any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void 대표_엔진이_아닌_엔진의_이상_판정은_알람을_내지_않는다() {
        Decision ruleNormal = rule(false, 0, FailureType.NORMAL);
        Decision mlAnomaly = decision("ml", true, 99, FailureType.OSF);

        AlertService.Outcome outcome = service("rule", "ml").raiseIfNeeded(List.of(mlAnomaly, ruleNormal));

        assertThat(outcome).isEqualTo(AlertService.Outcome.NONE);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void 대표_엔진이_실패해_판정이_없으면_알람도_없다() {
        Decision mlAnomaly = decision("ml", true, 99, FailureType.OSF);

        AlertService.Outcome outcome = service("rule", "ml").raiseIfNeeded(List.of(mlAnomaly));

        assertThat(outcome).isEqualTo(AlertService.Outcome.NONE);
    }

    @Test
    void 동시에_다른_요청이_알람을_먼저_만들면_다시_시도해_합친다() {
        given(repository.recordOccurrence(any(), any(), any(), any())).willReturn(0, 1);
        given(repository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("uq_alert_unresolved"));

        AlertService.Outcome outcome = service().raiseIfNeeded(List.of(rule(true, 90, FailureType.OSF)));

        assertThat(outcome).isEqualTo(AlertService.Outcome.MERGED);
        verify(repository, times(2)).recordOccurrence(any(), any(), any(), any());
        verify(repository, times(1)).saveAndFlush(any());
    }
}
