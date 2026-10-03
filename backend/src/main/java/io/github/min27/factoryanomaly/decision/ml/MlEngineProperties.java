package io.github.min27.factoryanomaly.decision.ml;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * ml-server 호출 설정 (D-017).
 *
 * @param enabled        true일 때만 MlEngine Bean을 등록한다. 엔진 선택은 Phase 3 후반에 정식으로 설계한다
 * @param baseUrl        ml-server 주소
 * @param connectTimeout 연결 제한 시간
 * @param readTimeout    응답 대기 제한 시간. 판정 자체는 약 3ms라 장애 시 수집 API의 지연 상한 역할을 한다
 */
@Validated
@ConfigurationProperties("engine.ml")
public record MlEngineProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("http://localhost:8000") @NotBlank String baseUrl,
        @DefaultValue("300ms") @NotNull Duration connectTimeout,
        @DefaultValue("1s") @NotNull Duration readTimeout
) {
}
