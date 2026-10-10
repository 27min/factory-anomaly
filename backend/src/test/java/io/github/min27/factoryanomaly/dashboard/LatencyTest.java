package io.github.min27.factoryanomaly.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class LatencyTest {

    @Test
    void 밀리초_미만은_마이크로초로_보여준다() {
        assertThat(Latency.format(13L)).isEqualTo("13 µs");
        assertThat(Latency.format(999L)).isEqualTo("999 µs");
        assertThat(Latency.format(1_000L)).isEqualTo("1.0 ms");
        assertThat(Latency.format(6_840L)).isEqualTo("6.8 ms");
        assertThat(Latency.format(null)).isEqualTo("-");
    }

    @Test
    void nearest_rank_백분위() {
        List<Long> oneToHundred = LongStream.rangeClosed(1, 100).boxed().toList();
        assertThat(Latency.percentile(oneToHundred, 50)).isEqualTo(50);
        assertThat(Latency.percentile(oneToHundred, 95)).isEqualTo(95);

        assertThat(Latency.percentile(List.of(7L), 50)).isEqualTo(7);
        assertThat(Latency.percentile(List.of(7L), 95)).isEqualTo(7);
        assertThat(Latency.percentile(List.of(1L, 2L, 3L), 50)).isEqualTo(2);
        assertThat(Latency.percentile(List.of(), 50)).isNull();
    }
}
