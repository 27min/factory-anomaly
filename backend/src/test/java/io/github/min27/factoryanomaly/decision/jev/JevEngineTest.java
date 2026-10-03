package io.github.min27.factoryanomaly.decision.jev;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

class JevEngineTest {

    @EnableConfigurationProperties(JevEngineProperties.class)
    @Import(JevEngine.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void 기본값으로는_등록되지_않는다() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(JevEngine.class);
        });
    }

    @Test
    void 키_없이_active에_넣으면_시작이_실패한다() {
        runner.withPropertyValues("engine.active=jev")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("JEV_API_KEY is empty"));
    }

    @Test
    void 키가_있어도_구현_전이라_시작이_실패한다() {
        runner.withPropertyValues("engine.active=jev", "engine.jev.api-key=secret-key-123")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause()
                        .hasMessageContaining("not implemented yet")
                        .hasMessageNotContaining("secret-key-123"));
    }

    @Test
    void 설정을_출력해도_키가_보이지_않는다() {
        JevEngineProperties properties = new JevEngineProperties(null, "secret-key-123", null, null);

        assertThat(properties.toString()).doesNotContain("secret-key-123").contains("apiKey=****");
    }
}
