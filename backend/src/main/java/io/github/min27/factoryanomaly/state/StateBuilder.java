package io.github.min27.factoryanomaly.state;

import org.springframework.stereotype.Component;

/**
 * 원본 센서값에서 물리적 의미가 있는 파생변수를 계산한다.
 *
 * <p>계산 결과를 반올림하지 않는다. 데이터셋의 고장 라벨이 부동소수점 계산 결과 그대로 매겨져 있어,
 * 반올림하면 경계값에 걸친 고장을 놓친다 (예: UDI 3237, 309.4 − 300.8 = 8.599999999999966 < 8.6 → HDF).
 */
@Component
public class StateBuilder {

    public SensorState build(SensorValues v) {
        double tempDiff = v.processTemp() - v.airTemp();
        // P = T·ω, ω[rad/s] = rpm × 2π / 60
        double power = v.torque() * v.rotSpeed() * 2 * Math.PI / 60;
        double wearTorque = v.toolWear() * v.torque();
        return new SensorState(v, tempDiff, power, wearTorque);
    }
}
