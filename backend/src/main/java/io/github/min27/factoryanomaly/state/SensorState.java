package io.github.min27.factoryanomaly.state;

import io.github.min27.factoryanomaly.reading.ProductType;

/**
 * 판정 엔진의 공통 입력. 원본 센서값과 파생변수를 함께 담는다.
 *
 * @param tempDiff   공정온도 − 대기온도 [K]. 열이 빠져나갈 여유 → HDF
 * @param power      기계적 동력 [W]. 너무 작거나 크면 → PWF
 * @param wearTorque 공구마모 × 토크 [min·Nm]. 마모된 공구에 걸리는 부하 → OSF
 */
public record SensorState(
        SensorValues values,
        double tempDiff,
        double power,
        double wearTorque
) {
    public ProductType productType() {
        return values.productType();
    }
}
