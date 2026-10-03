package io.github.min27.factoryanomaly.decision;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.min27.factoryanomaly.decision.jev.JevEngine;
import io.github.min27.factoryanomaly.decision.jev.JevEngineProperties;
import io.github.min27.factoryanomaly.decision.ml.MlEngine;
import io.github.min27.factoryanomaly.decision.ml.MlEngineProperties;
import io.github.min27.factoryanomaly.decision.rule.RuleEngine;
import io.github.min27.factoryanomaly.decision.rule.RuleEngineProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

/** engine.active / engine.primary 설정에 따라 어떤 엔진이 등록되는지, 잘못된 설정이 시작 시점에 막히는지 확인한다 (D-019). */
class EngineSelectionTest {

    @EnableConfigurationProperties({EngineProperties.class, RuleEngineProperties.class,
            MlEngineProperties.class, JevEngineProperties.class})
    @Import({RuleEngine.class, MlEngine.class, JevEngine.class, ActiveEnginesVerifier.class})
    static class Config {
        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    private static List<String> engineNames(ApplicationContext ctx) {
        return ctx.getBeansOfType(DecisionEngine.class).values().stream().map(DecisionEngine::name).sorted().toList();
    }

    @Test
    void 설정이_없으면_rule만_실행한다() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(engineNames(ctx)).containsExactly("rule");
            assertThat(ctx.getBean(EngineProperties.class).primary()).isEqualTo("rule");
        });
    }

    @ParameterizedTest(name = "active={0}")
    @CsvSource(delimiter = '|', value = {
            "rule     | rule | rule",
            "ml       | ml   | ml",
            "rule, ml | rule | ml, rule",
            "ml, rule | ml   | ml, rule",
    })
    void active에_있는_엔진만_등록한다(String active, String primary, String expected) {
        runner.withPropertyValues("engine.active=" + active, "engine.primary=" + primary)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(String.join(", ", engineNames(ctx))).isEqualTo(expected);
                });
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "알 수 없는 엔진         | engine.active=rule,gpt  | engine.primary=rule | unknown engine",
            "중복                   | engine.active=rule,rule | engine.primary=rule | duplicate engine",
            "primary가 active에 없음 | engine.active=rule      | engine.primary=ml   | must be one of engine.active",
            "빈 목록                 | engine.active=          | engine.primary=rule | at least one engine",
    })
    void 잘못된_설정은_시작_시점에_거부한다(String name, String active, String primary, String message) {
        runner.withPropertyValues(active, primary)
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().hasMessageContaining(message));
    }

    @Test
    void 알려진_엔진_이름은_각_엔진의_name과_같다() {
        RuleEngine rule = new RuleEngine(new RuleEngineProperties(90, 40));
        MlEngine ml = new MlEngine(RestClient.builder(), new MlEngineProperties("http://localhost:8000",
                Duration.ofMillis(300), Duration.ofSeconds(1)));

        assertThat(EngineProperties.KNOWN_ENGINES).contains(rule.name(), ml.name(), "jev");
    }
}
