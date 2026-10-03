package io.github.min27.factoryanomaly.decision.ml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.min27.factoryanomaly.decision.DecisionResult;
import io.github.min27.factoryanomaly.decision.EngineException;
import io.github.min27.factoryanomaly.decision.FailureReason;
import io.github.min27.factoryanomaly.decision.FailureType;
import io.github.min27.factoryanomaly.reading.ProductType;
import io.github.min27.factoryanomaly.state.SensorState;
import io.github.min27.factoryanomaly.state.SensorValues;
import io.github.min27.factoryanomaly.state.StateBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

/**
 * 실제 HTTP 서버(JDK 내장)를 띄워 ml-server를 흉내 낸다. 타임아웃·연결 실패는 목으로는 재현되지 않으므로 실제 네트워크로 확인한다.
 */
class MlEngineTest {

    // AI4I UDI 70
    static final SensorState STATE = new StateBuilder().build(
            new SensorValues(ProductType.L, 298.9, 309.0, 1410, 65.7, 191));

    static final String PWF_RESPONSE = """
            {"anomaly":true,"severity":99.99,"category":"PWF","confidence":0.9999,
             "probabilities":{"NORMAL":0.0001,"HDF":0.0,"PWF":0.9996,"OSF":0.0003,"TWF":0.0},
             "modelId":"44d25f5670a0"}
            """;

    private HttpServer server;
    private final AtomicReference<Handler> handler = new AtomicReference<>();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();

    @FunctionalInterface
    interface Handler {
        void handle(HttpExchange exchange) throws Exception;
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/predict", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                handler.get().handle(exchange);
            } catch (Exception e) {
                // 클라이언트가 먼저 끊은 경우 등
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private MlEngine engine(Duration readTimeout) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new MlEngine(RestClient.builder(),
                new MlEngineProperties(true, baseUrl, Duration.ofMillis(300), readTimeout));
    }

    private MlEngine engine() {
        return engine(Duration.ofSeconds(1));
    }

    private static Handler respond(int status, String body) {
        return exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        };
    }

    private void assertFailsWith(MlEngine engine, FailureReason reason) {
        assertThatThrownBy(() -> engine.decide(STATE))
                .isInstanceOfSatisfying(EngineException.class, e -> assertThat(e.getReason()).isEqualTo(reason));
    }

    @Test
    void 응답을_DecisionResult로_옮긴다() {
        handler.set(respond(200, PWF_RESPONSE));

        DecisionResult r = engine().decide(STATE);

        assertThat(r).isEqualTo(new DecisionResult(true, 99.99, FailureType.PWF, 0.9999));
    }

    @Test
    void 원본_센서값만_보낸다() {
        handler.set(respond(200, PWF_RESPONSE));

        engine().decide(STATE);

        assertThat(receivedBody.get())
                .contains("\"productType\":\"L\"", "\"airTemp\":298.9", "\"processTemp\":309.0",
                        "\"rotSpeed\":1410", "\"torque\":65.7", "\"toolWear\":191")
                .doesNotContain("tempDiff", "power", "wearTorque");
    }

    @Test
    void 응답이_늦으면_TIMEOUT() {
        handler.set(exchange -> {
            Thread.sleep(1_000);
            respond(200, PWF_RESPONSE).handle(exchange);
        });

        long start = System.nanoTime();
        assertFailsWith(engine(Duration.ofMillis(200)), FailureReason.TIMEOUT);
        assertThatThrownBy(() -> engine(Duration.ofMillis(200)).decide(STATE))
                .hasMessageStartingWith("HttpTimeoutException");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(900));
    }

    @Test
    void 서버가_없으면_CONNECTION() {
        MlEngine engine = engine();
        server.stop(0);

        assertFailsWith(engine, FailureReason.CONNECTION);
        assertThatThrownBy(() -> engine.decide(STATE)).message().isNotBlank().doesNotContain("null");
    }

    @ParameterizedTest
    @ValueSource(ints = {422, 500, 503})
    void 에러_응답은_HTTP_ERROR(int status) {
        handler.set(respond(status, "{\"detail\":\"error\"}"));

        assertFailsWith(engine(), FailureReason.HTTP_ERROR);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not json",
            "{\"anomaly\":true,\"severity\":99,\"confidence\":0.99}",                         // category 누락
            "{\"anomaly\":true,\"severity\":99,\"category\":\"XYZ\",\"confidence\":0.99}",    // 알 수 없는 유형
            "{\"anomaly\":true,\"severity\":99,\"category\":\"NORMAL\",\"confidence\":0.99}", // anomaly와 불일치
            "{\"anomaly\":true,\"severity\":120,\"category\":\"HDF\",\"confidence\":0.99}",   // 범위 초과
    })
    void 형식이나_불변식이_맞지_않는_응답은_INVALID_RESPONSE(String body) {
        handler.set(respond(200, body));

        assertFailsWith(engine(), FailureReason.INVALID_RESPONSE);
    }
}
