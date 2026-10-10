package io.github.min27.factoryanomaly.dashboard;

import java.util.List;

/** 응답시간(마이크로초) 표시와 백분위 계산. */
final class Latency {

    private Latency() {
    }

    /** 1ms 미만은 µs, 그 이상은 ms로 보여준다. 룰 엔진은 수십 µs, ML은 수 ms라 단위를 섞는다. */
    static String format(Long micros) {
        if (micros == null) {
            return "-";
        }
        if (micros < 1_000) {
            return micros + " µs";
        }
        return String.format("%.1f ms", micros / 1_000.0);
    }

    /** nearest-rank 방식 백분위. 정렬된 목록이어야 한다. 비어 있으면 null. */
    static Long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return null;
        }
        int rank = (int) Math.ceil(p / 100.0 * sorted.size());
        return sorted.get(Math.max(rank, 1) - 1);
    }
}
