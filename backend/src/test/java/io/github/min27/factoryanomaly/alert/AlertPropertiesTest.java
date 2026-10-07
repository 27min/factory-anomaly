package io.github.min27.factoryanomaly.alert;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AlertPropertiesTest {

    @EnableConfigurationProperties(AlertProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void 설정이_없으면_기본값_50() {
        runner.run(ctx -> assertThat(ctx.getBean(AlertProperties.class)).isEqualTo(new AlertProperties(50)));
    }

    @Test
    void 설정값을_읽는다() {
        runner.withPropertyValues("alert.min-severity=30")
                .run(ctx -> assertThat(ctx.getBean(AlertProperties.class)).isEqualTo(new AlertProperties(30)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-10", "100.1"})
    void 범위를_벗어난_하한은_시작_시점에_거부한다(String value) {
        runner.withPropertyValues("alert.min-severity=" + value).run(ctx -> assertThat(ctx).hasFailed());
    }
}
