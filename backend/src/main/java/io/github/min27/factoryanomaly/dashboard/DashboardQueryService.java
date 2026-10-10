package io.github.min27.factoryanomaly.dashboard;

import io.github.min27.factoryanomaly.alert.AlertRepository;
import io.github.min27.factoryanomaly.alert.AlertStatus;
import io.github.min27.factoryanomaly.decision.Decision;
import io.github.min27.factoryanomaly.decision.DecisionFailure;
import io.github.min27.factoryanomaly.decision.DecisionFailureRepository;
import io.github.min27.factoryanomaly.decision.DecisionRepository;
import io.github.min27.factoryanomaly.decision.DecisionStat;
import io.github.min27.factoryanomaly.decision.EngineProperties;
import io.github.min27.factoryanomaly.decision.FailureCount;
import io.github.min27.factoryanomaly.equipment.Equipment;
import io.github.min27.factoryanomaly.equipment.EquipmentRepository;
import io.github.min27.factoryanomaly.reading.SensorReading;
import io.github.min27.factoryanomaly.reading.SensorReadingRepository;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 대시보드 화면용 조회. 상태를 바꾸지 않는다 (D-022). */
@Service
@Transactional(readOnly = true)
public class DashboardQueryService {

    private final EquipmentRepository equipmentRepository;
    private final SensorReadingRepository readingRepository;
    private final DecisionRepository decisionRepository;
    private final DecisionFailureRepository failureRepository;
    private final AlertRepository alertRepository;
    private final DashboardProperties properties;
    private final List<String> engines;
    private final String primaryEngine;

    public DashboardQueryService(EquipmentRepository equipmentRepository, SensorReadingRepository readingRepository,
                                 DecisionRepository decisionRepository, DecisionFailureRepository failureRepository,
                                 AlertRepository alertRepository, DashboardProperties properties,
                                 EngineProperties engineProperties) {
        this.equipmentRepository = equipmentRepository;
        this.readingRepository = readingRepository;
        this.decisionRepository = decisionRepository;
        this.failureRepository = failureRepository;
        this.alertRepository = alertRepository;
        this.properties = properties;
        this.engines = engineProperties.active().stream().sorted().toList();
        this.primaryEngine = engineProperties.primary();
    }

    public List<AlertView> unresolvedAlerts() {
        ZoneId zone = properties.timeZone();
        return alertRepository.findByStatusInOrderByLastOccurredAtDesc(AlertStatus.UNRESOLVED).stream()
                .map(a -> AlertView.from(a, zone))
                .toList();
    }

    /** 설비마다 최신 측정값 1건과 그 판정을 읽는다. 설비 수(5대)만큼 인덱스 조회가 반복된다. */
    public List<EquipmentStatusView> equipmentStatuses() {
        ZoneId zone = properties.timeZone();
        Map<String, List<AlertView>> alertsByEquipment = unresolvedAlerts().stream()
                .collect(Collectors.groupingBy(AlertView::equipmentCode));

        List<EquipmentStatusView> result = new ArrayList<>();
        for (Equipment eq : equipmentRepository.findAll(Sort.by("code"))) {
            List<AlertView> alerts = alertsByEquipment.getOrDefault(eq.getCode(), List.of());
            Optional<SensorReading> latest = readingRepository.findFirstByEquipmentIdOrderByReceivedAtDescIdDesc(eq.getId());
            Optional<Decision> decision = latest.flatMap(r -> decisionRepository.findByReadingIdAndEngine(r.getId(), primaryEngine));

            result.add(new EquipmentStatusView(
                    eq.getCode(), eq.getName(),
                    state(!alerts.isEmpty(), latest.isPresent(), decision),
                    latest.map(r -> r.getReceivedAt().atZone(zone)).orElse(null),
                    decision.map(Decision::getCategory).orElse(null),
                    decision.map(Decision::getSeverity).orElse(null),
                    alerts));
        }
        return result;
    }

    static EquipmentState state(boolean hasUnresolvedAlert, boolean hasReading, Optional<Decision> primaryDecision) {
        if (hasUnresolvedAlert) {
            return EquipmentState.ALERT;
        }
        if (!hasReading) {
            return EquipmentState.NO_DATA;
        }
        return primaryDecision
                .map(d -> d.isAnomaly() ? EquipmentState.WARNING : EquipmentState.NORMAL)
                .orElse(EquipmentState.UNKNOWN);
    }

    public EngineComparison engineComparison() {
        List<SensorReading> recent = readingRepository.findByOrderByIdDesc(Limit.of(properties.recentRows()));
        List<Long> ids = recent.stream().map(SensorReading::getId).toList();

        Map<Long, Map<String, EngineComparison.Cell>> cells = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Decision d : decisionRepository.findByReadingIdIn(ids)) {
                cells.computeIfAbsent(d.getReading().getId(), k -> new HashMap<>()).put(d.getEngine(),
                        new EngineComparison.Cell(d.isAnomaly(), d.getCategory(), d.getSeverity(), d.getLatencyUs(), null));
            }
            for (DecisionFailure f : failureRepository.findByReadingIdIn(ids)) {
                cells.computeIfAbsent(f.getReading().getId(), k -> new HashMap<>()).put(f.getEngine(),
                        new EngineComparison.Cell(false, null, 0, f.getLatencyUs(), f.getReason().name()));
            }
        }

        ZoneId zone = properties.timeZone();
        List<EngineComparison.Row> rows = recent.stream().map(r -> {
            Map<String, EngineComparison.Cell> byEngine = cells.getOrDefault(r.getId(), Map.of());
            long anomalyVotes = byEngine.values().stream().filter(c -> !c.failed()).map(c -> c.anomaly()).distinct().count();
            return new EngineComparison.Row(r.getId(), r.getEquipment().getCode(), r.getReceivedAt().atZone(zone),
                    r.getLabels() == null ? null : r.getLabels().machineFailure(),
                    byEngine, anomalyVotes > 1);
        }).toList();

        List<Long> windowIds = readingRepository.findRecentIds(Limit.of(properties.summaryWindow()));
        List<EngineComparison.Summary> summaries = windowIds.isEmpty()
                ? engines.stream().map(e -> new EngineComparison.Summary(e, 0, 0, 0, null, null)).toList()
                : summarize(windowIds.get(windowIds.size() - 1));

        return new EngineComparison(engines, summaries, rows, windowIds.size());
    }

    /** 최근 구간의 판정을 엔진별로 모아 건수와 응답시간 백분위를 계산한다. 구간이 작아(기본 500건) 애플리케이션에서 계산한다. */
    private List<EngineComparison.Summary> summarize(long minReadingId) {
        Map<String, List<DecisionStat>> statsByEngine = decisionRepository.findStatsSince(minReadingId).stream()
                .collect(Collectors.groupingBy(DecisionStat::engine));
        Map<String, Long> failures = failureRepository.countByEngineSince(minReadingId).stream()
                .collect(Collectors.toMap(FailureCount::engine, FailureCount::count));

        return engines.stream().map(engine -> {
            List<DecisionStat> stats = statsByEngine.getOrDefault(engine, List.of());
            List<Long> latencies = stats.stream().map(DecisionStat::latencyUs).sorted().toList();
            return new EngineComparison.Summary(engine, stats.size(),
                    (int) stats.stream().filter(DecisionStat::anomaly).count(),
                    failures.getOrDefault(engine, 0L),
                    Latency.percentile(latencies, 50), Latency.percentile(latencies, 95));
        }).toList();
    }
}
