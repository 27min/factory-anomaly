package io.github.min27.factoryanomaly.decision;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DecisionResultTest {

    @Test
    void 경계값은_허용한다() {
        assertThatCode(() -> new DecisionResult(false, 0, FailureType.NORMAL, 0)).doesNotThrowAnyException();
        assertThatCode(() -> new DecisionResult(true, 100, FailureType.HDF, 1)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 100.1, Double.NaN})
    void severity_범위를_벗어나면_거부한다(double severity) {
        assertThatThrownBy(() -> new DecisionResult(true, severity, FailureType.HDF, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN})
    void confidence_범위를_벗어나면_거부한다(double confidence) {
        assertThatThrownBy(() -> new DecisionResult(true, 90, FailureType.HDF, confidence))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anomaly와_category가_어긋나면_거부한다() {
        assertThatThrownBy(() -> new DecisionResult(true, 90, FailureType.NORMAL, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DecisionResult(false, 0, FailureType.OSF, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void category는_null일_수_없다() {
        assertThatThrownBy(() -> new DecisionResult(false, 0, null, 1))
                .isInstanceOf(NullPointerException.class);
    }
}
