package io.github.min27.factoryanomaly.decision.rule;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.min27.factoryanomaly.decision.DecisionResult;
import io.github.min27.factoryanomaly.decision.FailureType;
import io.github.min27.factoryanomaly.reading.ProductType;
import io.github.min27.factoryanomaly.state.SensorState;
import io.github.min27.factoryanomaly.state.SensorValues;
import io.github.min27.factoryanomaly.state.StateBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RuleEngineTest {

    private final RuleEngine engine = new RuleEngine(new RuleEngineProperties(90, 40));

    /**
     * 파생변수를 직접 지정해 판정 조건만 검증한다. 엔진은 파생변수와 rotSpeed·toolWear·productType만 보므로
     * 원본 온도·토크는 아무 값이어도 된다.
     */
    private static SensorState state(ProductType type, double tempDiff, int rotSpeed,
                                     double power, double wearTorque, int toolWear) {
        SensorValues v = new SensorValues(type, 300, 300 + tempDiff, rotSpeed, 40, toolWear);
        return new SensorState(v, tempDiff, power, wearTorque);
    }

    /** ml-server/notebooks/03_rule_engine.ipynb의 경계값 케이스와 같다. 기본값: L, 10 K, 1500 rpm, 6000 W, 5000, 100 min */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            # case                       | type | tempDiff | rotSpeed | power  | wearTorque | toolWear | category | severity
            정상                          | L    | 10.0     | 1500     | 6000   | 5000       | 100      | NORMAL   | 0
            HDF 경계: tempDiff=8.6         | L    | 8.6      | 1300     | 6000   | 5000       | 100      | NORMAL   | 0
            HDF: tempDiff=8.59            | L    | 8.59     | 1300     | 6000   | 5000       | 100      | HDF      | 90
            HDF 경계: rotSpeed=1380        | L    | 8.0      | 1380     | 6000   | 5000       | 100      | NORMAL   | 0
            HDF: rotSpeed=1379            | L    | 8.0      | 1379     | 6000   | 5000       | 100      | HDF      | 90
            PWF 경계: power=3500           | L    | 10.0     | 1500     | 3500   | 5000       | 100      | NORMAL   | 0
            PWF: power=3499.9             | L    | 10.0     | 1500     | 3499.9 | 5000       | 100      | PWF      | 90
            PWF 경계: power=9000           | L    | 10.0     | 1500     | 9000   | 5000       | 100      | NORMAL   | 0
            PWF: power=9000.1             | L    | 10.0     | 1500     | 9000.1 | 5000       | 100      | PWF      | 90
            OSF 경계: L 11000              | L    | 10.0     | 1500     | 6000   | 11000      | 100      | NORMAL   | 0
            OSF: L 11000.1                | L    | 10.0     | 1500     | 6000   | 11000.1    | 100      | OSF      | 90
            OSF: M 11500 (M 한계 미만)      | M    | 10.0     | 1500     | 6000   | 11500      | 100      | NORMAL   | 0
            OSF: H 13000.1                | H    | 10.0     | 1500     | 6000   | 13000.1    | 100      | OSF      | 90
            TWF: toolWear=199             | L    | 10.0     | 1500     | 6000   | 5000       | 199      | NORMAL   | 0
            TWF 경계: toolWear=200         | L    | 10.0     | 1500     | 6000   | 5000       | 200      | TWF      | 40
            """)
    void 공개된_고장_조건의_경계값(String name, ProductType type, double tempDiff, int rotSpeed, double power,
                         double wearTorque, int toolWear, FailureType category, double severity) {
        DecisionResult r = engine.decide(state(type, tempDiff, rotSpeed, power, wearTorque, toolWear));

        assertThat(r.category()).isEqualTo(category);
        assertThat(r.severity()).isEqualTo(severity);
        assertThat(r.anomaly()).isEqualTo(category != FailureType.NORMAL);
        assertThat(r.confidence()).isEqualTo(1.0);
    }

    @ParameterizedTest(name = "{0} 한계 {1}")
    @CsvSource({"L, 11000", "M, 12000", "H, 13000"})
    void OSF_한계는_제품_타입별로_다르다(ProductType type, double limit) {
        assertThat(engine.decide(state(type, 10, 1500, 6000, limit, 100)).category()).isEqualTo(FailureType.NORMAL);
        assertThat(engine.decide(state(type, 10, 1500, 6000, Math.nextUp(limit), 100)).category()).isEqualTo(FailureType.OSF);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            # case               | tempDiff | rotSpeed | power  | wearTorque | toolWear | category
            HDF + PWF            | 8.0      | 1300     | 3000   | 5000       | 100      | HDF
            PWF + OSF            | 10.0     | 1500     | 9500   | 12000      | 100      | PWF
            OSF + TWF (90 > 40)  | 10.0     | 1500     | 6000   | 12000      | 210      | OSF
            TWF + HDF (90 > 40)  | 8.0      | 1300     | 6000   | 5000       | 210      | HDF
            네 조건 모두           | 8.0      | 1300     | 3000   | 12000      | 210      | HDF
            """)
    void 여러_조건이_맞으면_심각도가_가장_높은_유형_같으면_HDF_PWF_OSF_TWF_순(
            String name, double tempDiff, int rotSpeed, double power, double wearTorque, int toolWear,
            FailureType category) {
        DecisionResult r = engine.decide(state(ProductType.L, tempDiff, rotSpeed, power, wearTorque, toolWear));

        assertThat(r.category()).isEqualTo(category);
        assertThat(r.severity()).isEqualTo(90);
    }

    @Test
    void 심각도는_설정값을_따른다() {
        RuleEngine custom = new RuleEngine(new RuleEngineProperties(80, 30));

        assertThat(custom.decide(state(ProductType.L, 8.0, 1300, 6000, 5000, 100)).severity()).isEqualTo(80);
        assertThat(custom.decide(state(ProductType.L, 10, 1500, 6000, 5000, 200)).severity()).isEqualTo(30);
    }

    @Test
    void 부동소수점_경계의_실제_고장을_잡는다() {
        // UDI 3237: 309.4 − 300.8 = 8.599999999999966 < 8.6, 실제 라벨 HDF
        SensorState s = new StateBuilder().build(new SensorValues(ProductType.M, 300.8, 309.4, 1342, 62.4, 113));

        assertThat(engine.decide(s).category()).isEqualTo(FailureType.HDF);
    }

    /**
     * 전체 10,000행을 판정해 노트북(03_rule_engine.ipynb)의 혼동행렬과 비교한다.
     * Python 프로토타입과 Java 구현이 같은 결과를 내는지 확인한다. 정답은 machineFailure (D-003).
     */
    @Test
    void 전체_데이터에서_노트북과_같은_성능이_나온다() throws IOException {
        StateBuilder builder = new StateBuilder();
        List<String> rows = Files.readAllLines(Path.of("../data/ai4i2020.csv"));
        int[] warn = new int[4];   // A. 경고 포함 (severity ≥ 40): TP, FP, FN, TN
        int[] alarm = new int[4];  // B. 알람만 (severity ≥ 70)

        for (String row : rows.subList(1, rows.size())) {
            // UDI, Product ID, Type, Air, Process, Rot, Torque, Tool wear, Machine failure, ...
            String[] c = row.split(",");
            SensorState s = builder.build(new SensorValues(ProductType.valueOf(c[2]),
                    Double.parseDouble(c[3]), Double.parseDouble(c[4]),
                    Integer.parseInt(c[5]), Double.parseDouble(c[6]), Integer.parseInt(c[7])));
            boolean actual = c[8].equals("1");
            double severity = engine.decide(s).severity();
            count(warn, actual, severity >= 40);
            count(alarm, actual, severity >= 70);
        }

        assertThat(rows).hasSize(10_001);
        assertThat(warn).containsExactly(330, 678, 9, 8983);
        assertThat(alarm).containsExactly(287, 0, 52, 9661);
    }

    private static void count(int[] matrix, boolean actual, boolean predicted) {
        matrix[actual ? (predicted ? 0 : 2) : (predicted ? 1 : 3)]++;
    }
}
