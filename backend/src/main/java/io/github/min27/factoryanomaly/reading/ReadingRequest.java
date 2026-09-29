package io.github.min27.factoryanomaly.reading;

import io.github.min27.factoryanomaly.state.SensorValues;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 센서 측정값 수신 요청.
 *
 * <p>검증은 물리적으로 불가능한 값만 거른다 (D-009). 비정상적이지만 가능한 값을 판단하는 것은 판정 엔진의 몫이다.
 * 숫자 필드는 누락을 잡아내기 위해 래퍼 타입을 쓴다 (기본형이면 누락 시 0이 들어간다).
 *
 * @param labels    정답 라벨. 시뮬레이션 / 벤치마크 전용이며 실제 설비는 보내지 않는다.
 * @param sourceUdi 원본 CSV의 UDI. 시뮬레이션 / 벤치마크 전용.
 */
public record ReadingRequest(
        @NotBlank String equipmentCode,
        @NotNull ProductType productType,
        @NotNull @DecimalMin("200") @DecimalMax("500") Double airTemp,
        @NotNull @DecimalMin("200") @DecimalMax("500") Double processTemp,
        @NotNull @PositiveOrZero Integer rotSpeed,
        @NotNull @PositiveOrZero Double torque,
        @NotNull @PositiveOrZero Integer toolWear,
        FailureLabels labels,
        @Min(1) Integer sourceUdi
) {
    public SensorValues toSensorValues() {
        return new SensorValues(productType, airTemp, processTemp, rotSpeed, torque, toolWear);
    }
}
