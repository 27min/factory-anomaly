package io.github.min27.factoryanomaly.decision.jev;

import io.github.min27.factoryanomaly.decision.DecisionEngine;
import io.github.min27.factoryanomaly.decision.DecisionResult;
import io.github.min27.factoryanomaly.decision.EngineException;
import io.github.min27.factoryanomaly.decision.FailureReason;
import io.github.min27.factoryanomaly.state.SensorState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Jev(TypeSafe AI) 판정 엔진 자리. <b>연동 구조만 준비되어 있고 API 호출은 구현되지 않았다</b> (D-018).
 *
 * <p>Jev의 질문 3종(예/아니오, 점수, 분류)은 {@link DecisionResult}의 anomaly / severity / category에 대응시킬 예정이다.
 * API 스펙과 early access 키를 확보하면 {@link #decide}를 MlEngine과 같은 방식(RestClient, 타임아웃,
 * 실패를 {@link EngineException}으로 분류)으로 구현한다.
 *
 * <p>미구현 상태에서 켜면 매 요청마다 실패가 쌓이는 대신 애플리케이션 시작을 실패시킨다.
 */
@Component
@ConditionalOnProperty(prefix = "engine.jev", name = "enabled", havingValue = "true")
public class JevEngine implements DecisionEngine {

    public JevEngine(JevEngineProperties properties) {
        if (!properties.hasApiKey()) {
            throw new IllegalStateException(
                    "engine.jev.enabled=true but JEV_API_KEY is empty. Set it in .env or disable the Jev engine.");
        }
        throw new IllegalStateException(
                "Jev engine is not implemented yet (waiting for API spec). Set engine.jev.enabled=false.");
    }

    @Override
    public String name() {
        return "jev";
    }

    @Override
    public DecisionResult decide(SensorState state) {
        // 생성자에서 막히므로 도달하지 않는다. 구현 시 이 부분을 채운다
        throw new EngineException(FailureReason.UNEXPECTED, "Jev engine is not implemented", null);
    }
}
