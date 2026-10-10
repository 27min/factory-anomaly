package io.github.min27.factoryanomaly.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.min27.factoryanomaly.alert.Alert;
import io.github.min27.factoryanomaly.alert.AlertRepository;
import io.github.min27.factoryanomaly.alert.AlertStatus;
import io.github.min27.factoryanomaly.decision.FailureType;
import io.github.min27.factoryanomaly.equipment.Equipment;
import io.github.min27.factoryanomaly.equipment.EquipmentRepository;
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
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드 화면·조회·알람 처리를 실제 SQL Server로 확인한다. 각 테스트는 롤백된다.
 * 시뮬레이터가 남긴 데이터가 있어도 결과가 같도록, 대상 설비(EQ-05)의 알람을 테스트 트랜잭션 안에서 먼저 해결 처리하고,
 * 최근 측정값은 테스트가 방금 보낸 값(가장 큰 id)으로 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardIntegrationTest {

    static final String PWF = """
            {"equipmentCode":"EQ-05","productType":"L",
             "airTemp":298.9,"processTemp":309.0,"rotSpeed":1410,"torque":65.7,"toolWear":191,
             "labels":{"machineFailure":true,"twf":false,"hdf":false,"pwf":true,"osf":true,"rnf":false}}
            """;
    static final String TWF_WARNING = """
            {"equipmentCode":"EQ-05","productType":"L",
             "airTemp":300,"processTemp":310,"rotSpeed":1500,"torque":40,"toolWear":210}
            """;
    static final String NORMAL = """
            {"equipmentCode":"EQ-05","productType":"L",
             "airTemp":300,"processTemp":310,"rotSpeed":1500,"torque":40,"toolWear":100}
            """;

    @Autowired MockMvcTester mvc;
    @Autowired DashboardQueryService queryService;
    @Autowired AlertRepository alertRepository;
    @Autowired EquipmentRepository equipmentRepository;
    @Autowired JdbcTemplate jdbc;

    private Equipment eq;

    @BeforeEach
    void resolveExistingAlerts() {
        eq = equipmentRepository.findByCode("EQ-05").orElseThrow();
        jdbc.update("UPDATE alert SET status = 'RESOLVED', resolved_at = SYSUTCDATETIME()"
                + " WHERE equipment_id = ? AND status <> 'RESOLVED'", eq.getId());
    }

    private long post(String body) {
        MvcTestResult result = mvc.post().uri("/api/readings").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String location = result.getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    private EquipmentStatusView eq05() {
        return queryService.equipmentStatuses().stream().filter(s -> s.code().equals("EQ-05")).findFirst().orElseThrow();
    }

    private Alert unresolvedPwf() {
        return alertRepository.findByEquipmentIdAndCategoryAndStatusIn(eq.getId(), FailureType.PWF, AlertStatus.UNRESOLVED)
                .orElseThrow();
    }

    @Test
    void 루트는_대시보드로_보낸다() {
        assertThat(mvc.get().uri("/")).hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/dashboard");
    }

    @Test
    void 대시보드_전체_페이지와_htmx를_내려준다() {
        post(PWF);

        assertThat(mvc.get().uri("/dashboard")).hasStatusOk().bodyText()
                .contains("<html", "EQ-01", "EQ-05", "미해결 알람", "엔진별 판정")
                .contains("/webjars/htmx.org/2.0.11/dist/htmx.min.js"); // webjars-locator-lite가 버전 경로로 바꿔 렌더링
        assertThat(mvc.get().uri("/webjars/htmx.org/dist/htmx.min.js")).hasStatusOk();
    }

    @Test
    void 구역_요청은_fragment만_내려준다() {
        assertThat(mvc.get().uri("/dashboard/equipment")).hasStatusOk().bodyText()
                .doesNotContain("<html").contains("id=\"equipment\"", "EQ-05");
        assertThat(mvc.get().uri("/dashboard/alerts")).hasStatusOk().bodyText()
                .doesNotContain("<html").contains("id=\"alerts\"");
        assertThat(mvc.get().uri("/dashboard/decisions")).hasStatusOk().bodyText()
                .doesNotContain("<html").contains("id=\"decisions\"");
    }

    @Test
    void 설비_상태는_미해결_알람이_우선이고_해결하면_최신_판정을_따른다() {
        post(NORMAL);
        assertThat(eq05().state()).isEqualTo(EquipmentState.NORMAL);

        post(PWF);
        post(NORMAL);
        assertThat(eq05().state()).isEqualTo(EquipmentState.ALERT); // 최신 판정은 정상이지만 알람이 남아 있다
        assertThat(eq05().alerts()).singleElement().satisfies(a -> assertThat(a.category()).isEqualTo(FailureType.PWF));

        mvc.post().uri("/dashboard/alerts/{id}/resolve", unresolvedPwf().getId()).exchange();
        assertThat(eq05().state()).isEqualTo(EquipmentState.NORMAL);

        post(TWF_WARNING);
        assertThat(eq05().state()).isEqualTo(EquipmentState.WARNING);
        assertThat(eq05().latestCategory()).isEqualTo(FailureType.TWF);
    }

    @Test
    void 확인_해결은_알람_구역을_다시_그리고_이벤트를_알린다() {
        post(PWF);
        long id = unresolvedPwf().getId();

        MvcTestResult ack = mvc.post().uri("/dashboard/alerts/{id}/acknowledge", id).exchange();
        assertThat(ack).hasStatusOk().hasHeader("HX-Trigger", DashboardController.ALERTS_CHANGED);
        assertThat(ack).bodyText().doesNotContain("<html").contains("id=\"alerts\"", "확인됨");
        Alert acked = alertRepository.findById(id).orElseThrow();
        assertThat(acked.getStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(acked.getAcknowledgedAt()).isNotNull();

        assertThat(mvc.post().uri("/dashboard/alerts/{id}/resolve", id)).hasStatusOk();
        Alert resolved = alertRepository.findById(id).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(resolved.getResolvedAt()).isNotNull();
    }

    @Test
    void 허용되지_않는_상태_변경은_409_없는_알람은_404() {
        post(PWF);
        long id = unresolvedPwf().getId();
        mvc.post().uri("/dashboard/alerts/{id}/resolve", id).exchange();

        assertThat(mvc.post().uri("/dashboard/alerts/{id}/resolve", id)).hasStatus(HttpStatus.CONFLICT);
        assertThat(mvc.post().uri("/dashboard/alerts/{id}/acknowledge", id)).hasStatus(HttpStatus.CONFLICT);
        assertThat(mvc.post().uri("/dashboard/alerts/{id}/resolve", Long.MAX_VALUE)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void 엔진별_판정_표의_첫_행은_방금_받은_측정값이다() {
        long id = post(PWF);

        EngineComparison comparison = queryService.engineComparison();

        assertThat(comparison.engines()).containsExactly("rule");
        assertThat(comparison.rows().get(0)).satisfies(r -> {
            assertThat(r.readingId()).isEqualTo(id);
            assertThat(r.equipmentCode()).isEqualTo("EQ-05");
            assertThat(r.actualFailure()).isTrue();
            assertThat(r.cells().get("rule").category()).isEqualTo(FailureType.PWF);
            assertThat(r.disagreement()).isFalse(); // 엔진이 하나면 엇갈릴 수 없다
        });
        assertThat(comparison.summaries()).singleElement().satisfies(s -> {
            assertThat(s.engine()).isEqualTo("rule");
            assertThat(s.decisions()).isPositive();
            assertThat(s.anomalies()).isPositive();
            assertThat(s.p50Us()).isNotNull();
        });
    }

    @Test
    void 상태와_처리_시각이_어긋나면_DB가_거부한다() {
        post(PWF);
        long id = unresolvedPwf().getId();

        assertThatThrownBy(() -> jdbc.update("UPDATE alert SET status = 'RESOLVED' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_alert_resolved_at");
    }
}
