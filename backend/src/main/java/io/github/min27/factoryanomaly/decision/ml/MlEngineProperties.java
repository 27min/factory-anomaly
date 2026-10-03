package io.github.min27.factoryanomaly.decision.ml;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * ml-server 호출 설정 (D-017). 엔진을 켜고 끄는 것은 engine.active로 한다 (D-019).
 *
 * @param baseUrl        ml-server 주소
 * @param connectTimeout 연결 제한 시간
 * @param readTimeout    응답 대기 제한 시간. 판정 자체는 약 3ms라 장애 시 수집 API의 지연 상한 역할을 한다
 */
@Validated
@ConfigurationProperties("engine.ml")
public record MlEngineProperties(
        @DefaultValue("http://localhost:8000") @NotBlank String baseUrl,
        @DefaultValue("300ms") @NotNull Duration connectTimeout,
        @DefaultValue("1s") @NotNull Duration readTimeout
) {
}
