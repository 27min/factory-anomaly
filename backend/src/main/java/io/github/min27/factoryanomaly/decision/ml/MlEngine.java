package io.github.min27.factoryanomaly.decision.ml;

import io.github.min27.factoryanomaly.decision.ConditionalOnActiveEngine;
import io.github.min27.factoryanomaly.decision.DecisionEngine;
import io.github.min27.factoryanomaly.decision.DecisionResult;
import io.github.min27.factoryanomaly.decision.EngineException;
import io.github.min27.factoryanomaly.decision.FailureReason;
import io.github.min27.factoryanomaly.decision.FailureType;
import io.github.min27.factoryanomaly.state.SensorState;
import io.github.min27.factoryanomaly.state.SensorValues;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * ml-server의 POST /predict를 호출해 판정한다.
 *
 * <p>원본 센서값만 보내고, 파생변수 계산과 임계값 적용은 ml-server가 한다 (D-016).
 * 실패는 이유를 분류한 {@link EngineException}으로 던지고 재시도하지 않는다 (D-017).
 */
@Component
@ConditionalOnActiveEngine("ml")
public class MlEngine implements DecisionEngine {

    private final RestClient client;

    public MlEngine(RestClient.Builder builder, MlEngineProperties properties) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(properties.connectTimeout(), properties.readTimeout());
        this.client = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
                .build();
    }

    @Override
    public String name() {
        return "ml";
    }

    @Override
    public DecisionResult decide(SensorState state) {
        PredictResponse response;
        try {
            response = client.post()
                    .uri("/predict")
                    .body(PredictRequest.from(state.values()))
                    .retrieve()
                    .body(PredictResponse.class);
        } catch (ResourceAccessException e) {
            throw new EngineException(classify(e), describe(e), e);
        } catch (RestClientResponseException e) {
            throw new EngineException(FailureReason.HTTP_ERROR, "HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            // 본문을 읽지 못함 (JSON 형식 오류 등)
            throw new EngineException(FailureReason.INVALID_RESPONSE, e.getMessage(), e);
        }
        return toResult(response);
    }

    /** 연결 단계의 시간 초과는 서버에 닿지 못한 것이므로 CONNECTION, 응답 대기 시간 초과만 TIMEOUT으로 본다. */
    private static FailureReason classify(ResourceAccessException e) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
        boolean readTimeout = cause instanceof HttpTimeoutException && !(cause instanceof HttpConnectTimeoutException);
        return readTimeout ? FailureReason.TIMEOUT : FailureReason.CONNECTION;
    }

    /** JDK HttpClient의 연결 예외는 메시지가 null인 경우가 많아, 근본 원인의 클래스 이름을 함께 남긴다. */
    private static String describe(ResourceAccessException e) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
        return cause.getMessage() == null
                ? cause.getClass().getSimpleName()
                : cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    private static DecisionResult toResult(PredictResponse r) {
        if (r == null || r.anomaly() == null || r.severity() == null || r.category() == null || r.confidence() == null) {
            throw new EngineException(FailureReason.INVALID_RESPONSE, "missing field in response: " + r, null);
        }
        try {
            return new DecisionResult(r.anomaly(), r.severity(), FailureType.valueOf(r.category()), r.confidence());
        } catch (IllegalArgumentException e) {
            // 알 수 없는 category, 범위를 벗어난 값, anomaly와 category 불일치
            throw new EngineException(FailureReason.INVALID_RESPONSE, e.getMessage(), e);
        }
    }

    /** ml-server 요청. 필드 이름은 ml-server의 PredictRequest와 같다. */
    record PredictRequest(String productType, double airTemp, double processTemp,
                          int rotSpeed, double torque, int toolWear) {
        static PredictRequest from(SensorValues v) {
            return new PredictRequest(v.productType().name(), v.airTemp(), v.processTemp(),
                    v.rotSpeed(), v.torque(), v.toolWear());
        }
    }

    /** ml-server 응답 중 판정에 쓰는 필드. 클래스별 확률(probabilities)은 쓰지 않는다. */
    record PredictResponse(Boolean anomaly, Double severity, String category, Double confidence, String modelId) {
    }
}
