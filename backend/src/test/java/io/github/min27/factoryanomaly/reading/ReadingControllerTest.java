package io.github.min27.factoryanomaly.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.min27.factoryanomaly.common.ReadingNotFoundException;
import io.github.min27.factoryanomaly.common.UnknownEquipmentException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 요청 검증과 에러 응답 형식을 확인한다. 서비스는 목으로 대체한다. */
@WebMvcTest(ReadingController.class)
class ReadingControllerTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean ReadingService readingService;

    private static final String VALID = """
            {"equipmentCode":"EQ-01","productType":"M",
             "airTemp":298.1,"processTemp":308.6,"rotSpeed":1551,"torque":42.8,"toolWear":0}
            """;

    private MvcTestResult post(String body) {
        return mvc.post().uri("/api/readings").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    @Test
    void 정상_요청은_201과_Location을_돌려준다() {
        given(readingService.ingest(any())).willReturn(new ReadingResponse(
                7L, "EQ-01", ProductType.M, 10.5, 6951.59, 0.0, Instant.parse("2026-09-29T00:00:00Z")));

        assertThat(post(VALID))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "/api/readings/7")
                .bodyJson().extractingPath("$.power").isEqualTo(6951.59);
    }

    @Test
    void 필수_필드가_없으면_400과_필드별_오류를_돌려준다() {
        String missingTorque = """
                {"equipmentCode":"EQ-01","productType":"M",
                 "airTemp":298.1,"processTemp":308.6,"rotSpeed":1551,"toolWear":0}
                """;

        MvcTestResult result = post(missingTorque);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Validation failed");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("torque");
        verify(readingService, never()).ingest(any());
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
            "rotSpeed,    -1",
            "torque,      -0.1",
            "toolWear,    -5",
            "airTemp,     199.9",
            "processTemp, 500.1",
    })
    void 물리적으로_불가능한_값은_거부한다(String field, String value) {
        String body = VALID.replaceFirst("\"" + field + "\":[0-9.]+", "\"" + field + "\":" + value);

        MvcTestResult result = post(body);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo(field);
    }

    @Test
    void 데이터셋_범위를_벗어나도_물리적으로_가능하면_받는다() {
        // 데이터셋 최대 회전수는 2886 rpm이지만, 이상 여부는 판정 엔진이 판단한다
        given(readingService.ingest(any())).willReturn(new ReadingResponse(
                1L, "EQ-01", ProductType.M, 10.5, 0, 0, Instant.EPOCH));

        assertThat(post(VALID.replace("1551", "5000"))).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void 알_수_없는_제품타입은_400() {
        assertThat(post(VALID.replace("\"M\"", "\"X\""))).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 등록되지_않은_설비는_422() {
        given(readingService.ingest(any())).willThrow(new UnknownEquipmentException("EQ-99"));

        MvcTestResult result = post(VALID.replace("EQ-01", "EQ-99"));

        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Unknown equipment");
        assertThat(result).bodyJson().extractingPath("$.detail").asString().contains("EQ-99");
    }

    @Test
    void 없는_측정값_조회는_404() {
        given(readingService.get(999L)).willThrow(new ReadingNotFoundException(999L));

        assertThat(mvc.get().uri("/api/readings/999"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.title").isEqualTo("Reading not found");
    }
}
