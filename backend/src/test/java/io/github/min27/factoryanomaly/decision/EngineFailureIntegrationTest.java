package io.github.min27.factoryanomaly.decision;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.min27.factoryanomaly.reading.SensorReadingRepository;
import io.github.min27.factoryanomaly.state.SensorState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * 엔진 하나가 실패해도 측정값과 다른 엔진의 판정은 커밋되는지 실제 DB로 확인한다.
 * 트랜잭션 분리 자체를 검증하므로 테스트를 @Transactional로 감싸지 않고, 만든 행은 직접 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EngineFailureIntegrationTest.BrokenEngine.class)
class EngineFailureIntegrationTest {

    @TestConfiguration
    static class BrokenEngine {
        @Bean
        DecisionEngine brokenEngine() {
            return new DecisionEngine() {
                @Override public String name() { return "broken"; }
                @Override public DecisionResult decide(SensorState s) {
                    throw new IllegalStateException("engine down");
                }
            };
        }
    }

    @Autowired MockMvcTester mvc;
    @Autowired SensorReadingRepository readingRepository;
    @Autowired DecisionRepository decisionRepository;

    private Long createdId;

    @AfterEach
    void cleanUp() {
        if (createdId != null) {
            decisionRepository.deleteAll(decisionRepository.findByReadingIdOrderByEngine(createdId));
            readingRepository.deleteById(createdId);
        }
    }

    @Test
    void 엔진이_실패해도_측정값과_다른_엔진의_판정은_남는다() {
        String body = """
                {"equipmentCode":"EQ-02","productType":"L",
                 "airTemp":300,"processTemp":310,"rotSpeed":1500,"torque":40,"toolWear":100}
                """;

        var result = mvc.post().uri("/api/readings").contentType(MediaType.APPLICATION_JSON).content(body).exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        String location = result.getResponse().getHeader("Location");
        createdId = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));

        assertThat(readingRepository.existsById(createdId)).isTrue();
        assertThat(decisionRepository.findByReadingIdOrderByEngine(createdId))
                .extracting(Decision::getEngine).containsExactly("rule");
        assertThat(result).bodyJson().extractingPath("$.decisions.length()").isEqualTo(1);
    }
}
