package io.github.min27.factoryanomaly.decision.rule;

import io.github.min27.factoryanomaly.decision.ConditionalOnActiveEngine;
import io.github.min27.factoryanomaly.decision.DecisionEngine;
import io.github.min27.factoryanomaly.decision.DecisionResult;
import io.github.min27.factoryanomaly.decision.FailureType;
import io.github.min27.factoryanomaly.reading.ProductType;
import io.github.min27.factoryanomaly.state.SensorState;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AI4I 데이터셋에 공개된 고장 발생 조건으로 판정한다. 로직은 ml-server/notebooks/03_rule_engine.ipynb와 같다.
 *
 * <p>임계값은 데이터셋이 정의한 조건이라 상수로 둔다. 경계값 자체는 정상이다 (부등호 방향은 데이터셋 정의를 따른다).
 * TWF는 200~240분 구간에서 무작위로 발생하므로 "위험 구간 진입" 경고로만 판정한다 (D-004).
 */
@Component
@ConditionalOnActiveEngine("rule")
@RequiredArgsConstructor
public class RuleEngine implements DecisionEngine {

    static final double HDF_MAX_TEMP_DIFF = 8.6;    // K, 미만이면 HDF 조건
    static final int HDF_MAX_ROT_SPEED = 1380;      // rpm, 미만이면 HDF 조건
    static final double PWF_MIN_POWER = 3500;       // W, 미만이면 PWF
    static final double PWF_MAX_POWER = 9000;       // W, 초과하면 PWF
    static final int TWF_WARN_TOOL_WEAR = 200;      // min, 이상이면 TWF 경고

    /** 룰은 결정적이라 확률 개념이 없다 (D-011). */
    private static final double CONFIDENCE = 1.0;

    private final RuleEngineProperties properties;

    @Override
    public String name() {
        return "rule";
    }

    @Override
    public DecisionResult decide(SensorState s) {
        // 대표 유형 우선순위(HDF > PWF > OSF > TWF) 순서대로 담는다
        List<Match> matches = new ArrayList<>(4);
        if (s.tempDiff() < HDF_MAX_TEMP_DIFF && s.values().rotSpeed() < HDF_MAX_ROT_SPEED) {
            matches.add(new Match(FailureType.HDF, properties.failureSeverity()));
        }
        if (s.power() < PWF_MIN_POWER || s.power() > PWF_MAX_POWER) {
            matches.add(new Match(FailureType.PWF, properties.failureSeverity()));
        }
        if (s.wearTorque() > osfLimit(s.productType())) {
            matches.add(new Match(FailureType.OSF, properties.failureSeverity()));
        }
        if (s.values().toolWear() >= TWF_WARN_TOOL_WEAR) {
            matches.add(new Match(FailureType.TWF, properties.warningSeverity()));
        }

        // 심각도가 가장 높은 유형, 같으면 먼저 담긴 유형 (D-011)
        return matches.stream()
                .reduce((best, next) -> next.severity() > best.severity() ? next : best)
                .map(m -> new DecisionResult(true, m.severity(), m.type(), CONFIDENCE))
                .orElseGet(() -> new DecisionResult(false, 0, FailureType.NORMAL, CONFIDENCE));
    }

    /** 과부하 한계 [min·Nm]. 고급 제품일수록 공구가 더 큰 부하를 견딘다. */
    static double osfLimit(ProductType type) {
        return switch (type) {
            case L -> 11_000;
            case M -> 12_000;
            case H -> 13_000;
        };
    }

    private record Match(FailureType type, double severity) {
    }
}
