package io.github.min27.factoryanomaly.decision;

import io.github.min27.factoryanomaly.reading.SensorReading;
import io.github.min27.factoryanomaly.state.SensorState;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 측정값을 판정 엔진에 넘기고, 응답시간을 재서 엔진별 판정 결과를 저장한다. */
@Slf4j
@Service
public class DecisionService {

    private final List<DecisionEngine> engines;
    private final DecisionRepository decisionRepository;
    private final Clock clock;

    public DecisionService(List<DecisionEngine> engines, DecisionRepository decisionRepository, Clock clock) {
        this.engines = engines.stream().sorted(Comparator.comparing(DecisionEngine::name)).toList();
        this.decisionRepository = decisionRepository;
        this.clock = clock;
    }

    /**
     * 이미 저장된 측정값을 엔진마다 판정하고 저장한다. 판정 하나하나가 별도 트랜잭션(save)이다.
     * 엔진이 예외를 던지면 그 엔진의 판정만 빠지고 나머지는 계속한다 (D-013).
     */
    public List<Decision> decide(SensorReading reading, SensorState state) {
        List<Decision> saved = new ArrayList<>(engines.size());
        for (DecisionEngine engine : engines) {
            DecisionResult result;
            long start = System.nanoTime();
            try {
                result = engine.decide(state);
            } catch (RuntimeException e) {
                log.warn("Engine '{}' failed for reading {}", engine.name(), reading.getId(), e);
                continue;
            }
            // 응답시간은 엔진 밖에서 같은 구간(decide 호출 전후)으로 잰다 (D-011)
            long latencyUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start);

            saved.add(decisionRepository.save(Decision.builder()
                    .reading(reading)
                    .engine(engine.name())
                    .anomaly(result.anomaly())
                    .severity(result.severity())
                    .category(result.category())
                    .confidence(result.confidence())
                    .latencyUs(latencyUs)
                    .decidedAt(Instant.now(clock))
                    .build()));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Decision> findByReading(long readingId) {
        return decisionRepository.findByReadingIdOrderByEngine(readingId);
    }
}
