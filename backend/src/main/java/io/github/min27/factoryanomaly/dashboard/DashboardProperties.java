package io.github.min27.factoryanomaly.dashboard;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 대시보드 표시 설정 (D-022).
 *
 * @param timeZone      화면에 보여줄 시간대. 저장은 UTC다 (D-008)
 * @param refresh       구역별 자동 갱신 간격
 * @param recentRows    엔진별 판정 표에 보여줄 최근 측정값 수
 * @param summaryWindow 엔진별 요약(이상 건수, 실패 건수, 응답시간 p50/p95)을 계산할 최근 측정값 수
 */
@Validated
@ConfigurationProperties("dashboard")
public record DashboardProperties(
        @DefaultValue("Asia/Seoul") @NotNull ZoneId timeZone,
        @DefaultValue("3s") @NotNull Duration refresh,
        @DefaultValue("20") @Min(1) @Max(200) int recentRows,
        @DefaultValue("500") @Min(1) @Max(10_000) int summaryWindow
) {
    public long refreshSeconds() {
        return Math.max(1, refresh.toSeconds());
    }
}
