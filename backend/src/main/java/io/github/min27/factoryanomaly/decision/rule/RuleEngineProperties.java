package io.github.min27.factoryanomaly.decision.rule;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 룰 엔진의 심각도 설정. 임계값과 달리 Phase 4 알람 임계값과 함께 조정할 값이라 설정으로 뺀다 (D-004, D-012).
 *
 * @param failureSeverity HDF·PWF·OSF 조건에 해당할 때의 심각도 (고장 조건 충족)
 * @param warningSeverity TWF 조건에 해당할 때의 심각도 (위험 구간 진입 경고)
 */
@Validated
@ConfigurationProperties("engine.rule")
public record RuleEngineProperties(
        @DefaultValue("90") @Positive @DecimalMax("100") double failureSeverity,
        @DefaultValue("40") @Positive @DecimalMax("100") double warningSeverity
) {
}
