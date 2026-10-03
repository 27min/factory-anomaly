package io.github.min27.factoryanomaly.decision;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 시작 시점에 engine.active의 엔진이 모두 Bean으로 등록되었는지 확인한다 (D-019).
 * 등록 조건({@link ConditionalOnActiveEngine})이 어긋나 일부 엔진이 조용히 빠진 채 도는 것을 막는다.
 */
@Slf4j
@Component
public class ActiveEnginesVerifier {

    public ActiveEnginesVerifier(List<DecisionEngine> engines, EngineProperties properties) {
        Set<String> registered = engines.stream().map(DecisionEngine::name).collect(Collectors.toSet());
        List<String> missing = properties.active().stream().filter(name -> !registered.contains(name)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("engines in engine.active are not registered: " + missing);
        }
        log.info("Active decision engines: {} (primary: {})", properties.active(), properties.primary());
    }
}
