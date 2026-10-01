package io.github.min27.factoryanomaly.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import io.github.min27.factoryanomaly.reading.ProductType;
import io.github.min27.factoryanomaly.reading.SensorReading;
import io.github.min27.factoryanomaly.state.SensorState;
import io.github.min27.factoryanomaly.state.SensorValues;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DecisionServiceTest {

    static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    static final DecisionResult NORMAL = new DecisionResult(false, 0, FailureType.NORMAL, 1);

    private final DecisionRepository repository = mock(DecisionRepository.class);
    private final SensorReading reading = SensorReading.builder().build();
    private final SensorState state = new SensorState(
            new SensorValues(ProductType.L, 300, 310, 1500, 40, 100), 10, 6283, 4000);

    @BeforeEach
    void saveReturnsArgument() {
        given(repository.save(any(Decision.class))).willAnswer(returnsFirstArg());
    }

    private DecisionService service(DecisionEngine... engines) {
        return new DecisionService(List.of(engines), repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static DecisionEngine engine(String name, DecisionResult result) {
        return new DecisionEngine() {
            @Override public String name() { return name; }
            @Override public DecisionResult decide(SensorState s) { return result; }
        };
    }

    private static DecisionEngine failing(String name) {
        return new DecisionEngine() {
            @Override public String name() { return name; }
            @Override public DecisionResult decide(SensorState s) { throw new IllegalStateException("down"); }
        };
    }

    @Test
    void 엔진_결과를_그대로_저장하고_측정값과_시각을_붙인다() {
        DecisionResult hdf = new DecisionResult(true, 90, FailureType.HDF, 1);

        List<Decision> saved = service(engine("rule", hdf)).decide(reading, state);

        assertThat(saved).singleElement().satisfies(d -> {
            assertThat(d.getReading()).isSameAs(reading);
            assertThat(d.getEngine()).isEqualTo("rule");
            assertThat(d.isAnomaly()).isTrue();
            assertThat(d.getSeverity()).isEqualTo(90);
            assertThat(d.getCategory()).isEqualTo(FailureType.HDF);
            assertThat(d.getConfidence()).isEqualTo(1);
            assertThat(d.getDecidedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void 응답시간은_엔진_호출_전후로_잰다() {
        DecisionEngine slow = new DecisionEngine() {
            @Override public String name() { return "slow"; }
            @Override public DecisionResult decide(SensorState s) {
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return NORMAL;
            }
        };

        assertThat(service(slow).decide(reading, state).get(0).getLatencyMs()).isGreaterThanOrEqualTo(30);
    }

    @Test
    void 모든_엔진을_이름_순서로_실행한다() {
        List<Decision> saved = service(engine("rule", NORMAL), engine("ml", NORMAL)).decide(reading, state);

        assertThat(saved).extracting(Decision::getEngine).containsExactly("ml", "rule");
    }

    @Test
    void 엔진이_실패하면_그_엔진만_빠지고_나머지는_저장한다() {
        List<Decision> saved = service(failing("ml"), engine("rule", NORMAL)).decide(reading, state);

        assertThat(saved).extracting(Decision::getEngine).containsExactly("rule");
    }
}
