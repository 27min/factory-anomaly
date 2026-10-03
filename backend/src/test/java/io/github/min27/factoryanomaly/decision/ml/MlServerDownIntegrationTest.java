package io.github.min27.factoryanomaly.decision.ml;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.min27.factoryanomaly.decision.Decision;
import io.github.min27.factoryanomaly.decision.DecisionFailureRepository;
import io.github.min27.factoryanomaly.decision.DecisionRepository;
import io.github.min27.factoryanomaly.decision.FailureReason;
import io.github.min27.factoryanomaly.reading.SensorReadingRepository;
import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * ML 엔진을 켠 채 ml-server가 죽어 있으면: 수집은 201로 성공하고, 룰 판정은 저장되고, ML은 CONNECTION 실패로 기록된다.
 * 아무도 듣지 않는 포트를 base-url로 쓴다. 커밋을 확인해야 하므로 @Transactional 없이 만든 행을 직접 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MlServerDownIntegrationTest {

    @DynamicPropertySource
    static void mlServerDown(DynamicPropertyRegistry registry) throws IOException {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        registry.add("engine.active", () -> "rule,ml");
        registry.add("engine.ml.base-url", () -> "http://127.0.0.1:" + unusedPort);
    }

    @Autowired MockMvcTester mvc;
    @Autowired MlEngine mlEngine;
    @Autowired SensorReadingRepository readingRepository;
    @Autowired DecisionRepository decisionRepository;
    @Autowired DecisionFailureRepository failureRepository;

    private Long createdId;

    @AfterEach
    void cleanUp() {
        if (createdId != null) {
            decisionRepository.deleteAll(decisionRepository.findByReadingIdOrderByEngine(createdId));
            failureRepository.deleteAll(failureRepository.findByReadingIdOrderByEngine(createdId));
            readingRepository.deleteById(createdId);
        }
    }

    @Test
    void ml_server가_죽어도_수집과_룰_판정은_성공하고_ML_실패가_기록된다() {
        String body = """
                {"equipmentCode":"EQ-04","productType":"L",
                 "airTemp":298.9,"processTemp":309.0,"rotSpeed":1410,"torque":65.7,"toolWear":191}
                """;

        var result = mvc.post().uri("/api/readings").contentType(MediaType.APPLICATION_JSON).content(body).exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        String location = result.getResponse().getHeader("Location");
        createdId = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));

        assertThat(decisionRepository.findByReadingIdOrderByEngine(createdId))
                .extracting(Decision::getEngine).containsExactly("rule");
        assertThat(failureRepository.findByReadingIdOrderByEngine(createdId)).singleElement().satisfies(f -> {
            assertThat(f.getEngine()).isEqualTo("ml");
            assertThat(f.getReason()).isEqualTo(FailureReason.CONNECTION);
        });
    }
}
