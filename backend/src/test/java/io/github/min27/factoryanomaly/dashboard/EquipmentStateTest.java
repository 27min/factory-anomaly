package io.github.min27.factoryanomaly.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.min27.factoryanomaly.decision.Decision;
import io.github.min27.factoryanomaly.decision.FailureType;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EquipmentStateTest {

    private static Optional<Decision> decision(boolean anomaly, FailureType category) {
        return Optional.of(Decision.builder().engine("rule").anomaly(anomaly).severity(anomaly ? 40 : 0)
                .category(category).confidence(1).latencyUs(10).decidedAt(Instant.EPOCH).build());
    }

    @Test
    void 미해결_알람이_있으면_최신_판정과_관계없이_알람() {
        assertThat(DashboardQueryService.state(true, true, decision(false, FailureType.NORMAL)))
                .isEqualTo(EquipmentState.ALERT);
    }

    @Test
    void 알람이_없으면_최신_판정을_본다() {
        assertThat(DashboardQueryService.state(false, true, decision(true, FailureType.TWF)))
                .isEqualTo(EquipmentState.WARNING);
        assertThat(DashboardQueryService.state(false, true, decision(false, FailureType.NORMAL)))
                .isEqualTo(EquipmentState.NORMAL);
    }

    @Test
    void 대표_엔진_판정이_없거나_측정값이_없는_경우() {
        assertThat(DashboardQueryService.state(false, true, Optional.empty())).isEqualTo(EquipmentState.UNKNOWN);
        assertThat(DashboardQueryService.state(false, false, Optional.empty())).isEqualTo(EquipmentState.NO_DATA);
    }
}
