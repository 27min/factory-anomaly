package io.github.min27.factoryanomaly.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

import io.github.min27.factoryanomaly.reading.ProductType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StateBuilderTest {

    private final StateBuilder builder = new StateBuilder();

    /**
     * 기대값은 ml-server/notebooks/02_features.ipynb와 같은 식(pandas / numpy)으로 계산한 값이다.
     * Python과 Java가 같은 파생변수를 만드는지 확인한다.
     */
    @ParameterizedTest(name = "UDI {0}")
    @CsvSource({
            // udi, type, airTemp, processTemp, rotSpeed, torque, toolWear, tempDiff, power, wearTorque
            "1,    M, 298.1, 308.6, 1551, 42.8,   0, 10.5,               6951.5905601573495,  0.0",     // 정상
            "3237, M, 300.8, 309.4, 1342, 62.4, 113, 8.599999999999966,  8769.316069524406,   7051.2",  // HDF
            "51,   L, 298.9, 309.1, 2861,  4.6, 143, 10.200000000000045, 1378.1748092277944,  657.8",   // PWF (저동력)
            "70,   L, 298.9, 309.0, 1410, 65.7, 191, 10.100000000000023, 9700.923955019922,   12548.7", // PWF + OSF
            "78,   L, 298.8, 308.9, 1455, 41.3, 208, 10.099999999999966, 6292.767164773034,   8590.4",  // TWF
            "1785, L, 298.3, 308.0, 2886,  3.8,  57, 9.699999999999989,  1148.4406104462846,  216.6",   // 최저 동력
            "9085, L, 297.2, 307.9, 1326, 75.4, 172, 10.699999999999989, 10469.923004765633,  12968.8",  // 최고 동력
    })
    void 파생변수가_노트북_계산값과_일치한다(int udi, ProductType type, double airTemp, double processTemp,
                                 int rotSpeed, double torque, int toolWear,
                                 double tempDiff, double power, double wearTorque) {
        SensorState s = builder.build(new SensorValues(type, airTemp, processTemp, rotSpeed, torque, toolWear));

        assertThat(s.tempDiff()).isCloseTo(tempDiff, within(1e-9));
        assertThat(s.power()).isCloseTo(power, within(1e-9));
        assertThat(s.wearTorque()).isCloseTo(wearTorque, within(1e-9));
        assertThat(s.productType()).isEqualTo(type);
    }

    @Test
    void 온도차를_반올림하지_않아_경계값_고장을_놓치지_않는다() {
        // UDI 3237: 10진수로는 정확히 8.6이지만 부동소수점 결과는 8.6보다 작고, 실제 라벨도 HDF다
        SensorState s = builder.build(new SensorValues(ProductType.M, 300.8, 309.4, 1342, 62.4, 113));

        assertThat(s.tempDiff()).isLessThan(8.6);
    }

    @Test
    void 동력은_토크와_각속도의_곱이다() {
        // 1 Nm, 60 rpm(= 1 rev/s = 2π rad/s) → 2π W
        SensorState s = builder.build(new SensorValues(ProductType.L, 300, 310, 60, 1.0, 0));

        assertThat(s.power()).isCloseTo(2 * Math.PI, within(1e-12));
    }

    @Test
    void 새_공구는_마모부하가_0이다() {
        SensorState s = builder.build(new SensorValues(ProductType.H, 300, 310, 1500, 76.6, 0));

        assertThat(s.wearTorque()).isZero();
    }

    @Test
    void 원본값을_그대로_보존한다() {
        SensorValues v = new SensorValues(ProductType.M, 298.1, 308.6, 1551, 42.8, 0);

        assertThat(builder.build(v).values()).isEqualTo(v);
    }

    @Test
    void 제품타입은_필수다() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SensorValues(null, 300, 310, 1500, 40, 100))
                .withMessage("productType");
    }
}
