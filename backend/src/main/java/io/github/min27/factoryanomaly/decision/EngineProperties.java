package io.github.min27.factoryanomaly.decision;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 어떤 엔진을 실행할지 정한다 (D-019).
 *
 * @param active  실행할 엔진 이름 목록. 여기 있는 엔진만 Bean으로 등록되고, 측정값마다 이름 순서로 순차 실행된다.
 *                벤치마크는 비교할 엔진을 모두 나열한다 (예: rule, ml)
 * @param primary 알람·대시보드의 기준 엔진 (Phase 4에서 사용). active에 포함되어야 한다
 */
@ConfigurationProperties("engine")
public record EngineProperties(
        @DefaultValue("rule") List<String> active,
        @DefaultValue("rule") String primary
) {
    /** 등록 가능한 엔진 이름. 각 엔진의 {@link DecisionEngine#name()}과 같다. */
    public static final Set<String> KNOWN_ENGINES = Set.of("rule", "ml", "jev");

    /** 잘못된 설정은 바인딩 단계에서 막아 애플리케이션이 시작되지 않게 한다. */
    public EngineProperties {
        if (active == null || active.isEmpty()) {
            throw new IllegalArgumentException("engine.active must list at least one engine " + KNOWN_ENGINES);
        }
        Set<String> seen = new HashSet<>();
        for (String name : active) {
            if (!KNOWN_ENGINES.contains(name)) {
                throw new IllegalArgumentException("unknown engine in engine.active: '" + name + "', known: " + KNOWN_ENGINES);
            }
            if (!seen.add(name)) {
                throw new IllegalArgumentException("duplicate engine in engine.active: '" + name + "'");
            }
        }
        if (!active.contains(primary)) {
            throw new IllegalArgumentException("engine.primary '" + primary + "' must be one of engine.active " + active);
        }
        active = List.copyOf(active);
    }
}
