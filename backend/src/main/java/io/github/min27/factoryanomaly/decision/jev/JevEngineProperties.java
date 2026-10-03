package io.github.min27.factoryanomaly.decision.jev;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Jev(TypeSafe AI) 호출 설정. API 스펙과 키를 확보하기 전이라 값은 자리만 잡아둔다 (D-018).
 *
 * @param enabled        true면 JevEngine을 등록한다. 현재는 켜면 애플리케이션 시작이 실패한다
 * @param baseUrl        Jev API 주소 (미정)
 * @param apiKey         레포 루트 .env의 JEV_API_KEY
 * @param connectTimeout 외부 API라 ml-server보다 넉넉하게 둔다 (값은 연동 시 다시 정함)
 * @param readTimeout    〃
 */
@ConfigurationProperties("engine.jev")
public record JevEngineProperties(
        @DefaultValue("false") boolean enabled,
        String baseUrl,
        String apiKey,
        @DefaultValue("1s") Duration connectTimeout,
        @DefaultValue("3s") Duration readTimeout
) {
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 로그나 예외 메시지에 키가 찍히지 않도록 가린다. */
    @Override
    public String toString() {
        return "JevEngineProperties[enabled=" + enabled + ", baseUrl=" + baseUrl
                + ", apiKey=" + (hasApiKey() ? "****" : "(none)")
                + ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout + "]";
    }
}
