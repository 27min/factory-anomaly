package io.github.min27.factoryanomaly.alert;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 알람 조건 (D-021).
 *
 * @param minSeverity 이상 판정 중 알람을 낼 최소 심각도. 기본 50은 룰 엔진의 TWF 경고(40)를 알람에서 빼고 고장 조건(90)만 남긴다.
 *                    ML 엔진은 이상 판정이면 심각도가 임계값(95.4) 이상이라 사실상 영향이 없다
 */
@Validated
@ConfigurationProperties("alert")
public record AlertProperties(
        @DefaultValue("50") @Positive @DecimalMax("100") double minSeverity
) {
}
