package io.github.min27.factoryanomaly.decision.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RuleEnginePropertiesTest {

    @EnableConfigurationProperties(RuleEngineProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void 설정이_없으면_기본값_90_40() {
        runner.run(ctx -> assertThat(ctx.getBean(RuleEngineProperties.class))
                .isEqualTo(new RuleEngineProperties(90, 40)));
    }

    @Test
    void 설정값을_읽는다() {
        runner.withPropertyValues("engine.rule.failure-severity=80", "engine.rule.warning-severity=30")
                .run(ctx -> assertThat(ctx.getBean(RuleEngineProperties.class))
                        .isEqualTo(new RuleEngineProperties(80, 30)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-10", "100.1"})
    void 범위를_벗어난_심각도는_시작_시점에_거부한다(String value) {
        runner.withPropertyValues("engine.rule.failure-severity=" + value)
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
