package io.github.min27.factoryanomaly.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.min27.factoryanomaly.common.InvalidAlertTransitionException;
import io.github.min27.factoryanomaly.decision.Decision;
import io.github.min27.factoryanomaly.decision.FailureType;
import io.github.min27.factoryanomaly.equipment.Equipment;
import io.github.min27.factoryanomaly.reading.SensorReading;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AlertTest {

    static final Instant T0 = Instant.parse("2026-10-07T00:00:00Z");
    static final Instant T1 = T0.plusSeconds(60);
    static final Instant T2 = T0.plusSeconds(120);

    private final Equipment equipment = new Equipment("EQ-01", "설비 1", T0);

    private Alert open() {
        Decision decision = Decision.builder().reading(SensorReading.builder().equipment(equipment).build())
                .engine("rule").anomaly(true).severity(90).category(FailureType.HDF).confidence(1)
                .latencyUs(10).decidedAt(T0).build();
        return Alert.open(decision, equipment, T0);
    }

    @Test
    void 확인_후_해결() {
        Alert alert = open();

        alert.acknowledge(T1);
        alert.resolve(T2);

        assertThat(alert.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(alert.getAcknowledgedAt()).isEqualTo(T1);
        assertThat(alert.getResolvedAt()).isEqualTo(T2);
    }

    @Test
    void 확인_없이_바로_해결할_수_있다() {
        Alert alert = open();

        alert.resolve(T1);

        assertThat(alert.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(alert.getAcknowledgedAt()).isNull();
        assertThat(alert.getResolvedAt()).isEqualTo(T1);
    }

    @Test
    void 이미_확인한_알람은_다시_확인할_수_없다() {
        Alert alert = open();
        alert.acknowledge(T1);

        assertThatThrownBy(() -> alert.acknowledge(T2)).isInstanceOf(InvalidAlertTransitionException.class);
        assertThat(alert.getAcknowledgedAt()).isEqualTo(T1);
    }

    @Test
    void 해결한_알람은_확인도_해결도_할_수_없다() {
        Alert alert = open();
        alert.resolve(T1);

        assertThatThrownBy(() -> alert.acknowledge(T2)).isInstanceOf(InvalidAlertTransitionException.class);
        assertThatThrownBy(() -> alert.resolve(T2)).isInstanceOf(InvalidAlertTransitionException.class);
        assertThat(alert.getResolvedAt()).isEqualTo(T1);
    }

    @Test
    void 정상_판정으로는_알람을_만들_수_없다() {
        Decision normal = Decision.builder().reading(SensorReading.builder().equipment(equipment).build())
                .engine("rule").anomaly(false).severity(0).category(FailureType.NORMAL).confidence(1)
                .latencyUs(10).decidedAt(T0).build();

        assertThatThrownBy(() -> Alert.open(normal, equipment, T0)).isInstanceOf(IllegalArgumentException.class);
    }
}
