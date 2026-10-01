package io.github.min27.factoryanomaly.decision;

import io.github.min27.factoryanomaly.state.SensorState;

/**
 * 판정 엔진의 공통 인터페이스. 룰 / ML / Jev 엔진이 같은 입력을 받아 같은 형태로 답한다.
 *
 * <p>응답시간은 엔진이 아니라 호출하는 쪽에서 측정한다. 세 엔진을 같은 구간으로 재기 위함이다 (D-011).
 */
public interface DecisionEngine {

    /** decision.engine 컬럼에 저장되는 이름. 예: "rule", "ml", "jev" */
    String name();

    DecisionResult decide(SensorState state);
}
