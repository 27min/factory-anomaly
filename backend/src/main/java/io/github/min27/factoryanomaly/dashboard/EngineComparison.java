package io.github.min27.factoryanomaly.dashboard;

import io.github.min27.factoryanomaly.decision.FailureType;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * 엔진별 판정 비교 (D-022).
 *
 * @param engines    표의 열. 실행 중인 엔진(engine.active), 이름 순서
 * @param summaries  최근 {@code windowSize}건 기준 엔진별 요약
 * @param rows       최근 측정값별 엔진 판정
 * @param windowSize 요약에 쓴 측정값 수 (데이터가 적으면 설정값보다 작다)
 */
public record EngineComparison(List<String> engines, List<Summary> summaries, List<Row> rows, int windowSize) {

    /** 엔진 하나의 최근 구간 요약. 응답시간은 판정 성공 건 기준이다. */
    public record Summary(String engine, int decisions, int anomalies, long failures, Long p50Us, Long p95Us) {
        public String p50() {
            return Latency.format(p50Us);
        }

        public String p95() {
            return Latency.format(p95Us);
        }
    }

    /**
     * @param actualFailure 정답 라벨 machineFailure. 시뮬레이터가 보낸 경우에만 있다
     * @param cells         엔진 이름 → 판정 또는 실패. 해당 엔진의 기록이 없으면 키가 없다
     * @param disagreement  판정한 엔진들의 이상 여부가 서로 다름
     */
    public record Row(long readingId, String equipmentCode, ZonedDateTime receivedAt, Boolean actualFailure,
                      Map<String, Cell> cells, boolean disagreement) {
    }

    /** 판정 결과, 또는 실패(failureReason != null). */
    public record Cell(boolean anomaly, FailureType category, double severity, long latencyUs, String failureReason) {
        public boolean failed() {
            return failureReason != null;
        }

        public String latency() {
            return Latency.format(latencyUs);
        }
    }
}
