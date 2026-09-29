package io.github.min27.factoryanomaly.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** 요청 → 파생변수 계산 → 실제 SQL Server 저장까지 한 번에 확인한다. 각 테스트는 롤백된다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(ReadingApiIntegrationTest.FixedClock.class)
class ReadingApiIntegrationTest {

    static final Instant NOW = Instant.parse("2026-09-29T06:00:00Z");

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired MockMvcTester mvc;
    @Autowired SensorReadingRepository readingRepository;

    private static long idFromLocation(MvcTestResult result) {
        String location = result.getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    @Test
    void 측정값을_받아_파생변수와_정답라벨을_함께_저장한다() {
        // AI4I UDI 70: PWF + OSF 동시 고장
        String body = """
                {"equipmentCode":"EQ-03","productType":"L",
                 "airTemp":298.9,"processTemp":309.0,"rotSpeed":1410,"torque":65.7,"toolWear":191,
                 "labels":{"machineFailure":true,"twf":false,"hdf":false,"pwf":true,"osf":true,"rnf":false},
                 "sourceUdi":70}
                """;

        var result = mvc.post().uri("/api/readings").contentType(MediaType.APPLICATION_JSON).content(body).exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        long id = idFromLocation(result);

        SensorReading saved = readingRepository.findById(id).orElseThrow();
        assertThat(saved.getEquipment().getCode()).isEqualTo("EQ-03");
        assertThat(saved.getTempDiff()).isCloseTo(10.100000000000023, within(1e-9));
        assertThat(saved.getPower()).isCloseTo(9700.923955019922, within(1e-9));
        assertThat(saved.getWearTorque()).isCloseTo(12548.7, within(1e-9));
        assertThat(saved.getLabels().pwf()).isTrue();
        assertThat(saved.getLabels().osf()).isTrue();
        assertThat(saved.getSourceUdi()).isEqualTo(70);
        assertThat(saved.getReceivedAt()).isEqualTo(NOW);

        assertThat(mvc.get().uri("/api/readings/" + id))
                .hasStatusOk()
                .bodyJson().extractingPath("$.equipmentCode").isEqualTo("EQ-03");
    }

    @Test
    void 라벨_없이_보낸_측정값은_라벨이_비어_저장된다() {
        String body = """
                {"equipmentCode":"EQ-01","productType":"H",
                 "airTemp":300,"processTemp":310,"rotSpeed":1500,"torque":40,"toolWear":10}
                """;

        var result = mvc.post().uri("/api/readings").contentType(MediaType.APPLICATION_JSON).content(body).exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        long id = idFromLocation(result);
        assertThat(readingRepository.findById(id).orElseThrow().getLabels()).isNull();
    }

    @Test
    void 등록되지_않은_설비는_저장하지_않는다() {
        long before = readingRepository.count();
        String body = """
                {"equipmentCode":"EQ-99","productType":"L",
                 "airTemp":300,"processTemp":310,"rotSpeed":1500,"torque":40,"toolWear":10}
                """;

        assertThat(mvc.post().uri("/api/readings").contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(readingRepository.count()).isEqualTo(before);
    }
}
